package com.mobile.novabox.ui.adapter;

import android.widget.ImageView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.mobile.novabox.R;
import com.mobile.novabox.picasso.RoundTransformation;
import com.mobile.novabox.util.MediaCoverCache;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;

public class LocalVideoFileAdapter extends BaseQuickAdapter<File, BaseViewHolder> {

    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("0.#");

    public LocalVideoFileAdapter() {
        super(R.layout.item_local_video_file, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, File item) {
        helper.setText(R.id.tvVideoName, item.getName());
        helper.setText(R.id.tvVideoSize, formatSize(item.length()));

        ImageView ivCover = helper.getView(R.id.ivVideoCover);
        // 扫描阶段已经把封面缓存到磁盘了，这里只做“查表”，没有则显示默认图标
        File cover = MediaCoverCache.peekVideoCover(mContext, item);
        if (cover != null) {
            ivCover.setPadding(0, 0, 0, 0);
            Picasso.get()
                    .load(cover)
                    .transform(new RoundTransformation(cover.getAbsolutePath())
                            .centerCorp(true)
                            .override(dp(52), dp(36))
                            .roundRadius(dp(6), RoundTransformation.RoundType.ALL))
                    .placeholder(R.drawable.icon_local_video)
                    .error(R.drawable.icon_local_video)
                    .noFade()
                    .into(ivCover);
        } else {
            int pad = dp(10);
            ivCover.setPadding(pad, pad, pad, pad);
            ivCover.setImageResource(R.drawable.icon_local_video);
        }
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
