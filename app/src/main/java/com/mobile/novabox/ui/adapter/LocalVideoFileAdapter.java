package com.mobile.novabox.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.mobile.novabox.R;

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
