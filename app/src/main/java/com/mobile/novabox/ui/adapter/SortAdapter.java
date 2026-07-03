package com.mobile.novabox.ui.adapter;

import android.graphics.drawable.GradientDrawable;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.mobile.novabox.R;
import com.mobile.novabox.bean.MovieSort;

import java.util.ArrayList;

/**
 * @author pj567
 * @date :2020/12/21
 * @description:
 */
public class SortAdapter extends BaseQuickAdapter<MovieSort.SortData, BaseViewHolder> {
    public SortAdapter() {
        super(R.layout.item_home_sort, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, MovieSort.SortData item) {
        helper.setText(R.id.tvTitle, item.name);
        // 选中时用代码绘制圆角蓝色背景，确保圆角在所有系统版本上生效
        boolean selected = helper.itemView.isSelected();
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(60f); // px，足够大保证完整圆角
        if (selected) {
            bg.setColor(0xBD0CADE2);
        } else {
            bg.setColor(android.graphics.Color.TRANSPARENT);
        }
        helper.itemView.setBackground(bg);
    }
}