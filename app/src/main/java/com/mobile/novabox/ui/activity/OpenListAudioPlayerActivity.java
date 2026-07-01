package com.mobile.novabox.ui.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
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
 * OpenList 音频播放页（竖屏，手机/平板适配）。
 * 使用 MyVideoView 内核播放音频，展示音乐播放器 UI。
 * 背景复用 NovaBox 全局壁纸，字体/图标为黑色。
 */
public class OpenListAudioPlayerActivity extends BaseActivity {
    private MyVideoView mVideoView;
    private TextView tvSongName;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private SeekBar seekBar;
    private ProgressBar pbLoading;
    private ImageView ivPlayPause;
    private ImageView ivBack;

    private String path;
    private String name;
    private boolean userSeeking = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mVideoView != null && !userSeeking) {
                int position = PlayerUtils.safeTimeMs(mVideoView.getCurrentPosition());
                int duration = PlayerUtils.safeTimeMs(mVideoView.getDuration());
                if (duration > 0) seekBar.setProgress(position * 1000 / duration);
                tvCurrentTime.setText(PlayerUtils.stringForTime(position));
                tvTotalTime.setText(PlayerUtils.stringForTime(duration));
            }
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_openlist_audio_player;
    }

    @Override
    protected void init() {
        Bundle bundle = getIntent() != null ? getIntent().getExtras() : null;
        path = bundle != null ? bundle.getString("path", "") : "";
        name = bundle != null ? bundle.getString("name", "") : "";

        if (TextUtils.isEmpty(path)) {
            Toast.makeText(mContext, "文件路径无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mVideoView    = findViewById(R.id.mOpenListAudioView);
        tvSongName    = findViewById(R.id.tvOpenListSongName);
        tvCurrentTime = findViewById(R.id.tvOpenListAudioCurTime);
        tvTotalTime   = findViewById(R.id.tvOpenListAudioTotalTime);
        seekBar       = findViewById(R.id.seekBarOpenListAudio);
        pbLoading     = findViewById(R.id.pbOpenListAudioLoading);
        ivPlayPause   = findViewById(R.id.ivOpenListAudioPlayPause);
        ivBack        = findViewById(R.id.ivOpenListAudioBack);

        tvSongName.setText(name);
        PlayerHelper.updateCfg(mVideoView);

        ivBack.setOnClickListener(v -> finish());

        ivPlayPause.setOnClickListener(v -> {
            if (mVideoView.isPlaying()) {
                mVideoView.pause();
                ivPlayPause.setImageResource(R.drawable.icon_play_mini);
            } else {
                mVideoView.start();
                ivPlayPause.setImageResource(R.drawable.icon_pause);
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    long duration = mVideoView.getDuration();
                    tvCurrentTime.setText(PlayerUtils.stringForTime((int)(duration * progress / 1000)));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { userSeeking = true; }
            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                long duration = mVideoView.getDuration();
                mVideoView.seekTo(duration * bar.getProgress() / 1000);
                userSeeking = false;
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
                    default:
                        pbLoading.setVisibility(View.GONE);
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
