package com.mobile.novabox.bean;

public class LocalAudioFile {
    public String title;      // 歌名（ID3或文件名去扩展名）
    public String artist;     // 艺术家
    public String album;      // 专辑
    public String path;       // 完整路径
    public String folderPath; // 所在文件夹路径
    public long   modified;   // 修改时间（ms）
    public long   size;
    public String coverPath;  // 缓存的封面图本地路径（没有内嵌封面时为 null）

    public LocalAudioFile() {}
}
