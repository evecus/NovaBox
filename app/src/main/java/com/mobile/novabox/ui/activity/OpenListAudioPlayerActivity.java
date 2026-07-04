package com.mobile.novabox.ui.activity;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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
import com.mobile.novabox.ui.widget.LrcView;
import com.mobile.novabox.util.AudioMetadataLoader;
import com.mobile.novabox.util.OkGoHelper;
import com.mobile.novabox.util.OpenListApi;
import com.mobile.novabox.util.PadUiHelper;
import com.mobile.novabox.util.PlayerHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.util.PlayerUtils;

public class OpenListAudioPlayerActivity extends BaseActivity {

    // ─── 播放模式 ──────────────────────────────────────────────────────────────
    private static final int MODE_LIST     = 0; // 列表顺序
    private static final int MODE_SHUFFLE  = 1; // 随机
    private static final int MODE_REPEAT_1 = 2; // 单曲循环

    // ─── Views ────────────────────────────────────────────────────────────────
    private MyVideoView mVideoView;
    private TextView    tvSongName, tvArtist;
    private TextView    tvCurrentTime, tvTotalTime;
    private SeekBar     seekBar;
    private ProgressBar pbLoading;
    private ImageView   ivPlayPause, ivBack, ivAlbumArt;
    private ImageView   ivSkipPrev, ivSkipNext;
    private ImageView   ivPlayMode, ivQueueList;
    private View        llNoCover;
    private LrcView     lrcView;

    // 手机端专用
    private FrameLayout flMobileCenter;
    private LinearLayout llCoverPanel;

    // 平板端专用
    private FrameLayout flPadCoverArea;

    // 播放列表面板
    private LinearLayout llQueuePanel;
    private RecyclerView rvQueueList;
    private TextView     tvQueueCurrentSong, tvQueueCurrentArtist;
    private TextView     tvQueueCount, tvQueuePlayMode;

    // ─── 状态 ─────────────────────────────────────────────────────────────────
    private String  path;          // 当前文件完整路径
    private String  dirPath;       // 所在目录路径
    private String  name;          // 文件名（含扩展名）
    private boolean userSeeking   = false;
    private boolean isPad;
    private boolean showingLrc    = false;   // 是否显示歌词
    private boolean queueVisible  = false;   // 是否显示播放列表
    private int     playMode      = MODE_LIST;

    // ─── 播放列表 ──────────────────────────────────────────────────────────────
    private final List<OpenListFile> playlist      = new ArrayList<>();
    private final List<Integer>      shuffleOrder  = new ArrayList<>();
    private int currentIndex = -1;   // 在 playlist 中的索引

    private QueueAdapter queueAdapter;

    // ─── URL缓存 ──────────────────────────────────────────────────────────────
    private Map<String, String> currentHeaders = new HashMap<>();

    // ─── Handler ──────────────────────────────────────────────────────────────
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

    // ─── 手势 ─────────────────────────────────────────────────────────────────
    private GestureDetector playerGestureDetector;
    private GestureDetector queueGestureDetector;
    private float playerTouchStartY;
    private float queueTouchStartY;

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

        // 计算目录路径
        int lastSlash = path.lastIndexOf('/');
        dirPath = lastSlash > 0 ? path.substring(0, lastSlash) : "/";

        // ─── 绑定 Views ──────────────────────────────────────────────────────
        mVideoView        = findViewById(R.id.mOpenListAudioView);
        tvSongName        = findViewById(R.id.tvOpenListSongName);
        tvArtist          = findViewById(R.id.tvOpenListArtist);
        tvCurrentTime     = findViewById(R.id.tvOpenListAudioCurTime);
        tvTotalTime       = findViewById(R.id.tvOpenListAudioTotalTime);
        seekBar           = findViewById(R.id.seekBarOpenListAudio);
        pbLoading         = findViewById(R.id.pbOpenListAudioLoading);
        ivPlayPause       = findViewById(R.id.ivOpenListAudioPlayPause);
        ivBack            = findViewById(R.id.ivOpenListAudioBack);
        ivAlbumArt        = findViewById(R.id.ivAlbumArt);
        llNoCover         = findViewById(R.id.llNoCover);
        lrcView           = findViewById(R.id.lrcViewMobile);
        ivSkipPrev        = findViewById(R.id.ivSkipPrev);
        ivSkipNext        = findViewById(R.id.ivSkipNext);
        ivPlayMode        = findViewById(R.id.ivPlayMode);
        ivQueueList       = findViewById(R.id.ivQueueList);
        llQueuePanel      = findViewById(R.id.llQueuePanel);
        rvQueueList       = findViewById(R.id.rvQueueList);
        tvQueueCurrentSong  = findViewById(R.id.tvQueueCurrentSong);
        tvQueueCurrentArtist= findViewById(R.id.tvQueueCurrentArtist);
        tvQueueCount      = findViewById(R.id.tvQueueCount);
        tvQueuePlayMode   = findViewById(R.id.tvQueuePlayMode);
        flMobileCenter    = findViewById(R.id.flMobileCenter);
        llCoverPanel      = findViewById(R.id.llCoverPanel);

