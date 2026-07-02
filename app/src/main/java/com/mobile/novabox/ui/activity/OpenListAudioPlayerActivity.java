package com.mobile.novabox.ui.activity;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.mobile.novabox.R;
import com.mobile.novabox.base.BaseActivity;
import com.mobile.novabox.bean.OpenListFsGetData;
import com.mobile.novabox.player.MyVideoView;
import com.mobile.novabox.ui.widget.LrcView;
import com.mobile.novabox.util.AudioMetadataLoader;
import com.mobile.novabox.util.OkGoHelper;
import com.mobile.novabox.util.OpenListApi;
import com.mobile.novabox.util.PadUiHelper;
import com.mobile.novabox.util.PlayerHelper;

import java.util.HashMap;
import java.util.Map;

import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.util.PlayerUtils;

public class OpenListAudioPlayerActivity extends BaseActivity {

    private MyVideoView mVideoView;
    private TextView    tvSongName;
    private TextView    tvArtist;
    private TextView    tvCurrentTime;
    private TextView    tvTotalTime;
    private SeekBar     seekBar;
    private ProgressBar pbLoading;
    private ImageView   ivPlayPause;
    private ImageView   ivBack;
    private ImageView   ivAlbumArt;
    private View        llNoCover;
    private LrcView     lrcView;

    // 手机端专用
    private FrameLayout flMobileCenter;
    private LinearLayout llCoverPanel;
    private boolean showingLrc = false;  // 手机端：当前显示歌词还是封面

    private String  path;
    private String  name;
    private boolean userSeeking = false;
    private boolean isPad;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override public void run() {
            if (mVideoView != null && !userSeeking) {
                int pos = PlayerUtils.safeTimeMs(mVideoView.getCurrentPosition());
                int dur = PlayerUtils.safeTimeMs(mVideoView.getDuration());
                if (dur > 0) seekBar.setProgress(pos * 1000 / dur);
                tvCurrentTime.setText(PlayerUtils.stringForTime(pos));
                tvTotalTime.setText(PlayerUtils.stringForTime(dur));
                if (lrcView != null) lrcView.updateProgress(pos);
            }
            handler.postDelayed(this, 250);
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_openlist_audio_player;
    }

