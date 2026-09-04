package com.mobile.novabox.event;

/**
 * 下载任务进度/状态事件,通过 EventBus 广播。
 * 进度事件携带实时数据(速度/大小),UI 直接用它刷新,不依赖 Room 落库延迟。
 */
public class DownloadEvent {
    public static final int TYPE_PROGRESS = 0;   // 进度更新
    public static final int TYPE_STATUS = 1;     // 状态变更(开始/完成/失败/取消)

    public int type;
    /** 下载任务 id */
    public int downloadId;
    /** 状态(DownloadEntity.STATUS_*) */
    public int status;
    /** 进度 0~100 */
    public int progress;
    /** 总大小(字节),-1 表示未知(服务器无 Content-Length 或 m3u8) */
    public long totalSize;
    /** 已下载大小(字节) */
    public long downloadedSize;
    /** 实时下载速度(字节/秒) */
    public long speedBps;

    public DownloadEvent(int type, int downloadId) {
        this.type = type;
        this.downloadId = downloadId;
    }

    public DownloadEvent(int type, int downloadId, int status, int progress) {
        this.type = type;
        this.downloadId = downloadId;
        this.status = status;
        this.progress = progress;
    }

    public DownloadEvent(int type, int downloadId, int status, int progress,
                         long totalSize, long downloadedSize, long speedBps) {
        this.type = type;
        this.downloadId = downloadId;
        this.status = status;
        this.progress = progress;
        this.totalSize = totalSize;
        this.downloadedSize = downloadedSize;
        this.speedBps = speedBps;
    }
}
