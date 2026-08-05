package com.mobile.novabox.player.danmu;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
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
        HashMap<Integer, Integer> maxLines = new HashMap<>();
        int maxLine = DanmuHelper.getMaxLine();
        maxLines.put(BaseDanmaku.TYPE_FIX_TOP, maxLine);
        maxLines.put(BaseDanmaku.TYPE_SCROLL_RL, maxLine);
        maxLines.put(BaseDanmaku.TYPE_SCROLL_LR, maxLine);
        maxLines.put(BaseDanmaku.TYPE_FIX_BOTTOM, maxLine);
        danmakuContext.setMaximumLines(maxLines)
                .setScrollSpeedFactor(DanmuHelper.getSpeed())
                .setDanmakuTransparency(DanmuHelper.getAlpha())
                .setScaleTextSize(DanmuHelper.getSizeScale());
        danmakuContext.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3)
                .setDanmakuMargin(8);
        if (reload && !TextUtils.isEmpty(danmuText) && DanmuHelper.isOpen()) {
            prepare(danmuText);
        }
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
            toast("弹幕已关闭");
            if (danmuView != null) danmuView.setVisibility(View.GONE);
            return;
        }

        if (!hasDanmu) {
            toast("暂无弹幕源，等待自动搜索...");
            if (danmuView != null) danmuView.setVisibility(View.GONE);
            return;
        }

        toast("找到弹幕源，开始加载...");
        if (danmuView != null) danmuView.setVisibility(View.VISIBLE);
        if (!isVideoReady()) {
            toast("视频未就绪，等待视频准备...");
            pendingPrepare = true;
            return;
        }
        prepare(danmuText);
    }

    public void startIfReady() {
        if (pendingPrepare && !TextUtils.isEmpty(danmuText) && DanmuHelper.isOpen() && isVideoReady()) {
            pendingPrepare = false;
            toast("视频已就绪，开始加载弹幕...");
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
                        toast("弹幕解析为空（共0条），尝试搜索其他来源...");
                        danmuView.setVisibility(View.GONE);
                        notifyLoadFailed(seq);
                        return;
                    }
                    danmuView.prepare(parser, danmakuContext);
                    clearLoadCallback(seq);
                    danmuView.setVisibility(DanmuHelper.isOpen() ? View.VISIBLE : View.GONE);
                    toast("弹幕加载成功，共 " + danmuCount + " 条，等待播放启动...");
                    startIfReady(seq);
                    danmuView.postDelayed(() -> startIfReady(seq), 300);
                    danmuView.postDelayed(() -> startIfReady(seq), 1000);
                } catch (Throwable th) {
                    LOG.e("echo-danmu prepare error: " + th.getMessage());
                    toast("弹幕准备失败: " + th.getMessage());
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
        toast("弹幕已启动，位置: " + position + "ms");
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
