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
 * 纯 Java 音频元数据读取器，支持 MP3(ID3v2) / FLAC / OGG(Vorbis)。
 *
 * 网络流场景（loadAsync）：不依赖 MediaMetadataRetriever（该方式在盒子上读网络流很不
 * 稳定），改为用 OkHttp 只下载文件头部若干 KB。
 * 本地文件场景（loadLocal）：直接读文件头部若干字节，不需要网络请求。
 *
 * 读取文件头部魔数（magic number）判断实际格式，不依赖扩展名：
 * - "ID3"  → MP3 (ID3v2.3 / ID3v2.4)
 *     TIT2 歌名 / TPE1 歌手 / TALB 专辑 / APIC 封面图 / USLT 非同步歌词（LRC 格式）
 * - "fLaC" → FLAC
 *     VORBIS_COMMENT 块的 TITLE/ARTIST/ALBUM → 歌名/歌手/专辑
 *     PICTURE 块 → 封面图
 * - "OggS" → OGG (Vorbis Comment Header，字段格式与 FLAC 的 VORBIS_COMMENT 一致)
 *     TITLE/ARTIST/ALBUM → 歌名/歌手/专辑（内嵌封面较少见，暂不解析）
 * 都不匹配时返回空 Metadata（各字段为 null），调用方按“这首歌读不到信息”处理。
 */
public class AudioMetadataLoader {

    private static final String TAG = "AudioMetaLoader";
    // 256KB：FLAC 的 VORBIS_COMMENT + PICTURE 元数据块可能比 ID3 tag 大不少，
    // 128KB 在部分专辑封面较大的 FLAC 文件上会读不全，统一放宽到 256KB。
    private static final int FETCH_BYTES = 256 * 1024;

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

    /**
     * 同步读取本地音频文件的 ID3 tag（歌词/封面等），在子线程调用。
     */
    public static Metadata loadLocal(String filePath) {
        try {
            java.io.File file = new java.io.File(filePath);
            if (!file.exists()) return null;
            long readLen = Math.min(file.length(), FETCH_BYTES);
            byte[] data = new byte[(int) readLen];
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            int read = 0;
            try {
                while (read < data.length) {
                    int n = fis.read(data, read, data.length - read);
                    if (n < 0) break;
                    read += n;
                }
            } finally {
                fis.close();
            }
            if (read < 10) return null;
            return parse(data);
        } catch (Exception e) {
            Log.e(TAG, "loadLocal error: " + e.getMessage());
            return null;
        }
    }

    /**
     * 按文件头部魔数分派到对应格式的解析器。
     */
    private static Metadata parse(byte[] data) {
        if (isId3(data)) return parseId3(data);
        if (isFlac(data)) return parseFlac(data);
        if (isOgg(data)) return parseOgg(data);
        Log.w(TAG, "Unrecognized audio header, no ID3/FLAC/OGG magic found");
        return new Metadata();
    }

    // ─── 格式嗅探 ─────────────────────────────────────────────────────────────

    private static boolean isId3(byte[] d) {
        return d.length >= 3 && d[0] == 'I' && d[1] == 'D' && d[2] == '3';
    }

    private static boolean isFlac(byte[] d) {
        return d.length >= 4 && d[0] == 'f' && d[1] == 'L' && d[2] == 'a' && d[3] == 'C';
    }

    private static boolean isOgg(byte[] d) {
        return d.length >= 4 && d[0] == 'O' && d[1] == 'g' && d[2] == 'g' && d[3] == 'S';
    }

    // ─── ID3v2 解析 ───────────────────────────────────────────────────────────

