package com.mobile.novabox.ui.activity;

import android.content.Intent;
import android.view.View;

import com.mobile.novabox.R;
import com.mobile.novabox.base.BaseActivity;
import com.mobile.novabox.ui.dialog.AboutDialog;
import com.mobile.novabox.ui.dialog.BackupDialog;
import com.mobile.novabox.util.FastClickCheckUtil;
import com.mobile.novabox.util.OpenListApi;

/**
 * 我的页面：收纳 OpenList 网盘、本地视频、本地音乐、设置 等功能入口。
 */
public class MyActivity extends BaseActivity {

    // 当前正在显示的备份弹窗；用于把 SAF 文件/目录选择器的 onActivityResult 转发给它。
    // Dialog 无法自己接收 startActivityForResult 的结果，必须由宿主 Activity 转发。
    private BackupDialog activeBackupDialog;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_my;
    }

    @Override
    protected void init() {
        initView();
    }

    private void initView() {
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

        // 下载管理入口（本地音乐下面）
        View btnDownload = findViewById(R.id.btnDownload);
        if (btnDownload != null) {
            btnDownload.setOnClickListener(v -> {
                FastClickCheckUtil.check(v);
                jumpActivity(DownloadActivity.class);
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

        // 数据备份入口（从设置页移到"我的"页，放设置按钮底部）
        View btnBackup = findViewById(R.id.btnBackup);
        if (btnBackup != null) {
            btnBackup.setOnClickListener(v -> {
                FastClickCheckUtil.check(v);
                BackupDialog dialog = new BackupDialog(this);
                activeBackupDialog = dialog;
                dialog.setOnDismissListener(d -> {
                    if (activeBackupDialog == dialog) {
                        activeBackupDialog = null;
                    }
                });
                dialog.show();
            });
        }

        // 关于入口（从设置页移到"我的"页，放数据备份底部）
        View btnAbout = findViewById(R.id.btnAbout);
        if (btnAbout != null) {
            btnAbout.setOnClickListener(v -> {
                FastClickCheckUtil.check(v);
                AboutDialog dialog = new AboutDialog(this);
                dialog.show();
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 转发给正在显示的备份弹窗，处理 SAF 目录/文件选择器的返回结果
        if (activeBackupDialog != null) {
            activeBackupDialog.onActivityResult(requestCode, resultCode, data);
        }
    }
}
