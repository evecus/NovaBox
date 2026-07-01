package com.mobile.novabox.ui.activity;

import android.Manifest;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.mobile.novabox.R;
import com.mobile.novabox.base.BaseActivity;
import com.mobile.novabox.ui.adapter.VideoFolderAdapter;
import com.mobile.novabox.util.PadUiHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalVideoActivity extends BaseActivity {

    private static final int REQUEST_STORAGE = 101;
    private RecyclerView rvFolders;
    private VideoFolderAdapter folderAdapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    // 防止 onResume 重复触发扫描
    private boolean hasScanned = false;

    private static final String[] VIDEO_EXTS = {
            ".mp4", ".mkv", ".avi", ".mov", ".flv", ".wmv", ".ts", ".m4v", ".rmvb", ".3gp"
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_local_video;
    }

    @Override
    protected void init() {
        // 返回
        View ivBack = findViewById(R.id.ivBack);
        if (ivBack != null) ivBack.setOnClickListener(v -> onBackPressed());

        // 链接播放按钮
        View ivLinkPlay = findViewById(R.id.ivLinkPlay);
        if (ivLinkPlay != null) ivLinkPlay.setOnClickListener(v -> showLinkDialog());

        // 文件夹列表
        rvFolders = findViewById(R.id.rvFolders);
        folderAdapter = new VideoFolderAdapter();
        if (PadUiHelper.isPad(this)) {
            rvFolders.setLayoutManager(new GridLayoutManager(this, 2));
        } else {
            rvFolders.setLayoutManager(new LinearLayoutManager(this));
        }
        rvFolders.setAdapter(folderAdapter);

        folderAdapter.setOnItemClickListener((adapter, view, position) -> {
            VideoFolderAdapter.FolderInfo info = folderAdapter.getData().get(position);
            Bundle bundle = new Bundle();
            bundle.putString("folderPath", info.path);
            bundle.putString("folderName", info.name);
            jumpActivity(VideoFolderActivity.class, bundle);
        });

        checkPermissionAndScan();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 兜底：应对定制ROM权限回调不可靠的情况
        // 从权限设置页返回后，若列表仍为空则重新扫描
        if (hasScanned && folderAdapter != null && folderAdapter.getData().isEmpty()) {
            checkPermissionAndScan();
        }
    }

    private void checkPermissionAndScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_VIDEO}, REQUEST_STORAGE);
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE);
                return;
            }
        }
        scanVideos();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scanVideos();
            } else {
                // 回调显示拒绝，但实际权限可能已授予（定制ROM兼容性问题），再检查一次
                if (hasStoragePermission()) {
                    scanVideos();
                } else {
                    Toast.makeText(this, "需要存储权限才能扫描本地视频", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void scanVideos() {
        hasScanned = true;
        executor.execute(() -> {
            List<VideoFolderAdapter.FolderInfo> folders = scanVideoFolders();
            mainHandler.post(() -> {
                folderAdapter.setNewData(folders);
                if (folders.isEmpty()) {
                    Toast.makeText(LocalVideoActivity.this, "未找到本地视频", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private List<VideoFolderAdapter.FolderInfo> scanVideoFolders() {
        Map<String, Integer> folderCount = new HashMap<>();
        Map<String, String> folderPaths = new HashMap<>();
        // 记录已扫描的真实路径（用于去重软链接）
        java.util.Set<String> scannedRealPaths = new java.util.HashSet<>();

        // 方式1：MediaStore 查询，兼容 Android 10+ DATA 字段为 null 的情况
        Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Video.Media.DATA,          // Android 9 及以下有效
                MediaStore.Video.Media.RELATIVE_PATH, // Android 10+ 新字段
                MediaStore.Video.Media.DISPLAY_NAME
        };
        ContentResolver cr = getContentResolver();
        try (Cursor cursor = cr.query(uri, projection, null, null,
                MediaStore.Video.Media.DATE_MODIFIED + " DESC")) {
            if (cursor != null) {
                int dataIdx = cursor.getColumnIndex(MediaStore.Video.Media.DATA);
                int relPathIdx = cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH);
                while (cursor.moveToNext()) {
                    String path = (dataIdx >= 0) ? cursor.getString(dataIdx) : null;
                    if (path != null && !path.isEmpty()) {
                        // 正常路径，直接用
                        addToFolder(path, folderCount, folderPaths);
                    } else if (relPathIdx >= 0) {
                        // Android 10+ DATA 为 null，用 RELATIVE_PATH 拼出文件夹路径
                        String relativePath = cursor.getString(relPathIdx);
                        if (relativePath != null) {
                            // RELATIVE_PATH 格式如 "DCIM/Camera/"，拼上存储根路径
                            String folderPath = "/storage/emulated/0/" + relativePath;
                            // 去掉末尾斜线
                            if (folderPath.endsWith("/")) {
                                folderPath = folderPath.substring(0, folderPath.length() - 1);
                            }
                            File folderFile = new File(folderPath);
                            String folderName = folderFile.getName();
                            if (folderName.isEmpty() && folderFile.getParentFile() != null) {
                                folderName = folderFile.getParentFile().getName();
                            }
                            Integer count = folderCount.get(folderPath);
                            folderCount.put(folderPath, count == null ? 1 : count + 1);
                            folderPaths.put(folderPath, folderName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 方式2：MediaStore 为空时（模拟器/定制ROM媒体库未建立），直接扫描文件系统兜底
        if (folderCount.isEmpty()) {
            for (String root : new String[]{
                    "/sdcard",
                    "/storage/emulated/0",
                    "/storage/emulated/legacy",
                    "/mnt/sdcard"
            }) {
                File dir = new File(root);
                if (!dir.exists() || !dir.canRead()) continue;
                // 解析真实路径，避免软链接重复扫描
                try {
                    String realPath = dir.getCanonicalPath();
                    if (!scannedRealPaths.add(realPath)) continue; // 已扫描过则跳过
                } catch (Exception e) {
                    // 无法解析时用原始路径去重
                    if (!scannedRealPaths.add(dir.getAbsolutePath())) continue;
                }
                scanFileSystem(dir, folderCount, folderPaths, 0);
            }
        }

        List<VideoFolderAdapter.FolderInfo> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : folderPaths.entrySet()) {
            String path = entry.getKey();
            String name = entry.getValue();
            int count = folderCount.containsKey(path) ? folderCount.get(path) : 0;
            result.add(new VideoFolderAdapter.FolderInfo(name, path, count));
        }
        Collections.sort(result, (a, b) -> b.videoCount - a.videoCount);
        return result;
    }

    private void addToFolder(String path, Map<String, Integer> folderCount,
                             Map<String, String> folderPaths) {
        if (path == null || path.isEmpty()) return;
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent == null) return;
        String folderPath = parent.getAbsolutePath();
        String folderName = parent.getName();
        Integer count = folderCount.get(folderPath);
        folderCount.put(folderPath, count == null ? 1 : count + 1);
        folderPaths.put(folderPath, folderName);
    }

    private void scanFileSystem(File dir, Map<String, Integer> folderCount,
                                Map<String, String> folderPaths, int depth) {
        if (depth > 8) return; // 最多递归8层，防止卡死
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                // 跳过系统隐藏目录
                String name = f.getName();
                if (name.startsWith(".") || name.equals("Android")) continue;
                scanFileSystem(f, folderCount, folderPaths, depth + 1);
            } else {
                String name = f.getName().toLowerCase();
                for (String ext : VIDEO_EXTS) {
                    if (name.endsWith(ext)) {
                        addToFolder(f.getAbsolutePath(), folderCount, folderPaths);
                        break;
                    }
                }
            }
        }
    }

    private void showLinkDialog() {
        android.app.Dialog dialog = new android.app.Dialog(this, R.style.CustomDialogStyle);
        dialog.setContentView(R.layout.dialog_link_play);
        dialog.setCanceledOnTouchOutside(true);

        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int w = (int) (Math.min(dm.widthPixels, dm.heightPixels) * 0.88f);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(w, android.view.WindowManager.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        EditText etUrl = dialog.findViewById(R.id.etVideoUrl);
        TextView tvCancel = dialog.findViewById(R.id.tvCancel);
        TextView tvConfirm = dialog.findViewById(R.id.tvConfirm);

        tvCancel.setOnClickListener(v -> dialog.dismiss());
        tvConfirm.setOnClickListener(v -> {
            String url = etUrl.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(this, "请输入视频地址", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            Bundle bundle = new Bundle();
            bundle.putString("videoUrl", url);
            bundle.putString("videoTitle", url);
            bundle.putBoolean("isUrl", true);
            jumpActivity(LocalPlayerActivity.class, bundle);
        });

        dialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
