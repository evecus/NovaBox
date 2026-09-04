package com.mobile.novabox.ui.fragment;

import android.content.Intent;

import com.mobile.novabox.R;
import com.mobile.novabox.base.BaseLazyFragment;
import com.mobile.novabox.ui.dialog.AboutDialog;
import com.mobile.novabox.ui.dialog.BackupDialog;
import com.mobile.novabox.util.FastClickCheckUtil;
import com.mobile.novabox.util.OpenListApi;
import com.mobile.novabox.ui.activity.DownloadActivity;
import com.mobile.novabox.ui.activity.LocalAudioActivity;
import com.mobile.novabox.ui.activity.LocalVideoActivity;
import com.mobile.novabox.ui.activity.OpenListBrowseActivity;
import com.mobile.novabox.ui.activity.OpenListLoginActivity;
import com.mobile.novabox.ui.activity.SettingActivity;

import android.view.View;

/**
 * "我的"页面：收纳 OpenList 网盘、本地视频、本地音乐、设置 等功能入口。
 * 由容器 MainActivity 以 Tab 形式承载(原 MyActivity 转换而来)。
 */
public class MyFragment extends BaseLazyFragment {

    // 当前正在显示的备份弹窗；用于把 SAF 文件/目录选择器的 onActivityResult 转发给它。
    // Dialog 无法自己接收 startActivityForResult 的结果，必须由宿主转发。
    private BackupDialog activeBackupDialog;

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_my;
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
                BackupDialog dialog = new BackupDialog(mActivity);
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
                AboutDialog dialog = new AboutDialog(mActivity);
                dialog.show();
            });
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 由容器 MainActivity 转发而来:转发给正在显示的备份弹窗，
        // 处理 SAF 目录/文件选择器的返回结果
        if (activeBackupDialog != null) {
            activeBackupDialog.onActivityResult(requestCode, resultCode, data);
        }
    }
}
