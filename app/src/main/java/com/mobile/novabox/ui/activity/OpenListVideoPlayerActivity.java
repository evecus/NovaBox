package com.mobile.novabox.ui.activity;

import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mobile.novabox.R;
import com.mobile.novabox.base.BaseActivity;
import com.mobile.novabox.bean.OpenListFile;
import com.mobile.novabox.bean.OpenListFsGetData;
import com.mobile.novabox.bean.OpenListFsListData;
import com.mobile.novabox.player.MyVideoView;
import com.mobile.novabox.ui.adapter.OpenListPlaylistAdapter;
import com.mobile.novabox.util.OpenListApi;
import com.mobile.novabox.util.PadUiHelper;
import com.mobile.novabox.util.PlayerHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import xyz.doikki.videoplayer.player.VideoView;

/**
 * OpenList 视频播放页（手机/平板通用）。
 *
 * 手机端：上方16:9播放区域，中间文件名，下方同目录视频播放列表。
 *         控制栏内有全屏按钮，点击旋转横屏并真正全屏。
 *
 * 平板端：左65%播放区域 + 右35%标题和播放列表的小屏布局。
 *         点击全屏按钮切换为完全全屏沉浸式横屏播放。
 */
public class OpenListVideoPlayerActivity extends BaseActivity {

    // ── 播放器区域 ──────────────────────────────────────────────────────────────
    private MyVideoView mVideoView;
    private FrameLayout flPlayerContainer;
    private FrameLayout flControlOverlay;
    private ImageView ivBack;           // 控制层上的返回（全屏时用）
    private ImageView ivPlayPause;
    private SeekBar seekBar;
    private TextView tvCurrentPos;
    private TextView tvDuration;
    private ProgressBar pbLoading;
    private ImageView ivFullscreen;

    // ── 标题和播放列表 ─────────────────────────────────────────────────────────
    private TextView tvVideoTitle;
    private RecyclerView rvPlaylist;
    /** 平板端：右侧面板（含返回键+标题+列表） */
    private View llSidePanel;
    /** 平板端：右侧返回键 */
    private ImageView ivSideBack;

    // ── 状态 ──────────────────────────────────────────────────────────────────
    private String dirPath;             // 当前播放文件所在目录
    private List<OpenListFile> playlist = new ArrayList<>();
    private int currentIndex = 0;
    private OpenListPlaylistAdapter playlistAdapter;

    private boolean controlsVisible = false;
    private boolean isFullScreen = false;
    private boolean isPad = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideControlsRunnable = this::hideControls;
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    // ── 平板：不加 statusBar padding，保持正常显示 ─────────────────────────────
    @Override
    protected void applyStatusBarPadding() {
        // 平板在全屏时会主动隐藏状态栏，小屏时需要正常 padding
        // 此处保持父类默认行为，全屏时再覆盖
        super.applyStatusBarPadding();
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_openlist_video_player;
    }

    @Override
    protected void init() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        isPad = PadUiHelper.isPad(this);

        Bundle bundle = getIntent() != null ? getIntent().getExtras() : null;
        if (bundle == null) { finish(); return; }

        String filePath = bundle.getString("path", "");
        String fileName = bundle.getString("name", "");
        dirPath        = bundle.getString("dirPath", "");
        currentIndex   = bundle.getInt("index", 0);

