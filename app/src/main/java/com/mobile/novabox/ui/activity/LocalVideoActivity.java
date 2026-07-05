package com.mobile.novabox.ui.activity;

import android.Manifest;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mobile.novabox.R;
import com.mobile.novabox.base.BaseActivity;
import com.mobile.novabox.ui.adapter.VideoFolderAdapter;
import com.mobile.novabox.util.PadUiHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalVideoActivity extends BaseActivity {

    // 分类常量
    private static final int CAT_VIDEO  = 0;
    private static final int CAT_FOLDER = 1;

    // 排序常量（视频模式）
    private static final int SORT_NAME_ASC  = 0;
    private static final int SORT_NAME_DESC = 1;
    private static final int SORT_TIME_ASC  = 2;
    private static final int SORT_TIME_DESC = 3;

    // 排序常量（文件夹模式）
    private static final int SORT_FOLDER_NAME_ASC  = 0;
    private static final int SORT_FOLDER_NAME_DESC = 1;

    private static final int REQUEST_STORAGE = 101;
    private static final String[] VIDEO_EXTS = {
            ".mp4", ".mkv", ".avi", ".mov", ".flv", ".wmv",
            ".ts", ".m4v", ".rmvb", ".3gp", ".webm"
    };

    private RecyclerView rvFolders;
    private VideoFolderAdapter folderAdapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean hasScanned = false;

    private int currentCategory = CAT_VIDEO;
    private int currentSortVideo  = SORT_NAME_ASC;
    private int currentSortFolder = SORT_FOLDER_NAME_ASC;

    // 扫描到的原始数据
    private List<File>                                 allVideoFiles = new ArrayList<>();
    private List<Map.Entry<String, List<File>>>        folderEntries = new ArrayList<>();

    @Override
    protected int getLayoutResID() { return R.layout.activity_local_video; }

    @Override
    protected void init() {
        findViewById(R.id.ivBack).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.ivLinkPlay).setOnClickListener(v -> showLinkDialog());
        findViewById(R.id.tvRefresh).setOnClickListener(v -> {
            Toast.makeText(this, "正在扫描本地视频...", Toast.LENGTH_SHORT).show();
            scanVideos();
        });
        findViewById(R.id.tvCategory).setOnClickListener(v -> showCategoryDialog());
        findViewById(R.id.tvSort).setOnClickListener(v -> showSortDialog());

        rvFolders = findViewById(R.id.rvFolders);
        folderAdapter = new VideoFolderAdapter();
        if (PadUiHelper.isPad(this)) {
            rvFolders.setLayoutManager(new GridLayoutManager(this, 2));
        } else {
            rvFolders.setLayoutManager(new LinearLayoutManager(this));
        }
        rvFolders.setAdapter(folderAdapter);

        checkPermissionAndScan();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasScanned && folderAdapter != null && folderAdapter.getData().isEmpty()) {
            checkPermissionAndScan();
        }
    }

    // ─── 权限 ──────────────────────────────────────────────────────────────────

    private void checkPermissionAndScan() {
        String perm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_VIDEO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{perm}, REQUEST_STORAGE);
        } else {
            scanVideos();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE) {
            if ((grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    || hasStoragePermission()) {
                scanVideos();
            } else {
                Toast.makeText(this, "需要存储权限才能扫描本地视频", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean hasStoragePermission() {
        String perm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_VIDEO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED;
    }

    // ─── 扫描 ──────────────────────────────────────────────────────────────────

    private void scanVideos() {
        hasScanned = true;
        if (executor.isShutdown()) executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<File> files = doScanAll();
            mainHandler.post(() -> {
                allVideoFiles = files;
                buildFolderEntries();
                refreshList();
                if (files.isEmpty())
                    Toast.makeText(this, "未找到本地视频", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private List<File> doScanAll() {
        List<File> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] proj = {MediaStore.Video.Media.DATA, MediaStore.Video.Media.DATE_MODIFIED};
        ContentResolver cr = getContentResolver();
        try (Cursor c = cr.query(uri, proj, null, null,
                MediaStore.Video.Media.DATE_MODIFIED + " DESC")) {
            if (c != null) {
                int iData = c.getColumnIndex(MediaStore.Video.Media.DATA);
                while (c.moveToNext()) {
                    String path = iData >= 0 ? c.getString(iData) : null;
                    if (path == null || !isVideoFile(path) || !seen.add(path)) continue;
                    File f = new File(path);
                    if (f.exists()) list.add(f);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        if (list.isEmpty()) {
            for (String root : new String[]{"/sdcard", "/storage/emulated/0"}) {
                File dir = new File(root);
                if (!dir.exists() || !dir.canRead()) continue;
                scanFs(dir, list, seen, 0);
            }
        }
        return list;
    }

    private void scanFs(File dir, List<File> list, Set<String> seen, int depth) {
        if (depth > 8) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                String n = f.getName();
                if (!n.startsWith(".") && !n.equals("Android")) scanFs(f, list, seen, depth + 1);
            } else if (isVideoFile(f.getName()) && seen.add(f.getAbsolutePath())) {
                list.add(f);
            }
        }
    }

    private boolean isVideoFile(String path) {
        String lower = path.toLowerCase();
        for (String e : VIDEO_EXTS) if (lower.endsWith(e)) return true;
        return false;
    }

    // ─── 分组构建 ──────────────────────────────────────────────────────────────

    private void buildFolderEntries() {
        Map<String, List<File>> map = new HashMap<>();
        for (File f : allVideoFiles) {
            String folderPath = f.getParent() != null ? f.getParent() : "/";
            if (!map.containsKey(folderPath)) map.put(folderPath, new ArrayList<>());
            map.get(folderPath).add(f);
        }
        folderEntries = new ArrayList<>(map.entrySet());
    }

    // ─── 刷新列表 ──────────────────────────────────────────────────────────────

    private void refreshList() {
        if (currentCategory == CAT_VIDEO) {
            // 所有视频平铺，以 FolderInfo 包装
            List<File> sorted = new ArrayList<>(allVideoFiles);
            sortVideoFiles(sorted, currentSortVideo);
            List<VideoFolderAdapter.FolderInfo> items = new ArrayList<>();
            for (File f : sorted) {
                items.add(new VideoFolderAdapter.FolderInfo(f.getName(), f.getAbsolutePath(), -1));
            }
            folderAdapter.setNewData(items);
        } else {
            // 文件夹模式
            List<Map.Entry<String, List<File>>> entries = new ArrayList<>(folderEntries);
            if (currentSortFolder == SORT_FOLDER_NAME_DESC) {
                Collections.sort(entries, (a, b) -> b.getKey().compareToIgnoreCase(a.getKey()));
            } else {
                Collections.sort(entries, (a, b) -> a.getKey().compareToIgnoreCase(b.getKey()));
            }
            List<VideoFolderAdapter.FolderInfo> items = new ArrayList<>();
            for (Map.Entry<String, List<File>> entry : entries) {
                File dir = new File(entry.getKey());
                String name = dir.getName().isEmpty() ? entry.getKey() : dir.getName();
                items.add(new VideoFolderAdapter.FolderInfo(name, entry.getKey(), entry.getValue().size()));
            }
            folderAdapter.setNewData(items);
        }

        // 根据分类重新设置点击行为
        folderAdapter.setOnItemClickListener((adapter, view, position) -> {
            VideoFolderAdapter.FolderInfo info = folderAdapter.getData().get(position);
            Bundle bundle = new Bundle();
            if (currentCategory == CAT_VIDEO) {
                // 直接播放
                bundle.putString("videoPath", info.path);
                bundle.putString("videoTitle", info.name);
                bundle.putBoolean("isUrl", false);
                jumpActivity(LocalPlayerActivity.class, bundle);
            } else {
                // 进入文件夹
                bundle.putString("folderPath", info.path);
                bundle.putString("folderName", info.name);
                bundle.putInt("sortVideo", currentSortVideo);
                jumpActivity(VideoFolderActivity.class, bundle);
            }
        });
    }

    private void sortVideoFiles(List<File> list, int sort) {
        switch (sort) {
            case SORT_NAME_DESC: Collections.sort(list, (a, b) -> b.getName().compareToIgnoreCase(a.getName())); break;
            case SORT_TIME_ASC:  Collections.sort(list, (a, b) -> Long.compare(a.lastModified(), b.lastModified())); break;
            case SORT_TIME_DESC: Collections.sort(list, (a, b) -> Long.compare(b.lastModified(), a.lastModified())); break;
            default:             Collections.sort(list, (a, b) -> a.getName().compareToIgnoreCase(b.getName())); break;
        }
    }

    private String[] getVideoPathArray() {
        List<File> sorted = new ArrayList<>(allVideoFiles);
        sortVideoFiles(sorted, currentSortVideo);
        String[] paths = new String[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) paths[i] = sorted.get(i).getAbsolutePath();
        return paths;
    }

    // ─── 弹窗 ─────────────────────────────────────────────────────────────────

    private void showCategoryDialog() {
        showOptionDialog("选择分类",
                new String[]{"视频", "文件夹"},
                currentCategory,
                idx -> { currentCategory = idx; refreshList(); });
    }

    private void showSortDialog() {
        if (currentCategory == CAT_VIDEO) {
            showOptionDialog("视频排序",
                    new String[]{"名称升序", "名称降序", "修改时间升序", "修改时间降序"},
                    currentSortVideo,
                    idx -> { currentSortVideo = idx; refreshList(); });
        } else {
            showOptionDialog("文件夹排序",
                    new String[]{"名称升序", "名称降序"},
                    currentSortFolder,
                    idx -> { currentSortFolder = idx; refreshList(); });
        }
    }

    private void showOptionDialog(String title, String[] options, int selected,
                                  OnPickListener listener) {
        Dialog dlg = new Dialog(this, R.style.CustomDialogStyle);
        View root = LayoutInflater.from(this).inflate(R.layout.dialog_local_audio_option, null);
        dlg.setContentView(root);
        dlg.setCanceledOnTouchOutside(true);
        ((TextView) root.findViewById(R.id.tvDialogTitle)).setText(title);
        RadioGroup rg = root.findViewById(R.id.rgOptions);
        for (int i = 0; i < options.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(options[i]);
            rb.setTextColor(0xFF000000);
            rb.setTextSize(15f);
            rb.setPadding(8, 20, 8, 20);
            rb.setId(i);
            if (i == selected) rb.setChecked(true);
            rg.addView(rb);
        }
        rg.setOnCheckedChangeListener((group, id) -> { listener.onPick(id); dlg.dismiss(); });
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int w = (int) (Math.min(dm.widthPixels, dm.heightPixels) * 0.8f);
        if (dlg.getWindow() != null) {
            dlg.getWindow().setLayout(w, WindowManager.LayoutParams.WRAP_CONTENT);
            dlg.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dlg.show();
    }

    interface OnPickListener { void onPick(int index); }

    // ─── 链接播放 ──────────────────────────────────────────────────────────────

    private void showLinkDialog() {
        Dialog dialog = new Dialog(this, R.style.CustomDialogStyle);
        dialog.setContentView(R.layout.dialog_link_play);
        dialog.setCanceledOnTouchOutside(true);
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int w = (int) (Math.min(dm.widthPixels, dm.heightPixels) * 0.88f);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(w, WindowManager.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        EditText etUrl = dialog.findViewById(R.id.etVideoUrl);
        dialog.findViewById(R.id.tvCancel).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.tvConfirm).setOnClickListener(v -> {
            String url = etUrl.getText().toString().trim();
            if (url.isEmpty()) { Toast.makeText(this, "请输入视频地址", Toast.LENGTH_SHORT).show(); return; }
            dialog.dismiss();
            Bundle b = new Bundle();
            b.putString("videoUrl", url);
            b.putString("videoTitle", url);
            b.putBoolean("isUrl", true);
            jumpActivity(LocalPlayerActivity.class, b);
        });
        dialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
