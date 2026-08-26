package com.mobile.novabox.download;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.mobile.novabox.base.App;

import java.io.File;

/**
 * 下载存储路径工具。
 * <p>
 * 目标目录固定为 /sdcard/Download/novabox:
 * - API 28-:File 直写,需 WRITE_EXTERNAL_STORAGE 运行时权限
 * - API 29:requestLegacyExternalStorage(Manifest 已声明)后仍可 File 直写
 * - API 30+:scoped storage 下公共 Download 目录不能 File 直写,需走 MediaStore,
 *   但 MediaStore.Downloads 相对路径 "Download/novabox" 落盘后,
 *   通过 content uri 拿到的 data 列就是 /storage/emulated/0/Download/novabox/xxx。
 *   这里统一返回 File 路径给播放器用(MediaStore 写完再查一次 data 列)。
 */
public class DownloadStorage {

    public static final String DIR_NAME = "novabox";

    /** 是否走 MediaStore(Android 11+,targetSdk 33 下公共目录必须) */
    public static boolean useMediaStore() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    /** 目标目录 File(API 30+ 此目录实际由 MediaStore 管理,此处仅用于展示/播放路径拼接) */
    public static File getDownloadDir() {
        File base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File dir = new File(base, DIR_NAME);
        if (!useMediaStore() && !dir.exists()) dir.mkdirs();
        return dir;
    }

    /** MediaStore 相对路径 */
    public static String getRelativePath() {
        return Environment.DIRECTORY_DOWNLOADS + "/" + DIR_NAME;
    }

    /** 根据文件名拼目标 File 路径 */
    public static File buildTargetFile(String fileName) {
        return new File(getDownloadDir(), sanitize(fileName));
    }

    /** 生成临时文件(下载中先写临时,完成后 rename) */
    public static File buildTempFile(String fileName) {
        File cache = new File(App.getInstance().getCacheDir(), "download_tmp");
        if (!cache.exists()) cache.mkdirs();
        return new File(cache, sanitize(fileName) + ".part");
    }

    /** 文件名清洗:去掉路径分隔符和非法字符,保留 .ts/.mp4 等后缀 */
    public static String sanitize(String name) {
        if (name == null) return "download";
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (cleaned.isEmpty()) cleaned = "download";
        return cleaned;
    }

    /** 根据原始 URL 推断保存的文件名(带扩展名) */
    public static String inferFileName(String vodName, String episodeName, String url, boolean isM3u8) {
        String base = sanitize(vodName == null ? "novabox" : vodName);
        String ep = sanitize(episodeName == null ? "" : episodeName);
        StringBuilder sb = new StringBuilder();
        sb.append(base);
        if (!ep.isEmpty() && !base.contains(ep)) {
            sb.append("_").append(ep);
        }
        if (isM3u8) {
            sb.append(".ts"); // m3u8 分片拼接为单一 .ts 文件
        } else {
            // 从 URL 推断扩展名,默认 .mp4
            String ext = inferExt(url);
            sb.append(ext);
        }
        return sb.toString();
    }

    private static String inferExt(String url) {
        if (url == null) return ".mp4";
        String lower = url.toLowerCase();
        if (lower.contains(".mp4")) return ".mp4";
        if (lower.contains(".mkv")) return ".mkv";
        if (lower.contains(".flv")) return ".flv";
        if (lower.contains(".ts")) return ".ts";
        if (lower.contains(".avi")) return ".avi";
        if (lower.contains(".mov")) return ".mov";
        if (lower.contains(".webm")) return ".webm";
        return ".mp4";
    }

    /** MediaStore 列:data 真实路径(Android 11+ Download 集合可用) */
    public static String queryDataColumn(Context ctx, android.net.Uri uri) {
        try {
            String[] proj = {MediaStore.MediaColumns.DATA};
            android.database.Cursor c = ctx.getContentResolver().query(uri, proj, null, null, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        int idx = c.getColumnIndex(MediaStore.MediaColumns.DATA);
                        if (idx >= 0) return c.getString(idx);
                    }
                } finally {
                    c.close();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
