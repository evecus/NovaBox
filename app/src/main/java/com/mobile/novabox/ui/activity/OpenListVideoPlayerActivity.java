package com.mobile.novabox.ui.activity;

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
import com.mobile.novabox.util.PlayerHelper;

import java.util.HashMap;
import java.util.Map;

import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.util.PlayerUtils;

/**
 * OpenList 视频播放页（竖屏，手机/平板适配）。
 * 复用 NovaBox MyVideoView 播放器内核，布局参考 LocalPlayerActivity。
 * 背景复用 NovaBox 全局壁纸，字体/图标为黑色。
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

    private String path;
    private String name;
    private boolean controlsVisible = false;
    private boolean userSeeking = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideControlsRunnable = this::hideControls;
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

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

        mVideoView     = findViewById(R.id.mOpenListVideoView);
        tvVideoTitle   = findViewById(R.id.tvOpenListVideoTitle);
        ivBack         = findViewById(R.id.ivOpenListVideoBack);
        ivPlayPause    = findViewById(R.id.ivOpenListPlayPause);
        seekBar        = findViewById(R.id.seekBarOpenListVideo);
        tvCurrentPos   = findViewById(R.id.tvOpenListCurrentPos);
        tvDuration     = findViewById(R.id.tvOpenListDuration);
        pbLoading      = findViewById(R.id.pbOpenListVideoLoading);
        flControlOverlay = findViewById(R.id.flOpenListControlOverlay);

        // 动态设置播放器区域高度为屏幕宽度的 9/16
        android.view.View playerContainer = findViewById(R.id.flOpenListPlayerContainer);
        playerContainer.post(() -> {
            int width = playerContainer.getWidth();
            if (width <= 0) width = getResources().getDisplayMetrics().widthPixels;
            android.view.ViewGroup.LayoutParams lp = playerContainer.getLayoutParams();
            lp.height = width * 9 / 16;
            playerContainer.setLayoutParams(lp);
        });

        tvVideoTitle.setText(name);
        PlayerHelper.updateCfg(mVideoView);

        // 点击视频区域显示/隐藏控制层
        mVideoView.setOnClickListener(v -> toggleControls());
        flControlOverlay.setOnClickListener(v -> toggleControls());

        ivBack.setOnClickListener(v -> finish());

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
    protected void onResume() {
        super.onResume();
        if (mVideoView != null) mVideoView.resume();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (mVideoView != null) { mVideoView.release(); mVideoView = null; }
        super.onDestroy();
    }

    private boolean isActivityUnavailable() {
        return isFinishing() || (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed());
    }
}
