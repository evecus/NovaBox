package com.mobile.novabox.ui.adapter;

import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.mobile.novabox.R;
import com.mobile.novabox.api.ApiConfig;
import com.mobile.novabox.bean.Movie;
import com.mobile.novabox.bean.SourceBean;
import com.mobile.novabox.picasso.RoundTransformation;
import com.mobile.novabox.util.ImgUtil;
import com.mobile.novabox.util.MD5;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class FastSearchAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
    public FastSearchAdapter() {
        super(R.layout.item_fast_search_row, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video item) {
        helper.setText(R.id.tvName, item.name);

        SourceBean source = ApiConfig.get().getSource(item.sourceKey);
        String siteName = source != null ? source.getName() : "";
        helper.setText(R.id.tvSite, siteName);

        TextView tvNote = helper.getView(R.id.tvNote);
        if (item.note != null && !item.note.isEmpty()) {
            tvNote.setVisibility(View.VISIBLE);
            tvNote.setText(item.note);
        } else {
            tvNote.setVisibility(View.GONE);
        }

        ImageView ivThumb = helper.getView(R.id.ivThumb);
        if (!TextUtils.isEmpty(item.pic)) {
            Picasso.get()
                    .load(item.pic)
                    .transform(new RoundTransformation(MD5.string2MD5(item.pic))
                            .centerCorp(true)
                            .override(AutoSizeUtils.mm2px(mContext, 144), AutoSizeUtils.mm2px(mContext, 192))
                            .roundRadius(AutoSizeUtils.mm2px(mContext, 6), RoundTransformation.RoundType.ALL))
                    .placeholder(R.drawable.img_loading_placeholder)
                    .noFade()
                    .error(ImgUtil.createTextDrawable(item.name))
                    .into(ivThumb);
        } else {
            ivThumb.setImageDrawable(ImgUtil.createTextDrawable(item.name));
        }
    }
}
