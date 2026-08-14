package com.mobile.novabox.ui.activity;

import android.app.Dialog;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

    private static final int SORT_NAME_ASC  = 0;
    private static final int SORT_NAME_DESC = 1;
    private static final int SORT_TIME_ASC  = 2;
    private static final int SORT_TIME_DESC = 3;

    private RecyclerView rvVideos;
    private LocalVideoFileAdapter videoAdapter;
    private String folderPath;
    private String folderName;
    private int sortMode;
    private List<File> allVideos = new ArrayList<>();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected int getLayoutResID() { return R.layout.activity_video_folder; }

    @Override
    protected void init() {
        folderPath = getIntent().getStringExtra("folderPath");
        folderName = getIntent().getStringExtra("folderName");
        sortMode   = getIntent().getIntExtra("sortVideo", SORT_NAME_ASC);

        TextView tvTitle = findViewById(R.id.tvFolderTitle);
        if (tvTitle != null) tvTitle.setText(folderName != null ? folderName : "视频列表");

        findViewById(R.id.ivBack).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.tvSort).setOnClickListener(v -> showSortDialog());

        rvVideos = findViewById(R.id.rvVideos);
        videoAdapter = new LocalVideoFileAdapter();
        rvVideos.setLayoutManager(PadUiHelper.isPad(this)
                ? new GridLayoutManager(this, 2)
                : new LinearLayoutManager(this));
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
                allVideos = videos;
                refreshList();
                if (videos.isEmpty())
                    Toast.makeText(VideoFolderActivity.this, "此文件夹无视频", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void refreshList() {
        List<File> sorted = new ArrayList<>(allVideos);
        sortFiles(sorted, sortMode);
        videoAdapter.setNewData(sorted);
    }

    private void sortFiles(List<File> list, int sort) {
        switch (sort) {
            case SORT_NAME_DESC: Collections.sort(list, (a, b) -> b.getName().compareToIgnoreCase(a.getName())); break;
            case SORT_TIME_ASC:  Collections.sort(list, (a, b) -> Long.compare(a.lastModified(), b.lastModified())); break;
            case SORT_TIME_DESC: Collections.sort(list, (a, b) -> Long.compare(b.lastModified(), a.lastModified())); break;
            default:             Collections.sort(list, (a, b) -> a.getName().compareToIgnoreCase(b.getName())); break;
        }
    }

    private void showSortDialog() {
        Dialog dlg = new Dialog(this, R.style.CustomDialogStyle);
        View root = LayoutInflater.from(this).inflate(R.layout.dialog_local_audio_option, null);
        dlg.setContentView(root);
        dlg.setCanceledOnTouchOutside(true);
        ((TextView) root.findViewById(R.id.tvDialogTitle)).setText("视频排序");
        RadioGroup rg = root.findViewById(R.id.rgOptions);
        String[] opts = {"名称升序", "名称降序", "修改时间升序", "修改时间降序"};
        for (int i = 0; i < opts.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(opts[i]);
            rb.setTextColor(0xFF000000);
            rb.setTextSize(15f);
            rb.setPadding(8, 20, 8, 20);
            rb.setId(i);
            if (i == sortMode) rb.setChecked(true);
            rg.addView(rb);
        }
        rg.setOnCheckedChangeListener((group, id) -> {
            sortMode = id;
            refreshList();
            dlg.dismiss();
        });
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int w = (int) (Math.min(dm.widthPixels, dm.heightPixels) * 0.8f);
        if (dlg.getWindow() != null) {
            dlg.getWindow().setLayout(w, WindowManager.LayoutParams.WRAP_CONTENT);
            dlg.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dlg.show();
    }

    private List<File> getVideosInFolder(String folder) {
        List<File> result = new ArrayList<>();
        Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.Video.Media.DATA};
        String selection = MediaStore.Video.Media.DATA + " LIKE ?";
        ContentResolver cr = getContentResolver();
        try (Cursor cursor = cr.query(uri, projection, selection,
                new String[]{folder + "/%"}, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String path = cursor.getString(0);
                    if (path == null) continue;
                    File f = new File(path);
                    if (f.getParentFile() != null
                            && f.getParentFile().getAbsolutePath().equals(folder)
                            && f.exists()) {
                        result.add(f);
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }

        if (result.isEmpty()) {
            File dir = new File(folder);
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && isVideoFile(f.getName())) result.add(f);
                }
            }
        }
        return result;
    }

    private boolean isVideoFile(String name) {
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
