package com.mobile.novabox.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;

/**
 * 本地视频 / 本地音频封面缓存。
 *
 * 扫描到视频 / 音频文件时，额外提取一张封面图（视频取第一帧，音频取 ID3 内嵌封面），
 * 并以“文件路径+修改时间”的 MD5 作为文件名缓存到 app 私有缓存目录下，
 * 下次扫描到同一个文件（且未被修改）时直接复用缓存，不用重复解码。
 *
 * 列表页展示时优先用缓存的封面图，没有封面（提取失败/没有内嵌封面）则使用默认图标。
 */
public class MediaCoverCache {

    private static final String VIDEO_DIR = "covers_video";
    private static final String AUDIO_DIR = "covers_audio";

    private static final int MAX_DIMEN = 480; // 缩略图最大边长，节省空间

    // ─── 目录 ──────────────────────────────────────────────────────────────

    private static File videoDir(Context context) {
        File dir = new File(context.getApplicationContext().getCacheDir(), VIDEO_DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File audioDir(Context context) {
        File dir = new File(context.getApplicationContext().getCacheDir(), AUDIO_DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static String keyFor(String path, long lastModified) {
        String raw = path + "_" + lastModified;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(raw.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(raw.hashCode());
        }
    }

    // ─── 视频封面 ──────────────────────────────────────────────────────────

    /**
     * 获取（必要时提取并缓存）视频封面文件。子线程调用。
     * @return 封面图文件，如果没有可用封面则返回 null
     */
    public static File getOrCreateVideoCover(Context context, File videoFile) {
        String key = keyFor(videoFile.getAbsolutePath(), videoFile.lastModified());
        File cacheFile = new File(videoDir(context), key + ".jpg");
        if (cacheFile.exists() && cacheFile.length() > 0) return cacheFile;

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(videoFile.getAbsolutePath());
            Bitmap frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) {
                frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            }
            if (frame == null) return null;
            Bitmap scaled = scaleDown(frame, MAX_DIMEN);
            boolean saved = saveBitmap(scaled, cacheFile);
            if (scaled != frame) frame.recycle();
            return saved ? cacheFile : null;
        } catch (Exception e) {
            return null;
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }
    }

    // ─── 音频封面 ──────────────────────────────────────────────────────────

    /**
     * 获取（必要时提取并缓存）音频内嵌封面文件。子线程调用。
     * @return 封面图文件，如果没有内嵌封面则返回 null
     */
    public static File getOrCreateAudioCover(Context context, String path, long lastModified) {
        String key = keyFor(path, lastModified);
        File cacheFile = new File(audioDir(context), key + ".jpg");
        if (cacheFile.exists() && cacheFile.length() > 0) return cacheFile;

        // 优先用已有的 ID3 解析工具读取内嵌封面（AudioMetadataLoader 已经实现了 APIC 帧解析）
        try {
            AudioMetadataLoader.Metadata meta = AudioMetadataLoader.loadLocal(path);
            if (meta != null && meta.cover != null) {
                Bitmap scaled = scaleDown(meta.cover, MAX_DIMEN);
                boolean saved = saveBitmap(scaled, cacheFile);
                if (scaled != meta.cover) meta.cover.recycle();
                return saved ? cacheFile : null;
            }
        } catch (Exception ignored) {}

        // 兜底：用 MediaMetadataRetriever 读取内嵌图片（对某些格式如 flac/m4a 更可靠）
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(path);
            byte[] art = retriever.getEmbeddedPicture();
            if (art == null || art.length == 0) return null;
            Bitmap bmp = BitmapFactory.decodeByteArray(art, 0, art.length);
            if (bmp == null) return null;
            Bitmap scaled = scaleDown(bmp, MAX_DIMEN);
            boolean saved = saveBitmap(scaled, cacheFile);
            if (scaled != bmp) bmp.recycle();
            return saved ? cacheFile : null;
        } catch (Exception e) {
            return null;
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }
    }

    /**
     * 仅从缓存读取（不做提取），用于列表快速绑定时避免重复解码开销。
     */
    public static File peekVideoCover(Context context, File videoFile) {
        File f = new File(videoDir(context), keyFor(videoFile.getAbsolutePath(), videoFile.lastModified()) + ".jpg");
        return (f.exists() && f.length() > 0) ? f : null;
    }

    public static File peekAudioCover(Context context, String path, long lastModified) {
        File f = new File(audioDir(context), keyFor(path, lastModified) + ".jpg");
        return (f.exists() && f.length() > 0) ? f : null;
    }

    // ─── 工具 ──────────────────────────────────────────────────────────────

    private static Bitmap scaleDown(Bitmap src, int maxDimen) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxDimen && h <= maxDimen) return src;
        float scale = Math.min((float) maxDimen / w, (float) maxDimen / h);
        int nw = Math.max(1, Math.round(w * scale));
        int nh = Math.max(1, Math.round(h * scale));
        return Bitmap.createScaledBitmap(src, nw, nh, true);
    }

    private static boolean saveBitmap(Bitmap bmp, File dest) {
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            return bmp.compress(Bitmap.CompressFormat.JPEG, 85, fos);
        } catch (Exception e) {
            return false;
        }
    }
}
