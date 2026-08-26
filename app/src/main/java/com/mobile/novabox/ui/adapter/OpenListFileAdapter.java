package com.mobile.novabox.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.mobile.novabox.R;
import com.mobile.novabox.bean.OpenListFile;

import java.util.ArrayList;

public class OpenListFileAdapter extends BaseQuickAdapter<OpenListFile, BaseViewHolder> {
    private OpenListFile parentItem;

    public OpenListFileAdapter() {
        super(R.layout.item_openlist_file, new ArrayList<>());
    }

    public void setParentItem(OpenListFile parentItem) {
        this.parentItem = parentItem;
    }

    @Override
    protected void convert(BaseViewHolder helper, OpenListFile item) {
        boolean isParent = item == parentItem;
        helper.setText(R.id.tvOpenFileName, isParent ? ".." : item.name);
        if (isParent) {
            helper.setImageResource(R.id.ivOpenFileIcon, R.drawable.icon_folder);
            helper.setText(R.id.tvOpenFileInfo, "返回上级");
        } else if (item.isDir) {
            helper.setImageResource(R.id.ivOpenFileIcon, R.drawable.icon_folder);
            helper.setText(R.id.tvOpenFileInfo, "进入");
        } else if (item.isVideo()) {
            helper.setImageResource(R.id.ivOpenFileIcon, R.drawable.icon_video);
            helper.setText(R.id.tvOpenFileInfo, item.formattedSize());
        } else if (item.isAudio()) {
            helper.setImageResource(R.id.ivOpenFileIcon, R.drawable.icon_live);
            helper.setText(R.id.tvOpenFileInfo, item.formattedSize());
        } else {
            helper.setImageResource(R.id.ivOpenFileIcon, R.drawable.icon_setting);
            helper.setText(R.id.tvOpenFileInfo, item.formattedSize());
        }
    }
}
