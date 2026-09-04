package com.mobile.novabox.ui.widget;

import android.graphics.Rect;
import android.view.View;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 通用网格间距装饰器。
 *
 * 用于给 GridLayoutManager 的 RecyclerView 在行列之间加上统一的间距，
 * 避免网格项互相贴在一起（尤其是 Pad 宽屏下卡片式布局显得拥挤的问题）。
 *
 * 用法：
 *   recyclerView.addItemDecoration(new GridSpacingItemDecoration(spanCount, spacingPx, true));
 */
public class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {

    private final int spanCount;
    private final int spacing;
    private final boolean includeEdge;

    public GridSpacingItemDecoration(int spanCount, int spacing, boolean includeEdge) {
        this.spanCount = spanCount;
        this.spacing = spacing;
        this.includeEdge = includeEdge;
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        RecyclerView.LayoutManager lm = parent.getLayoutManager();
        if (!(lm instanceof GridLayoutManager) || spanCount <= 1) {
            // 非网格（如单列列表）时只在项之间留出纵向间距
            int position = parent.getChildAdapterPosition(view);
            outRect.top = position == 0 ? 0 : spacing;
            return;
        }

        int position = parent.getChildAdapterPosition(view);
        int column = position % spanCount;

        if (includeEdge) {
            outRect.left = spacing - column * spacing / spanCount;
            outRect.right = (column + 1) * spacing / spanCount;
            if (position < spanCount) {
                outRect.top = spacing;
            }
            outRect.bottom = spacing;
        } else {
            outRect.left = column * spacing / spanCount;
            outRect.right = spacing - (column + 1) * spacing / spanCount;
            if (position >= spanCount) {
                outRect.top = spacing;
            }
        }
    }
}
