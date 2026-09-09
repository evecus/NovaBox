package com.mobile.novabox.ui.activity;

import android.content.ContentResolver;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
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
import com.mobile.novabox.util.HawkConfig;
import com.mobile.novabox.util.LOG;
import com.mobile.novabox.util.PadUiHelper;
import com.mobile.novabox.ui.dialog.EpisodeSelectDialog;
import com.mobile.novabox.ui.dialog.PlayerSelectDialog;
import com.mobile.novabox.ui.dialog.SpeedSelectDialog;
import com.mobile.novabox.util.PlayerHelper;
import com.mobile.novabox.util.PlayerSwitchUtil;
import com.orhanobut.hawk.Hawk;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import xyz.doikki.videoplayer.player.AbstractPlayer;
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
    private TextView fsTimeInfo;
    private TextView fsCurrTime;
    private TextView fsTotalTime;
    private ImageView fsEnterFullscreen;
    private ImageView fsExitFullscreen;
    private ProgressBar pbLoading;
    private TextView tvVideoTitle;
    private RecyclerView rvPlaylist;
    /** 全屏功能按钮行(仅全屏时显示) */
    private View fsFunctionRow;

    private LocalPlaylistAdapter playlistAdapter;
    /** 会话级倍速(不持久化) */
    private float currentSpeed = 1.0f;

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
    private boolean isLoaded = false;

    // ── 播放失败自动切内核重试 ────────────────────────────────────────────────
    /** 已尝试过的播放内核(0=EXO硬解 1=EXO软解 2=IJK硬解 3=IJK软解),失败时按序切换 */
    private final Set<Integer> triedPlayerTypes = new HashSet<>();
    /** 当前正在使用的内核档位,首次播放时按全局 PLAY_TYPE 初始化 */
    private int currentPlayType = -1;
    /** 当前播放路径(文件路径或 URL),切内核重试时复用 */
    private String currentPlayPath;

    // 标记：是否正在等待方向切回竖屏后还原小屏布局
    private boolean pendingExitFullScreen = false;

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
        // 推断 folderPath:如果调用方没传但 videoUrl 是本地文件路径,取其父目录
        if (folderPath == null && videoUrl != null) {
            java.io.File f = new java.io.File(videoUrl);
            if (f.exists() && f.isAbsolute()) {
                java.io.File parent = f.getParentFile();
                if (parent != null) {
                    folderPath = parent.getAbsolutePath();
                }
            }
        }

        findViews();
        setupPlayer();
        setupControls();
        setupPlaylist();

        if (isUrl && folderPath == null) {
            // 纯 URL 场景(无本地路径):列表为空,不显示
            if (rvPlaylist != null) rvPlaylist.setVisibility(View.GONE);
            if (tvVideoTitle != null) tvVideoTitle.setText(videoTitle != null ? videoTitle : "正在播放");
            isLoaded = true;
            startPlay(videoUrl);
        } else {
            // 有本地目录(不论 folderPath 传没传):走加载列表路径,和 OpenList 行为一致
            if (tvVideoTitle != null) tvVideoTitle.setText(videoTitle != null ? videoTitle : "正在播放");
            isLoaded = true;
            loadFolderAndPlay();
        }

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
        fsTimeInfo = findViewById(R.id.fs_time_info);
        fsCurrTime = findViewById(R.id.fs_curr_time);
        fsTotalTime = findViewById(R.id.fs_total_time);
        fsEnterFullscreen = findViewById(R.id.ivFullscreen);
        fsExitFullscreen = findViewById(R.id.fs_btn_exit_fullscreen);
        pbLoading = findViewById(R.id.pbLoading);
        tvVideoTitle = findViewById(R.id.tvVideoTitle);
        rvPlaylist = findViewById(R.id.rvPlaylist);
    }

    private void adjustPlayerHeight() {
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
        PlayerHelper.updateCfg(mVideoView);

        mVideoView.setOnStateChangeListener(new VideoView.SimpleOnStateChangeListener() {
            @Override
            public void onPlayStateChanged(int playState) {
                switch (playState) {
                    case VideoView.STATE_PLAYING:
                        if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                        updatePlayPauseIcon(true);
                        handler.post(progressRunnable);
                        // 播放成功:重置内核尝试状态,若中途再次失败可重新按序尝试其余内核
                        triedPlayerTypes.clear();
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
                        playNext();
                        break;
                    case VideoView.STATE_ERROR:
                        if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                        handler.removeCallbacks(progressRunnable);
                        // 播放失败:按顺序尝试其余三个内核,全部试完才提示
                        if (!retryWithNextPlayer()) {
                            Toast.makeText(LocalPlayerActivity.this, "播放失败", Toast.LENGTH_SHORT).show();
                        }
                        break;
                }
            }
        });
    }

    private void setupControls() {
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
                int vis = isLocked ? View.INVISIBLE : View.VISIBLE;
                if (ivBack != null) ivBack.setVisibility(vis);
                if (ivPlayPause != null) ivPlayPause.setVisibility(vis);
                if (seekBar != null) seekBar.setVisibility(vis);
                if (ivFullscreen != null) ivFullscreen.setVisibility(vis);
                // 锁定时整个按钮行隐藏(避免左下角播放按钮等"占位但不可点")
                if (fsFunctionRow != null) fsFunctionRow.setVisibility(isLocked ? View.GONE : vis);
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

        // ── 全屏功能按钮行(仅全屏时显示) ──
        fsFunctionRow = findViewById(R.id.fs_function_row);

        ImageView fsBtnNext = findViewById(R.id.fs_btn_next);
        if (fsBtnNext != null) {
            fsBtnNext.setOnClickListener(v -> {
                if (currentIndex + 1 < playlist.size()) playAtIndex(currentIndex + 1);
                scheduleHideControls();
            });
        }

        View fsBtnPlayer = findViewById(R.id.fs_btn_player_select);
        if (fsBtnPlayer != null) {
            fsBtnPlayer.setOnClickListener(v -> {
                new PlayerSelectDialog(LocalPlayerActivity.this, 0, type -> {
                    // 重建播放器重放当前视频(会话级)
                    if (mVideoView != null && currentIndex >= 0 && currentIndex < playlist.size()) {
                        playAtIndex(currentIndex);
                    }
                }).show();
            });
        }

        View fsBtnSpeed = findViewById(R.id.fs_btn_speed);
        if (fsBtnSpeed != null) {
            fsBtnSpeed.setOnClickListener(v -> {
                new SpeedSelectDialog(LocalPlayerActivity.this, currentSpeed, speed -> {
                    currentSpeed = speed;
                    if (mVideoView != null) mVideoView.setSpeed(speed);
                }).show();
            });
        }

        View fsBtnEpisode = findViewById(R.id.fs_btn_episode);
        if (fsBtnEpisode != null) {
            fsBtnEpisode.setOnClickListener(v -> showEpisodeDialog());
        }

        // 退出全屏按钮
        if (fsExitFullscreen != null) {
            fsExitFullscreen.setOnClickListener(v -> {
                if (isFullScreen) exitFullScreen();
            });
        }

        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        long duration = mVideoView.getDuration();
                        long newPos = duration * progress / 1000;
                        String cur = formatTime(newPos);
                        String dur = formatTime(duration);
                        if (fsTimeInfo != null) fsTimeInfo.setText(cur + " / " + dur);
                        if (fsCurrTime != null) fsCurrTime.setText(cur);
                        if (fsTotalTime != null) fsTotalTime.setText(dur);
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
            List<File> files = folderPath != null ? getVideosInFolder(folderPath) : new ArrayList<>();
            handler.post(() -> {
                playlist.clear();
                playlist.addAll(files);
                if (playlistAdapter != null) {
                    playlistAdapter.setNewData(new ArrayList<>(playlist));
                }
                if (playlist.isEmpty()) {
                    // 推断不出目录或目录没视频:切到单视频模式,Toast 提示
                    if (tvVideoTitle != null) tvVideoTitle.setText(videoTitle != null ? videoTitle : "正在播放");
                    if (videoUrl != null) startPlay(videoUrl);
                    Toast.makeText(this, "无可播放的视频列表", Toast.LENGTH_SHORT).show();
                    return;
                }
                // 这里重新扫描出来的 playlist 顺序不一定和调用方（列表页）的顺序一致
                // （不同页面的扫描/排序逻辑可能不同），不能直接信任调用方传来的 startIndex。
                // 优先按“点击时传入的具体文件路径”在这份新列表里查找真实下标，
                // 只有找不到匹配（比如文件已被外部删除/移动）时才退回用 startIndex 兜底。
                int resolvedIndex = resolveStartIndex(files);
                currentIndex = resolvedIndex;
                playAtIndex(currentIndex);
            });
        });
    }

    /**
     * 在新扫描出的播放列表里定位应该播放的文件下标：
     * 1. 优先用 videoUrl（点击时传入的具体文件路径）做绝对路径精确匹配；
     * 2. 匹配不到时，退回调用方传入的 startIndex（并做越界保护）。
     */
    private int resolveStartIndex(List<File> files) {
        if (videoUrl != null && !videoUrl.isEmpty()) {
            File target = new File(videoUrl);
            String targetPath = target.getAbsolutePath();
            for (int i = 0; i < files.size(); i++) {
                if (files.get(i).getAbsolutePath().equals(targetPath)) {
                    return i;
                }
            }
        }
        return Math.max(0, Math.min(startIndex, files.size() - 1));
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

    /** 选集弹窗:同目录视频列表 */
    private void showEpisodeDialog() {
        if (playlist == null || playlist.isEmpty()) {
            Toast.makeText(this, "无可播放的视频", Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> names = new ArrayList<>();
        for (File f : playlist) names.add(f.getName());
        new EpisodeSelectDialog(this, "本地视频", names, currentIndex, index -> {
            if (index >= 0 && index < playlist.size()) playAtIndex(index);
        }).show();
    }

    private void startPlay(String path) {
        if (mVideoView == null || path == null || path.isEmpty()) return;
        currentPlayPath = path;
        // 新文件从头开始:重置内核尝试状态,用默认内核起播
        triedPlayerTypes.clear();
        currentPlayType = -1;
        mVideoView.release();
        if (path.startsWith("http://") || path.startsWith("https://")
                || path.startsWith("rtmp://") || path.startsWith("rtsp://")) {
            mVideoView.setUrl(path);
        } else {
            mVideoView.setUrl(Uri.fromFile(new File(path)).toString());
        }
        mVideoView.start();
    }

    /**
     * 播放失败时按固定顺序 0→1→2→3 尝试其余三个内核,切换到下一个并重播当前视频。
     *
     * @return true 已切换内核并重新播放;false 其余三个内核都已试过,停止尝试
     */
    private boolean retryWithNextPlayer() {
        if (mVideoView == null || currentPlayPath == null) return false;
        // 网络原因访问不了播放地址(IO/超时/服务器不可达):切换播放内核无意义,直接停止尝试
        if (mVideoView.getLastErrorType() == AbstractPlayer.PlayerEventListener.ERROR_TYPE_NETWORK) {
            LOG.i("echo-localAutoRetry network error, skip player switch");
            return false;
        }
        if (currentPlayType < 0) currentPlayType = PlayerSwitchUtil.normalizePlayType(Hawk.get(HawkConfig.PLAY_TYPE, 2));
        int next = PlayerSwitchUtil.nextPlayerType(currentPlayType, triedPlayerTypes);
        if (next < 0) {
            // 全部内核都试过:重置,下次播放从头开始
            triedPlayerTypes.clear();
            currentPlayType = -1;
            return false;
        }
        currentPlayType = next;
        LOG.i("echo-localAutoRetry switch player: " + next);
        PlayerHelper.updateCfg(mVideoView, next);
        mVideoView.release();
        if (currentPlayPath.startsWith("http://") || currentPlayPath.startsWith("https://")
                || currentPlayPath.startsWith("rtmp://") || currentPlayPath.startsWith("rtsp://")) {
            mVideoView.setUrl(currentPlayPath);
        } else {
            mVideoView.setUrl(Uri.fromFile(new File(currentPlayPath)).toString());
        }
        mVideoView.start();
        return true;
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
            String cur = formatTime(current);
            String dur = formatTime(duration);
            // 全屏时间行
            if (fsTimeInfo != null) fsTimeInfo.setText(cur + " / " + dur);
            // 小屏左/右时间
            if (fsCurrTime != null) fsCurrTime.setText(cur);
            if (fsTotalTime != null) fsTotalTime.setText(dur);
        }
    }

    private void enterFullScreen() {
        isFullScreen = true;
        pendingExitFullScreen = false;

        // 根据手机端全屏方向策略决定是否旋转（平板端跳过）
        com.mobile.novabox.util.OrientationHelper.applyEnterFullscreen(this, isLandscapeVideo());

        // 隐藏状态栏和导航栏（不加 FLAG_LAYOUT_NO_LIMITS，避免退出时内容撑出屏幕边界）
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
        // 清除状态栏 padding（消除顶部留白）
        clearStatusBarPadding();

        // 全屏:显示时间行 + 按钮行 + 退出全屏;隐藏小屏左侧时间/总时长/进入全屏图标
        if (fsTimeInfo != null) fsTimeInfo.setVisibility(View.VISIBLE);
        if (fsFunctionRow != null) fsFunctionRow.setVisibility(View.VISIBLE);
        if (fsExitFullscreen != null) fsExitFullscreen.setVisibility(View.VISIBLE);
        if (fsCurrTime != null) fsCurrTime.setVisibility(View.GONE);
        if (fsTotalTime != null) fsTotalTime.setVisibility(View.GONE);
        if (fsEnterFullscreen != null) fsEnterFullscreen.setVisibility(View.GONE);

        // 播放器容器撑满全屏
        if (flPlayerContainer != null) {
            ViewGroup.LayoutParams lp = flPlayerContainer.getLayoutParams();
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            if (lp instanceof LinearLayout.LayoutParams) {
                ((LinearLayout.LayoutParams) lp).weight = 1;
            }
            flPlayerContainer.setLayoutParams(lp);
        }
        // 隐藏标题、列表等非播放区域（平板端为右侧栏）
        hideNonPlayerViews(true);
    }

    private void exitFullScreen() {
        isFullScreen = false;

        // 退出全屏:隐藏功能按钮行(避免小屏界面残留显示)
        if (fsFunctionRow != null) fsFunctionRow.setVisibility(View.GONE);

        // 立即恢复系统 UI 标志（状态栏/导航栏重新显示），与 BaseActivity 保持一致
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        int uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            uiFlags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(uiFlags);

        // 退出全屏:隐藏时间行 + 按钮行 + 退出全屏;恢复小屏左侧时间/总时长/进入全屏图标
        if (fsTimeInfo != null) fsTimeInfo.setVisibility(View.GONE);
        if (fsFunctionRow != null) fsFunctionRow.setVisibility(View.GONE);
        if (fsExitFullscreen != null) fsExitFullscreen.setVisibility(View.GONE);
        if (fsCurrTime != null) fsCurrTime.setVisibility(View.VISIBLE);
        if (fsTotalTime != null) fsTotalTime.setVisibility(View.VISIBLE);
        if (fsEnterFullscreen != null) fsEnterFullscreen.setVisibility(View.VISIBLE);

        if (PadUiHelper.isPad(this)) {
            // 平板端：BaseActivity 始终保持横屏，无需切方向，直接还原布局
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            pendingExitFullScreen = false;
            restoreSmallScreenLayout();
        } else {
            // 根据策略决定是否旋转回竖屏
            boolean willRotate = com.mobile.novabox.util.OrientationHelper.applyExitFullscreen(this, isLandscapeVideo());
            if (willRotate) {
                // 发起了旋转，等 onConfigurationChanged 后还原布局
                pendingExitFullScreen = true;
            } else {
                // 没有旋转，直接还原布局
                pendingExitFullScreen = false;
                restoreSmallScreenLayout();
            }
        }
    }

    /**
     * 由于 Manifest 中配置了 configChanges="orientation|screenSize|..."，
     * 方向切换不会重建 Activity，而是回调此方法。
     * 手机端：在这里做退出全屏后的布局还原，时机比 postDelayed 固定延迟精准。
     * 平板端：exitFullScreen() 直接调用 restoreSmallScreenLayout()，此处无需处理。
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // 平板横屏开屏时方向变化事件不会触发,且小屏不算全屏 → 不在这里强制显示按钮行
        // 按钮行只在 enterFullScreen 时显示,exitFullScreen 时隐藏

        if (pendingExitFullScreen && newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            pendingExitFullScreen = false;
            restoreSmallScreenLayout();
        }
    }

    /** 退出全屏后还原小屏播放布局 */
    /** 判断当前视频是否为横屏（宽 > 高）。未知时默认按横屏处理。 */
    private boolean isLandscapeVideo() {
        if (mVideoView == null) return true;
        int[] size = mVideoView.getVideoSize();
        if (size == null || size.length < 2 || size[1] == 0) return true;
        return size[0] > size[1];
    }

    private void restoreSmallScreenLayout() {
        if (isFinishing()) return;

        // 恢复状态栏顶部 padding（BaseActivity 在 onCreate 时设置的）
        restoreStatusBarPadding();

        if (flPlayerContainer != null) {
            ViewGroup.LayoutParams lp = flPlayerContainer.getLayoutParams();
            if (PadUiHelper.isPad(this)) {
                // 平板端：水平 LinearLayout，播放器占 65% 宽度，高度 match_parent
                lp.width = 0;
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                if (lp instanceof LinearLayout.LayoutParams) {
                    ((LinearLayout.LayoutParams) lp).weight = 74;
                }
            } else {
                // 手机端：垂直 LinearLayout，恢复播放器容器为 16:9 高度
                android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(dm);
                // 竖屏时宽度是短边
                int w = Math.min(dm.widthPixels, dm.heightPixels);
                int h = w * 9 / 16;
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                lp.height = h;
                if (lp instanceof LinearLayout.LayoutParams) {
                    ((LinearLayout.LayoutParams) lp).weight = 0;
                }
            }
            flPlayerContainer.setLayoutParams(lp);
        }

        // 恢复标题、列表等兄弟视图
        hideNonPlayerViews(false);

        // 平板端：还原右侧栏的 LayoutParams（weight=26），确保两栏比例正确
        if (PadUiHelper.isPad(this) && flPlayerContainer != null) {
            ViewGroup parent = (ViewGroup) flPlayerContainer.getParent();
            if (parent != null) {
                for (int i = 0; i < parent.getChildCount(); i++) {
                    View sibling = parent.getChildAt(i);
                    if (sibling != flPlayerContainer) {
                        ViewGroup.LayoutParams slp = sibling.getLayoutParams();
                        if (slp instanceof LinearLayout.LayoutParams) {
                            slp.width = 0;
                            slp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                            ((LinearLayout.LayoutParams) slp).weight = 26;
                            sibling.setLayoutParams(slp);
                        }
                    }
                }
            }
        }

        // 重置 RecyclerView adapter，清除横屏期间缓存的错误 item 测量
        if (rvPlaylist != null && playlistAdapter != null) {
            rvPlaylist.setAdapter(null);
            rvPlaylist.setAdapter(playlistAdapter);
            if (currentIndex >= 0) {
                rvPlaylist.scrollToPosition(currentIndex);
            }
        }
    }

    /** 全屏时隐藏/恢复播放器区域以外的所有视图 */
    private void hideNonPlayerViews(boolean hide) {
        int visibility = hide ? View.GONE : View.VISIBLE;
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

        android.content.ContentResolver cr = getContentResolver();
        try (android.database.Cursor cursor = cr.query(uri, projection, selection, args,
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
