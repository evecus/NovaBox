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
import com.mobile.novabox.ui.adapter.LocalVideoFileAdapter;
import com.mobile.novabox.ui.adapter.VideoFolderAdapter;
import com.mobile.novabox.util.LocalMediaPrefs;
import com.mobile.novabox.util.MediaCoverCache;
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

    private static final int CAT_VIDEO  = 0;
    private static final int CAT_FOLDER = 1;

    private static final int SORT_NAME_ASC  = 0;
    private static final int SORT_NAME_DESC = 1;
    private static final int SORT_TIME_ASC  = 2;
    private static final int SORT_TIME_DESC = 3;

    private static final int SORT_FOLDER_NAME_ASC  = 0;
    private static final int SORT_FOLDER_NAME_DESC = 1;

    private static final int REQUEST_STORAGE = 101;
    private static final String[] VIDEO_EXTS = {
            ".mp4", ".mkv", ".avi", ".mov", ".flv", ".wmv",
            ".ts", ".m4v", ".rmvb", ".3gp", ".webm"
    };

    private RecyclerView rvFolders;
    // 两套 adapter：视频分类用 videoAdapter，文件夹分类用 folderAdapter
    private LocalVideoFileAdapter videoAdapter;
    private VideoFolderAdapter    folderAdapter;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private int currentCategory  = CAT_VIDEO;
    private int currentSortVideo  = SORT_NAME_ASC;
    private int currentSortFolder = SORT_FOLDER_NAME_ASC;

    private List<File>                              allVideoFiles = new ArrayList<>();
    private List<Map.Entry<String, List<File>>>     folderEntries = new ArrayList<>();

    @Override
    protected int getLayoutResID() { return R.layout.activity_local_video; }

    @Override
    protected void init() {
        // 恢复上次保存的“分类”“排序”选择，不再每次进入页面都回到默认值
        currentCategory   = LocalMediaPrefs.loadVideoCategory(this, CAT_VIDEO);
        currentSortVideo  = LocalMediaPrefs.loadVideoSortVideo(this, SORT_NAME_ASC);
        currentSortFolder = LocalMediaPrefs.loadVideoSortFolder(this, SORT_FOLDER_NAME_ASC);

        findViewById(R.id.ivBack).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.ivLinkPlay).setOnClickListener(v -> showLinkDialog());
        findViewById(R.id.tvRefresh).setOnClickListener(v -> {
            Toast.makeText(this, "正在扫描本地视频...", Toast.LENGTH_SHORT).show();
            scanVideos();
        });
        findViewById(R.id.tvCategory).setOnClickListener(v -> showCategoryDialog());
        findViewById(R.id.tvSort).setOnClickListener(v -> showSortDialog());

        rvFolders = findViewById(R.id.rvFolders);

        // 预初始化两个 adapter
        videoAdapter  = new LocalVideoFileAdapter();
        folderAdapter = new VideoFolderAdapter();

        // 视频 adapter 点击：直接播放
        videoAdapter.setOnItemClickListener((adapter, view, position) -> {
            File file = videoAdapter.getData().get(position);
            Bundle b = new Bundle();
            b.putString("videoPath",  file.getAbsolutePath());
            b.putString("videoTitle", file.getName());
            b.putBoolean("isUrl", false);
            jumpActivity(LocalPlayerActivity.class, b);
        });

        // 文件夹 adapter 点击：进入子目录
        folderAdapter.setOnItemClickListener((adapter, view, position) -> {
            VideoFolderAdapter.FolderInfo info = folderAdapter.getData().get(position);
            Bundle b = new Bundle();
            b.putString("folderPath", info.path);
            b.putString("folderName", info.name);
            b.putInt("sortVideo", currentSortVideo);
            jumpActivity(VideoFolderActivity.class, b);
        });

        checkPermissionAndScan();
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
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scanVideos();
            } else {
                Toast.makeText(this, "需要存储权限才能扫描本地视频", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ─── 扫描 ──────────────────────────────────────────────────────────────────

    private void scanVideos() {
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
            // 扫描到的视频逐个提取/缓存封面帧，生成一个后刷新一次列表，
            // 用户能看到封面逐步"点亮"，不用等全部扫描完
            extractCoversAndRefresh(files);
        });
    }

    private void extractCoversAndRefresh(List<File> files) {
        int refreshEvery = 5;
        int count = 0;
        for (File f : files) {
            if (executor.isShutdown()) return;
            MediaCoverCache.getOrCreateVideoCover(this, f);
            count++;
            if (count % refreshEvery == 0) {
                mainHandler.post(this::refreshList);
            }
        }
        mainHandler.post(this::refreshList);
    }

    private List<File> doScanAll() {
        List<File> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] proj = {MediaStore.Video.Media.DATA};
        try (Cursor c = getContentResolver().query(uri, proj, null, null,
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

    // ─── 分组 ──────────────────────────────────────────────────────────────────

    private void buildFolderEntries() {
        Map<String, List<File>> map = new HashMap<>();
        for (File f : allVideoFiles) {
            String fp = f.getParent() != null ? f.getParent() : "/";
            if (!map.containsKey(fp)) map.put(fp, new ArrayList<>());
            map.get(fp).add(f);
        }
        folderEntries = new ArrayList<>(map.entrySet());
    }

    // ─── 刷新列表（切换 adapter）─────────────────────────────────────────────

    private void refreshList() {
        if (currentCategory == CAT_VIDEO) {
            // 视频分类：用 LocalVideoFileAdapter，直接显示视频文件
            List<File> sorted = new ArrayList<>(allVideoFiles);
            sortVideoFiles(sorted, currentSortVideo);
            boolean isPad = PadUiHelper.isPad(this);
            rvFolders.setLayoutManager(isPad
                    ? new GridLayoutManager(this, 2)
                    : new LinearLayoutManager(this));
            rvFolders.setAdapter(videoAdapter);
            videoAdapter.setNewData(sorted);
        } else {
            // 文件夹分类：用 VideoFolderAdapter
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
            boolean isPad = PadUiHelper.isPad(this);
            rvFolders.setLayoutManager(isPad
                    ? new GridLayoutManager(this, 2)
                    : new LinearLayoutManager(this));
            rvFolders.setAdapter(folderAdapter);
            folderAdapter.setNewData(items);
        }
    }

    private void sortVideoFiles(List<File> list, int sort) {
        switch (sort) {
            case SORT_NAME_DESC: Collections.sort(list, (a, b) -> b.getName().compareToIgnoreCase(a.getName())); break;
            case SORT_TIME_ASC:  Collections.sort(list, (a, b) -> Long.compare(a.lastModified(), b.lastModified())); break;
            case SORT_TIME_DESC: Collections.sort(list, (a, b) -> Long.compare(b.lastModified(), a.lastModified())); break;
            default:             Collections.sort(list, (a, b) -> a.getName().compareToIgnoreCase(b.getName())); break;
        }
    }

    // ─── 弹窗 ─────────────────────────────────────────────────────────────────

    private void showCategoryDialog() {
        showOptionDialog("选择分类",
                new String[]{"视频", "文件夹"},
                currentCategory,
                idx -> {
                    currentCategory = idx;
                    LocalMediaPrefs.saveVideoCategory(this, currentCategory);
                    refreshList();
                });
    }

    private void showSortDialog() {
        if (currentCategory == CAT_VIDEO) {
            showOptionDialog("视频排序",
                    new String[]{"名称升序", "名称降序", "修改时间升序", "修改时间降序"},
                    currentSortVideo,
                    idx -> {
                        currentSortVideo = idx;
                        LocalMediaPrefs.saveVideoSortVideo(this, currentSortVideo);
                        refreshList();
                    });
        } else {
            showOptionDialog("文件夹排序",
                    new String[]{"名称升序", "名称降序"},
                    currentSortFolder,
                    idx -> {
                        currentSortFolder = idx;
                        LocalMediaPrefs.saveVideoSortFolder(this, currentSortFolder);
                        refreshList();
                    });
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
