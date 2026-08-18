package com.mobile.novabox.ui.activity;

import android.content.Intent;
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
import com.orhanobut.hawk.Hawk;

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
    private TextView tvGoHome;
    private ProgressBar pbLoading;
    private RecyclerView fileList;
    private OpenListFileAdapter adapter;
    private String currentPath = "/";
    private boolean requesting = false;
    private int currentPage = 1;
    private boolean hasMore = true;
    private boolean loadingMore = false;

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
        tvGoHome = findViewById(R.id.tvOpenListGoHome);
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

        // 滚动到底部自动加载下一页(BRVAH 2.9 API:listener 直接挂 adapter)
        adapter.setOnLoadMoreListener(new BaseQuickAdapter.RequestLoadMoreListener() {
            @Override
            public void onLoadMoreRequested() {
                loadMore();
            }
        });
        adapter.setEnableLoadMore(true);

        adapter.setOnItemClickListener((baseAdapter, view, position) -> {
            OpenListFile item = (OpenListFile) baseAdapter.getItem(position);
            if (item != null) open(item);
        });

        // 回到首页：清空返回栈，直接跳回 HomeActivity
        tvGoHome.setOnClickListener(v -> {
            Intent intent = new Intent(mContext, HomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        tvLogout.setOnClickListener(v -> {
            OpenListApi.logout();
            jumpActivity(OpenListLoginActivity.class);
            finish();
        });

        // 每次进入浏览页时，若用户之前勾选了"保存登录信息"，
        // 则先用保存的凭据静默重新登录，刷新服务端 token，避免 token 过期导致内容加载失败。
        // 登录成功后再发起目录请求；登录失败则跳回登录页让用户重新输入。
        boolean hasSavedLogin = Hawk.get(com.mobile.novabox.util.HawkConfig.OPENLIST_SAVE_LOGIN, false);
        if (hasSavedLogin) {
            String savedUrl  = Hawk.get(com.mobile.novabox.util.HawkConfig.OPENLIST_SAVED_URL, "");
            String savedUser = Hawk.get(com.mobile.novabox.util.HawkConfig.OPENLIST_SAVED_USERNAME, "");
            String savedPwd  = Hawk.get(com.mobile.novabox.util.HawkConfig.OPENLIST_SAVED_PASSWORD, "");
            if (!android.text.TextUtils.isEmpty(savedUrl) && !android.text.TextUtils.isEmpty(savedUser)) {
                pbLoading.setVisibility(View.VISIBLE);
                OpenListApi.login(savedUrl, savedUser, savedPwd, new OpenListApi.Callback<String>() {
                    @Override
                    public void onSuccess(String token) {
                        runOnUiThread(() -> {
                            if (isActivityUnavailable()) return;
                            // token 已由 OpenListApi.login() 写入 Hawk，直接加载目录
                            loadDir("/");
                        });
                    }

                    @Override
                    public void onError(String msg) {
                        runOnUiThread(() -> {
                            if (isActivityUnavailable()) return;
                            pbLoading.setVisibility(View.GONE);
                            // 登录失败（凭据无效），跳回登录页
                            Toast.makeText(mContext, "登录已失效，请重新登录", Toast.LENGTH_SHORT).show();
                            OpenListApi.logout();
                            jumpActivity(OpenListLoginActivity.class);
                            finish();
                        });
                    }
                });
                return; // 等待登录回调，不在此处直接 loadDir
            }
        }
        // 没有保存登录信息：直接用已有 token 加载目录（原有行为）
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
        currentPage = 1;
        hasMore = true;
        pbLoading.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        OpenListApi.listFiles(path, 1, OpenListApi.PAGE_SIZE, new OpenListApi.Callback<OpenListFsListData>() {
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
                    // 分页:本次返回条数 == 每页条数 且还有更多,才能继续翻页
                    hasMore = data.content != null && data.content.size() >= OpenListApi.PAGE_SIZE
                            && (data.total <= 0 || currentPage * OpenListApi.PAGE_SIZE < data.total);
                    // 先 setNewData,再设置 load-more 状态!
                    // BRVAH 2.9 的 setNewData 会把 load-more 状态重置为"加载中",
                    // 若先 loadMoreEnd 再 setNewData,footer 会永远卡在"正在加载中"。
                    adapter.setParentItem(parentItem);
                    adapter.setNewData(files);
                    if (hasMore) {
                        adapter.setEnableLoadMore(true);
                        adapter.loadMoreComplete();
                    } else {
                        // 全部加载完(不足一页):彻底关闭 footer,不再显示"加载中"
                        adapter.setEnableLoadMore(false);
                    }
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

    /** 滚动到底部时加载下一页(追加到列表尾部) */
    private void loadMore() {
        if (requesting || loadingMore || !hasMore) return;
        loadingMore = true;
        final int nextPage = currentPage + 1;
        OpenListApi.listFiles(currentPath, nextPage, OpenListApi.PAGE_SIZE, new OpenListApi.Callback<OpenListFsListData>() {
            @Override
            public void onSuccess(final OpenListFsListData data) {
                runOnUiThread(() -> {
                    loadingMore = false;
                    if (isActivityUnavailable()) return;
                    if (data.content == null || data.content.isEmpty()) {
                        hasMore = false;
                        adapter.setEnableLoadMore(false);
                        return;
                    }
                    currentPage = nextPage;
                    List<OpenListFile> sorted = new ArrayList<>(data.content);
                    for (OpenListFile f : sorted) f.parentPath = currentPath;
                    Collections.sort(sorted, (a, b) -> {
                        if (a.isDir != b.isDir) return a.isDir ? -1 : 1;
                        return a.name.compareToIgnoreCase(b.name);
                    });
                    adapter.addData(sorted);
                    hasMore = data.content.size() >= OpenListApi.PAGE_SIZE
                            && (data.total <= 0 || currentPage * OpenListApi.PAGE_SIZE < data.total);
                    if (!hasMore) {
                        // 全部加载完:彻底关闭 footer,不再显示"加载中"
                        adapter.setEnableLoadMore(false);
                    } else {
                        adapter.loadMoreComplete();
                    }
                });
            }

            @Override
            public void onError(final String msg) {
                runOnUiThread(() -> {
                    loadingMore = false;
                    if (isActivityUnavailable()) return;
                    adapter.loadMoreFail();
                    Toast.makeText(mContext, TextUtils.isEmpty(msg) ? "加载更多失败" : msg, Toast.LENGTH_SHORT).show();
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
