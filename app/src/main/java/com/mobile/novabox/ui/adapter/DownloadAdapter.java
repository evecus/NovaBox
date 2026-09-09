package com.mobile.novabox.ui.adapter;

import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.mobile.novabox.R;
import com.mobile.novabox.cache.DownloadEntity;
import com.mobile.novabox.event.DownloadEvent;

import java.io.File;
import java.util.ArrayList;

/**
 * 下载管理列表 adapter。
 * 状态:排队中/下载中 x%/已完成/失败/已取消
 */
public class DownloadAdapter extends BaseQuickAdapter<DownloadEntity, BaseViewHolder> {

    // 封面格子实际尺寸手机 64dp×44dp、Pad 88dp×58dp,统一按 Pad 尺寸的 2 倍解码/缓存,
    // 兼顾两种布局的清晰度,同时远小于原始视频分辨率,缩略图缓存文件体积很小。
    private static final int COVER_W_DP = 176;
    private static final int COVER_H_DP = 116;

    public DownloadAdapter() {
        super(R.layout.item_download, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, DownloadEntity item) {
        TextView tvStatus = helper.getView(R.id.tvDownloadStatus);
        TextView tvName = helper.getView(R.id.tvDownloadName);
        TextView tvEpisode = helper.getView(R.id.tvDownloadEpisode);
        TextView tvProgress = helper.getView(R.id.tvDownloadProgress);
        ProgressBar pb = helper.getView(R.id.pbDownload);
        ImageView ivCover = helper.getView(R.id.ivDownloadCover);

        tvName.setText(item.vodName == null ? "" : item.vodName);
        tvEpisode.setText(item.episodeName == null ? "" : item.episodeName);
        bindCover(ivCover, item);

        switch (item.status) {
            case DownloadEntity.STATUS_QUEUED:
                tvStatus.setText("排队中");
                tvStatus.setBackgroundResource(R.drawable.bg_status_chip);
                pb.setIndeterminate(false);
                pb.setProgress(0);
                tvProgress.setText("等待下载");
                break;
            case DownloadEntity.STATUS_DOWNLOADING:
                tvStatus.setText("下载中");
                tvStatus.setBackgroundResource(R.drawable.bg_status_chip);
                if (item.totalSize > 0) {
                    // 知道总大小:真实百分比
                    pb.setIndeterminate(false);
                    pb.setProgress(item.progress);
                    tvProgress.setText(item.progress + "% · " + formatSize(item.downloadedSize) + "/" + formatSize(item.totalSize)
                            + (item.speedBps > 0 ? " · " + formatSpeed(item.speedBps) : ""));
                } else {
                    // 总大小未知(m3u8/无 Content-Length):不确定进度条,显示已下载大小 + 速度
                    pb.setIndeterminate(true);
                    tvProgress.setText("下载中 " + formatSize(item.downloadedSize)
                            + (item.speedBps > 0 ? " · " + formatSpeed(item.speedBps) : ""));
                }
                break;
            case DownloadEntity.STATUS_DONE:
                tvStatus.setText("已完成");
                tvStatus.setBackgroundResource(R.drawable.bg_status_chip);
                pb.setIndeterminate(false);
                pb.setProgress(100);
                tvProgress.setText("已完成 · " + formatSize(item.totalSize));
                break;
            case DownloadEntity.STATUS_FAILED:
                tvStatus.setText("失败");
                tvStatus.setBackgroundResource(R.drawable.bg_status_chip);
                pb.setIndeterminate(false);
                pb.setProgress(item.progress);
                tvProgress.setText("失败: " + (item.errorMsg == null ? "" : item.errorMsg));
                break;
            case DownloadEntity.STATUS_CANCELED:
            default:
                tvStatus.setText("已取消");
                tvStatus.setBackgroundResource(R.drawable.bg_status_chip);
                pb.setIndeterminate(false);
                pb.setProgress(item.progress);
                tvProgress.setText("已取消");
                break;
        }
    }

    /**
     * 绑定封面缩略图。只对"已完成且本地文件存在"的任务用 Glide 加载视频路径取第一帧;
     * 未完成的任务文件还不完整,不去解码,直接用默认图标。
     * 只需要按列表格子的小尺寸解码/缓存(见 {@link #COVER_W_DP}/{@link #COVER_H_DP}),
     * 用 {@link DiskCacheStrategy#RESOURCE} 只缓存变换后的小图,不缓存原始解码大图
     * (同 LocalVideoFileAdapter 的做法)。
     */
    private void bindCover(ImageView ivCover, DownloadEntity item) {
        if (ivCover == null) return;
        boolean hasLocalFile = item.status == DownloadEntity.STATUS_DONE
                && item.localPath != null && !item.localPath.isEmpty()
                && new File(item.localPath).exists();
        if (hasLocalFile) {
            Glide.with(mContext)
                    .load(new File(item.localPath))
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .override(dp(COVER_W_DP), dp(COVER_H_DP))
                    .placeholder(R.drawable.icon_local_video)
                    .error(R.drawable.icon_local_video)
                    .centerCrop()
                    .into(ivCover);
        } else {
            Glide.with(mContext).clear(ivCover);
            ivCover.setImageResource(R.drawable.icon_local_video);
        }
    }

    private int dp(int value) {
        float density = mContext.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    /** 用实时事件更新指定任务并局部刷新(不重建整个列表) */
    public void updateFromEvent(DownloadEvent event) {
        for (int i = 0; i < getData().size(); i++) {
            DownloadEntity item = getData().get(i);
            if (item.getId() == event.downloadId) {
                item.status = event.status;
                item.progress = event.progress;
                if (event.totalSize > 0) item.totalSize = event.totalSize;
                if (event.downloadedSize > 0) item.downloadedSize = event.downloadedSize;
                item.speedBps = event.speedBps;
                notifyItemChanged(i);
                return;
            }
        }
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "0B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(java.util.Locale.CHINA, "%.1fKB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(java.util.Locale.CHINA, "%.1fMB", mb);
        double gb = mb / 1024.0;
        return String.format(java.util.Locale.CHINA, "%.2fGB", gb);
    }

    private String formatSpeed(long bps) {
        if (bps <= 0) return "";
        if (bps < 1024) return bps + "B/s";
        double kb = bps / 1024.0;
        if (kb < 1024) return String.format(java.util.Locale.CHINA, "%.1fKB/s", kb);
        double mb = kb / 1024.0;
        return String.format(java.util.Locale.CHINA, "%.1fMB/s", mb);
    }
}
