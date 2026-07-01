package com.mobile.novabox.ui.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.mobile.novabox.R;
import com.mobile.novabox.base.BaseActivity;
import com.mobile.novabox.util.HawkConfig;
import com.mobile.novabox.util.OpenListApi;
import com.orhanobut.hawk.Hawk;

/**
 * OpenList 网盘登录页（手机/平板适配）。
 * 背景自动复用 NovaBox 全局壁纸（BaseActivity 已处理）。
 * 字体 / 图标均为黑色。
 */
public class OpenListLoginActivity extends BaseActivity {
    private EditText etServerUrl;
    private EditText etUsername;
    private EditText etPassword;
    private TextView tvError;
    private TextView btnLogin;
    private ProgressBar pbLogin;
    private boolean requesting = false;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_openlist_login;
    }

    @Override
    protected void init() {
        etServerUrl = findViewById(R.id.etOpenListServerUrl);
        etUsername  = findViewById(R.id.etOpenListUsername);
        etPassword  = findViewById(R.id.etOpenListPassword);
        tvError     = findViewById(R.id.tvOpenListError);
        btnLogin    = findViewById(R.id.btnOpenListLogin);
        pbLogin     = findViewById(R.id.pbOpenListLogin);

        // 预填上次使用的服务器地址 / 用户名
        String lastUrl  = OpenListApi.getServerUrl();
        String lastUser = Hawk.get(HawkConfig.OPENLIST_USERNAME, "");
        if (!TextUtils.isEmpty(lastUrl))  etServerUrl.setText(lastUrl);
        if (!TextUtils.isEmpty(lastUser)) etUsername.setText(lastUser);

        btnLogin.setOnClickListener(v -> doLogin());

        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            doLogin();
            return true;
        });

        // 自动聚焦到第一个空字段
        etServerUrl.postDelayed(() -> {
            if (TextUtils.isEmpty(etServerUrl.getText().toString())) {
                etServerUrl.requestFocus();
            } else if (TextUtils.isEmpty(etUsername.getText().toString())) {
                etUsername.requestFocus();
            } else {
                etPassword.requestFocus();
            }
        }, 200);
    }

    private void doLogin() {
        if (requesting) return;
        String url  = etServerUrl.getText().toString().trim();
        String user = etUsername.getText().toString().trim();
        String pwd  = etPassword.getText().toString();
        if (TextUtils.isEmpty(url)) {
            showError("请输入服务器地址");
            etServerUrl.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(user)) {
            showError("请输入用户名");
            etUsername.requestFocus();
            return;
        }
        requesting = true;
        tvError.setVisibility(View.GONE);
        pbLogin.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);

        OpenListApi.login(url, user, pwd, new OpenListApi.Callback<String>() {
            @Override
            public void onSuccess(String data) {
                runOnUiThread(() -> {
                    requesting = false;
                    pbLogin.setVisibility(View.GONE);
                    btnLogin.setEnabled(true);
                    Toast.makeText(mContext, "登录成功", Toast.LENGTH_SHORT).show();
                    jumpActivity(OpenListBrowseActivity.class);
                    finish();
                });
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    requesting = false;
                    pbLogin.setVisibility(View.GONE);
                    btnLogin.setEnabled(true);
                    showError(msg);
                });
            }
        });
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }
}
