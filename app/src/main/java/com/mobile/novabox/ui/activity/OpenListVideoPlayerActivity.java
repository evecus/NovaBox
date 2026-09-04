package com.mobile.novabox.ui.activity;

import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.res.Configuration;
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
import com.mobile.novabox.ui.dialog.EpisodeSelectDialog;
import com.mobile.novabox.ui.dialog.PlayerSelectDialog;
import com.mobile.novabox.ui.dialog.SpeedSelectDialog;
import com.mobile.novabox.util.OpenListApi;
import com.mobile.novabox.util.PadUiHelper;
import com.mobile.novabox.util.HawkConfig;
import com.mobile.novabox.util.LOG;
import com.mobile.novabox.util.PlayerHelper;
import com.mobile.novabox.util.PlayerSwitchUtil;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import xyz.doikki.videoplayer.player.AbstractPlayer;
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
    private TextView fsTimeInfo;
    private TextView fsCurrTime;
    private TextView fsTotalTime;
    private ImageView fsEnterFullscreen;  // 小屏末尾"进入全屏"图标
    private ImageView fsExitFullscreen;
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
    /** 全屏功能按钮行(仅全屏时显示) */
    private View fsFunctionRow;
    /** 会话级倍速(不持久化) */
    private float currentSpeed = 1.0f;

    private boolean controlsVisible = false;
    private boolean isFullScreen = false;
    private boolean isPad = false;
    /** 标记：是否正在等待方向切回竖屏后还原小屏布局（避免退出全屏时的尺寸计算时序错乱） */
    private boolean pendingExitFullScreen = false;

    // ── 播放失败自动切内核重试 ────────────────────────────────────────────────
    /** 已尝试过的播放内核(0=EXO硬解 1=EXO软解 2=IJK硬解 3=IJK软解),失败时按序切换 */
    private final Set<Integer> triedPlayerTypes = new HashSet<>();
    /** 当前正在使用的内核档位,首次播放时按全局 PLAY_TYPE 初始化 */
    private int currentPlayType = -1;
    /** 当前播放 URL 与请求头,切内核重试时复用 */
    private String currentPlayUrl;
    private Map<String, String> currentPlayHeaders = new HashMap<>();

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
        fsTimeInfo        = findViewById(R.id.fs_time_info);
        fsCurrTime        = findViewById(R.id.fs_curr_time);
        fsTotalTime       = findViewById(R.id.fs_total_time);
        fsEnterFullscreen = findViewById(R.id.ivOpenListFullscreen);
        fsExitFullscreen  = findViewById(R.id.fs_btn_exit_fullscreen);
        pbLoading         = findViewById(R.id.pbOpenListVideoLoading);
        ivFullscreen      = findViewById(R.id.ivOpenListFullscreen);
        tvVideoTitle      = findViewById(R.id.tvOpenListVideoTitle);
        rvPlaylist        = findViewById(R.id.rvOpenListPlaylist);
        llSidePanel       = findViewById(R.id.llOpenListSidePanel);   // 平板才有，手机为null
        ivSideBack        = findViewById(R.id.ivOpenListSideBack);     // 平板才有

        tvVideoTitle.setText(fileName);

        // 投屏按钮(小屏标题行最右侧):用当前播放直链弹设备搜索弹窗,流程与点播详情页投屏一致
        View tvCast = findViewById(R.id.tvOpenListCast);
        if (tvCast != null) {
            tvCast.setOnClickListener(v -> openCastDialog());
        }

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
                        // 播放成功:重置内核尝试状态,若中途再次失败可重新按序尝试其余内核
                        triedPlayerTypes.clear();
                        break;
                    case VideoView.STATE_PAUSED:
                        pbLoading.setVisibility(View.GONE);
                        ivPlayPause.setImageResource(R.drawable.icon_play_mini);
                        break;
                    case VideoView.STATE_ERROR:
                        pbLoading.setVisibility(View.GONE);
                        handler.removeCallbacks(progressRunnable);
                        // 播放失败:按顺序尝试其余三个内核,全部试完才提示
                        if (!retryWithNextPlayer()) {
                            Toast.makeText(mContext, "播放出错", Toast.LENGTH_SHORT).show();
                        }
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
                new PlayerSelectDialog(OpenListVideoPlayerActivity.this, 0, type -> {
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
                new SpeedSelectDialog(OpenListVideoPlayerActivity.this, currentSpeed, speed -> {
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

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    long dur = mVideoView.getDuration();
                    String cur = formatTime(dur * progress / 1000);
                    String total = formatTime(dur);
                    if (fsTimeInfo != null) fsTimeInfo.setText(cur + " / " + total);
                    if (fsCurrTime != null) fsCurrTime.setText(cur);
                    if (fsTotalTime != null) fsTotalTime.setText(total);
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

        // 同时异步加载目录列表(播放器连播需要完整列表,显式请求全量,不走浏览页分页)
        if (!TextUtils.isEmpty(dir)) {
            OpenListApi.listFiles(dir, 1, 100000, new OpenListApi.Callback<OpenListFsListData>() {
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

    /** 选集弹窗:同目录视频列表 */
    private void showEpisodeDialog() {
        if (playlist == null || playlist.isEmpty()) {
            Toast.makeText(this, "无可播放的视频", Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> names = new ArrayList<>();
        for (OpenListFile f : playlist) names.add(f.name);
        new EpisodeSelectDialog(this, "OpenList 视频", names, currentIndex, index -> {
            if (index >= 0 && index < playlist.size()) playAtIndex(index);
        }).show();
    }

    /**
     * 投屏:OpenList 的 rawUrl 已是直链,直接用 CastUrlResolver 处理代理地址转换后,
     * 弹出设备搜索弹窗,流程与点播详情页投屏一致。
     */
    private void openCastDialog() {
        if (TextUtils.isEmpty(currentPlayUrl)) {
            Toast.makeText(this, "暂无可投屏内容", Toast.LENGTH_SHORT).show();
            return;
        }
        com.mobile.novabox.cast.CastUrlResolver.CastResolveResult cr =
                com.mobile.novabox.cast.CastUrlResolver.resolveWithProxyFlag(currentPlayUrl, currentPlayHeaders);
        String castUrl = cr.url;
        if (castUrl == null || castUrl.isEmpty()) {
            Toast.makeText(this, "解析失败,无法投屏", Toast.LENGTH_SHORT).show();
            return;
        }
        String title = tvVideoTitle.getText().toString();
        HashMap<String, String> headers = new HashMap<>();
        if (currentPlayHeaders != null) headers.putAll(currentPlayHeaders);
        com.mobile.novabox.dlna.CastVideo video = new com.mobile.novabox.dlna.CastVideo(castUrl, title, headers, 0);
        com.mobile.novabox.ui.dialog.CastDeviceDialog dialog =
                new com.mobile.novabox.ui.dialog.CastDeviceDialog(OpenListVideoPlayerActivity.this, video);
        // 本次投屏地址依赖本机代理(127.0.0.1:9978 转换而来):
        // 一旦 App 被系统回收/退出,代理消失会导致电视端播放中断,
        // 因此投屏成功后需要启动前台服务 CastProxyService 保活。
        dialog.setUsesLocalProxy(cr.usesLocalProxy);
        dialog.show();
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
                    // 新文件从头开始:重置内核尝试状态,用默认内核起播
                    currentPlayUrl = data.rawUrl;
                    currentPlayHeaders = headers;
                    triedPlayerTypes.clear();
                    currentPlayType = -1;
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

    /**
     * 播放失败时按固定顺序 0→1→2→3 尝试其余三个内核,切换到下一个并重播当前 URL。
     *
     * @return true 已切换内核并重新播放;false 其余三个内核都已试过,停止尝试
     */
    private boolean retryWithNextPlayer() {
        if (mVideoView == null || currentPlayUrl == null) return false;
        // 网络原因访问不了播放地址(IO/超时/服务器不可达):切换播放内核无意义,直接停止尝试
        if (mVideoView.getLastErrorType() == AbstractPlayer.PlayerEventListener.ERROR_TYPE_NETWORK) {
            LOG.i("echo-openlistAutoRetry network error, skip player switch");
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
        LOG.i("echo-openlistAutoRetry switch player: " + next);
        PlayerHelper.updateCfg(mVideoView, next);
        mVideoView.release();
        mVideoView.setUrl(currentPlayUrl, currentPlayHeaders);
        mVideoView.start();
        return true;
    }

    // ── 全屏切换 ──────────────────────────────────────────────────────────────

    private void enterFullScreen() {
        isFullScreen = true;
        // 全屏:显示功能按钮行
        if (fsFunctionRow != null) fsFunctionRow.setVisibility(View.VISIBLE);
        // 根据手机端全屏方向策略决定是否旋转（平板端跳过）
        com.mobile.novabox.util.OrientationHelper.applyEnterFullscreen(this, isLandscapeVideo());
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
        // 全屏:显示时间行 + 按钮行 + 退出全屏;隐藏小屏左/右时间 + 进入全屏图标
        if (fsTimeInfo != null) fsTimeInfo.setVisibility(View.VISIBLE);
        if (fsFunctionRow != null) fsFunctionRow.setVisibility(View.VISIBLE);
        if (fsExitFullscreen != null) fsExitFullscreen.setVisibility(View.VISIBLE);
        if (fsCurrTime != null) fsCurrTime.setVisibility(View.GONE);
        if (fsTotalTime != null) fsTotalTime.setVisibility(View.GONE);
        if (fsEnterFullscreen != null) fsEnterFullscreen.setVisibility(View.GONE);
    }

    private void exitFullScreen() {
        isFullScreen = false;
        // 退出全屏:隐藏时间行 + 按钮行 + 退出全屏;恢复小屏控件
        if (fsTimeInfo != null) fsTimeInfo.setVisibility(View.GONE);
        if (fsFunctionRow != null) fsFunctionRow.setVisibility(View.GONE);
        if (fsExitFullscreen != null) fsExitFullscreen.setVisibility(View.GONE);
        if (fsCurrTime != null) fsCurrTime.setVisibility(View.VISIBLE);
        if (fsTotalTime != null) fsTotalTime.setVisibility(View.VISIBLE);
        if (fsEnterFullscreen != null) fsEnterFullscreen.setVisibility(View.VISIBLE);
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
        // 恢复控制层上返回按钮：平板小屏时隐藏（用右侧返回），手机竖屏时隐藏
        if (ivBack != null) {
            ivBack.setVisibility(View.GONE);
        }

        // 恢复方向:手机端如果确实发起了旋转，要等 onConfigurationChanged 里方向真正
        // 切回竖屏之后再还原播放器容器尺寸，否则 flPlayerContainer.post() 读到的宽度
        // 仍是旋转前(横屏)的宽度，算出来的 16:9 高度是错的，且在旋转完成前的这一帧里
        // 容器高度可能塌陷为 0，导致下面播放列表内容"透"进播放器区域(黑屏上出现残留文字)。
        if (isPad) {
            // 平板端：BaseActivity 始终保持横屏，无需切方向，直接还原布局
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            pendingExitFullScreen = false;
            restoreSmallScreenLayout();
        } else {
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
     * 手机端：在这里做退出全屏后的布局还原，时机比固定延迟精准，避免播放器容器
     * 尺寸计算时用到旋转前的宽度。
     * 平板端：exitFullScreen() 直接调用 restoreSmallScreenLayout()，此处无需处理。
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 平板横屏开屏方向变化事件不触发,且小屏不算全屏 → 不在这里强制显示按钮行
        // 按钮行只在 enterFullScreen 时显示,exitFullScreen 时隐藏
        if (pendingExitFullScreen && newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            pendingExitFullScreen = false;
            restoreSmallScreenLayout();
        }
    }

    /** 退出全屏后还原小屏播放布局(播放器容器尺寸 + statusBar padding + 播放列表) */
    private void restoreSmallScreenLayout() {
        if (isActivityUnavailable()) return;

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
            // 手机端：此时方向已确定切回竖屏(或本就没有旋转)，直接用当前屏幕的短边计算 16:9 高度，
            // 不再依赖 flPlayerContainer.post() 读取可能仍是旧方向的宽度。
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(dm);
            int w = Math.min(dm.widthPixels, dm.heightPixels);
            int h = w * 9 / 16;
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = h;
            if (lp instanceof LinearLayout.LayoutParams) {
                ((LinearLayout.LayoutParams) lp).weight = 0;
            }
        }
        flPlayerContainer.setLayoutParams(lp);

        // 恢复非播放器视图
        setNonPlayerViewsVisible(true);

        // 重置 RecyclerView adapter，清除横屏期间缓存的错误 item 测量，
        // 避免退出全屏后播放列表的旧测量结果残留、错位叠加到播放器区域上方
        if (rvPlaylist != null && playlistAdapter != null) {
            rvPlaylist.setAdapter(null);
            rvPlaylist.setAdapter(playlistAdapter);
            if (currentIndex >= 0) {
                rvPlaylist.scrollToPosition(currentIndex);
            }
        }
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
            String cur = formatTime(current);
            String dur = formatTime(duration);
            if (fsTimeInfo != null) fsTimeInfo.setText(cur + " / " + dur);
            if (fsCurrTime != null) fsCurrTime.setText(cur);
            if (fsTotalTime != null) fsTotalTime.setText(dur);
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
