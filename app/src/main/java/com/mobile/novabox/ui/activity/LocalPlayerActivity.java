package com.mobile.novabox.ui.activity;

import android.content.ContentResolver;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mobile.novabox.R;
import com.mobile.novabox.base.BaseActivity;
import com.mobile.novabox.player.MyVideoView;
import com.mobile.novabox.ui.adapter.LocalPlaylistAdapter;
import com.mobile.novabox.util.PadUiHelper;
import com.mobile.novabox.util.PlayerHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import xyz.doikki.videoplayer.player.VideoView;

public class LocalPlayerActivity extends BaseActivity {

    private MyVideoView mVideoView;
    private FrameLayout flPlayerContainer;
    private FrameLayout flControlOverlay;
    private ImageView ivBack;
    private ImageView ivLock;
    private ImageView ivPlayPause;
    private ImageView ivFullscreen;
    private SeekBar seekBar;
    private TextView tvCurrentPos;
    private TextView tvDuration;
    private ProgressBar pbLoading;
    private TextView tvVideoTitle;
    private RecyclerView rvPlaylist;

    private LocalPlaylistAdapter playlistAdapter;

    private boolean isUrl = false;
    private String videoUrl;
    private String videoTitle;
    private String folderPath;
    private int startIndex = 0;
    private List<File> playlist = new ArrayList<>();
    private int currentIndex = 0;

