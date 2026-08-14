package com.mobile.novabox.ui.activity;

import android.view.View;

import com.mobile.novabox.R;
import com.mobile.novabox.base.BaseActivity;
import com.mobile.novabox.util.FastClickCheckUtil;
import com.mobile.novabox.util.OpenListApi;

/**
 * 我的页面：收纳 OpenList 网盘、本地视频、本地音乐、设置 等功能入口。
 */
public class MyActivity extends BaseActivity {

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_my;
    }

    @Override
    protected void init() {
        initView();
    }

    private void initView() {
        // 返回
        View ivBack = findViewById(R.id.ivBack);
        if (ivBack != null) {
            ivBack.setOnClickListener(v -> onBackPressed());
        }

        // OpenList 网盘入口
        View btnOpenList = findViewById(R.id.btnOpenList);
        if (btnOpenList != null) {
            btnOpenList.setOnClickListener(v -> {
                FastClickCheckUtil.check(v);
                if (OpenListApi.isLogin()) {
                    jumpActivity(OpenListBrowseActivity.class);
                } else {
                    jumpActivity(OpenListLoginActivity.class);
                }
            });
        }

        // 本地视频入口
        View btnLocalVideo = findViewById(R.id.btnLocalVideo);
        if (btnLocalVideo != null) {
            btnLocalVideo.setOnClickListener(v -> {
                FastClickCheckUtil.check(v);
                jumpActivity(LocalVideoActivity.class);
            });
        }

        // 本地音乐入口
        View btnLocalAudio = findViewById(R.id.btnLocalAudio);
        if (btnLocalAudio != null) {
            btnLocalAudio.setOnClickListener(v -> {
                FastClickCheckUtil.check(v);
                jumpActivity(LocalAudioActivity.class);
            });
        }

        // 设置入口
        View btnSetting = findViewById(R.id.btnSetting);
        if (btnSetting != null) {
            btnSetting.setOnClickListener(v -> {
                FastClickCheckUtil.check(v);
                jumpActivity(SettingActivity.class);
            });
        }

        initNav();
    }

    private void initNav() {
        // 左侧/底部导航：首页
        View navHome = findViewById(R.id.navHome);
        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                FastClickCheckUtil.check(v);
                jumpActivity(HomeActivity.class);
            });
        }

        // 左侧/底部导航：直播
        View navLive = findViewById(R.id.navLive);
        if (navLive != null) {
            navLive.setOnClickListener(v -> {
                FastClickCheckUtil.check(v);
                jumpActivity(LivePlayActivity.class);
            });
        }

        // 左侧/底部导航：我的（当前页，无需跳转）
    }
}
