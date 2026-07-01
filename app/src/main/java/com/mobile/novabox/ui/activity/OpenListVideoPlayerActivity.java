package com.mobile.novabox.ui.activity;

import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.mobile.novabox.R;
import com.mobile.novabox.base.BaseActivity;
import com.mobile.novabox.bean.OpenListFsGetData;
import com.mobile.novabox.player.MyVideoView;
import com.mobile.novabox.util.OpenListApi;
import com.mobile.novabox.util.PadUiHelper;
import com.mobile.novabox.util.PlayerHelper;

import java.util.HashMap;
import java.util.Map;

import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.util.PlayerUtils;

/**
 * OpenList 视频播放页（手机/平板适配）。
 *
 * 手机端：竖屏进入，进度条右侧有全屏按钮；点击旋转为横屏真正全屏；再次点击退回竖屏。
 * 平板端：横屏进入即真正全屏（状态栏/导航栏全隐藏），不显示全屏切换按钮。
 */
public class OpenListVideoPlayerActivity extends BaseActivity {
    private MyVideoView mVideoView;
    private TextView tvVideoTitle;
    private ImageView ivBack;
    private ImageView ivPlayPause;
    private SeekBar seekBar;
    private TextView tvCurrentPos;
    private TextView tvDuration;
    private ProgressBar pbLoading;
    private View flControlOverlay;
    private ImageView ivFullscreen;

