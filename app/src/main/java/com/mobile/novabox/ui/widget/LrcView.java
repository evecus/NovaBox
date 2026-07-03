package com.mobile.novabox.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 滚动歌词控件：当前行始终滚动到控件垂直中心，左右居中显示。
 */
public class LrcView extends View {

    public static class LrcLine {
        public long timeMs;
        public String text;
        public LrcLine(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    private final List<LrcLine> mLines = new ArrayList<>();
    private final TextPaint mNormalPaint    = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mHighlightPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private int   mCurrentIndex  = -1;
    // mOffset: 绘制时整体 Y 偏移，正值 = 向上移动
    // 第 i 行顶部 Y = i * mLineSpacing - mOffset + h/2 - mLineSpacing/2
    private float mOffset        = 0f;
    private float mTargetOffset  = 0f;
    private boolean mHasLrc      = false;
    private String  mEmptyText   = "暂无歌词";

    private float mLineSpacing;
    private float mNormalTextSize;
    private float mHighlightTextSize;

    private static final float SCROLL_FRACTION = 0.14f;

    private final Runnable mScrollRunnable = new Runnable() {
        @Override public void run() {
            float diff = mTargetOffset - mOffset;
            if (Math.abs(diff) > 0.5f) {
                mOffset += diff * SCROLL_FRACTION;
                invalidate();
                postDelayed(this, 16);
            } else {
                mOffset = mTargetOffset;
                invalidate();
            }
        }
    };

    public LrcView(Context context) { this(context, null); }
    public LrcView(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public LrcView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float sp = context.getResources().getDisplayMetrics().scaledDensity;
        float dp = context.getResources().getDisplayMetrics().density;
        mNormalTextSize    = 17 * sp;
        mHighlightTextSize = 20 * sp;
        mLineSpacing       = dp * 50f;   // 行间距 50dp

        mNormalPaint.setTextSize(mNormalTextSize);
        mNormalPaint.setColor(0xAA000000);
        mNormalPaint.setTextAlign(Paint.Align.LEFT);

        mHighlightPaint.setTextSize(mHighlightTextSize);
        mHighlightPaint.setColor(0xFF000000);
        mHighlightPaint.setTextAlign(Paint.Align.LEFT);
        mHighlightPaint.setTypeface(Typeface.DEFAULT_BOLD);
    }

    /** 解析并设置 LRC 歌词 */
    public void setLrc(String lrcContent) {
        mLines.clear();
        mCurrentIndex = -1;
        mOffset = 0f;
        mTargetOffset = 0f;

        if (lrcContent == null || lrcContent.trim().isEmpty()) {
            mHasLrc = false;
            invalidate();
            return;
        }

        Pattern timePattern = Pattern.compile("\\[(\\d{2}):(\\d{2})(?:\\.(\\d+))?\\](.*)");
        String[] rawLines   = lrcContent.split("\n");
        boolean  found      = false;

        for (String raw : rawLines) {
            raw = raw.trim();
            // 跳过纯元数据标签行，如 [ar:xxx]、[ti:xxx]
            if (raw.matches("\\[(?:ar|ti|al|by|offset|total|hash|sign|qq)[^\\]]*\\].*")) continue;

            Matcher m     = timePattern.matcher(raw);
            List<Long> ts = new ArrayList<>();
            String text   = "";
            while (m.find()) {
                found = true;
                int min = Integer.parseInt(m.group(1));
                int sec = Integer.parseInt(m.group(2));
                String msStr = m.group(3);
                int ms = 0;
                if (msStr != null) {
                    if (msStr.length() == 1)      ms = Integer.parseInt(msStr) * 100;
                    else if (msStr.length() == 2) ms = Integer.parseInt(msStr) * 10;
                    else                           ms = Integer.parseInt(msStr.substring(0, 3));
                }
                ts.add((long)(min * 60000 + sec * 1000 + ms));
                text = m.group(4).trim();
            }
            if (!ts.isEmpty() && !text.isEmpty()) {
                // 过滤制作信息行
                boolean meta = text.matches(".*[词曲编混录制监制作].*[：:].+")
                        || text.matches("(OP|SP|Arranger|Composer|Lyricist|Producer|Mixer|Recorder)[：:].+")
                        || text.matches(".*[许可翻唱翻录使用授权版权].+");
                if (meta) continue;
                for (long t : ts) mLines.add(new LrcLine(t, text));
            }
        }

        if (!found) {
            for (String raw : rawLines) {
                String t = raw.trim();
                if (!t.isEmpty()) mLines.add(new LrcLine(-1, t));
            }
        }

        if (found) mLines.sort((a, b) -> Long.compare(a.timeMs, b.timeMs));

        mHasLrc = !mLines.isEmpty();
        invalidate();
    }

    public void setEmptyText(String text) {
        mEmptyText = text;
        if (!mHasLrc) invalidate();
    }

    public void updateProgress(long positionMs) {
        if (!mHasLrc || mLines.isEmpty()) return;
        if (mLines.get(0).timeMs < 0) return;

        int newIdx = -1;
        for (int i = mLines.size() - 1; i >= 0; i--) {
            if (positionMs >= mLines.get(i).timeMs) { newIdx = i; break; }
        }
        if (newIdx != mCurrentIndex) {
            mCurrentIndex = newIdx;
            // 目标偏移：让当前行中心对齐控件中心
            // 第 i 行中心 = i * mLineSpacing + mLineSpacing/2 - mOffset + h/2
            // 令 = h/2  =>  mOffset = i * mLineSpacing + mLineSpacing/2
            mTargetOffset = mCurrentIndex < 0 ? 0
                    : mCurrentIndex * mLineSpacing + mLineSpacing / 2f;
            removeCallbacks(mScrollRunnable);
            post(mScrollRunnable);
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        if (!mHasLrc) {
            // 暂无歌词居中
            float ty = h / 2f - (mNormalPaint.descent() + mNormalPaint.ascent()) / 2f;
            canvas.drawText(mEmptyText, w / 2f, ty, mNormalPaint);
            return;
        }

        int slWidth = (int)(w * 0.92f);
        int leftMargin = (w - slWidth) / 2;   // 居中边距

        for (int i = 0; i < mLines.size(); i++) {
            // 第 i 行顶部 Y（相对于控件）
            float top = h / 2f + i * mLineSpacing - mOffset;
            // 可见性裁剪
            if (top > h + mLineSpacing || top + mLineSpacing < -mLineSpacing) continue;

            boolean isHL = (i == mCurrentIndex);
            TextPaint paint = isHL ? mHighlightPaint : mNormalPaint;

            StaticLayout sl = new StaticLayout(
                    mLines.get(i).text, paint, slWidth,
                    Layout.Alignment.ALIGN_CENTER,
                    1.0f, 0f, false);

            canvas.save();
            canvas.translate(leftMargin, top - sl.getHeight() / 2f);
            sl.draw(canvas);
            canvas.restore();
        }
    }

    public boolean hasLrc() { return mHasLrc; }
}
