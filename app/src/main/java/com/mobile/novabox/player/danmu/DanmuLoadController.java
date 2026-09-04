package com.mobile.novabox.player.danmu;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.mobile.novabox.api.DanmakuApi;
import com.mobile.novabox.player.MyVideoView;
import com.mobile.novabox.player.controller.VodController;
import com.mobile.novabox.util.DanmuHelper;
import com.mobile.novabox.util.LOG;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.ui.widget.DanmakuView;
import xyz.doikki.videoplayer.player.VideoView;

public class DanmuLoadController {
    public interface LoadCallback {
        void onFailed();
    }

    private final MyVideoView videoView;
    private final VodController controller;
    private final DanmakuView danmuView;
    private final DanmakuContext danmakuContext;
    private final AtomicInteger loadSeq = new AtomicInteger();
    private ExecutorService executor;
    private String danmuText = "";
    private String danmuTitle = "";
    private String danmuEpisode = "";
    private int startedSeq = -1;
    private boolean pendingPrepare;
    private LoadCallback loadCallback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public DanmuLoadController(MyVideoView videoView, VodController controller, DanmakuView danmuView) {
        this.videoView = videoView;
        this.controller = controller;
        this.danmuView = danmuView;
        this.danmakuContext = DanmakuContext.create();
        if (this.videoView != null) {
            this.videoView.setDanmuView(this.danmuView);
        }
        applySettings(false);
    }

    /** 在主线程弹出 Toast（DanmuHelper.isOpen() 时才显示，避免关闭时刷屏） */
    private void toast(String msg) {
        if (!DanmuHelper.isOpen()) return;
        Context ctx = danmuView != null ? danmuView.getContext() : null;
        if (ctx == null && videoView != null) ctx = videoView.getContext();
        if (ctx == null) return;
        final Context finalCtx = ctx;
        mainHandler.post(() -> {
            LOG.i("echo-danmu-toast: " + msg);
            Toast.makeText(finalCtx, "[弹幕] " + msg, Toast.LENGTH_SHORT).show();
        });
    }

