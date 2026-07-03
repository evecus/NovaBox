package com.mobile.novabox.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;

import androidx.viewpager.widget.ViewPager;

import com.mobile.novabox.R;
import com.mobile.novabox.api.ApiConfig;
import com.mobile.novabox.base.BaseActivity;
import com.mobile.novabox.base.BaseLazyFragment;
import com.mobile.novabox.ui.activity.LivePlayActivity;
import com.mobile.novabox.ui.adapter.SettingPageAdapter;
import com.mobile.novabox.ui.fragment.ModelSettingFragment;
import com.mobile.novabox.util.AppManager;
import com.mobile.novabox.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.List;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class SettingActivity extends BaseActivity {
    private ViewPager mViewPager;
    private List<BaseLazyFragment> fragments = new ArrayList<>();
    private int defaultSelected = 0;
    private Handler mHandler = new Handler();
    private String homeSourceKey;
    private String currentApi;
    private int homeRec;
    private String currentLiveApi;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_setting;
    }

    @Override
    protected void init() {
        initView();
        initData();
    }

    private void initView() {
        mViewPager = findViewById(R.id.mViewPager);

        // 底部导航：首页
        findViewById(R.id.navHome).setOnClickListener(v -> {
            // 返回首页，保留原 onBackPressed 逻辑
            onBackPressed();
        });

        // 底部导航：直播
        findViewById(R.id.navLive).setOnClickListener(v -> {
            Intent intent = new Intent(this, LivePlayActivity.class);
            startActivity(intent);
        });

        // 底部导航：设置（当前页，无操作）
        // navSetting 点击不做任何事
    }

    private void initData() {
        currentApi = Hawk.get(HawkConfig.API_URL, "");
        homeSourceKey = ApiConfig.get().getHomeSourceBean().getKey();
        homeRec = Hawk.get(HawkConfig.HOME_REC, 0);
        currentLiveApi = Hawk.get(HawkConfig.LIVE_API_URL, "");
        initViewPager();
    }

    private void initViewPager() {
        fragments.add(ModelSettingFragment.newInstance());
        SettingPageAdapter pageAdapter = new SettingPageAdapter(getSupportFragmentManager(), fragments);
        mViewPager.setAdapter(pageAdapter);
        mViewPager.setCurrentItem(0);
    }

    private Runnable mDevModeRun = new Runnable() {
        @Override
        public void run() {
            devMode = "";
        }
    };


    public interface DevModeCallback {
        void onChange();
    }

    public static DevModeCallback callback = null;

    String devMode = "";

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            switch (keyCode) {
                case KeyEvent.KEYCODE_0:
                    mHandler.removeCallbacks(mDevModeRun);
                    devMode += "0";
                    mHandler.postDelayed(mDevModeRun, 200);
                    if (devMode.length() >= 4) {
                        if (callback != null) {
                            callback.onChange();
                        }
                    }
                    break;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (currentApi.equals(Hawk.get(HawkConfig.API_URL, ""))) {
            if ((homeSourceKey != null && !homeSourceKey.equals(Hawk.get(HawkConfig.HOME_API, "")))  || homeRec != Hawk.get(HawkConfig.HOME_REC, 0)) {
                jumpActivity(HomeActivity.class, createBundle());
            }else if(!currentLiveApi.equals(Hawk.get(HawkConfig.LIVE_API_URL, ""))){
                jumpActivity(HomeActivity.class);
            }
        } else {
            AppManager.getInstance().finishAllActivity();
            jumpActivity(HomeActivity.class);
        }
        super.onBackPressed();
    }

    private Bundle createBundle() {
        Bundle bundle = new Bundle();
        bundle.putBoolean("useCache", true);
        return bundle;
    }
}