        // ─── 初始歌名 ────────────────────────────────────────────────────────
        tvSongName.setText(stripExtension(name));
        lrcView.setEmptyText("暂无歌词");
        PlayerHelper.updateCfg(mVideoView);

        // ─── 播放列表 RecyclerView ───────────────────────────────────────────
        queueAdapter = new QueueAdapter();
        rvQueueList.setLayoutManager(new LinearLayoutManager(this));
        rvQueueList.setAdapter(queueAdapter);

        // ─── 点击事件 ────────────────────────────────────────────────────────
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

        ivSkipPrev.setOnClickListener(v -> playPrevious());
        ivSkipNext.setOnClickListener(v -> playNext());

        ivPlayMode.setOnClickListener(v -> cyclePlayMode());

        ivQueueList.setOnClickListener(v -> showQueuePanel());

        tvQueuePlayMode.setOnClickListener(v -> cyclePlayMode());

        // ─── SeekBar ─────────────────────────────────────────────────────────
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

        // ─── 播放状态监听 ─────────────────────────────────────────────────────
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
                    case VideoView.STATE_PLAYBACK_COMPLETED:
                        pbLoading.setVisibility(View.GONE);
                        onTrackCompleted();
                        break;
                    default:
                        pbLoading.setVisibility(View.GONE);
                        break;
                }
            }
        });

        // ─── 手势初始化 ───────────────────────────────────────────────────────
        if (isPad) {
            flPadCoverArea = findViewById(R.id.flPadCoverArea);
            setupPadCoverGesture();
        } else {
            // 手机端：中间区域左/右划切换封面/歌词，上划打开播放列表
            setupPlayerGesture();
        }
        setupQueueGesture();

        // ─── 开始加载 ─────────────────────────────────────────────────────────
        handler.post(progressRunnable);
        // 先加载目录列表，再播放当前曲目
        loadDirAndPlay();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  手势设置
    // ═══════════════════════════════════════════════════════════════════════════

    // ═══════════════════════════════════════════════════════════════════════════
    //  平板端手势：左侧封面区 上划=打开播放列表 / 下划=关闭播放列表
    // ═══════════════════════════════════════════════════════════════════════════

    @SuppressLint("ClickableViewAccessibility")
    private void setupPadCoverGesture() {
        GestureDetector padGesture = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2,
                                   float vX, float vY) {
                if (e1 == null || e2 == null) return false;
                float dY = e2.getY() - e1.getY();
                if (Math.abs(dY) > 80 && Math.abs(vY) > 100) {
                    if (dY < 0 && !queueVisible) {
                        showQueuePanel();
                        return true;
                    } else if (dY > 0 && queueVisible) {
                        hideQueuePanel();
                        return true;
                    }
                }
                return false;
            }
        });
        flPadCoverArea.setOnTouchListener((v, event) -> {
            padGesture.onTouchEvent(event);
            return !queueVisible; // 列表显示时不拦截（让 RecyclerView 能滚动）
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupPlayerGesture() {
        playerGestureDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD     = 80;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2,
                                   float vX, float vY) {
                if (e1 == null || e2 == null) return false;
                float dX = e2.getX() - e1.getX();
                float dY = e2.getY() - e1.getY();
                if (Math.abs(dX) > Math.abs(dY)) {
                    // 水平划动：切换封面/歌词
                    if (Math.abs(dX) > SWIPE_THRESHOLD
                            && Math.abs(vX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (dX < 0 && !showingLrc) {
                            // 右→左：封面→歌词
                            animateSwitchToLrc();
                        } else if (dX > 0 && showingLrc) {
                            // 左→右：歌词→封面
                            animateSwitchToCover();
                        }
                        return true;
                    }
                } else {
                    // 垂直划动：上划打开播放列表
                    if (dY < -SWIPE_THRESHOLD
                            && Math.abs(vY) > SWIPE_VELOCITY_THRESHOLD) {
                        showQueuePanel();
                        return true;
                    }
                }
                return false;
            }
        });

        flMobileCenter.setOnTouchListener((v, event) -> {
            playerGestureDetector.onTouchEvent(event);
            return true;
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupQueueGesture() {
        queueGestureDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2,
                                   float vX, float vY) {
                if (e1 == null || e2 == null) return false;
                float dY = e2.getY() - e1.getY();
                if (dY > 80 && Math.abs(vY) > 100) {
                    hideQueuePanel();
                    return true;
                }
                return false;
            }
        });

        // 提示文字区域：下划关闭（手机端/平板端通用）
        View tipArea = llQueuePanel.getChildAt(0);
        if (tipArea != null) {
            tipArea.setOnTouchListener((v, event) -> {
                queueGestureDetector.onTouchEvent(event);
                return true;
            });
        }
        // 面板整体：不拦截，让 RecyclerView 能正常滚动，手势探测只是旁听
        llQueuePanel.setOnTouchListener((v, event) -> {
            queueGestureDetector.onTouchEvent(event);
            return false;
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  封面 / 歌词切换动画
    // ═══════════════════════════════════════════════════════════════════════════

    private void animateSwitchToLrc() {
        if (showingLrc) return;
        lrcView.setVisibility(View.VISIBLE);
        lrcView.setAlpha(0f);
        lrcView.animate().alpha(1f).setDuration(250).start();
        llCoverPanel.animate().alpha(0f).setDuration(250)
                .withEndAction(() -> {
                    llCoverPanel.setVisibility(View.GONE);
                    llCoverPanel.setAlpha(1f);
                }).start();
        showingLrc = true;
    }

    private void animateSwitchToCover() {
        if (!showingLrc) return;
        llCoverPanel.setVisibility(View.VISIBLE);
        llCoverPanel.setAlpha(0f);
        llCoverPanel.animate().alpha(1f).setDuration(250).start();
        lrcView.animate().alpha(0f).setDuration(250)
                .withEndAction(() -> {
                    lrcView.setVisibility(View.GONE);
                    lrcView.setAlpha(1f);
                }).start();
        showingLrc = false;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  播放列表面板
    // ═══════════════════════════════════════════════════════════════════════════

    private void showQueuePanel() {
        if (queueVisible) return;
        queueVisible = true;
        llQueuePanel.setVisibility(View.VISIBLE);
        if (isPad) {
            // 平板：从上往下滑入（覆盖左侧封面区）
            float startY = -(llQueuePanel.getHeight() > 0 ? llQueuePanel.getHeight() : 2000f);
            llQueuePanel.setTranslationY(startY);
        } else {
            // 手机：从底部往上滑入
            llQueuePanel.setTranslationY(llQueuePanel.getHeight() > 0
                    ? llQueuePanel.getHeight() : 2000f);
        }
        llQueuePanel.animate()
                .translationY(0f)
                .setDuration(300)
                .start();
        updateQueueUI();
        if (currentIndex >= 0) {
            rvQueueList.post(() -> rvQueueList.scrollToPosition(currentIndex));
        }
    }

    private void hideQueuePanel() {
        if (!queueVisible) return;
        queueVisible = false;
        float targetY;
        if (isPad) {
            // 平板：向上滑出
            targetY = -(llQueuePanel.getHeight() > 0 ? llQueuePanel.getHeight() : 2000f);
        } else {
            // 手机：向下滑出
            targetY = llQueuePanel.getHeight() > 0 ? llQueuePanel.getHeight() : 2000f;
        }
        llQueuePanel.animate()
                .translationY(targetY)
                .setDuration(300)
                .withEndAction(() -> llQueuePanel.setVisibility(View.GONE))
                .start();
    }

    private void updateQueueUI() {
        // 当前播放歌曲
        if (currentIndex >= 0 && currentIndex < playlist.size()) {
            OpenListFile f = playlist.get(currentIndex);
            tvQueueCurrentSong.setText(stripExtension(f.name));
            tvQueueCurrentArtist.setVisibility(View.GONE);
        } else {
            tvQueueCurrentSong.setText(stripExtension(name));
        }
        tvQueueCount.setText(playlist.size() + " / 2271");
        // 播放模式文字
        tvQueuePlayMode.setText(playModeLabel());
        queueAdapter.notifyDataSetChanged();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  播放模式
    // ═══════════════════════════════════════════════════════════════════════════

    private void cyclePlayMode() {
        playMode = (playMode + 1) % 3;
        if (playMode == MODE_SHUFFLE) buildShuffleOrder();
        updatePlayModeIcon();
        Toast.makeText(mContext, playModeLabel(), Toast.LENGTH_SHORT).show();
        if (queueVisible) tvQueuePlayMode.setText(playModeLabel());
    }

    private void updatePlayModeIcon() {
        switch (playMode) {
            case MODE_LIST:
                ivPlayMode.setImageResource(R.drawable.ic_play_mode_list);
                break;
            case MODE_SHUFFLE:
                ivPlayMode.setImageResource(R.drawable.ic_play_mode_shuffle);
                break;
            case MODE_REPEAT_1:
                ivPlayMode.setImageResource(R.drawable.ic_play_mode_repeat_one);
                break;
        }
    }

    private String playModeLabel() {
        switch (playMode) {
            case MODE_SHUFFLE:  return "随机播放";
            case MODE_REPEAT_1: return "单曲循环";
            default:            return "列表顺序";
        }
    }

    private void buildShuffleOrder() {
        shuffleOrder.clear();
        for (int i = 0; i < playlist.size(); i++) shuffleOrder.add(i);
        Collections.shuffle(shuffleOrder, new Random());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  播放控制
    // ═══════════════════════════════════════════════════════════════════════════

    private void onTrackCompleted() {
        switch (playMode) {
            case MODE_REPEAT_1:
                // 单曲循环：重播
                mVideoView.replay(true);
                break;
            case MODE_SHUFFLE:
                playNext();
                break;
            default:
                playNext();
                break;
        }
    }

    private void playNext() {
        if (playlist.isEmpty()) return;
        int next;
        if (playMode == MODE_SHUFFLE) {
            next = getShuffleNext(currentIndex, 1);
        } else {
            next = (currentIndex + 1) % playlist.size();
        }
        playByIndex(next);
    }

    private void playPrevious() {
        if (playlist.isEmpty()) return;
        int prev;
        if (playMode == MODE_SHUFFLE) {
            prev = getShuffleNext(currentIndex, -1);
        } else {
            prev = (currentIndex - 1 + playlist.size()) % playlist.size();
        }
        playByIndex(prev);
    }

    private int getShuffleNext(int current, int dir) {
        if (shuffleOrder.isEmpty()) buildShuffleOrder();
        int pos = shuffleOrder.indexOf(current);
        if (pos < 0) return shuffleOrder.get(0);
        int next = (pos + dir + shuffleOrder.size()) % shuffleOrder.size();
        return shuffleOrder.get(next);
    }

    private void playByIndex(int index) {
        if (index < 0 || index >= playlist.size()) return;
        currentIndex = index;
        OpenListFile f = playlist.get(index);
        name = f.name;
        path = f.fullPath();
        // 重置 UI
        tvSongName.setText(stripExtension(name));
        tvArtist.setVisibility(View.GONE);
        ivAlbumArt.setVisibility(View.GONE);
        llNoCover.setVisibility(View.VISIBLE);
        lrcView.setLrc(null);
        seekBar.setProgress(0);
        tvCurrentTime.setText("00:00");
        tvTotalTime.setText("00:00");
        if (queueVisible) updateQueueUI();
        queueAdapter.notifyDataSetChanged();
        loadAndPlay();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  目录加载 & 播放
    // ═══════════════════════════════════════════════════════════════════════════

    private void loadDirAndPlay() {
        // 先播放当前曲目，同时异步拉取目录列表
        loadAndPlay();
        OpenListApi.listFiles(dirPath, new OpenListApi.Callback<OpenListFsListData>() {
            @Override
            public void onSuccess(OpenListFsListData data) {
                runOnUiThread(() -> {
                    if (isActivityUnavailable()) return;
                    playlist.clear();
                    if (data.content != null) {
                        for (OpenListFile f : data.content) {
                            if (!f.isDir && f.isAudio()) {
                                f.parentPath = dirPath;
                                playlist.add(f);
                            }
                        }
                    }
                    // 找到当前文件在列表中的位置
                    currentIndex = 0;
                    for (int i = 0; i < playlist.size(); i++) {
                        if (playlist.get(i).name.equals(name)) {
                            currentIndex = i;
                            break;
                        }
                    }
                    buildShuffleOrder();
                    if (queueVisible) updateQueueUI();
                    queueAdapter.notifyDataSetChanged();
                    if (tvQueueCount != null) {
                        tvQueueCount.setText(playlist.size() + " 首");
                    }
                });
            }
            @Override
            public void onError(String msg) { /* 不影响播放，playlist 为空时按钮无效 */ }
        });
    }

    private void loadAndPlay() {
        pbLoading.setVisibility(View.VISIBLE);
        OpenListApi.getFile(path, new OpenListApi.Callback<OpenListFsGetData>() {
            @Override
            public void onSuccess(OpenListFsGetData data) {
                runOnUiThread(() -> {
                    if (isActivityUnavailable()) return;
                    if (TextUtils.isEmpty(data.rawUrl)) {
                        Toast.makeText(mContext, "未获取到播放地址", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    currentHeaders = new HashMap<>();
                    String token = OpenListApi.getToken();
                    if (!TextUtils.isEmpty(token)) currentHeaders.put("Authorization", token);

                    mVideoView.release();
                    mVideoView.setUrl(data.rawUrl, currentHeaders);
                    mVideoView.start();

                    // 异步读取 ID3
                    AudioMetadataLoader.loadAsync(
                            data.rawUrl, currentHeaders, OkGoHelper.getDefaultClient(),
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
                });
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  元数据应用
    // ═══════════════════════════════════════════════════════════════════════════

    private void applyMetadata(AudioMetadataLoader.Metadata meta) {
        if (!TextUtils.isEmpty(meta.title)) {
            tvSongName.setText(meta.title);
        }
        if (!TextUtils.isEmpty(meta.artist)) {
            tvArtist.setText(meta.artist);
            tvArtist.setVisibility(View.VISIBLE);
        }
        if (meta.cover != null) {
            ivAlbumArt.setImageBitmap(meta.cover);
            ivAlbumArt.setVisibility(View.VISIBLE);
            llNoCover.setVisibility(View.GONE);
        } else {
            ivAlbumArt.setVisibility(View.GONE);
            llNoCover.setVisibility(View.VISIBLE);
        }
        lrcView.setLrc(meta.lyrics);
        if (queueVisible) updateQueueUI();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  播放列表 Adapter
    // ═══════════════════════════════════════════════════════════════════════════

    private class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvArtist;
            ImageView ivPlaying;
            VH(View v) {
                super(v);
                tvName    = v.findViewById(R.id.tvQueueItemName);
                tvArtist  = v.findViewById(R.id.tvQueueItemArtist);
                ivPlaying = v.findViewById(R.id.ivQueueItemPlaying);
            }
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_audio_queue, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            OpenListFile f = playlist.get(position);
            holder.tvName.setText(stripExtension(f.name));
            holder.tvArtist.setVisibility(View.GONE);
            boolean isCurrent = position == currentIndex;
            holder.tvName.setTextColor(isCurrent
                    ? 0xFF1890FF : 0xFF000000);
            holder.ivPlaying.setVisibility(isCurrent ? View.VISIBLE : View.INVISIBLE);
            holder.itemView.setOnClickListener(v -> {
                hideQueuePanel();
                playByIndex(position);
            });
        }

        @Override public int getItemCount() { return playlist.size(); }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  返回键
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void onBackPressed() {
        if (queueVisible) {
            hideQueuePanel();
        } else {
            super.onBackPressed();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  生命周期
    // ═══════════════════════════════════════════════════════════════════════════

    @Override protected void onPause()  { super.onPause();  if (mVideoView != null) mVideoView.pause(); }
    @Override protected void onResume() { super.onResume(); if (mVideoView != null) mVideoView.resume(); }
    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (mVideoView != null) { mVideoView.release(); mVideoView = null; }
        super.onDestroy();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  工具
    // ═══════════════════════════════════════════════════════════════════════════

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
