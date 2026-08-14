package com.mobile.novabox.cache;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 本地音频扫描结果的持久化记录。
 *
 * 只存扫描阶段能拿到的信息（路径、文件名、大小、修改时间、所在文件夹，以及
 * MediaStore/ID3 能提供的 title/artist/album）；封面图不落库，也不落盘——封面数据
 * 本身嵌在音频文件标签里，读取成本低，由 AudioCoverMemoryCache 在展示时按需分批
 * 加载进内存，重启 App 或重进页面后会重新读取，不额外占用存储空间。
 *
 * path 作为唯一键：同一个文件路径重复扫描到时直接覆盖旧记录，不会无限堆积。
 */
@Entity(tableName = "localAudio")
public class LocalAudioEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "path")
    public String path = "";

    @ColumnInfo(name = "title")
    public String title;

    @ColumnInfo(name = "artist")
    public String artist;

    @ColumnInfo(name = "album")
    public String album;

    @ColumnInfo(name = "folder")
    public String folder;

    @ColumnInfo(name = "size")
    public long size;

    @ColumnInfo(name = "modified")
    public long modified;

    public LocalAudioEntity() {}
}
