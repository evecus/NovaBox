package com.mobile.novabox.cache;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 本地视频扫描结果的持久化记录。
 *
 * 用于"进入本地视频页时，若已有缓存则直接展示，不用每次重新扫描磁盘、重新截图"——
 * 扫描是遍历所有存储卷的重 IO 操作，缩略图生成也有一定耗时，缓存后可以做到秒开。
 * 点击"刷新"按钮（或设置页扫描入口）时才强制重新扫描一次，覆盖本表数据。
 *
 * path 作为唯一键：同一个文件路径重复扫描到时直接覆盖旧记录，不会无限堆积。
 */
@Entity(tableName = "localVideo")
public class LocalVideoEntity {

    @PrimaryKey
    @ColumnInfo(name = "path")
    public String path;

    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "folder")
    public String folder;

    @ColumnInfo(name = "size")
    public long size;

    @ColumnInfo(name = "modified")
    public long modified;

    /** 磁盘缓存目录中的缩略图文件路径（由 MediaCoverCache 生成）；空/null 表示还未生成或生成失败。 */
    @ColumnInfo(name = "thumbPath")
    public String thumbPath;

    public LocalVideoEntity() {}
}
