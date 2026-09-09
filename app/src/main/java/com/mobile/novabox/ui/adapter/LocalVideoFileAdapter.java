package com.mobile.novabox.ui.adapter;

import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.mobile.novabox.R;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;

/**
 * 本地视频列表 adapter。
 * <p>
 * 封面直接用 Glide 加载视频文件路径——Glide 内置的 VideoDecoder 会自动截取视频第一帧
 * 作为缩略图，不需要我们自己用 MediaMetadataRetriever 截帧、管理缓存文件
 * （参考 TVBoxOS 的做法：{@code Glide.with(context).load(videoPath).centerCrop()}）。
 * <p>
 * 缩略图只在这个 52dp×36dp 的小格子里显示，不需要原始视频分辨率那么大的图：
 * 用 {@link com.bumptech.glide.request.RequestOptions#override(int, int)} 显式限制解码目标尺寸，
 * Glide 会按这个小尺寸解码并只缓存变换后的小图（{@link DiskCacheStrategy#RESOURCE}），
 * 而不是缓存/加载原始大图再压缩显示，磁盘占用和解码开销都小很多。
 */
public class LocalVideoFileAdapter extends BaseQuickAdapter<File, BaseViewHolder> {

    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("0.#");

    // 列表格子实际尺寸 52dp×36dp，缩略图按 2 倍尺寸解码/缓存，兼顾高密度屏清晰度,
    // 远小于原始视频分辨率(通常几百 dp 起步),缓存文件体积可以小一个数量级。
    private static final int COVER_W_DP = 104;
    private static final int COVER_H_DP = 72;

    public LocalVideoFileAdapter() {
        super(R.layout.item_local_video_file, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, File item) {
        helper.setText(R.id.tvVideoName, item.getName());
        helper.setText(R.id.tvVideoSize, formatSize(item.length()));

        ImageView ivCover = helper.getView(R.id.ivVideoCover);
        ivCover.setPadding(0, 0, 0, 0);
        Glide.with(mContext)
                .load(item)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE) // 只缓存缩小后的结果图,不缓存原始解码大图
                .override(dp(COVER_W_DP), dp(COVER_H_DP))      // 按小尺寸解码,避免读出整帧原始分辨率
                .placeholder(R.drawable.icon_local_video)
                .error(R.drawable.icon_local_video)
                .centerCrop()
                .into(ivCover);
    }

    private int dp(int value) {
        float density = mContext.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        double kb = size / 1024.0;
        if (kb < 1024) return SIZE_FORMAT.format(kb) + " KB";
        double mb = kb / 1024.0;
        if (mb < 1024) return SIZE_FORMAT.format(mb) + " MB";
        return SIZE_FORMAT.format(mb / 1024.0) + " GB";
    }
}