        if (TextUtils.isEmpty(filePath)) {
            Toast.makeText(mContext, "文件路径无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // ── 绑定视图 ──────────────────────────────────────────────────────────
        flPlayerContainer = findViewById(R.id.flOpenListPlayerContainer);
        mVideoView        = findViewById(R.id.mOpenListVideoView);
        flControlOverlay  = findViewById(R.id.flOpenListControlOverlay);
        ivBack            = findViewById(R.id.ivOpenListVideoBack);
        ivPlayPause       = findViewById(R.id.ivOpenListPlayPause);
        seekBar           = findViewById(R.id.seekBarOpenListVideo);
        tvCurrentPos      = findViewById(R.id.tvOpenListCurrentPos);
        tvDuration        = findViewById(R.id.tvOpenListDuration);
        pbLoading         = findViewById(R.id.pbOpenListVideoLoading);
        ivFullscreen      = findViewById(R.id.ivOpenListFullscreen);
        tvVideoTitle      = findViewById(R.id.tvOpenListVideoTitle);
        rvPlaylist        = findViewById(R.id.rvOpenListPlaylist);
        llSidePanel       = findViewById(R.id.llOpenListSidePanel);   // 平板才有，手机为null
        ivSideBack        = findViewById(R.id.ivOpenListSideBack);     // 平板才有

        tvVideoTitle.setText(fileName);

        // ── 手机：动态设置播放器高度 16:9 ──────────────────────────────────────
        if (!isPad) {
            flPlayerContainer.post(() -> {
                int w = flPlayerContainer.getWidth();
                if (w <= 0) w = getResources().getDisplayMetrics().widthPixels;
                ViewGroup.LayoutParams lp = flPlayerContainer.getLayoutParams();
                lp.height = w * 9 / 16;
                flPlayerContainer.setLayoutParams(lp);
            });
        }

        // ── 播放器 ────────────────────────────────────────────────────────────
        PlayerHelper.updateCfg(mVideoView);
        mVideoView.addOnStateChangeListener(new VideoView.SimpleOnStateChangeListener() {
            @Override
            public void onPlayStateChanged(int state) {
                switch (state) {
                    case VideoView.STATE_PREPARING:
                    case VideoView.STATE_BUFFERING:
                        pbLoading.setVisibility(View.VISIBLE);
                        break;
                    case VideoView.STATE_PLAYING:
                    case VideoView.STATE_BUFFERED:
                    case VideoView.STATE_PREPARED:
                        pbLoading.setVisibility(View.GONE);
                        ivPlayPause.setImageResource(R.drawable.icon_pause);
                        handler.post(progressRunnable);
                        break;
                    case VideoView.STATE_PAUSED:
                        pbLoading.setVisibility(View.GONE);
                        ivPlayPause.setImageResource(R.drawable.icon_play_mini);
                        break;
                    case VideoView.STATE_ERROR:
                        pbLoading.setVisibility(View.GONE);
                        Toast.makeText(mContext, "播放出错", Toast.LENGTH_SHORT).show();
                        break;
                    case VideoView.STATE_PLAYBACK_COMPLETED:
                        pbLoading.setVisibility(View.GONE);
                        handler.removeCallbacks(progressRunnable);
                        playNext();
                        break;
                }
            }
        });

        // ── 控制层交互 ────────────────────────────────────────────────────────
        flPlayerContainer.setOnClickListener(v -> toggleControls());
        flControlOverlay.setOnClickListener(v -> toggleControls());

        // 控制层上的返回：全屏→退出全屏，小屏→返回上页（平板小屏时此按钮隐藏）
        if (ivBack != null) {
            ivBack.setOnClickListener(v -> {
                if (isFullScreen) exitFullScreen();
                else finish();
            });
        }

        // 平板右侧返回键：始终返回上页
        if (ivSideBack != null) {
            ivSideBack.setOnClickListener(v -> finish());
        }

        ivPlayPause.setOnClickListener(v -> {
            if (mVideoView.isPlaying()) {
                mVideoView.pause();
            } else {
                mVideoView.start();
            }
            scheduleHideControls();
        });

        ivFullscreen.setOnClickListener(v -> {
            if (isFullScreen) exitFullScreen();
            else enterFullScreen();
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    long dur = mVideoView.getDuration();
                    tvCurrentPos.setText(formatTime(dur * progress / 1000));
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                handler.removeCallbacks(hideControlsRunnable);
            }
            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                long dur = mVideoView.getDuration();
                mVideoView.seekTo(dur * bar.getProgress() / 1000);
                scheduleHideControls();
            }
        });

        // ── 播放列表 ──────────────────────────────────────────────────────────
        playlistAdapter = new OpenListPlaylistAdapter();
        if (rvPlaylist != null) {
            rvPlaylist.setLayoutManager(new LinearLayoutManager(this));
            rvPlaylist.setAdapter(playlistAdapter);
            playlistAdapter.setOnItemClickListener((adapter, view, position) -> {
                currentIndex = position;
                playAtIndex(currentIndex);
            });
        }

