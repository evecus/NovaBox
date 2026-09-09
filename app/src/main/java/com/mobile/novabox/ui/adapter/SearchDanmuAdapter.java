package com.mobile.novabox.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.mobile.novabox.R;
import com.mobile.novabox.bean.DanmuSearchResult;

import java.util.ArrayList;

public class SearchDanmuAdapter extends BaseQuickAdapter<DanmuSearchResult, BaseViewHolder> {

    public SearchDanmuAdapter() {
        super(R.layout.item_search_danmu_result, new ArrayList<DanmuSearchResult>());
    }

    @Override
    protected void convert(BaseViewHolder helper, DanmuSearchResult item) {
        helper.setText(R.id.danmuName, item.getName());
    }
}