    @Override
    protected void init() {
        isPad = PadUiHelper.isPad(this);

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
        tvArtist      = findViewById(R.id.tvOpenListArtist);
        tvCurrentTime = findViewById(R.id.tvOpenListAudioCurTime);
        tvTotalTime   = findViewById(R.id.tvOpenListAudioTotalTime);
        seekBar       = findViewById(R.id.seekBarOpenListAudio);
        pbLoading     = findViewById(R.id.pbOpenListAudioLoading);
        ivPlayPause   = findViewById(R.id.ivOpenListAudioPlayPause);
        ivBack        = findViewById(R.id.ivOpenListAudioBack);
        ivAlbumArt    = findViewById(R.id.ivAlbumArt);
        llNoCover     = findViewById(R.id.llNoCover);
        lrcView       = findViewById(R.id.lrcViewMobile);

        if (!isPad) {
            flMobileCenter = findViewById(R.id.flMobileCenter);
            llCoverPanel   = findViewById(R.id.llCoverPanel);
            // 手机端：点击中间区域切换封面/歌词
            flMobileCenter.setOnClickListener(v -> toggleCoverLrc());
        }

        tvSongName.setText(stripExtension(name));
        lrcView.setEmptyText("暂无歌词");
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
                    long dur = mVideoView.getDuration();
                    tvCurrentTime.setText(PlayerUtils.stringForTime((int)(dur * progress / 1000)));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { userSeeking = true; }
            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                mVideoView.seekTo(mVideoView.getDuration() * bar.getProgress() / 1000);
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
                    default:
                        pbLoading.setVisibility(View.GONE);
                        break;
                }
            }
        });

        handler.post(progressRunnable);
        loadAndPlay();
    }

    // ─── 手机端：切换封面 / 歌词 ─────────────────────────────────────────────

    private void toggleCoverLrc() {
        showingLrc = !showingLrc;
        if (showingLrc) {
            llCoverPanel.setVisibility(View.GONE);
            lrcView.setVisibility(View.VISIBLE);
        } else {
            lrcView.setVisibility(View.GONE);
            llCoverPanel.setVisibility(View.VISIBLE);
        }
    }

    // ─── 加载播放 ────────────────────────────────────────────────────────────

    private void loadAndPlay() {
        pbLoading.setVisibility(View.VISIBLE);
        OpenListApi.getFile(path, new OpenListApi.Callback<OpenListFsGetData>() {
            @Override
            public void onSuccess(OpenListFsGetData data) {
                runOnUiThread(() -> {
                    if (isActivityUnavailable()) return;
                    if (TextUtils.isEmpty(data.rawUrl)) {
                        Toast.makeText(mContext, "未获取到播放地址", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    Map<String, String> headers = new HashMap<>();
                    String token = OpenListApi.getToken();
                    if (!TextUtils.isEmpty(token)) headers.put("Authorization", token);

                    mVideoView.setUrl(data.rawUrl, headers);
                    mVideoView.start();

                    // 异步读取 ID3（封面 / 歌手 / 歌词）
                    AudioMetadataLoader.loadAsync(
                            data.rawUrl, headers, OkGoHelper.getDefaultClient(),
                            new AudioMetadataLoader.Callback() {
                                @Override
                                public void onLoaded(AudioMetadataLoader.Metadata meta) {
                                    runOnUiThread(() -> {
                                        if (isActivityUnavailable()) return;
                                        applyMetadata(meta);
                                    });
                                }
                                @Override public void onError(String msg) { /* 不影响播放 */ }
                            });
                });
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    if (isActivityUnavailable()) return;
                    Toast.makeText(mContext,
                            TextUtils.isEmpty(msg) ? "获取播放地址失败" : msg,
                            Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    // ─── 应用元数据 ───────────────────────────────────────────────────────────

    private void applyMetadata(AudioMetadataLoader.Metadata meta) {
        // 歌名
        if (!TextUtils.isEmpty(meta.title)) {
            tvSongName.setText(meta.title);
        }
        // 歌手
        if (!TextUtils.isEmpty(meta.artist)) {
            // 专辑名与歌曲名相同时不显示专辑（避免重复）
            boolean showAlbum = !TextUtils.isEmpty(meta.album)
                    && !meta.album.equalsIgnoreCase(tvSongName.getText().toString().trim());
            String display = showAlbum ? meta.artist + " · " + meta.album : meta.artist;
            tvArtist.setText(display);
            tvArtist.setVisibility(View.VISIBLE);
        }
        // 封面
        if (meta.cover != null) {
            ivAlbumArt.setImageBitmap(meta.cover);
            ivAlbumArt.setVisibility(View.VISIBLE);
            llNoCover.setVisibility(View.GONE);
        } else {
            ivAlbumArt.setVisibility(View.GONE);
            llNoCover.setVisibility(View.VISIBLE);
        }
        // 歌词
        lrcView.setLrc(meta.lyrics);
    }

    // ─── 生命周期 ─────────────────────────────────────────────────────────────

    @Override protected void onPause()  { super.onPause();  if (mVideoView != null) mVideoView.pause(); }
    @Override protected void onResume() { super.onResume(); if (mVideoView != null) mVideoView.resume(); }
    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (mVideoView != null) { mVideoView.release(); mVideoView = null; }
        super.onDestroy();
    }

    private String stripExtension(String filename) {
        if (TextUtils.isEmpty(filename)) return "";
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private boolean isActivityUnavailable() {
        return isFinishing() ||
                (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1
                        && isDestroyed());
    }
}
