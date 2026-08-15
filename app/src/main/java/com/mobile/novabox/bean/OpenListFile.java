package com.mobile.novabox.bean;

import com.google.gson.annotations.SerializedName;

import java.util.Locale;

public class OpenListFile {
    @SerializedName("name")
    public String name = "";
    @SerializedName("size")
    public long size = 0;
    @SerializedName("is_dir")
    public boolean isDir = false;
    @SerializedName("modified")
    public String modified = "";
    @SerializedName("sign")
    public String sign = "";
    @SerializedName("thumb")
    public String thumb = "";
    // 0=未知 1=文件夹 2=视频 3=音频 4=文本 5=图片 6=office 7=pdf 8=压缩包
    @SerializedName("type")
    public int type = 0;

    // 由调用方填充，当前条目所在目录的完整路径
    public transient String parentPath = "/";

    public boolean isVideo() {
        if (type == 2) return true;
        if (isDir) return false;
        return hasExt(VIDEO_EXT);
    }

    public boolean isAudio() {
        if (type == 3) return true;
        if (isDir) return false;
        return hasExt(AUDIO_EXT);
    }

    public String fullPath() {
        if (parentPath == null || parentPath.isEmpty()) return "/" + name;
        if (parentPath.endsWith("/")) return parentPath + name;
        return parentPath + "/" + name;
    }

    private static final String[] VIDEO_EXT = {"mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "ts", "rmvb", "rm"};
    private static final String[] AUDIO_EXT = {"mp3", "flac", "aac", "wav", "ogg", "m4a", "wma", "opus", "ape", "alac"};

    private boolean hasExt(String[] exts) {
        String n = name.toLowerCase(Locale.getDefault());
        int dot = n.lastIndexOf('.');
        if (dot < 0 || dot == n.length() - 1) return false;
        String ext = n.substring(dot + 1);
        for (String e : exts) {
            if (e.equals(ext)) return true;
        }
        return false;
    }

    public String formattedSize() {
        if (isDir) return "";
        long bytes = size;
        if (bytes <= 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int dg = (int) (Math.log10(bytes) / Math.log10(1024));
        if (dg >= units.length) dg = units.length - 1;
        double val = bytes / Math.pow(1024, dg);
        return String.format(Locale.getDefault(), "%.1f %s", val, units[dg]);
    }
}
