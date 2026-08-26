package com.mobile.novabox.cache;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;

/**
 * 下载任务/记录实体。
 * <p>
 * status 约定:
 * 0=排队中, 1=下载中, 2=已完成, 3=失败, 4=已取消, 5=已删除(仅标记,列表过滤)
 */
@Entity(tableName = "download")
public class DownloadEntity implements Serializable {

    public static final int STATUS_QUEUED = 0;
    public static final int STATUS_DOWNLOADING = 1;
    public static final int STATUS_DONE = 2;
    public static final int STATUS_FAILED = 3;
    public static final int STATUS_CANCELED = 4;

    @PrimaryKey(autoGenerate = true)
    private int id;
    /** 站点 key */
    @ColumnInfo(name = "sourceKey")
    public String sourceKey;
    /** 影片标题 */
    @ColumnInfo(name = "vodName")
    public String vodName;
    /** 集数名,如 "第1集" */
    @ColumnInfo(name = "episodeName")
    public String episodeName;
    /** 线路名 */
    @ColumnInfo(name = "playFlag")
    public String playFlag;
    /** 原始播放地址(未解析) */
    @ColumnInfo(name = "playUrl")
    public String playUrl;
    /** 解析后的直链(下载用) */
    @ColumnInfo(name = "downloadUrl")
    public String downloadUrl;
    /** 本地文件绝对路径 */
    @ColumnInfo(name = "localPath")
    public String localPath;
    /** 文件总大小(字节),m3u8 分片下载时未知则 -1 */
    @ColumnInfo(name = "totalSize")
    public long totalSize;
    /** 已下载大小(字节) */
    @ColumnInfo(name = "downloadedSize")
    public long downloadedSize;
    /** 0~100,整数百分比 */
    @ColumnInfo(name = "progress")
    public int progress;
    /** 状态:见类常量 */
    @ColumnInfo(name = "status")
    public int status;
    /** 错误信息 */
    @ColumnInfo(name = "errorMsg")
    public String errorMsg;
    /** 创建时间戳(ms) */
    @ColumnInfo(name = "createTime")
    public long createTime;
    /** 完成时间戳(ms) */
    @ColumnInfo(name = "finishTime")
    public long finishTime;
    /** 实时下载速度(字节/秒)。仅内存态,不落库(Room @Ignore) */
    @androidx.room.Ignore
    public long speedBps;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
