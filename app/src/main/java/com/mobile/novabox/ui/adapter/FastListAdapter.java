package com.mobile.novabox.ui.adapter;

import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.mobile.novabox.R;

import java.util.ArrayList;

public class FastListAdapter extends BaseQuickAdapter<String, BaseViewHolder> {
    private String selectedName = "\u5168\u90e8";

    public FastListAdapter() {
        super(R.layout.item_search_word_hot, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, String item) {
        // 隐藏"全部"条目：将 itemView 高度设为 0 且不可见，使源列表从第一个真实源开始显示
        if ("\u5168\u90e8".equals(item)) {
            helper.itemView.setVisibility(android.view.View.GONE);
            helper.itemView.getLayoutParams().height = 0;
            return;
        }
        helper.itemView.setVisibility(android.view.View.VISIBLE);
        helper.itemView.getLayoutParams().height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
        TextView textView = helper.getView(R.id.tvSearchWord);
        textView.setText(item);
        textView.setBackgroundResource(R.drawable.bg_fast_site_word);
        updateSelection(textView, item);
    }

    public void setSelectedName(String selectedName) {
        this.selectedName = TextUtils.isEmpty(selectedName) ? "\u5168\u90e8" : selectedName;
    }

    public void refreshVisibleSelection(ViewGroup parent) {
        if (parent == null) return;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof TextView) {
                updateSelection((TextView) child, ((TextView) child).getText().toString());
            }
        }
    }

    private void updateSelection(TextView textView, String item) {
        boolean selected = TextUtils.equals(item, selectedName);
        textView.setSelected(selected);
        textView.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
    }
}
