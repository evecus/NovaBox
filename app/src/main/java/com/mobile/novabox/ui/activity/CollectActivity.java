package com.mobile.novabox.ui.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.widget.TextView;
import android.widget.ImageView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.mobile.novabox.R;
import com.mobile.novabox.api.ApiConfig;
import com.mobile.novabox.base.BaseActivity;
import com.mobile.novabox.cache.RoomDataManger;
import com.mobile.novabox.cache.VodCollect;
import com.mobile.novabox.event.RefreshEvent;
import com.mobile.novabox.ui.adapter.CollectAdapter;
import com.mobile.novabox.ui.dialog.ConfirmClearDialog;
import com.mobile.novabox.util.FastClickCheckUtil;
import com.mobile.novabox.util.HawkConfig;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

public class CollectActivity extends BaseActivity {

    /**
     * dp 转 px，用于 GridSpacingItemDecoration 的间距参数。
     */
    private int dp2px(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private ImageView tvDelete;
    private ImageView tvClear;
    private TextView tvDelTip;
    private RecyclerView mGridView;
    public static CollectAdapter collectAdapter;
    private boolean delMode = false;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_collect;
    }

    @Override
    protected void init() {
        initView();
        initData();
    }

    private void toggleDelMode() {
    	HawkConfig.hotVodDelete = !HawkConfig.hotVodDelete;
        collectAdapter.notifyDataSetChanged();
        delMode = !delMode;
        tvDelTip.setVisibility(delMode ? View.VISIBLE : View.GONE);
    }

    private void initView() {
        EventBus.getDefault().register(this);
        tvDelete = findViewById(R.id.tvDelete);
        tvClear = findViewById(R.id.tvClear);
        tvDelTip = findViewById(R.id.tvDelTip);
        mGridView = findViewById(R.id.mGridView);
        mGridView.setHasFixedSize(true);
        int spanCount = com.mobile.novabox.util.PadUiHelper.getCollectGridSpanCount(this);
        mGridView.setLayoutManager(new GridLayoutManager(this.mContext, spanCount));
        // Pad 端加网格间距，避免卡片贴在一起显得拥挤；手机端间距为 0，保持原样。
        int spacingPx = dp2px(com.mobile.novabox.util.PadUiHelper.getCollectGridItemSpacingDp(this));
        mGridView.addItemDecoration(new com.mobile.novabox.ui.widget.GridSpacingItemDecoration(spanCount, spacingPx, true));
        collectAdapter = new CollectAdapter();
        mGridView.setAdapter(collectAdapter);
        // 返回键
        View ivBack = findViewById(R.id.ivBack);
        if (ivBack != null) {
            ivBack.setOnClickListener(v -> onBackPressed());
        }
        // 强制黑色顶栏(标题 + 左/右图标):layout 里虽然已经写了 android:textColor/@+tint=black,
        // 但该项目开启了 night mode,深色背景下 AppCompat 会把硬编码颜色反向解析为白色。
        // 在 init 里强行 setColorFilter / setTextColor 覆盖任何主题反转。
        if (ivBack instanceof ImageView) {
            ((ImageView) ivBack).setColorFilter(Color.BLACK);
        }
        if (tvClear != null) tvClear.setColorFilter(Color.BLACK);
        TextView tvTitle = findViewById(R.id.tvTitle);
        if (tvTitle != null) tvTitle.setTextColor(Color.BLACK);
        // 提示文字常驻显示
        if (tvDelTip != null) {
            tvDelTip.setTextColor(Color.BLACK);   // 强制黑色,覆盖 DayNight 主题反转
            tvDelTip.setVisibility(View.VISIBLE);
        }
        tvClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ConfirmClearDialog dialog = new ConfirmClearDialog(mContext, "Collect");
                dialog.show();
            }
        });
// phone: TV border key listener removed
// phone: TV item listener removed - use adapter click callbacks
        collectAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                VodCollect vodInfo = collectAdapter.getData().get(position);
                if (vodInfo != null) {
                    if (delMode) {
                        collectAdapter.remove(position);
                        RoomDataManger.deleteVodCollect(vodInfo.getId());
                    } else {
                        if (ApiConfig.get().getSource(vodInfo.sourceKey) != null) {
                            Bundle bundle = new Bundle();
                            bundle.putString("id", vodInfo.vodId);
                            bundle.putString("sourceKey", vodInfo.sourceKey);
                            bundle.putString("title", vodInfo.name);
                            bundle.putString("picture", vodInfo.pic);
                            jumpActivity(DetailActivity.class, bundle);
                        } else {
                            Intent newIntent = new Intent(mContext, SearchActivity.class);
                            newIntent.putExtra("title", vodInfo.name);
                            newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(newIntent);
                        }
                    }
                }
            }
        });
        collectAdapter.setOnItemLongClickListener(new BaseQuickAdapter.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
                VodCollect vodInfo = collectAdapter.getData().get(position);
                if (vodInfo != null) {
                    collectAdapter.remove(position);
                    RoomDataManger.deleteVodCollect(vodInfo.getId());
                }
                return true;
            }
        });
    }

    private void initData() {
        List<VodCollect> allVodRecord = RoomDataManger.getAllVodCollect();
        List<VodCollect> vodInfoList = new ArrayList<>();
        for (VodCollect vodInfo : allVodRecord) {
            vodInfoList.add(vodInfo);
        }
        collectAdapter.setNewData(vodInfoList);
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_HISTORY_REFRESH) {
            initData();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onBackPressed() {
        if (delMode) {
            toggleDelMode();
            return;
        }
        super.onBackPressed();
    }
}