    public void applySettings(boolean reload) {
        if (danmuView == null || danmakuContext == null) return;
        if (!DanmuHelper.isOpen()) {
            releaseView();
            if (controller != null) controller.setHasDanmu(!TextUtils.isEmpty(danmuText));
            return;
        }
        danmakuContext.setScrollSpeedFactor(DanmuHelper.getSpeed())
                .setDanmakuTransparency(DanmuHelper.getAlpha())
                .setScaleTextSize(DanmuHelper.getSizeScale());
        // 行间距:弹幕库的行距由 DanmakuContext.margin 控制(DanmakusRetainer 里
        // topPos = lastItem.getBottom() + margin),这里直接用用户设置的 px 值。
        // 与 computeMaxLineForHeight 中 "字号 + 行间距" 的公式保持一致,行距和行数同步变化。
        danmakuContext.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3)
                .setDanmakuMargin(DanmuHelper.getLineSpacingPx());
        // 弹幕库默认允许同一行内多条弹幕互相重叠(preventOverlapping 默认是 null)，
        // 必须显式对每种类型开启防重叠，库才会在同一行里给弹幕排队，
        // 避免多条弹幕的显示时间窗口重合时叠在一起看不清。
        HashMap<Integer, Boolean> overlapping = new HashMap<>();
        overlapping.put(BaseDanmaku.TYPE_SCROLL_RL, true);
        overlapping.put(BaseDanmaku.TYPE_SCROLL_LR, true);
        overlapping.put(BaseDanmaku.TYPE_FIX_TOP, true);
        overlapping.put(BaseDanmaku.TYPE_FIX_BOTTOM, true);
        danmakuContext.preventOverlapping(overlapping);
        applyMaxLines();
        // 顶部边距变化只需重设 topMargin，不需要等待新的 layout pass 才能拿到行数，
        // 因为行数现在由用户直接指定，不再依赖 view 的实际测量高度(不再有屏占比这个中间概念)。
        applyTopMargin();
        if (reload && !TextUtils.isEmpty(danmuText) && DanmuHelper.isOpen()) {
            prepare(danmuText);
        }
    }

    /**
     * 弹幕行数直接使用用户设置值，不再按 view 高度反算——用户在设置里选几行，
     * 弹幕库的轨道就精确分配几行，所见即所得。
     */
    private void applyMaxLines() {
        if (danmakuContext == null) return;
        int maxLine = DanmuHelper.getMaxLine();
        HashMap<Integer, Integer> maxLines = new HashMap<>();
        maxLines.put(BaseDanmaku.TYPE_FIX_TOP, maxLine);
        maxLines.put(BaseDanmaku.TYPE_SCROLL_RL, maxLine);
        maxLines.put(BaseDanmaku.TYPE_SCROLL_LR, maxLine);
        maxLines.put(BaseDanmaku.TYPE_FIX_BOTTOM, maxLine);
        danmakuContext.setMaximumLines(maxLines);
    }

    /**
     * 弹幕轨道 view 固定占满播放器（MATCH_PARENT），弹幕显示区域的实际范围
     * 完全由"行数"设置决定，顶部边距只用来整体下移轨道起始位置，
     * 两者互相独立，不再需要屏占比这个中间概念。
     */
    private void applyTopMargin() {
        if (danmuView == null) return;
        ViewGroup.LayoutParams lp = danmuView.getLayoutParams();
        if (!(lp instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
        flp.gravity = Gravity.TOP;
        flp.topMargin = DanmuHelper.getTopMarginPx();
        if (flp.height != ViewGroup.LayoutParams.MATCH_PARENT) {
            flp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        }
        danmuView.setLayoutParams(flp);
    }

    public void check(String danmu) {
        check(danmu, "", "");
    }

    public void check(String danmu, String title, String episode) {
        check(danmu, title, episode, null);
    }

    public void check(String danmu, String title, String episode, LoadCallback callback) {
        loadCallback = callback;
        danmuText = TextUtils.isEmpty(danmu) ? "" : danmu.trim();
        danmuTitle = TextUtils.isEmpty(title) ? "" : title;
        danmuEpisode = TextUtils.isEmpty(episode) ? "" : episode;
        releaseView();
        boolean hasDanmu = !TextUtils.isEmpty(danmuText);
        if (controller != null) controller.setHasDanmu(hasDanmu);

        if (!DanmuHelper.isOpen()) {
            if (danmuView != null) danmuView.setVisibility(View.GONE);
            return;
        }

        if (!hasDanmu) {
            if (danmuView != null) danmuView.setVisibility(View.GONE);
            return;
        }

        if (danmuView != null) danmuView.setVisibility(View.VISIBLE);
        // 每次换集/重新加载弹幕都要重新应用当前的行数/顶部边距设置，
        // 否则复用旧的 danmuView 状态时设置不会生效。
        applyMaxLines();
        applyTopMargin();
        if (!isVideoReady()) {
            pendingPrepare = true;
            return;
        }
        prepare(danmuText);
    }

    public void startIfReady() {
        if (pendingPrepare && !TextUtils.isEmpty(danmuText) && DanmuHelper.isOpen() && isVideoReady()) {
            pendingPrepare = false;
            prepare(danmuText);
            return;
        }
        startIfReady(loadSeq.get());
    }

    public void reset() {
        DanmakuApi.cancel();
        danmuText = "";
        danmuTitle = "";
        danmuEpisode = "";
        pendingPrepare = false;
        loadCallback = null;
        loadSeq.incrementAndGet();
        startedSeq = -1;
        if (controller != null) controller.setHasDanmu(false);
        releaseView();
    }

    public void destroy() {
        reset();
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void prepare(String danmu) {
        if (TextUtils.isEmpty(danmu)) return;
        pendingPrepare = false;
        int seq = loadSeq.incrementAndGet();
        startedSeq = -1;
        LOG.i("echo-danmu load title: " + safeLog(danmuTitle) + ", episode: " + safeLog(danmuEpisode) + ", source: " + getSourceSummary(danmu));
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor();
        }
        executor.execute(() -> {
            Parser parser = new Parser(danmu, () -> seq != loadSeq.get());
            if (seq != loadSeq.get()) return;
            int danmuCount = parser.getDanmuCount();
            LOG.i("echo-danmu parsed count: " + danmuCount);
            if (danmuView == null) return;
            danmuView.post(() -> {
                if (seq != loadSeq.get() || danmakuContext == null) return;
                try {
                    danmuView.release();
                    if (videoView != null) videoView.setDanmuView(danmuView);
                    if (danmuCount <= 0) {
                        LOG.e("echo-danmu empty after parse");
                        danmuView.setVisibility(View.GONE);
                        notifyLoadFailed(seq);
                        return;
                    }
                    danmuView.prepare(parser, danmakuContext);
                    clearLoadCallback(seq);
                    danmuView.setVisibility(DanmuHelper.isOpen() ? View.VISIBLE : View.GONE);
                    startIfReady(seq);
                    danmuView.postDelayed(() -> startIfReady(seq), 300);
                    danmuView.postDelayed(() -> startIfReady(seq), 1000);
                } catch (Throwable th) {
                    LOG.e("echo-danmu prepare error: " + th.getMessage());
                    toast("加载弹幕失败");
                    danmuView.setVisibility(View.GONE);
                    notifyLoadFailed(seq);
                }
            });
        });
    }

    private void clearLoadCallback(int seq) {
        if (seq == loadSeq.get()) loadCallback = null;
    }

    private void notifyLoadFailed(int seq) {
        if (seq != loadSeq.get() || loadCallback == null) return;
        LoadCallback callback = loadCallback;
        loadCallback = null;
        callback.onFailed();
    }

    private void startIfReady(int seq) {
        if (seq != loadSeq.get()
                || seq == startedSeq
                || videoView == null
                || !videoView.isPlaying()
                || danmuView == null
                || !danmuView.isPrepared()
                || !DanmuHelper.isOpen()) {
            return;
        }
        long position = videoView.getCurrentPosition();
        danmuView.setVisibility(View.VISIBLE);
        danmuView.seekTo(position);
        danmuView.start(position);
        startedSeq = seq;
        toast("加载弹幕成功");
        LOG.i("echo-danmu start at: " + position);
    }

    private boolean isVideoReady() {
        if (videoView == null) return false;
        int state = videoView.getCurrentPlayState();
        return state == VideoView.STATE_PREPARED
                || state == VideoView.STATE_BUFFERED
                || state == VideoView.STATE_PLAYING;
    }

    private void releaseView() {
        if (danmuView == null) return;
        try {
            danmuView.release();
        } catch (Throwable ignored) {
        }
        danmuView.setVisibility(View.GONE);
    }

    private String getSourceSummary(String danmu) {
        if (TextUtils.isEmpty(danmu)) return "";
        if (danmu.startsWith("http") || danmu.startsWith("file")) return danmu;
        return "inline xml length=" + danmu.length();
    }

    private String safeLog(String text) {
        return TextUtils.isEmpty(text) ? "" : text;
    }
}
