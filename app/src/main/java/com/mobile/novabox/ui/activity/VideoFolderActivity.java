package com.mobile.novabox.ui.activity;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.mobile.novabox.R;
import com.mobile.novabox.base.BaseActivity;
import com.mobile.novabox.ui.adapter.LocalVideoFileAdapter;
import com.mobile.novabox.util.PadUiHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoFolderActivity extends BaseActivity {

    private RecyclerView rvVideos;
    private LocalVideoFileAdapter videoAdapter;
    private String folderPath;
    private String folderName;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_video_folder;
    }

    @Override
    protected void init() {
        folderPath = getIntent().getStringExtra("folderPath");
        folderName = getIntent().getStringExtra("folderName");

        View ivBack = findViewById(R.id.ivBack);
        if (ivBack != null) ivBack.setOnClickListener(v -> onBackPressed());

        TextView tvTitle = findViewById(R.id.tvFolderTitle);
        if (tvTitle != null) tvTitle.setText(folderName != null ? folderName : "视频列表");

        rvVideos = findViewById(R.id.rvVideos);
        videoAdapter = new LocalVideoFileAdapter();
        if (PadUiHelper.isPad(this)) {
            rvVideos.setLayoutManager(new GridLayoutManager(this, 2));
        } else {
            rvVideos.setLayoutManager(new LinearLayoutManager(this));
        }
        rvVideos.setAdapter(videoAdapter);

        videoAdapter.setOnItemClickListener((adapter, view, position) -> {
            File file = videoAdapter.getData().get(position);
            Bundle bundle = new Bundle();
            bundle.putString("videoPath", file.getAbsolutePath());
            bundle.putString("videoTitle", file.getName());
            bundle.putString("folderPath", folderPath);
            bundle.putInt("startIndex", position);
            bundle.putBoolean("isUrl", false);
            jumpActivity(LocalPlayerActivity.class, bundle);
        });

        loadVideos();
    }

    private void loadVideos() {
        if (folderPath == null) return;
        executor.execute(() -> {
            List<File> videos = getVideosInFolder(folderPath);
            mainHandler.post(() -> {
                videoAdapter.setNewData(videos);
                if (videos.isEmpty()) {
                    Toast.makeText(VideoFolderActivity.this, "此文件夹无视频", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private List<File> getVideosInFolder(String folder) {
        List<File> result = new ArrayList<>();
        Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.Video.Media.DATA};
        String selection = MediaStore.Video.Media.DATA + " LIKE ?";
        String[] args = {folder + "/%"};

        ContentResolver cr = getContentResolver();
        try (Cursor cursor = cr.query(uri, projection, selection, args,
                MediaStore.Video.Media.DISPLAY_NAME + " ASC")) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String path = cursor.getString(0);
                    if (path == null) continue;
                    File f = new File(path);
                    // 只取直接子文件（不含子文件夹）
                    if (f.getParentFile() != null &&
                            f.getParentFile().getAbsolutePath().equals(folder)) {
                        result.add(f);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (result.isEmpty()) {
            // fallback: 直接读文件系统
            File dir = new File(folder);
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && isVideoFile(f.getName())) {
                        result.add(f);
                    }
                }
                Collections.sort(result, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            }
        }
        return result;
    }

    private boolean isVideoFile(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi")
                || lower.endsWith(".mov") || lower.endsWith(".wmv") || lower.endsWith(".flv")
                || lower.endsWith(".ts") || lower.endsWith(".m3u8") || lower.endsWith(".rmvb")
                || lower.endsWith(".m4v") || lower.endsWith(".3gp") || lower.endsWith(".webm");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
