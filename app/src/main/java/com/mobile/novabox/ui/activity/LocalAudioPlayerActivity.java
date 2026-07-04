package com.mobile.novabox.ui.activity;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
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
import com.mobile.novabox.bean.LocalAudioFile;
import com.mobile.novabox.player.MyVideoView;
import com.mobile.novabox.ui.widget.LrcView;
import com.mobile.novabox.util.PadUiHelper;
import com.mobile.novabox.util.PlayerHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.util.PlayerUtils;

public class LocalAudioPlayerActivity extends BaseActivity {

    // 播放模式
    private static final int MODE_LIST     = 0;
    private static final int MODE_SHUFFLE  = 1;
    private static final int MODE_REPEAT_1 = 2;

    // Views
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
    private FrameLayout  flMobileCenter;
    private LinearLayout llCoverPanel;

    // 平板端专用
    private FrameLayout flPadCoverArea;

    // 播放列表面板
    private LinearLayout llQueuePanel;
    private RecyclerView rvQueueList;
    private TextView     tvQueueCurrentSong, tvQueueCurrentArtist;
    private TextView     tvQueueCount, tvQueuePlayMode;

    // 状态
    private boolean isPad;
    private boolean userSeeking  = false;
    private boolean showingLrc   = false;
    private boolean queueVisible = false;
    private int     playMode     = MODE_LIST;

    // 播放列表
    private final List<LocalAudioFile> playlist     = new ArrayList<>();
    private final List<Integer>        shuffleOrder = new ArrayList<>();
    private int currentIndex = 0;

    private QueueAdapter queueAdapter;
    private ExecutorService metaExecutor = Executors.newSingleThreadExecutor();

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

    private GestureDetector playerGestureDetector;
    private GestureDetector queueGestureDetector;

    @Override
    protected int getLayoutResID() {
        // 复用 OpenList 音频播放布局
        return R.layout.activity_openlist_audio_player;
    }

