package com.mobile.novabox.ui.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.mobile.novabox.R;
import com.mobile.novabox.base.BaseActivity;
import com.mobile.novabox.bean.OpenListFile;
import com.mobile.novabox.bean.OpenListFsListData;
import com.mobile.novabox.ui.adapter.OpenListFileAdapter;
import com.mobile.novabox.util.OpenListApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * OpenList 网盘浏览页：逐层浏览目录，点击视频/音频文件分别跳转播放。
 * 手机/平板适配，黑色字体图标，复用 NovaBox 全局壁纸背景。
 */
public class OpenListBrowseActivity extends BaseActivity {
    private TextView tvPath;
    private TextView tvEmpty;
    private TextView tvLogout;
    private ProgressBar pbLoading;
    private RecyclerView fileList;
    private OpenListFileAdapter adapter;
    private String currentPath = "/";
    private boolean requesting = false;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_openlist_browse;
    }

    @Override
    protected void init() {
        if (!OpenListApi.isLogin()) {
            jumpActivity(OpenListLoginActivity.class);
            finish();
            return;
        }

        tvPath   = findViewById(R.id.tvOpenListPath);
        tvEmpty  = findViewById(R.id.tvOpenListEmpty);
        tvLogout = findViewById(R.id.tvOpenListLogout);
        pbLoading = findViewById(R.id.pbOpenListLoading);
        fileList = findViewById(R.id.rvOpenListFiles);

        // 返回按钮
        ImageView ivBack = findViewById(R.id.ivOpenListBack);
        if (ivBack != null) {
            ivBack.setOnClickListener(v -> {
                if (!isRoot(currentPath)) {
                    loadDir(parentOf(currentPath));
                } else {
                    finish();
                }
            });
        }

        adapter = new OpenListFileAdapter();
        fileList.setLayoutManager(new LinearLayoutManager(this));
        fileList.setAdapter(adapter);

        adapter.setOnItemClickListener((baseAdapter, view, position) -> {
            OpenListFile item = (OpenListFile) baseAdapter.getItem(position);
            if (item != null) open(item);
        });

        tvLogout.setOnClickListener(v -> {
            OpenListApi.logout();
            jumpActivity(OpenListLoginActivity.class);
            finish();
        });

        loadDir("/");
    }

    private boolean isRoot(String path) {
        return path == null || path.isEmpty() || path.equals("/");
    }

    private String parentOf(String path) {
        if (isRoot(path)) return "/";
        String p = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int idx = p.lastIndexOf('/');
        if (idx <= 0) return "/";
        return p.substring(0, idx);
    }

    private void loadDir(final String path) {
        if (requesting) return;
        requesting = true;
        pbLoading.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        OpenListApi.listFiles(path, new OpenListApi.Callback<OpenListFsListData>() {
            @Override
            public void onSuccess(final OpenListFsListData data) {
                runOnUiThread(() -> {
                    requesting = false;
                    if (isActivityUnavailable()) return;
                    currentPath = path;
                    tvPath.setText(currentPath);
                    pbLoading.setVisibility(View.GONE);

                    List<OpenListFile> files = new ArrayList<>();
                    OpenListFile parentItem = null;
                    if (!isRoot(currentPath)) {
                        parentItem = new OpenListFile();
                        parentItem.name = "..";
                        parentItem.isDir = true;
                        parentItem.parentPath = parentOf(currentPath);
                        files.add(parentItem);
                    }
                    if (data.content != null) {
                        List<OpenListFile> sorted = new ArrayList<>(data.content);
                        for (OpenListFile f : sorted) f.parentPath = currentPath;
                        Collections.sort(sorted, (a, b) -> {
                            if (a.isDir != b.isDir) return a.isDir ? -1 : 1;
                            return a.name.compareToIgnoreCase(b.name);
                        });
                        files.addAll(sorted);
                    }
                    adapter.setParentItem(parentItem);
                    adapter.setNewData(files);
                    tvEmpty.setVisibility((data.content == null || data.content.isEmpty()) ? View.VISIBLE : View.GONE);
                });
            }

            @Override
            public void onError(final String msg) {
                runOnUiThread(() -> {
                    requesting = false;
                    if (isActivityUnavailable()) return;
                    pbLoading.setVisibility(View.GONE);
                    Toast.makeText(mContext, TextUtils.isEmpty(msg) ? "目录加载失败" : msg, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void open(OpenListFile item) {
        if (item.isDir) {
            loadDir(item.name.equals("..") ? item.parentPath : item.fullPath());
            return;
        }
        String path = item.fullPath();
        if (item.isVideo()) {
            // 收集当前目录下所有视频，计算当前文件序号
            List<OpenListFile> allItems = adapter.getData();
            int videoIndex = 0;
            List<OpenListFile> videoItems = new ArrayList<>();
            for (OpenListFile f : allItems) {
                if (!f.isDir && f.isVideo()) videoItems.add(f);
            }
            for (int i = 0; i < videoItems.size(); i++) {
                if (videoItems.get(i).name.equals(item.name)) { videoIndex = i; break; }
            }
            Bundle bundle = new Bundle();
            bundle.putString("path", path);
            bundle.putString("name", item.name);
            bundle.putString("dirPath", currentPath);
            bundle.putInt("index", videoIndex);
            jumpActivity(OpenListVideoPlayerActivity.class, bundle);
        } else if (item.isAudio()) {
            Bundle bundle = new Bundle();
            bundle.putString("path", path);
            bundle.putString("name", item.name);
            jumpActivity(OpenListAudioPlayerActivity.class, bundle);
        } else {
            Toast.makeText(mContext, "暂不支持该文件类型", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (!isRoot(currentPath)) {
            loadDir(parentOf(currentPath));
        } else {
            super.onBackPressed();
        }
    }

    private boolean isActivityUnavailable() {
        return isFinishing() || (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed());
    }
}
