package com.mobile.novabox.bean;

import android.graphics.Bitmap;

public class LocalAudioFile {
    public String title;      // 歌名（ID3/FLAC/OGG 标签或文件名去扩展名）
    public String artist;     // 艺术家
    public String album;      // 专辑
    public String path;       // 完整路径
    public String folderPath; // 所在文件夹路径
    public long   modified;   // 修改时间（ms）
    public long   size;

    // ── 封面：内存态，不落盘 ──────────────────────────────────────────────
    // 封面数据本身嵌在音频文件标签里，读取成本低，没必要占用本地存储空间。
    // 由 AudioCoverMemoryCache 按列表滚动位置分批异步加载填充；
    // coverLoaded=true 表示"已经尝试加载过"（无论是否读到封面），避免重复加载。
    public transient Bitmap coverBitmap;
    public transient boolean coverLoaded;

    public LocalAudioFile() {}
}
