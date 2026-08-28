package com.mobile.novabox.ui.activity;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.mobile.novabox.R;
import com.mobile.novabox.base.BaseActivity;
import com.mobile.novabox.cache.DownloadDao;
import com.mobile.novabox.cache.DownloadEntity;
import com.mobile.novabox.data.AppDataManager;
import com.mobile.novabox.download.DownloadManager;
import com.mobile.novabox.event.DownloadEvent;
import com.mobile.novabox.ui.adapter.DownloadAdapter;
import com.mobile.novabox.util.FastClickCheckUtil;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 下载管理页:
 * - 列表形式列出所有下载任务(历史/进行中),一行一个
 * - 点击已完成项 → 系统播放器播放本地文件
 * - 点击失败/取消项 → 重新下载
 * - 长按 → 弹出确认删除弹窗(可选是否同时删除本地文件)
 * 封面缩略图由 DownloadAdapter 用 Glide 直接解码本地视频文件第一帧,列表绑定时按需加载,
 * 这里不需要额外的截图/缓存调度逻辑。
 */
public class DownloadActivity extends BaseActivity {

    private RecyclerView mGridView;
    private DownloadAdapter adapter;
    private DownloadDao dao;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_download;
    }

    @Override
    protected void init() {
        dao = AppDataManager.get().getDownloadDao();
        EventBus.getDefault().register(this);
        initView();
        initData();
    }

    private void initView() {
        mGridView = findViewById(R.id.mGridView);
        mGridView.setHasFixedSize(true);
        mGridView.setLayoutManager(new LinearLayoutManager(this.mContext, LinearLayoutManager.VERTICAL, false));
        adapter = new DownloadAdapter();
        mGridView.setAdapter(adapter);

        View ivBack = findViewById(R.id.ivBack);
        if (ivBack != null) ivBack.setOnClickListener(v -> onBackPressed());
        if (ivBack instanceof ImageView) ((ImageView) ivBack).setColorFilter(Color.BLACK);

        TextView tvTitle = findViewById(R.id.tvTitle);
        if (tvTitle != null) tvTitle.setTextColor(Color.BLACK);

        adapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                if (position < 0 || position >= DownloadActivity.this.adapter.getData().size()) return;
                FastClickCheckUtil.check(view);
                DownloadEntity e = DownloadActivity.this.adapter.getData().get(position);
                if (e == null) return;
                switch (e.status) {
                    case DownloadEntity.STATUS_DONE:
                        playLocal(e);
                        break;
                    case DownloadEntity.STATUS_FAILED:
                    case DownloadEntity.STATUS_CANCELED:
                        DownloadManager.get().retry(e.getId());
                        break;
                    case DownloadEntity.STATUS_DOWNLOADING:
                    case DownloadEntity.STATUS_QUEUED:
                        DownloadManager.get().cancel(e.getId());
                        break;
                }
            }
        });

        adapter.setOnItemLongClickListener(new BaseQuickAdapter.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
                if (position < 0 || position >= DownloadActivity.this.adapter.getData().size()) return true;
                DownloadEntity e = DownloadActivity.this.adapter.getData().get(position);
                if (e != null) {
                    showDeleteConfirmDialog(e);
                }
                return true;
            }
        });
    }

    /** 长按弹出的确认删除弹窗:提示信息 + "同时删除本地文件"勾选框(默认不勾选) + 删除/取消按钮 */
    private void showDeleteConfirmDialog(DownloadEntity e) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_download_delete_confirm, null);
        TextView tvMsg = view.findViewById(R.id.tvDeleteMsg);
        CheckBox cbDeleteFile = view.findViewById(R.id.cbDeleteFile);

        String name = (e.vodName == null ? "" : e.vodName) + (e.episodeName == null ? "" : " " + e.episodeName);
        if (tvMsg != null) tvMsg.setText("确定要删除「" + name + "」的下载记录吗？");
        if (cbDeleteFile != null) cbDeleteFile.setChecked(false);

        new AlertDialog.Builder(this)
                .setTitle("删除下载")
                .setView(view)
                .setPositiveButton("删除", (dialog, which) -> {
                    boolean deleteFile = cbDeleteFile != null && cbDeleteFile.isChecked();
                    DownloadManager.get().delete(e.getId(), deleteFile);
                    initData();
                    Toast.makeText(DownloadActivity.this, deleteFile ? "已删除记录及文件" : "已删除下载记录", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private DownloadDao dao() {
        if (dao == null) dao = AppDataManager.get().getDownloadDao();
        return dao;
    }

    /** 本地文件播放:复用 NovaBox 自带的本地播放器 LocalPlayerActivity(dkplayer 内核,支持本地路径) */
    private void playLocal(DownloadEntity e) {
        if (e.localPath == null || e.localPath.isEmpty()) {
            Toast.makeText(this, "文件路径为空", Toast.LENGTH_SHORT).show();
            return;
        }
        File f = new File(e.localPath);
        if (!f.exists()) {
            Toast.makeText(this, "文件不存在(可能已被移动)", Toast.LENGTH_SHORT).show();
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putString("videoPath", e.localPath);
        bundle.putString("videoTitle", (e.vodName == null ? "" : e.vodName) + (e.episodeName == null ? "" : " " + e.episodeName));
        jumpActivity(LocalPlayerActivity.class, bundle);
    }

    private void initData() {
        List<DownloadEntity> all = dao.getVisible();
        List<DownloadEntity> list = new ArrayList<>(all);
        adapter.setNewData(list);
        adapter.notifyDataSetChanged();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDownloadEvent(DownloadEvent event) {
        if (isFinishing() || isDestroyed()) return;
        if (event.type == DownloadEvent.TYPE_PROGRESS) {
            // 进度:直接用事件里的实时数据局部刷新,不走 Room(避免落库延迟 + 列表闪烁)
            adapter.updateFromEvent(event);
        } else {
            // 状态变更(新任务/完成/失败/取消):重新从 Room 拉全量;任务变成"已完成"时
            // 文件已就位,列表重绘会让 DownloadAdapter 用 Glide 自动加载出封面
            DownloadEntity e = dao.getById(event.downloadId);
            if (e == null) return;
            initData();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}