    private String path;
    private String name;
    private boolean controlsVisible = false;
    private boolean userSeeking = false;
    /** 手机端当前是否处于横屏全屏状态 */
    private boolean isFullscreen = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideControlsRunnable = this::hideControls;
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    // ── 平板：覆盖 applyStatusBarPadding，不加 paddingTop，改为真正全屏 ──────
    @Override
    protected void applyStatusBarPadding() {
        if (PadUiHelper.isPad(this)) {
            // 平板：全屏，状态栏/导航栏都隐藏，内容不需要额外 padding
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
            }
            // 不设置 paddingTop，内容从屏幕顶部开始
        } else {
            // 手机：保持父类逻辑（竖屏时有状态栏 padding）
            super.applyStatusBarPadding();
        }
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_openlist_video_player;
    }

    @Override
    protected void init() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Bundle bundle = getIntent() != null ? getIntent().getExtras() : null;
        path = bundle != null ? bundle.getString("path", "") : "";
        name = bundle != null ? bundle.getString("name", "") : "";

        if (TextUtils.isEmpty(path)) {
            Toast.makeText(mContext, "文件路径无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mVideoView       = findViewById(R.id.mOpenListVideoView);
        tvVideoTitle     = findViewById(R.id.tvOpenListVideoTitle);
        ivBack           = findViewById(R.id.ivOpenListVideoBack);
        ivPlayPause      = findViewById(R.id.ivOpenListPlayPause);
        seekBar          = findViewById(R.id.seekBarOpenListVideo);
        tvCurrentPos     = findViewById(R.id.tvOpenListCurrentPos);
        tvDuration       = findViewById(R.id.tvOpenListDuration);
        pbLoading        = findViewById(R.id.pbOpenListVideoLoading);
        flControlOverlay = findViewById(R.id.flOpenListControlOverlay);
        ivFullscreen     = findViewById(R.id.ivOpenListFullscreen);

        boolean isPad = PadUiHelper.isPad(this);

        if (isPad) {
            // 平板：隐藏全屏按钮（本就横屏全屏），撑满全屏
            ivFullscreen.setVisibility(View.GONE);
            enterTabletFullscreen();
        } else {
            // 手机：显示全屏按钮，动态设置播放器区域高度为屏幕宽度的 9/16
            ivFullscreen.setVisibility(View.VISIBLE);
            View playerContainer = findViewById(R.id.flOpenListPlayerContainer);
            playerContainer.post(() -> {
                int width = playerContainer.getWidth();
                if (width <= 0) width = getResources().getDisplayMetrics().widthPixels;
                android.view.ViewGroup.LayoutParams lp = playerContainer.getLayoutParams();
                lp.height = width * 9 / 16;
                playerContainer.setLayoutParams(lp);
            });

            ivFullscreen.setOnClickListener(v -> {
                if (isFullscreen) {
                    exitFullscreen();
                } else {
                    enterFullscreen();
                }
            });
        }

        tvVideoTitle.setText(name);
        PlayerHelper.updateCfg(mVideoView);

        mVideoView.setOnClickListener(v -> toggleControls());
        flControlOverlay.setOnClickListener(v -> toggleControls());

        ivBack.setOnClickListener(v -> {
            if (isFullscreen) {
                exitFullscreen();
            } else {
                finish();
            }
        });

        ivPlayPause.setOnClickListener(v -> {
            if (mVideoView.isPlaying()) {
                mVideoView.pause();
                ivPlayPause.setImageResource(R.drawable.icon_play_mini);
            } else {
                mVideoView.start();
                ivPlayPause.setImageResource(R.drawable.icon_pause);
            }
            scheduleHideControls();
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    long duration = mVideoView.getDuration();
                    tvCurrentPos.setText(PlayerUtils.stringForTime((int)(duration * progress / 1000)));
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar bar) { userSeeking = true; }
            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                long duration = mVideoView.getDuration();
                mVideoView.seekTo(duration * bar.getProgress() / 1000);
                userSeeking = false;
                scheduleHideControls();
            }
        });

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
                        showControls();
                        break;
                }
            }
        });

        handler.post(progressRunnable);
        loadAndPlay();
    }

    // ── 手机端全屏切换 ────────────────────────────────────────────────────────

    /** 手机：切换为横屏全屏，撑满屏幕 */
    private void enterFullscreen() {
        isFullscreen = true;
        // 1. 旋转为横屏
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        // 2. 隐藏状态栏和导航栏
        hideStatusBarToo();
        // 3. 播放器容器撑满全屏
        View playerContainer = findViewById(R.id.flOpenListPlayerContainer);
        android.view.ViewGroup.LayoutParams lp = playerContainer.getLayoutParams();
        lp.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        playerContainer.setLayoutParams(lp);
        // 4. 隐藏标题、提示等非播放器 View（让外层 LinearLayout 只展示播放器）
        setNonPlayerViewsVisibility(View.GONE);
        // 5. 更新全屏按钮图标
        ivFullscreen.setImageResource(R.drawable.icon_exit_fullscreen);
    }

    /** 手机：退出横屏全屏，恢复竖屏布局 */
    private void exitFullscreen() {
        isFullscreen = false;
        // 1. 恢复竖屏
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        // 2. 恢复系统 UI（只保留 LAYOUT_STABLE）
        hideSysBar();
        // 3. 恢复播放器高度为 9/16
        View playerContainer = findViewById(R.id.flOpenListPlayerContainer);
        playerContainer.post(() -> {
            int width = playerContainer.getWidth();
            if (width <= 0) width = getResources().getDisplayMetrics().widthPixels;
            android.view.ViewGroup.LayoutParams lp = playerContainer.getLayoutParams();
            lp.height = width * 9 / 16;
            playerContainer.setLayoutParams(lp);
        });
        // 4. 恢复标题、提示等 View
        setNonPlayerViewsVisibility(View.VISIBLE);
        // 5. 更新全屏按钮图标
        ivFullscreen.setImageResource(R.drawable.icon_fullscreen);
    }

    /** 平板：进入时就真正全屏（隐藏状态栏/导航栏，撑满） */
    private void enterTabletFullscreen() {
        hideStatusBarToo();
        View playerContainer = findViewById(R.id.flOpenListPlayerContainer);
        android.view.ViewGroup.LayoutParams lp = playerContainer.getLayoutParams();
        lp.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        playerContainer.setLayoutParams(lp);
        setNonPlayerViewsVisibility(View.GONE);
    }

    /** 控制标题栏、分隔线、提示文字的可见性 */
    private void setNonPlayerViewsVisibility(int visibility) {
        View root = (View) findViewById(R.id.flOpenListPlayerContainer).getParent();
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                if (child.getId() != R.id.flOpenListPlayerContainer) {
                    child.setVisibility(visibility);
                }
            }
        }
    }

    // ── onResume：平板需要持续全屏（防止系统 UI 被父类 hideSysBar 恢复） ──────

    @Override
    protected void onResume() {
        super.onResume(); // 会调用 hideSysBar()
        if (mVideoView != null) mVideoView.resume();
        // 平板和手机全屏状态下，都要重新隐藏系统 UI
        if (PadUiHelper.isPad(this) || isFullscreen) {
            hideStatusBarToo();
        }
    }

    // ── 返回键处理 ─────────────────────────────────────────────────────────────

    @Override
    public void onBackPressed() {
        if (isFullscreen) {
            exitFullscreen();
        } else {
            super.onBackPressed();
        }
    }

    // ── 其余原有方法不变 ──────────────────────────────────────────────────────

    private void loadAndPlay() {
        pbLoading.setVisibility(View.VISIBLE);
        OpenListApi.getFile(path, new OpenListApi.Callback<OpenListFsGetData>() {
            @Override
            public void onSuccess(OpenListFsGetData data) {
                runOnUiThread(() -> {
                    if (isActivityUnavailable()) return;
                    if (data.rawUrl == null || data.rawUrl.isEmpty()) {
                        Toast.makeText(mContext, "未获取到播放地址", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    Map<String, String> headers = new HashMap<>();
                    String token = OpenListApi.getToken();
                    if (!TextUtils.isEmpty(token)) headers.put("Authorization", token);
                    mVideoView.setUrl(data.rawUrl, headers);
                    mVideoView.start();
                });
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    if (isActivityUnavailable()) return;
                    Toast.makeText(mContext, TextUtils.isEmpty(msg) ? "获取播放地址失败" : msg, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void updateProgress() {
        if (mVideoView == null || userSeeking) return;
        int position = PlayerUtils.safeTimeMs(mVideoView.getCurrentPosition());
        int duration = PlayerUtils.safeTimeMs(mVideoView.getDuration());
        if (duration > 0) seekBar.setProgress(position * 1000 / duration);
        tvCurrentPos.setText(PlayerUtils.stringForTime(position));
        tvDuration.setText(PlayerUtils.stringForTime(duration));
    }

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
    }

    private void scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable);
        handler.postDelayed(hideControlsRunnable, 3000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mVideoView != null) mVideoView.pause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (mVideoView != null) { mVideoView.release(); mVideoView = null; }
        super.onDestroy();
    }

    private boolean isActivityUnavailable() {
        return isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed());
    }
}