    @Override
    protected void init() {
        isPad = PadUiHelper.isPad(this);

        Bundle bundle = getIntent() != null ? getIntent().getExtras() : null;
        String[] paths = bundle != null ? bundle.getStringArray("playlistPaths") : null;
        currentIndex   = bundle != null ? bundle.getInt("playlistIndex", 0) : 0;

        if (paths == null || paths.length == 0) { finish(); return; }

        // 重建播放列表
        for (String p : paths) {
            LocalAudioFile f = new LocalAudioFile();
            f.path  = p;
            f.title = stripExt(new File(p).getName());
            f.folderPath = new File(p).getParent();
            playlist.add(f);
        }

        // 绑定 Views（与 OpenListAudioPlayerActivity 相同 ID）
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

        lrcView.setEmptyText("暂无歌词");
        PlayerHelper.updateCfg(mVideoView);

        // 播放列表 adapter
        queueAdapter = new QueueAdapter();
        rvQueueList.setLayoutManager(new LinearLayoutManager(this));
        rvQueueList.setAdapter(queueAdapter);

        // 点击事件
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

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean user) {
                if (user) {
                    long dur = mVideoView.getDuration();
                    tvCurrentTime.setText(PlayerUtils.stringForTime((int)(dur * p / 1000)));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar b) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar b) {
                mVideoView.seekTo(mVideoView.getDuration() * b.getProgress() / 1000);
                userSeeking = false;
            }
        });

        mVideoView.addOnStateChangeListener(new VideoView.SimpleOnStateChangeListener() {
            @Override public void onPlayStateChanged(int state) {
                switch (state) {
                    case VideoView.STATE_PREPARING:
                    case VideoView.STATE_BUFFERING:
                        pbLoading.setVisibility(View.VISIBLE); break;
                    case VideoView.STATE_PLAYING:
                    case VideoView.STATE_BUFFERED:
                        pbLoading.setVisibility(View.GONE);
                        ivPlayPause.setImageResource(R.drawable.icon_pause); break;
                    case VideoView.STATE_PAUSED:
                        pbLoading.setVisibility(View.GONE);
                        ivPlayPause.setImageResource(R.drawable.icon_play_mini); break;
                    case VideoView.STATE_PLAYBACK_COMPLETED:
                        pbLoading.setVisibility(View.GONE);
                        onTrackCompleted(); break;
                    default:
                        pbLoading.setVisibility(View.GONE); break;
                }
            }
        });

        // 手势
        if (isPad) {
            flPadCoverArea = findViewById(R.id.flPadCoverArea);
            setupPadCoverGesture();
        } else {
            setupPlayerGesture();
        }
        setupQueueGesture();

        handler.post(progressRunnable);
        buildShuffleOrder();
        playByIndex(currentIndex);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  手势
    // ═══════════════════════════════════════════════════════════════════════════

    @SuppressLint("ClickableViewAccessibility")
    private void setupPadCoverGesture() {
        if (flPadCoverArea == null) return;
        GestureDetector g = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                if (e1 == null || e2 == null) return false;
                float dY = e2.getY() - e1.getY();
                if (Math.abs(dY) > 80 && Math.abs(vY) > 100) {
                    if (dY < 0 && !queueVisible) { showQueuePanel(); return true; }
                    if (dY > 0 && queueVisible)  { hideQueuePanel(); return true; }
                }
                return false;
            }
        });
        flPadCoverArea.setOnTouchListener((v, e) -> { g.onTouchEvent(e); return !queueVisible; });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupPlayerGesture() {
        playerGestureDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                if (e1 == null || e2 == null) return false;
                float dX = e2.getX() - e1.getX();
                float dY = e2.getY() - e1.getY();
                if (Math.abs(dX) > Math.abs(dY)) {
                    if (Math.abs(dX) > 80 && Math.abs(vX) > 100) {
                        if (dX < 0 && !showingLrc)  { animateSwitchToLrc();   return true; }
                        if (dX > 0 && showingLrc)   { animateSwitchToCover(); return true; }
                    }
                } else {
                    if (dY < -80 && Math.abs(vY) > 100) { showQueuePanel(); return true; }
                }
                return false;
            }
        });
        if (flMobileCenter != null)
            flMobileCenter.setOnTouchListener((v, e) -> { playerGestureDetector.onTouchEvent(e); return true; });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupQueueGesture() {
        queueGestureDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                if (e1 == null || e2 == null) return false;
                float dY = e2.getY() - e1.getY();
                if (dY > 80 && Math.abs(vY) > 100) { hideQueuePanel(); return true; }
                return false;
            }
        });
        View tip = llQueuePanel.getChildAt(0);
        if (tip != null) tip.setOnTouchListener((v, e) -> { queueGestureDetector.onTouchEvent(e); return true; });
        llQueuePanel.setOnTouchListener((v, e) -> { queueGestureDetector.onTouchEvent(e); return false; });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  封面 / 歌词切换
    // ═══════════════════════════════════════════════════════════════════════════

    private void animateSwitchToLrc() {
        if (showingLrc || lrcView == null || llCoverPanel == null) return;
        lrcView.setVisibility(View.VISIBLE); lrcView.setAlpha(0f);
        lrcView.animate().alpha(1f).setDuration(250).start();
        llCoverPanel.animate().alpha(0f).setDuration(250)
                .withEndAction(() -> { llCoverPanel.setVisibility(View.GONE); llCoverPanel.setAlpha(1f); }).start();
        showingLrc = true;
    }

    private void animateSwitchToCover() {
        if (!showingLrc || llCoverPanel == null) return;
        llCoverPanel.setVisibility(View.VISIBLE); llCoverPanel.setAlpha(0f);
        llCoverPanel.animate().alpha(1f).setDuration(250).start();
        lrcView.animate().alpha(0f).setDuration(250)
                .withEndAction(() -> { lrcView.setVisibility(View.GONE); lrcView.setAlpha(1f); }).start();
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
            llQueuePanel.setTranslationY(-(llQueuePanel.getHeight() > 0 ? llQueuePanel.getHeight() : 2000f));
        } else {
            llQueuePanel.setTranslationY(llQueuePanel.getHeight() > 0 ? llQueuePanel.getHeight() : 2000f);
        }
        llQueuePanel.animate().translationY(0f).setDuration(300).start();
        updateQueueUI();
        if (currentIndex >= 0) rvQueueList.post(() -> rvQueueList.scrollToPosition(currentIndex));
    }

    private void hideQueuePanel() {
        if (!queueVisible) return;
        queueVisible = false;
        float targetY = isPad
                ? -(llQueuePanel.getHeight() > 0 ? llQueuePanel.getHeight() : 2000f)
                : (llQueuePanel.getHeight() > 0 ? llQueuePanel.getHeight() : 2000f);
        llQueuePanel.animate().translationY(targetY).setDuration(300)
                .withEndAction(() -> llQueuePanel.setVisibility(View.GONE)).start();
    }

    private void updateQueueUI() {
        if (currentIndex >= 0 && currentIndex < playlist.size()) {
            tvQueueCurrentSong.setText(playlist.get(currentIndex).title);
            tvQueueCurrentArtist.setVisibility(View.GONE);
        }
        tvQueueCount.setText(playlist.size() + " 首");
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
            case MODE_LIST:     ivPlayMode.setImageResource(R.drawable.ic_play_mode_list);       break;
            case MODE_SHUFFLE:  ivPlayMode.setImageResource(R.drawable.ic_play_mode_shuffle);    break;
            case MODE_REPEAT_1: ivPlayMode.setImageResource(R.drawable.ic_play_mode_repeat_one); break;
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
        if (playMode == MODE_REPEAT_1) { mVideoView.replay(true); return; }
        playNext();
    }

    private void playNext() {
        if (playlist.isEmpty()) return;
        int next = playMode == MODE_SHUFFLE ? getShuffleNext(currentIndex, 1) : (currentIndex + 1) % playlist.size();
        playByIndex(next);
    }

    private void playPrevious() {
        if (playlist.isEmpty()) return;
        int prev = playMode == MODE_SHUFFLE ? getShuffleNext(currentIndex, -1) : (currentIndex - 1 + playlist.size()) % playlist.size();
        playByIndex(prev);
    }

    private int getShuffleNext(int cur, int dir) {
        if (shuffleOrder.isEmpty()) buildShuffleOrder();
        int pos = shuffleOrder.indexOf(cur);
        if (pos < 0) return shuffleOrder.get(0);
        return shuffleOrder.get((pos + dir + shuffleOrder.size()) % shuffleOrder.size());
    }

    private void playByIndex(int index) {
        if (index < 0 || index >= playlist.size()) return;
        currentIndex = index;
        LocalAudioFile song = playlist.get(index);

        // 重置 UI
        tvSongName.setText(song.title);
        tvArtist.setVisibility(View.GONE);
        ivAlbumArt.setVisibility(View.GONE);
        llNoCover.setVisibility(View.VISIBLE);
        lrcView.setLrc(null);
        seekBar.setProgress(0);
        tvCurrentTime.setText("00:00");
        tvTotalTime.setText("00:00");
        if (queueVisible) updateQueueUI();
        queueAdapter.notifyDataSetChanged();

        // 播放本地文件
        pbLoading.setVisibility(View.VISIBLE);
        mVideoView.release();
        mVideoView.setUrl("file://" + song.path, new HashMap<>());
        mVideoView.start();

        // 异步读取 ID3
        loadMetaAsync(song);
    }

    private void loadMetaAsync(LocalAudioFile song) {
        if (metaExecutor.isShutdown()) metaExecutor = Executors.newSingleThreadExecutor();
        String path = song.path;
        metaExecutor.execute(() -> {
            MediaMetadataRetriever ret = new MediaMetadataRetriever();
            try {
                ret.setDataSource(path);
                String title  = ret.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                String artist = ret.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                byte[] cover  = ret.getEmbeddedPicture();
                Bitmap bmp = cover != null ? android.graphics.BitmapFactory.decodeByteArray(cover, 0, cover.length) : null;

                // 读歌词（同名 .lrc 文件）
                String lrcPath = path.replaceAll("\\.[^.]+$", ".lrc");
                String lrcContent = null;
                File lrcFile = new File(lrcPath);
                if (lrcFile.exists()) {
                    try {
                        lrcContent = new String(java.nio.file.Files.readAllBytes(lrcFile.toPath()), "UTF-8");
                    } catch (Exception ignore) {}
                }
                final String finalTitle  = title;
                final String finalArtist = artist;
                final Bitmap finalBmp    = bmp;
                final String finalLrc    = lrcContent;

                handler.post(() -> {
                    if (isActivityUnavailable()) return;
                    // 仅当还是同一首歌时才更新
                    if (currentIndex < playlist.size() && playlist.get(currentIndex).path.equals(path)) {
                        if (!TextUtils.isEmpty(finalTitle)) {
                            tvSongName.setText(finalTitle);
                            playlist.get(currentIndex).title = finalTitle;
                        }
                        if (!TextUtils.isEmpty(finalArtist)) {
                            tvArtist.setText(finalArtist);
                            tvArtist.setVisibility(View.VISIBLE);
                            playlist.get(currentIndex).artist = finalArtist;
                        }
                        if (finalBmp != null) {
                            ivAlbumArt.setImageBitmap(finalBmp);
                            ivAlbumArt.setVisibility(View.VISIBLE);
                            llNoCover.setVisibility(View.GONE);
                        }
                        lrcView.setLrc(finalLrc);
                        if (queueVisible) updateQueueUI();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try { ret.release(); } catch (Exception ignore) {}
            }
        });
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
        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(getLayoutInflater().inflate(R.layout.item_audio_queue, p, false));
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            LocalAudioFile f = playlist.get(pos);
            h.tvName.setText(f.title);
            h.tvArtist.setVisibility(View.GONE);
            boolean cur = pos == currentIndex;
            h.tvName.setTextColor(cur ? 0xFF1890FF : 0xFF000000);
            h.ivPlaying.setVisibility(cur ? View.VISIBLE : View.INVISIBLE);
            h.itemView.setOnClickListener(v -> { hideQueuePanel(); playByIndex(pos); });
        }
        @Override public int getItemCount() { return playlist.size(); }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  工具
    // ═══════════════════════════════════════════════════════════════════════════

    private String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private boolean isActivityUnavailable() {
        return isFinishing() || (android.os.Build.VERSION.SDK_INT >= 17 && isDestroyed());
    }

    @Override public void onBackPressed() {
        if (queueVisible) { hideQueuePanel(); } else { super.onBackPressed(); }
    }

    @Override protected void onPause()   { super.onPause();   if (mVideoView != null) mVideoView.pause();  }
    @Override protected void onResume()  { super.onResume();  if (mVideoView != null) mVideoView.resume(); }
    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        metaExecutor.shutdownNow();
        if (mVideoView != null) { mVideoView.release(); mVideoView = null; }
        super.onDestroy();
    }
}
