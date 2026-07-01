package com.mobile.novabox.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.mobile.novabox.R;

import java.util.ArrayList;

public class VideoFolderAdapter extends BaseQuickAdapter<VideoFolderAdapter.FolderInfo, BaseViewHolder> {

    public static class FolderInfo {
        public String name;
        public String path;
        public int videoCount;

        public FolderInfo(String name, String path, int videoCount) {
            this.name = name;
            this.path = path;
            this.videoCount = videoCount;
        }
    }

    public VideoFolderAdapter() {
        super(R.layout.item_video_folder, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, FolderInfo item) {
        helper.setText(R.id.tvFolderName, item.name);
        helper.setText(R.id.tvVideoCount, item.videoCount + " 个视频");
    }
}
