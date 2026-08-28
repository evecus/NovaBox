package com.mobile.novabox.player.danmu;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
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
        // 开启弹幕时 releaseView() 把 danmuView 设为 GONE，GONE 的 view 不参与 layout，
        // applyScreenRatio 里等待的 onGlobalLayout 永远不会触发，prepare() 就无法执行。
        // 因此在调用 applyScreenRatio 之前先把 danmuView 设为 INVISIBLE（占位但不可见），
        // 保证 layout pass 正常触发，prepare() 完成后 startIfReady 再改为 VISIBLE。
        if (danmuView.getVisibility() == View.GONE) {
            danmuView.setVisibility(View.INVISIBLE);
        }
        // 屏占比会改变 danmuView 的实际高度，弹幕库(DanmakuFlameMaster)在 prepare() 时
        // 才会按 view 当前的测量高度计算轨道范围，所以必须等 resize 的 layout pass 真正
        // 完成后再触发重新加载，否则库内部仍会用旧的画布尺寸计算轨迹。
        // 行数也必须用 resize 之后的真实高度来算，所以放在同一个回调里一起处理。
        applyScreenRatio(() -> {
            applyMaxLinesForCurrentHeight();
            if (reload && !TextUtils.isEmpty(danmuText) && DanmuHelper.isOpen()) {
                prepare(danmuText);
            }
        });
    }

    /**
     * 按“弹幕轨道 view 的当前实际高度 ÷ 单行占用高度”精确计算能容纳的行数。
     * 行数完全由“屏占比决定的轨道高度”和“字号”共同决定，不再提供独立的行数设置项，
     * 这样行数始终跟实际可用像素高度精确匹配，既不会重叠也不会留白。
     */
    private void applyMaxLinesForCurrentHeight() {
        if (danmuView == null || danmakuContext == null) return;
        int viewHeight = danmuView.getHeight();
        int maxLine = viewHeight > 0
                ? computeMaxLineForHeight(viewHeight, DanmuHelper.getSizeScale())
                : Integer.MAX_VALUE;
        HashMap<Integer, Integer> maxLines = new HashMap<>();
        maxLines.put(BaseDanmaku.TYPE_FIX_TOP, maxLine);
        maxLines.put(BaseDanmaku.TYPE_SCROLL_RL, maxLine);
        maxLines.put(BaseDanmaku.TYPE_SCROLL_LR, maxLine);
        maxLines.put(BaseDanmaku.TYPE_FIX_BOTTOM, maxLine);
        danmakuContext.setMaximumLines(maxLines);
    }

    /**
     * 弹幕库(DanmakuFlameMaster)实际解析弹幕时用的基础字号公式是：
     *   textSize(px) = 25f * (density - 0.6f)
     * 这里的 density 是屏幕物理密度(DisplayMetrics.density，如 xhdpi=2.0)，
     * 不是标准 Android sp→px 换算用的 scaledDensity，是弹幕库自己的经验公式
     * (来自库作者在 GitHub issue #124 中的说明)。setScaleTextSize() 再在此基础上乘倍数。
     * 用这个公式才能让算出来的单行高度和弹幕库实际渲染的文字大小一致。
     */
    private static final float BASE_TEXT_SIZE_FACTOR = 25f;
    private static final float BASE_TEXT_SIZE_DENSITY_OFFSET = 0.6f;

    private int computeMaxLineForHeight(int heightPx, float sizeScale) {
        Context ctx = danmuView != null ? danmuView.getContext() : null;
        float density = ctx != null ? ctx.getResources().getDisplayMetrics().density : 1f;
        float textSizePx = BASE_TEXT_SIZE_FACTOR * (density - BASE_TEXT_SIZE_DENSITY_OFFSET) * sizeScale;
        // 单行占用高度 = 字号 + 行间距(px)。行间距越大 → 行数越少 → 弹幕越稀疏,
        // 与 DanmakuContext.margin 的实际行距保持一致,行距和行数同步变化。
        float lineHeightPx = textSizePx + DanmuHelper.getLineSpacingPx();
        // 密度极小等异常情况下退化为单行,避免除零
        if (lineHeightPx <= 0) return 1;
        int lines = (int) Math.floor(heightPx / lineHeightPx);
        return Math.max(1, lines);
    }

    /**
     * 按屏占比调整弹幕轨道高度：弹幕从屏幕顶部开始，只占屏幕高度的一部分。
     * 由于 setLayoutParams 后的尺寸变化要等下一次 layout pass 才会体现在
     * getHeight() 上，这里通过 ViewTreeObserver 等 layout 真正完成后再回调，
     * 避免弹幕库在旧尺寸下 prepare 导致屏占比不生效。
     */
    private void applyScreenRatio(Runnable afterLayout) {
        if (danmuView == null) {
            if (afterLayout != null) afterLayout.run();
            return;
        }
        ViewGroup.LayoutParams lp = danmuView.getLayoutParams();
        if (!(lp instanceof FrameLayout.LayoutParams)) {
            if (afterLayout != null) afterLayout.run();
            return;
        }
        FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
        int ratio = DanmuHelper.getScreenRatio();
        flp.gravity = Gravity.TOP;
        // 顶部边距(view 顶端距离播放器顶部的偏移),与 height/gravity 独立,
        // 横竖屏切换、屏占比变更时通过本方法自动生效。
        flp.topMargin = DanmuHelper.getTopMarginPx();

        int targetHeight;
        if (ratio >= 100) {
            targetHeight = ViewGroup.LayoutParams.MATCH_PARENT;
        } else {
            ViewGroup parent = (ViewGroup) danmuView.getParent();
            int parentHeight = parent != null ? parent.getHeight() : 0;
            if (parentHeight <= 0) {
                // 父容器还没测量完成（例如 Controller 刚创建时），先占满，
                // 等下一次 applySettings/check 触发时父容器已有尺寸再重新计算。
                targetHeight = ViewGroup.LayoutParams.MATCH_PARENT;
            } else {
                targetHeight = Math.round(parentHeight * (ratio / 100f));
            }
        }

        if (flp.height == targetHeight) {
            // 尺寸没变化，不会有新的 layout pass，直接回调
            if (afterLayout != null) afterLayout.run();
            return;
        }

        flp.height = targetHeight;
        danmuView.setLayoutParams(flp);

        if (afterLayout == null) return;
        // 等待这次 requestLayout 真正跑完（onGlobalLayout），再执行回调，
        // 保证弹幕库 prepare() 时读到的是新高度。
        final ViewTreeObserver observer = danmuView.getViewTreeObserver();
        if (observer == null || !observer.isAlive()) {
            danmuView.post(afterLayout);
            return;
        }
        observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                ViewTreeObserver currentObserver = danmuView.getViewTreeObserver();
                if (currentObserver != null && currentObserver.isAlive()) {
                    currentObserver.removeOnGlobalLayoutListener(this);
                }
                afterLayout.run();
            }
        });
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
        // 每次换集/重新加载弹幕都要重新按当前屏占比调整轨道高度，
        // 否则复用旧的 danmuView 尺寸时屏占比设置不会生效。
        applyScreenRatio(() -> {
            applyMaxLinesForCurrentHeight();
            checkAfterRatioApplied();
        });
    }

    private void checkAfterRatioApplied() {
        if (!isVideoReady()) {
            pendingPrepare = true;
            return;
        }
        prepare(danmuText);
    }

    public void startIfReady() {
        if (pendingPrepare && !TextUtils.isEmpty(danmuText) && DanmuHelper.isOpen() && isVideoReady()) {
            pendingPrepare = false;
            // 延迟加载也要先按屏占比调整好轨道高度，再重新计算行数，
            // 否则弹幕库会在未缩放的满屏画布上 prepare。
            applyScreenRatio(() -> {
                applyMaxLinesForCurrentHeight();
                prepare(danmuText);
            });
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
