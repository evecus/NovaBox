package com.mobile.novabox.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.mobile.novabox.R;

import java.io.File;
import java.util.ArrayList;

public class LocalPlaylistAdapter extends BaseQuickAdapter<File, BaseViewHolder> {

    private int currentIndex = -1;

    public LocalPlaylistAdapter() {
        super(R.layout.item_local_playlist, new ArrayList<>());
    }

    public void setCurrentIndex(int idx) {
        int old = currentIndex;
        currentIndex = idx;
        if (old >= 0 && old < getData().size()) notifyItemChanged(old);
        if (currentIndex >= 0 && currentIndex < getData().size()) notifyItemChanged(currentIndex);
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    @Override
    protected void convert(BaseViewHolder helper, File item) {
        int pos = helper.getAdapterPosition();
        boolean isPlaying = (pos == currentIndex);
        helper.setText(R.id.tvName, item.getName());
        helper.setVisible(R.id.ivPlaying, isPlaying);
        helper.setTextColor(R.id.tvName, isPlaying ? 0xFF1ABC9C : 0xFF000000);
    }
}
