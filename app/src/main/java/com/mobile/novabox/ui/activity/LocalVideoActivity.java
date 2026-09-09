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
import com.mobile.novabox.cache.LocalVideoEntity;
import com.mobile.novabox.data.AppDataManager;
import com.mobile.novabox.ui.adapter.LocalVideoFileAdapter;
import com.mobile.novabox.ui.adapter.VideoFolderAdapter;
import com.mobile.novabox.util.LocalMediaPrefs;
import com.mobile.novabox.util.PadUiHelper;
import com.mobile.novabox.util.StorageVolumeHelper;

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

    // 当前页面内搜索关键词，仅在该页面内容里过滤（视频名 / 文件夹名），不涉及子文件夹页面
    private String currentKeyword = "";

    // 顶栏“搜索”文字按钮，有搜索结果时变为“清除”
    private TextView tvSearch;

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

        findViewById(R.id.ivBack).setOnClickListener(v -> {
            // 退出页面时清除搜索结果
            clearSearch();
            onBackPressed();
        });
        findViewById(R.id.ivLinkPlay).setOnClickListener(v -> {
            clearSearch();
            showLinkDialog();
        });
        findViewById(R.id.tvRefresh).setOnClickListener(v -> {
            clearSearch();
            Toast.makeText(this, "正在扫描本地视频...", Toast.LENGTH_SHORT).show();
            scanVideos();
        });
        findViewById(R.id.tvCategory).setOnClickListener(v -> {
            clearSearch();
            showCategoryDialog();
        });
        findViewById(R.id.tvSort).setOnClickListener(v -> {
            clearSearch();
            showSortDialog();
        });

        tvSearch = findViewById(R.id.tvSearch);
        // “搜索”文字按钮：无搜索时弹搜索框；有搜索结果时点击即清除
        tvSearch.setOnClickListener(v -> {
            if (hasSearchActive()) {
                clearSearch();
            } else {
                showSearchDialog();
            }
        });

        rvFolders = findViewById(R.id.rvFolders);

        // 预初始化两个 adapter
        videoAdapter  = new LocalVideoFileAdapter();
        folderAdapter = new VideoFolderAdapter();

        // 视频 adapter 点击：直接播放（进入播放前清除搜索结果）
        videoAdapter.setOnItemClickListener((adapter, view, position) -> {
            File file = videoAdapter.getData().get(position);
            Bundle b = new Bundle();
            b.putString("videoPath",  file.getAbsolutePath());
            b.putString("videoTitle", file.getName());
            b.putBoolean("isUrl", false);
            // startIndex 只作为兜底(播放页会优先用 videoPath 在自己重新扫描出的列表里精确匹配下标)
            b.putInt("startIndex", position);
            clearSearch();
            jumpActivity(LocalPlayerActivity.class, b);
        });

        // 文件夹 adapter 点击：进入子目录（进入前清除搜索结果）
        folderAdapter.setOnItemClickListener((adapter, view, position) -> {
            VideoFolderAdapter.FolderInfo info = folderAdapter.getData().get(position);
            Bundle b = new Bundle();
            b.putString("folderPath", info.path);
            b.putString("folderName", info.name);
            b.putInt("sortVideo", currentSortVideo);
            clearSearch();
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
            loadFromCacheOrScan();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadFromCacheOrScan();
            } else {
                Toast.makeText(this, "需要存储权限才能扫描本地视频", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ─── 扫描结果缓存（Room）─────────────────────────────────────────────────

    /**
     * 进页面时优先读数据库缓存直接展示，秒开、不用每次都重新扫描磁盘；
     * 缓存为空（首次进入 / 之前从未成功扫描过）时才自动触发一次完整扫描。
     * 封面缩略图交给 Glide 在列表绑定时按需解码+落盘缓存（见 LocalVideoFileAdapter），
     * 这里不用再预先批量截帧、写 thumbPath。
     */
    private void loadFromCacheOrScan() {
        if (executor.isShutdown()) executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<LocalVideoEntity> cached;
            try {
                cached = AppDataManager.get().getLocalVideoDao().getAll();
            } catch (Exception e) {
                cached = new ArrayList<>();
            }
            if (!cached.isEmpty()) {
                List<File> files = new ArrayList<>();
                for (LocalVideoEntity e : cached) files.add(new File(e.path));
                mainHandler.post(() -> {
                    allVideoFiles = files;
                    buildFolderEntries();
                    refreshList();
                });
            } else {
                scanVideos();
            }
        });
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
            // 扫描结果写入数据库，下次进页面即可直接从缓存恢复，不需要重新扫描磁盘。
            // 封面不在这里生成，交给列表绑定时的 Glide 按需处理。
            saveScanResultToDb(files);
        });
    }

    private void saveScanResultToDb(List<File> files) {
        List<LocalVideoEntity> entities = new ArrayList<>();
        for (File f : files) {
            LocalVideoEntity e = new LocalVideoEntity();
            e.path = f.getAbsolutePath();
            e.name = f.getName();
            e.folder = f.getParent() != null ? f.getParent() : "/";
            e.size = f.length();
            e.modified = f.lastModified();
            e.thumbPath = "";
            entities.add(e);
        }
        try {
            AppDataManager.get().getLocalVideoDao().replaceAll(entities);
        } catch (Exception ignored) {}
    }

    /**
     * 扫描本地视频文件。
     *
     * 先走 MediaStore 查询（速度快、覆盖大多数场景）；查不到结果时兜底走文件系统
     * 递归扫描——扫描根目录不再写死 /sdcard、/storage/emulated/0，而是用
     * {@link StorageVolumeHelper} 动态发现设备上所有已挂载的存储卷（内部存储 +
     * SD 卡 + U 盘等），覆盖外置存储设备。
     */
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
            for (File root : StorageVolumeHelper.discoverRoots(this)) {
                if (!root.canRead()) continue;
                scanFs(root, list, seen, 0);
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
            } else if (isVideoFile(f.getName())) {
                // 用真实路径（解析符号链接后）去重：不同存储卷根目录之间可能存在
                // 符号链接互指（如 /sdcard -> /storage/emulated/0），仅靠原始
                // 绝对路径字符串去重无法识别，会导致同一物理文件被重复记录。
                String dedupeKey;
                try {
                    dedupeKey = f.getCanonicalPath();
                } catch (Exception e) {
                    dedupeKey = f.getAbsolutePath();
                }
                if (seen.add(dedupeKey)) list.add(f);
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
        boolean hasKeyword = currentKeyword != null && !currentKeyword.isEmpty();
        if (currentCategory == CAT_VIDEO) {
            // 视频分类：用 LocalVideoFileAdapter，直接显示视频文件
            List<File> sorted = new ArrayList<>(allVideoFiles);
            if (hasKeyword) sorted = filterByKeyword(sorted, currentKeyword);
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
            if (hasKeyword) {
                List<Map.Entry<String, List<File>>> filtered = new ArrayList<>();
                String kw = currentKeyword.toLowerCase();
                for (Map.Entry<String, List<File>> entry : entries) {
                    File dir = new File(entry.getKey());
                    String name = dir.getName().isEmpty() ? entry.getKey() : dir.getName();
                    if (name.toLowerCase().contains(kw)) filtered.add(entry);
                }
                entries = filtered;
            }
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

    /** 按文件名（不含路径）包含关系过滤，忽略大小写。 */
    private List<File> filterByKeyword(List<File> list, String keyword) {
        String kw = keyword.toLowerCase();
        List<File> result = new ArrayList<>();
        for (File f : list) {
            if (f.getName().toLowerCase().contains(kw)) result.add(f);
        }
        return result;
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

    // ─── 搜索 ──────────────────────────────────────────────────────────────────

    /** 当前是否处于搜索过滤状态。 */
    private boolean hasSearchActive() {
        return currentKeyword != null && !currentKeyword.isEmpty();
    }

    /** 清除当前页面搜索结果，顶栏按钮恢复为“搜索”，并刷新列表。 */
    private void clearSearch() {
        currentKeyword = "";
        if (tvSearch != null) tvSearch.setText("搜索");
        refreshList();
    }

    /**
     * 页面内搜索：仅对当前页面已加载的内容（视频名 / 文件夹名）做关键词过滤，
     * 不涉及子文件夹页面（VideoFolderActivity）里的内容。
     */
    private void showSearchDialog() {
        Dialog dialog = new Dialog(this, R.style.CustomDialogStyle);
        dialog.setContentView(R.layout.dialog_search_local);
        dialog.setCanceledOnTouchOutside(true);
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int w = (int) (Math.min(dm.widthPixels, dm.heightPixels) * 0.88f);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(w, WindowManager.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        EditText etKeyword = dialog.findViewById(R.id.etSearchKeyword);
        etKeyword.setText(currentKeyword);
        if (currentKeyword != null && !currentKeyword.isEmpty()) etKeyword.setSelection(currentKeyword.length());
        dialog.findViewById(R.id.tvCancel).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.tvConfirm).setOnClickListener(v -> {
            currentKeyword = etKeyword.getText().toString().trim();
            dialog.dismiss();
            // 有搜索结果时，顶栏按钮切换为“清除”
            tvSearch.setText(hasSearchActive() ? "清除" : "搜索");
            refreshList();
        });
        dialog.show();
    }

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
