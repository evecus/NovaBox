package com.mobile.novabox.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 纯 Java ID3v2 元数据读取器。
 *
 * 不依赖 MediaMetadataRetriever（该方式在盒子上读网络流很不稳定），
 * 改为用 OkHttp 只下载文件前 64KB（ID3 tag 通常 < 64KB），
 * 然后手动解析 ID3v2.3 / ID3v2.4 帧，提取：
 *   - TIT2  歌名
 *   - TPE1  歌手
 *   - TALB  专辑
 *   - APIC  封面图
 *   - USLT  非同步歌词（LRC 格式）
 */
public class AudioMetadataLoader {

    private static final String TAG = "AudioMetaLoader";
    private static final int FETCH_BYTES = 128 * 1024; // 128KB，绝大多数 tag 够用

    public static class Metadata {
        public String title;
        public String artist;
        public String album;
        public Bitmap cover;
        public String lyrics; // LRC 文本
    }

    public interface Callback {
        void onLoaded(Metadata meta);
        void onError(String msg);
    }

    /**
     * 异步加载，结果回调在子线程，调用方自行 runOnUiThread。
     */
    public static void loadAsync(final String url,
                                 final Map<String, String> headers,
                                 final OkHttpClient client,
                                 final Callback callback) {
        new Thread(() -> {
            try {
                byte[] data = fetchHead(url, headers, client);
                if (data == null || data.length < 10) {
                    callback.onError("fetch failed");
                    return;
                }
                Metadata meta = parse(data);
                callback.onLoaded(meta);
            } catch (Exception e) {
                Log.w(TAG, "loadAsync error", e);
                callback.onError(e.getMessage());
            }
        }, "AudioMetaLoader").start();
    }

    // ─── HTTP 下载头部 ────────────────────────────────────────────────────────

