package com.mobile.novabox.util;

import android.graphics.Color;

import com.orhanobut.hawk.Hawk;

public class DanmuHelper {
    private static final String[] PALETTE = new String[]{
            "#ffffff", "#70f3ff", "#44cef6", "#3eede7", "#00e079", "#2edfa3",
            "#bce672", "#fff143", "#ffa631", "#ff7500", "#ff4e20", "#ff2d51",
            "#ef7a82", "#ff0097", "#b0a4e3", "#4b5cc4"
    };

    public static boolean isOpen() {
        return Hawk.get(HawkConfig.DANMU_OPEN, true);
    }

    public static void setOpen(boolean open) {
        Hawk.put(HawkConfig.DANMU_OPEN, open);
    }

    public static float getSpeed() {
        return Hawk.get(HawkConfig.DANMU_SPEED, 1.5f);
    }

    public static void setSpeed(float speed) {
        Hawk.put(HawkConfig.DANMU_SPEED, speed);
    }

    public static float getAlpha() {
        return Hawk.get(HawkConfig.DANMU_ALPHA, 0.9f);
    }

    public static void setAlpha(float alpha) {
        Hawk.put(HawkConfig.DANMU_ALPHA, Math.max(0.1f, Math.min(alpha, 1.0f)));
    }

    public static float getSizeScale() {
        return Hawk.get(HawkConfig.DANMU_SIZE_SCALE, 0.8f);
    }

    public static void setSizeScale(float scale) {
        Hawk.put(HawkConfig.DANMU_SIZE_SCALE, Math.max(0.6f, Math.min(scale, 2.0f)));
    }

    /** 弹幕屏占比：弹幕轨道从屏幕顶部开始，占屏幕高度的百分比（10~100），默认 20 */
    public static int getScreenRatio() {
        return Hawk.get(HawkConfig.DANMU_SCREEN_RATIO, 20);
    }

    public static void setScreenRatio(int ratio) {
        Hawk.put(HawkConfig.DANMU_SCREEN_RATIO, clamp(ratio, 10, 100));
    }

    /**
     * 弹幕行间距(px)：直接作为弹幕库 DanmakuContext.margin 使用,
     * 即相邻弹幕轨道之间的纵向间隔像素。默认 8px(与历史上固定值一致)。
     */
    public static int getLineSpacingPx() {
        return Hawk.get(HawkConfig.DANMU_LINE_SPACING_PX, 8);
    }

    public static void setLineSpacingPx(int px) {
        Hawk.put(HawkConfig.DANMU_LINE_SPACING_PX, clamp(px, 0, 32));
    }

    /**
     * 弹幕顶部边距(像素):弹幕 view 顶端距离播放器顶部的偏移量,
     * 用 FrameLayout.LayoutParams.topMargin 实现(view 实际绘制区整体下移,
     * 弹幕轨道范围自动跟着变,不影响库内部轨道分配)。
     * 默认 0(向后兼容,老用户视觉不变),可调 0~200px(≈0~200dp on xhdpi)。
     */
    public static int getTopMarginPx() {
        return Hawk.get(HawkConfig.DANMU_TOP_MARGIN_PX, 40);
    }

    public static void setTopMarginPx(int px) {
        Hawk.put(HawkConfig.DANMU_TOP_MARGIN_PX, clamp(px, 0, 200));
    }

    public static boolean useRandomColor() {
        return Hawk.get(HawkConfig.DANMU_RANDOM_COLOR, false);
    }

    public static void setRandomColor(boolean randomColor) {
        Hawk.put(HawkConfig.DANMU_RANDOM_COLOR, randomColor);
    }

    public static int randomColor() {
        int index = (int) (Math.random() * PALETTE.length);
        return Color.parseColor(PALETTE[index]);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
