package com.mobile.novabox.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;

import androidx.viewpager.widget.ViewPager;

import com.mobile.novabox.R;
import com.mobile.novabox.api.ApiConfig;
import com.mobile.novabox.base.BaseActivity;
import com.mobile.novabox.base.BaseLazyFragment;
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
        // 设置页不再显示底部/左侧导航栏，纯设置内容界面
        // 顶部栏返回按钮
        findViewById(R.id.ivBack).setOnClickListener(v -> onBackPressed());
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
                // 主容器:回首页 Tab,首页按缓存重载
                Intent intent = new Intent(mContext, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtras(createTabBundle(true));
                startActivity(intent);
            }else if(!currentLiveApi.equals(Hawk.get(HawkConfig.LIVE_API_URL, ""))){
                Intent intent = new Intent(mContext, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtras(createTabBundle(false));
                startActivity(intent);
            }
        } else {
            AppManager.getInstance().finishAllActivity();
            jumpActivity(MainActivity.class);
        }
        super.onBackPressed();
    }

    /** 跳回主容器:落到首页 Tab;useCache=true 表示首页按缓存重载 */
    private Bundle createTabBundle(boolean useCache) {
        Bundle bundle = new Bundle();
        bundle.putInt("tab", MainActivity.TAB_HOME);
        bundle.putBoolean("useCache", useCache);
        return bundle;
    }
}