    private boolean isLocked = false;
    private boolean controlsVisible = false;
    private boolean isFullScreen = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Runnable hideControlsRunnable = () -> hideControls();
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_local_player;
    }

    @Override
    protected void init() {
        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        isUrl = getIntent().getBooleanExtra("isUrl", false);
        videoUrl = getIntent().getStringExtra("videoUrl");
        videoTitle = getIntent().getStringExtra("videoTitle");
        folderPath = getIntent().getStringExtra("folderPath");
        startIndex = getIntent().getIntExtra("startIndex", 0);
        if (!isUrl) {
            String videoPath = getIntent().getStringExtra("videoPath");
            if (videoPath != null) videoUrl = videoPath;
        }

        findViews();
        setupPlayer();
        setupControls();
        setupPlaylist();

        if (isUrl) {
            // 直接播 URL，无播放列表
            if (rvPlaylist != null) rvPlaylist.setVisibility(View.GONE);
            if (tvVideoTitle != null) tvVideoTitle.setText(videoTitle != null ? videoTitle : "正在播放");
            startPlay(videoUrl);
        } else {
            // 加载文件夹列表
            loadFolderAndPlay();
        }

        // 设置播放器高度 (手机竖屏时16:9)
        if (!PadUiHelper.isPad(this)) {
            adjustPlayerHeight();
        }
    }

    private void findViews() {
        flPlayerContainer = findViewById(R.id.flPlayerContainer);
        mVideoView = findViewById(R.id.mVideoView);
        flControlOverlay = findViewById(R.id.flControlOverlay);
        ivBack = findViewById(R.id.ivBack);
        ivLock = findViewById(R.id.ivLock);
        ivPlayPause = findViewById(R.id.ivPlayPause);
        ivFullscreen = findViewById(R.id.ivFullscreen);
        seekBar = findViewById(R.id.seekBar);
        tvCurrentPos = findViewById(R.id.tvCurrentPos);
        tvDuration = findViewById(R.id.tvDuration);
        pbLoading = findViewById(R.id.pbLoading);
        tvVideoTitle = findViewById(R.id.tvVideoTitle);
        rvPlaylist = findViewById(R.id.rvPlaylist);
    }

    private void adjustPlayerHeight() {
        // 让播放器容器保持 16:9 宽高比
        flPlayerContainer.post(() -> {
            int w = flPlayerContainer.getWidth();
            if (w <= 0) {
                android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(dm);
                w = dm.widthPixels;
            }
            int h = w * 9 / 16;
            ViewGroup.LayoutParams lp = flPlayerContainer.getLayoutParams();
            lp.height = h;
            flPlayerContainer.setLayoutParams(lp);
        });
    }

    private void setupPlayer() {
        // 应用全局播放器设置（ijk/exo/系统）
        PlayerHelper.updateCfg(mVideoView);

        mVideoView.setOnStateChangeListener(new VideoView.SimpleOnStateChangeListener() {
            @Override
            public void onPlayStateChanged(int playState) {
                switch (playState) {
                    case VideoView.STATE_PLAYING:
                        if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                        updatePlayPauseIcon(true);
                        handler.post(progressRunnable);
                        break;
                    case VideoView.STATE_PAUSED:
                        updatePlayPauseIcon(false);
                        break;
                    case VideoView.STATE_PREPARING:
                    case VideoView.STATE_BUFFERING:
                        if (pbLoading != null) pbLoading.setVisibility(View.VISIBLE);
                        break;
                    case VideoView.STATE_PREPARED:
                    case VideoView.STATE_BUFFERED:
                        if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                        break;
                    case VideoView.STATE_PLAYBACK_COMPLETED:
                        handler.removeCallbacks(progressRunnable);
                        // 自动播放下一个
                        playNext();
                        break;
                    case VideoView.STATE_ERROR:
                        if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                        Toast.makeText(LocalPlayerActivity.this, "播放失败", Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        });
    }

    private void setupControls() {
        // 点击播放区域切换控制栏显示
        flPlayerContainer.setOnClickListener(v -> {
            if (controlsVisible) {
                hideControls();
            } else {
                showControls();
            }
        });

        if (ivBack != null) {
            ivBack.setOnClickListener(v -> {
                if (isFullScreen) {
                    exitFullScreen();
                } else {
                    onBackPressed();
                }
            });
        }

        if (ivLock != null) {
            ivLock.setOnClickListener(v -> {
                isLocked = !isLocked;
                ivLock.setImageResource(isLocked ? R.drawable.icon_lock : R.drawable.icon_unlock);
                // 显示/隐藏其他控制元素
                int vis = isLocked ? View.INVISIBLE : View.VISIBLE;
                if (ivBack != null) ivBack.setVisibility(vis);
                if (ivPlayPause != null) ivPlayPause.setVisibility(vis);
                if (seekBar != null) seekBar.setVisibility(vis);
                if (tvCurrentPos != null) tvCurrentPos.setVisibility(vis);
                if (tvDuration != null) tvDuration.setVisibility(vis);
                if (ivFullscreen != null) ivFullscreen.setVisibility(vis);
                scheduleHideControls();
            });
        }

        if (ivPlayPause != null) {
            ivPlayPause.setOnClickListener(v -> {
                if (mVideoView.isPlaying()) {
                    mVideoView.pause();
                } else {
                    mVideoView.resume();
                }
                scheduleHideControls();
            });
        }

        if (ivFullscreen != null) {
            ivFullscreen.setOnClickListener(v -> {
                if (isFullScreen) {
                    exitFullScreen();
                } else {
                    enterFullScreen();
                }
                scheduleHideControls();
            });
        }

        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        long duration = mVideoView.getDuration();
                        long newPos = duration * progress / 1000;
                        if (tvCurrentPos != null) tvCurrentPos.setText(formatTime(newPos));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    handler.removeCallbacks(hideControlsRunnable);
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    long duration = mVideoView.getDuration();
                    long newPos = duration * seekBar.getProgress() / 1000;
                    mVideoView.seekTo(newPos);
                    scheduleHideControls();
                }
            });
        }
    }

    private void setupPlaylist() {
        if (rvPlaylist == null) return;
        playlistAdapter = new LocalPlaylistAdapter();
        rvPlaylist.setLayoutManager(new LinearLayoutManager(this));
        rvPlaylist.setAdapter(playlistAdapter);
        playlistAdapter.setOnItemClickListener((adapter, view, position) -> {
            currentIndex = position;
            playAtIndex(currentIndex);
        });
    }

    private void loadFolderAndPlay() {
        executor.execute(() -> {
            List<File> files = getVideosInFolder(folderPath);
            handler.post(() -> {
                playlist.clear();
                playlist.addAll(files);
                if (!isUrl && playlistAdapter != null) {
                    playlistAdapter.setNewData(new ArrayList<>(playlist));
                }
                if (playlist.isEmpty()) {
                    Toast.makeText(this, "文件夹无视频", Toast.LENGTH_SHORT).show();
                    return;
                }
                currentIndex = Math.min(startIndex, playlist.size() - 1);
                playAtIndex(currentIndex);
            });
        });
    }

    private void playAtIndex(int index) {
        if (index < 0 || index >= playlist.size()) return;
        currentIndex = index;
        File f = playlist.get(index);
        if (tvVideoTitle != null) tvVideoTitle.setText(f.getName());
        if (playlistAdapter != null) {
            playlistAdapter.setCurrentIndex(index);
            rvPlaylist.scrollToPosition(index);
        }
        startPlay(f.getAbsolutePath());
    }

    private void startPlay(String path) {
        if (mVideoView == null || path == null || path.isEmpty()) return;
        mVideoView.release();
        if (path.startsWith("http://") || path.startsWith("https://")
                || path.startsWith("rtmp://") || path.startsWith("rtsp://")) {
            mVideoView.setUrl(path);
        } else {
            mVideoView.setUrl(Uri.fromFile(new File(path)).toString());
        }
        mVideoView.start();
    }

    private void playNext() {
        if (playlist.isEmpty()) return;
        int next = currentIndex + 1;
        if (next < playlist.size()) {
            playAtIndex(next);
        }
    }

    private void showControls() {
        if (flControlOverlay == null) return;
        controlsVisible = true;
        flControlOverlay.setVisibility(View.VISIBLE);
        scheduleHideControls();
    }

    private void hideControls() {
        if (flControlOverlay == null) return;
        controlsVisible = false;
        flControlOverlay.setVisibility(View.GONE);
        handler.removeCallbacks(hideControlsRunnable);
    }

    private void scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable);
        handler.postDelayed(hideControlsRunnable, 3500);
    }

    private void updatePlayPauseIcon(boolean playing) {
        if (ivPlayPause != null) {
            ivPlayPause.setImageResource(playing ? R.drawable.icon_pause : R.drawable.icon_play_mini);
        }
    }

    private void updateProgress() {
        if (mVideoView == null) return;
        long current = mVideoView.getCurrentPosition();
        long duration = mVideoView.getDuration();
        if (duration > 0) {
            if (seekBar != null) seekBar.setProgress((int) (current * 1000 / duration));
            if (tvCurrentPos != null) tvCurrentPos.setText(formatTime(current));
            if (tvDuration != null) tvDuration.setText(formatTime(duration));
        }
    }

    private void enterFullScreen() {
        isFullScreen = true;
        // 横屏
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        // 隐藏状态栏和导航栏（沉浸式全屏）
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
        // 让播放器容器撑满整个屏幕
        if (flPlayerContainer != null) {
            ViewGroup.LayoutParams lp = flPlayerContainer.getLayoutParams();
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            if (lp instanceof LinearLayout.LayoutParams) {
                ((LinearLayout.LayoutParams) lp).weight = 100;
            }
            flPlayerContainer.setLayoutParams(lp);
        }
        // 隐藏右侧列表/标题等非播放区域
        hideNonPlayerViews(true);
        if (ivFullscreen != null) ivFullscreen.setImageResource(R.drawable.icon_exit_fullscreen);
    }

    private void exitFullScreen() {
        isFullScreen = false;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        // 恢复状态栏和导航栏
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        // 恢复播放器容器原始大小（手机16:9，平板65%）
        if (flPlayerContainer != null) {
            ViewGroup.LayoutParams lp = flPlayerContainer.getLayoutParams();
            if (PadUiHelper.isPad(this)) {
                lp.width = 0;
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                if (lp instanceof LinearLayout.LayoutParams) {
                    ((LinearLayout.LayoutParams) lp).weight = 65;
                }
            } else {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                lp.height = 0;
                if (lp instanceof LinearLayout.LayoutParams) {
                    ((LinearLayout.LayoutParams) lp).weight = 0;
                }
            }
            flPlayerContainer.setLayoutParams(lp);
            if (!PadUiHelper.isPad(this)) {
                adjustPlayerHeight();
            }
        }
        // 恢复右侧列表/标题等视图
        hideNonPlayerViews(false);
        if (ivFullscreen != null) ivFullscreen.setImageResource(R.drawable.icon_fullscreen);
    }

    /** 全屏时隐藏播放器区域以外的所有视图 */
    private void hideNonPlayerViews(boolean hide) {
        int visibility = hide ? View.GONE : View.VISIBLE;
        // 遍历播放器容器的父布局，隐藏其兄弟节点（列表区、标题区等）
        if (flPlayerContainer == null) return;
        ViewGroup parent = (ViewGroup) flPlayerContainer.getParent();
        if (parent == null) return;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child != flPlayerContainer) {
                child.setVisibility(visibility);
            }
        }
    }

    private String formatTime(long ms) {
        long s = ms / 1000;
        long m = s / 60;
        s = s % 60;
        long h = m / 60;
        m = m % 60;
        if (h > 0) {
            return String.format("%d:%02d:%02d", h, m, s);
        }
        return String.format("%02d:%02d", m, s);
    }

    private List<File> getVideosInFolder(String folder) {
        List<File> result = new ArrayList<>();
        if (folder == null) return result;

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
            // fallback
            File dir = new File(folder);
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && isVideoFile(f.getName())) result.add(f);
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
    public void onBackPressed() {
        if (isFullScreen) {
            exitFullScreen();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mVideoView != null) mVideoView.pause();
        handler.removeCallbacks(progressRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mVideoView != null && !mVideoView.isPlaying()) mVideoView.resume();
        handler.post(progressRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (mVideoView != null) mVideoView.release();
        executor.shutdownNow();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}
