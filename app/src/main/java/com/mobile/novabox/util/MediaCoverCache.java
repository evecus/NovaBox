package com.mobile.novabox.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;

/**
 * 本地视频封面缓存。
 *
 * 扫描到视频文件时，额外提取一张封面图（取第一帧），并以“文件路径+修改时间”的 MD5
 * 作为文件名缓存到 app 私有缓存目录下，下次扫描到同一个文件（且未被修改）时直接
 * 复用缓存，不用重复解码。
 *
 * 列表页展示时优先用缓存的封面图，没有封面（提取失败）则使用默认图标。
 *
 * 注：本地音乐封面不再走这套落盘缓存——封面数据本身嵌在音频文件标签里，读取成本低，
 * 改为用 {@link AudioCoverMemoryCache} 只在内存里按需分批加载，详见该类注释。
 */
public class MediaCoverCache {

    private static final String VIDEO_DIR = "covers_video";

    private static final int MAX_DIMEN = 480; // 缩略图最大边长，节省空间

    // ─── 目录 ──────────────────────────────────────────────────────────────

    private static File videoDir(Context context) {
        File dir = new File(context.getApplicationContext().getCacheDir(), VIDEO_DIR);
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

    /**
     * 仅从缓存读取（不做提取），用于列表快速绑定时避免重复解码开销。
     */
    public static File peekVideoCover(Context context, File videoFile) {
        File f = new File(videoDir(context), keyFor(videoFile.getAbsolutePath(), videoFile.lastModified()) + ".jpg");
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