    private static byte[] fetchHead(String url, Map<String, String> headers,
                                    OkHttpClient client) throws IOException {
        Request.Builder rb = new Request.Builder()
                .url(url)
                .addHeader("Range", "bytes=0-" + (FETCH_BYTES - 1));
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                rb.addHeader(e.getKey(), e.getValue());
            }
        }
        try (Response resp = client.newCall(rb.build()).execute()) {
            if (resp.body() == null) return null;
            return resp.body().bytes();
        }
    }

    // ─── ID3v2 解析 ───────────────────────────────────────────────────────────

    private static Metadata parse(byte[] data) {
        Metadata meta = new Metadata();
        if (data.length < 10) return meta;

        // 检查 ID3 魔数
        if (data[0] != 'I' || data[1] != 'D' || data[2] != '3') {
            Log.w(TAG, "No ID3 header found");
            return meta;
        }

        int version = data[3]; // 3 = ID3v2.3, 4 = ID3v2.4
        // byte[5] = flags，暂不处理 unsync/ext header

        // ID3v2 tag size（syncsafe integer，4 bytes）
        int tagSize = syncsafeInt(data, 6) + 10; // +10 = header itself
        int limit = Math.min(tagSize, data.length);

        int pos = 10;
        // 跳过扩展头（bit 6 of flags）
        if ((data[5] & 0x40) != 0) {
            int extSize = (version == 4) ? syncsafeInt(data, pos) : readInt(data, pos);
            pos += extSize;
        }

        while (pos + 10 <= limit) {
            String frameId = new String(data, pos, 4);
            if (frameId.charAt(0) < 'A' || frameId.charAt(0) > 'Z') break; // 填充区

            int frameSize = (version == 4)
                    ? syncsafeInt(data, pos + 4)
                    : readInt(data, pos + 4);
            // int frameFlags = ((data[pos+8] & 0xff) << 8) | (data[pos+9] & 0xff);
            pos += 10;

            if (frameSize <= 0 || pos + frameSize > data.length) break;

            byte[] payload = slice(data, pos, frameSize);
            pos += frameSize;

            switch (frameId) {
                case "TIT2": meta.title  = decodeText(payload); break;
                case "TPE1": meta.artist = decodeText(payload); break;
                case "TALB": meta.album  = decodeText(payload); break;
                case "APIC": meta.cover  = decodePicture(payload); break;
                case "USLT": {
                    String lrc = decodeLyrics(payload);
                    if (!TextUtils.isEmpty(lrc)) meta.lyrics = lrc;
                    break;
                }
            }
        }
        return meta;
    }

    // ─── 帧解码 ───────────────────────────────────────────────────────────────

    /** 解码文本帧（TIT2 / TPE1 / TALB 等） */
    private static String decodeText(byte[] payload) {
        if (payload.length < 1) return null;
        int enc = payload[0] & 0xff;
        Charset cs = charsetForEnc(enc);
        int start = 1;
        // 跳过 UTF-16 BOM
        if (enc == 1 && payload.length >= 3 &&
                ((payload[1] == (byte)0xFF && payload[2] == (byte)0xFE) ||
                 (payload[1] == (byte)0xFE && payload[2] == (byte)0xFF))) {
            start = 1; // BOM 会被 Charset 自动处理
        }
        return new String(payload, start, payload.length - start, cs).trim().replace("\0", "");
    }

    /** 解码 APIC（封面图） */
    private static Bitmap decodePicture(byte[] payload) {
        if (payload.length < 4) return null;
        int enc = payload[0] & 0xff;
        // 跳过 MIME type（以 0x00 结尾）
        int mimeEnd = 1;
        while (mimeEnd < payload.length && payload[mimeEnd] != 0) mimeEnd++;
        mimeEnd++; // skip null
        if (mimeEnd >= payload.length) return null;
        // picture type (1 byte)
        mimeEnd++;
        // description（以 null 或 null-null 结尾）
        int descEnd = mimeEnd;
        if (enc == 0 || enc == 3) {
            while (descEnd < payload.length && payload[descEnd] != 0) descEnd++;
            descEnd++; // skip null
        } else {
            // UTF-16：双字节 null
            while (descEnd + 1 < payload.length &&
                    !(payload[descEnd] == 0 && payload[descEnd + 1] == 0)) descEnd += 2;
            descEnd += 2;
        }
        if (descEnd >= payload.length) return null;
        int imgLen = payload.length - descEnd;
        try {
            return BitmapFactory.decodeByteArray(payload, descEnd, imgLen);
        } catch (Exception e) {
            return null;
        }
    }

    /** 解码 USLT（非同步歌词） */
    private static String decodeLyrics(byte[] payload) {
        if (payload.length < 5) return null;
        int enc = payload[0] & 0xff;
        // lang: payload[1..3]
        // desc: payload[4..] 以 null 结尾
        int descStart = 4;
        int descEnd = descStart;
        Charset cs = charsetForEnc(enc);
        if (enc == 0 || enc == 3) {
            while (descEnd < payload.length && payload[descEnd] != 0) descEnd++;
            descEnd++; // skip null
        } else {
            while (descEnd + 1 < payload.length &&
                    !(payload[descEnd] == 0 && payload[descEnd + 1] == 0)) descEnd += 2;
            descEnd += 2;
        }
        if (descEnd >= payload.length) return null;
        return new String(payload, descEnd, payload.length - descEnd, cs)
                .trim().replace("\0", "");
    }

    // ─── 工具 ─────────────────────────────────────────────────────────────────

    private static Charset charsetForEnc(int enc) {
        switch (enc) {
            case 1: return Charset.forName("UTF-16");
            case 2: return Charset.forName("UTF-16BE");
            case 3: return Charset.forName("UTF-8");
            default: return Charset.forName("ISO-8859-1");
        }
    }

    private static int syncsafeInt(byte[] d, int off) {
        return ((d[off] & 0x7f) << 21) | ((d[off+1] & 0x7f) << 14)
             | ((d[off+2] & 0x7f) << 7) | (d[off+3] & 0x7f);
    }

    private static int readInt(byte[] d, int off) {
        return ((d[off] & 0xff) << 24) | ((d[off+1] & 0xff) << 16)
             | ((d[off+2] & 0xff) << 8) | (d[off+3] & 0xff);
    }

    private static byte[] slice(byte[] src, int off, int len) {
        byte[] out = new byte[len];
        System.arraycopy(src, off, out, 0, len);
        return out;
    }
}