    private static Metadata parseId3(byte[] data) {
        Metadata meta = new Metadata();
        if (data.length < 10) return meta;

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

    // ─── FLAC 解析 ────────────────────────────────────────────────────────────
    //
    // 结构：4 字节 "fLaC" 魔数之后紧跟若干个 METADATA_BLOCK，每块头部 4 字节：
    // bit7 = is-last-block 标记，bit0-6 = block type，后接 3 字节大端块长度。
    // 只关心两种块类型：
    //   type 4 = VORBIS_COMMENT（TITLE/ARTIST/ALBUM 等文本标签）
    //   type 6 = PICTURE（内嵌封面图）
    // 读取上限 FETCH_BYTES 内没扫描到全部块也没关系，元数据块通常在文件最前面。

    private static Metadata parseFlac(byte[] data) {
        Metadata meta = new Metadata();
        int pos = 4; // 跳过 "fLaC" 魔数

        while (pos + 4 <= data.length) {
            int blockHeader = data[pos] & 0xff;
            boolean isLast = (blockHeader & 0x80) != 0;
            int blockType = blockHeader & 0x7f;
            int blockLen = readInt24(data, pos + 1);
            pos += 4;

            if (blockLen < 0 || pos + blockLen > data.length) break;

            if (blockType == 4) {
                Map<String, String> tags = parseVorbisComment(data, pos, blockLen);
                if (tags != null) {
                    meta.title  = tags.get("TITLE");
                    meta.artist = tags.get("ARTIST");
                    meta.album  = tags.get("ALBUM");
                }
            } else if (blockType == 6) {
                Bitmap pic = parseFlacPicture(data, pos, blockLen);
                if (pic != null) meta.cover = pic;
            }

            pos += blockLen;
            if (isLast) break;
        }
        return meta;
    }

    /**
     * 解析 FLAC PICTURE 元数据块（type=6），返回解码后的封面 Bitmap。
     * 结构（全部大端，固定顺序）：picture type(4B) → MIME 长度(4B)+MIME →
     * 描述长度(4B)+描述 → 宽(4B) → 高(4B) → 色深(4B) → 索引色数(4B) →
     * 图片数据长度(4B) → 图片数据。
     */
    private static Bitmap parseFlacPicture(byte[] data, int offset, int len) {
        int pos = offset;
        int limit = offset + len;
        if (pos + 4 > limit) return null;
        pos += 4; // picture type，不关心具体类型（封面/艺术家照片等），一律当封面用

        if (pos + 4 > limit) return null;
        int mimeLen = readInt(data, pos);
        pos += 4 + mimeLen;

        if (pos + 4 > limit) return null;
        int descLen = readInt(data, pos);
        pos += 4 + descLen;

        // 宽、高、色深、索引色数：各 4 字节，共 16 字节，这里不需要具体数值
        pos += 16;

        if (pos + 4 > limit) return null;
        int picLen = readInt(data, pos);
        pos += 4;

        if (picLen <= 0 || pos + picLen > limit) return null;
        try {
            return BitmapFactory.decodeByteArray(data, pos, picLen);
        } catch (Exception e) {
            return null;
        }
    }

    // ─── OGG (Vorbis) 解析 ────────────────────────────────────────────────────
    //
    // Ogg 是分页（page）容器格式，每页以 "OggS" 开头。Vorbis 音频流的第 2 个逻辑包
    // 固定是 Comment Header，其内容格式跟 FLAC 的 VORBIS_COMMENT 完全一致（两者共用
    // 同一套标签规范），只是外面多包了一层 Ogg 分页结构需要先拆出来。
    //
    // 简化处理：只扫描文件开头的头几个 page（标签通常在最前面几页内），拼出每页的
    // segment 数据，定位到 Comment Header 包（"\x03vorbis" 开头）后解析，不追求完整
    // 重建整个 Ogg 逻辑流（对元数据读取来说没有必要）。

    private static Metadata parseOgg(byte[] data) {
        Metadata meta = new Metadata();
        int pos = 0;
        int pagesScanned = 0;
        final int maxPagesToScan = 8; // 标签通常在前几页，扫描过多页没有意义

        while (pos + 27 <= data.length && pagesScanned < maxPagesToScan) {
            if (!(data[pos] == 'O' && data[pos + 1] == 'g'
                    && data[pos + 2] == 'g' && data[pos + 3] == 'S')) {
                break; // 不是合法的 page 起始，停止扫描
            }
            pagesScanned++;

            int segCount = data[pos + 26] & 0xff;
            int segTableStart = pos + 27;
            if (segTableStart + segCount > data.length) break;

            int payloadLen = 0;
            for (int i = 0; i < segCount; i++) {
                payloadLen += data[segTableStart + i] & 0xff;
            }
            int payloadStart = segTableStart + segCount;
            if (payloadStart + payloadLen > data.length) break;

            // Comment Header packet type = 3，紧跟 6 字节 "vorbis" 标识
            if (payloadLen > 7
                    && data[payloadStart] == 0x03
                    && data[payloadStart + 1] == 'v' && data[payloadStart + 2] == 'o'
                    && data[payloadStart + 3] == 'r' && data[payloadStart + 4] == 'b'
                    && data[payloadStart + 5] == 'i' && data[payloadStart + 6] == 's') {
                Map<String, String> tags = parseVorbisComment(data, payloadStart + 7, payloadLen - 7);
                if (tags != null) {
                    meta.title  = tags.get("TITLE");
                    meta.artist = tags.get("ARTIST");
                    meta.album  = tags.get("ALBUM");
                    // 注：Ogg/Vorbis 的内嵌封面（METADATA_BLOCK_PICTURE，以 base64 存
                    // 在一条 Vorbis Comment 里）属于少数场景，这里暂不处理，只覆盖最
                    // 常见的文本标签；封面缺失时列表 UI 会展示占位图标。
                    return meta;
                }
            }

            pos = payloadStart + payloadLen;
        }
        return meta;
    }

    // ─── Vorbis Comment 解析（FLAC 与 OGG 共用） ────────────────────────────────
    //
    // 格式：vendor 字符串长度(4B 小端) + vendor 字符串
    //       + comment 数量(4B 小端)
    //       + 每条 comment：长度(4B 小端) + "KEY=VALUE" 格式的 UTF-8 文本
    // key 大小写不敏感，统一转大写存进返回的 Map，方便调用方按
    // 'TITLE'/'ARTIST'/'ALBUM' 这样固定的大写 key 取值。

    private static Map<String, String> parseVorbisComment(byte[] data, int offset, int len) {
        int pos = offset;
        int limit = offset + len;
        if (pos + 4 > limit) return null;

        int vendorLen = readIntLe(data, pos);
        pos += 4 + vendorLen;
        if (pos + 4 > limit) return null;

        int commentCount = readIntLe(data, pos);
        pos += 4;

        Map<String, String> tags = new java.util.HashMap<>();
        for (int i = 0; i < commentCount; i++) {
            if (pos + 4 > limit) break;
            int cLen = readIntLe(data, pos);
            pos += 4;
            if (cLen < 0 || pos + cLen > limit) break;

            String text;
            try {
                text = new String(data, pos, cLen, Charset.forName("UTF-8"));
            } catch (Exception e) {
                pos += cLen;
                continue;
            }
            pos += cLen;

            int eqIdx = text.indexOf('=');
            if (eqIdx <= 0) continue; // 没有 '=' 或 key 为空，跳过这条非法 comment

            String key = text.substring(0, eqIdx).toUpperCase(java.util.Locale.US);
            String value = text.substring(eqIdx + 1);
            if (value.isEmpty()) continue;
            // 同名 key 出现多次（比如多个 ARTIST）时保留第一条即可。
            if (!tags.containsKey(key)) tags.put(key, value);
        }
        return tags;
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

    /** 大端序 3 字节整数，FLAC METADATA_BLOCK 头部的块长度字段用的是 24 位大端。 */
    private static int readInt24(byte[] d, int off) {
        return ((d[off] & 0xff) << 16) | ((d[off+1] & 0xff) << 8) | (d[off+2] & 0xff);
    }

    /**
     * 小端序 4 字节整数，Vorbis Comment（FLAC/OGG）的长度字段用的是小端，
     * 跟 ID3 的大端字段不同，故单独提供。
     */
    private static int readIntLe(byte[] d, int off) {
        return (d[off] & 0xff) | ((d[off+1] & 0xff) << 8)
             | ((d[off+2] & 0xff) << 16) | ((d[off+3] & 0xff) << 24);
    }

    private static byte[] slice(byte[] src, int off, int len) {
        byte[] out = new byte[len];
        System.arraycopy(src, off, out, 0, len);
        return out;
    }
}