        // ── 加载目录列表后开始播放 ────────────────────────────────────────────
        loadDirAndPlay(filePath, dirPath);
    }

    // ── 目录加载 + 播放 ──────────────────────────────────────────────────────

    private void loadDirAndPlay(String filePath, String dir) {
        // 先直接开始播当前文件，不等目录列表
        pbLoading.setVisibility(View.VISIBLE);
        fetchAndPlay(filePath);

        // 同时异步加载目录列表
        if (!TextUtils.isEmpty(dir)) {
            OpenListApi.listFiles(dir, new OpenListApi.Callback<OpenListFsListData>() {
                @Override
                public void onSuccess(OpenListFsListData data) {
                    runOnUiThread(() -> {
                        if (isActivityUnavailable()) return;
                        List<OpenListFile> videos = new ArrayList<>();
                        if (data.content != null) {
                            for (OpenListFile f : data.content) {
                                if (!f.isDir && f.isVideo()) {
                                    f.parentPath = dir;
                                    videos.add(f);
                                }
                            }
                            Collections.sort(videos, (a, b) -> a.name.compareToIgnoreCase(b.name));
                        }
                        playlist.clear();
                        playlist.addAll(videos);
                        // 找到当前文件在列表中的位置
                        String fname = filePath.contains("/")
                                ? filePath.substring(filePath.lastIndexOf('/') + 1)
                                : filePath;
                        for (int i = 0; i < playlist.size(); i++) {
                            if (playlist.get(i).name.equals(fname)) {
                                currentIndex = i;
                                break;
                            }
                        }
                        playlistAdapter.setNewData(new ArrayList<>(playlist));
                        playlistAdapter.setCurrentIndex(currentIndex);
                        if (rvPlaylist != null) rvPlaylist.scrollToPosition(currentIndex);
                    });
                }
                @Override
                public void onError(String msg) { /* 列表加载失败不影响播放 */ }
            });
        }
    }

    private void playAtIndex(int index) {
        if (index < 0 || index >= playlist.size()) return;
        currentIndex = index;
        OpenListFile file = playlist.get(index);
        tvVideoTitle.setText(file.name);
        playlistAdapter.setCurrentIndex(index);
        if (rvPlaylist != null) rvPlaylist.scrollToPosition(index);
        fetchAndPlay(file.fullPath());
    }

    private void playNext() {
        if (playlist.isEmpty()) return;
        int next = currentIndex + 1;
        if (next < playlist.size()) playAtIndex(next);
    }

    private void fetchAndPlay(String path) {
        pbLoading.setVisibility(View.VISIBLE);
        OpenListApi.getFile(path, new OpenListApi.Callback<OpenListFsGetData>() {
            @Override
            public void onSuccess(OpenListFsGetData data) {
                runOnUiThread(() -> {
                    if (isActivityUnavailable()) return;
                    if (data.rawUrl == null || data.rawUrl.isEmpty()) {
                        Toast.makeText(mContext, "未获取到播放地址", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Map<String, String> headers = new HashMap<>();
                    String token = OpenListApi.getToken();
                    if (!TextUtils.isEmpty(token)) headers.put("Authorization", token);
                    mVideoView.release();
                    mVideoView.setUrl(data.rawUrl, headers);
                    mVideoView.start();
                });
            }
            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    if (isActivityUnavailable()) return;
                    pbLoading.setVisibility(View.GONE);
                    Toast.makeText(mContext, TextUtils.isEmpty(msg) ? "获取播放地址失败" : msg, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // ── 全屏切换 ──────────────────────────────────────────────────────────────

    private void enterFullScreen() {
        isFullScreen = true;
        if (!isPad) {
            // 手机端：仅横屏视频才旋转
            if (isLandscapeVideo()) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            }
        }
        // 隐藏状态栏和导航栏（沉浸式）
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // 清除根视图的 statusBar paddingTop，否则播放器容器顶部会空出一截并透出壁纸
        clearStatusBarPadding();
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
        // 播放器容器撑满屏幕
        ViewGroup.LayoutParams lp = flPlayerContainer.getLayoutParams();
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        if (lp instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) lp).weight = 100;
        }
        flPlayerContainer.setLayoutParams(lp);
        // 隐藏非播放器视图（标题栏、列表、右侧面板）
        setNonPlayerViewsVisible(false);
        // 全屏时控制层上的返回按钮可见（用于退出全屏）
        if (ivBack != null) ivBack.setVisibility(View.VISIBLE);
        // 更新图标
        ivFullscreen.setImageResource(R.drawable.icon_exit_fullscreen);
    }

    private void exitFullScreen() {
        isFullScreen = false;
        // 恢复方向
        if (isPad) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else if (isLandscapeVideo()) {
            // 手机横屏视频：旋转回竖屏
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        // 竖屏视频进入全屏时没有旋转，退出时也无需旋转
        // 恢复系统 UI：必须与 applyStatusBarPadding/hideSysBar 保持一致，
        // 不能用 SYSTEM_UI_FLAG_VISIBLE（会清除 LAYOUT_FULLSCREEN 和 LIGHT_STATUS_BAR，
        // 导致布局突然下移且状态栏图标变白）
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        int uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            uiFlags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(uiFlags);
        // 恢复根视图的 statusBar paddingTop，确保内容不被状态栏遮挡
        restoreStatusBarPadding();
        // 恢复播放器尺寸
        ViewGroup.LayoutParams lp = flPlayerContainer.getLayoutParams();
        if (isPad) {
            lp.width = 0;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            if (lp instanceof LinearLayout.LayoutParams) {
                ((LinearLayout.LayoutParams) lp).weight = 74;
            }
        } else {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = 0;
            if (lp instanceof LinearLayout.LayoutParams) {
                ((LinearLayout.LayoutParams) lp).weight = 0;
            }
        }
        flPlayerContainer.setLayoutParams(lp);
        if (!isPad) {
            // 手机：恢复 16:9 高度
            flPlayerContainer.post(() -> {
                int w = flPlayerContainer.getWidth();
                if (w <= 0) w = getResources().getDisplayMetrics().widthPixels;
                ViewGroup.LayoutParams lp2 = flPlayerContainer.getLayoutParams();
                lp2.height = w * 9 / 16;
                flPlayerContainer.setLayoutParams(lp2);
            });
        }
        // 恢复非播放器视图
        setNonPlayerViewsVisible(true);
        // 恢复控制层上返回按钮：平板小屏时隐藏（用右侧返回），手机竖屏时隐藏
        if (ivBack != null) {
            ivBack.setVisibility(View.GONE);
        }
        ivFullscreen.setImageResource(R.drawable.icon_fullscreen);
    }

    /** 判断当前视频是否为横屏（宽 > 高）。未知时默认按横屏处理。 */
    private boolean isLandscapeVideo() {
        if (mVideoView == null) return true;
        int[] size = mVideoView.getVideoSize();
        if (size == null || size.length < 2 || size[1] == 0) return true;
        return size[0] > size[1];
    }

    /** 全屏时隐藏播放器容器以外的所有兄弟视图 */
    private void setNonPlayerViewsVisible(boolean visible) {
        int v = visible ? View.VISIBLE : View.GONE;
        ViewGroup parent = (ViewGroup) flPlayerContainer.getParent();
        if (parent == null) return;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child != flPlayerContainer) child.setVisibility(v);
        }
    }

    // ── 控制层显示/隐藏 ──────────────────────────────────────────────────────

    private void toggleControls() {
        if (controlsVisible) hideControls();
        else showControls();
    }

    private void showControls() {
        controlsVisible = true;
        flControlOverlay.setVisibility(View.VISIBLE);
        scheduleHideControls();
    }

    private void hideControls() {
        controlsVisible = false;
        flControlOverlay.setVisibility(View.GONE);
        handler.removeCallbacks(hideControlsRunnable);
    }

    private void scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable);
        handler.postDelayed(hideControlsRunnable, 3500);
    }

    // ── 进度 ──────────────────────────────────────────────────────────────────

    private void updateProgress() {
        if (mVideoView == null) return;
        long current  = mVideoView.getCurrentPosition();
        long duration = mVideoView.getDuration();
        if (duration > 0) {
            seekBar.setProgress((int) (current * 1000 / duration));
            tvCurrentPos.setText(formatTime(current));
            tvDuration.setText(formatTime(duration));
        }
    }

    private String formatTime(long ms) {
        long s = ms / 1000, m = s / 60;
        s = s % 60;
        long h = m / 60; m = m % 60;
        return h > 0
                ? String.format("%d:%02d:%02d", h, m, s)
                : String.format("%02d:%02d", m, s);
    }

    // ── 生命周期 ──────────────────────────────────────────────────────────────

    @Override
    public void onBackPressed() {
        if (isFullScreen) { exitFullScreen(); return; }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mVideoView != null && !mVideoView.isPlaying()) mVideoView.resume();
        handler.post(progressRunnable);
        if (isFullScreen) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mVideoView != null) mVideoView.pause();
        handler.removeCallbacks(progressRunnable);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (mVideoView != null) { mVideoView.release(); mVideoView = null; }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onDestroy();
    }

    private boolean isActivityUnavailable() {
        return isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed());
    }
}
