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

    /** 弹幕行数：直接指定弹幕轨道可同时显示的行数，不再由屏占比换算得来。默认 3 行，可调 1~30 行。 */
    public static int getMaxLine() {
        return Hawk.get(HawkConfig.DANMU_MAX_LINE, 3);
    }

    public static void setMaxLine(int line) {
        Hawk.put(HawkConfig.DANMU_MAX_LINE, clamp(line, 1, 30));
    }

    public static float getSpeed() {
        return Hawk.get(HawkConfig.DANMU_SPEED, 1.5f);
    }

    public static void setSpeed(float speed) {
        Hawk.put(HawkConfig.DANMU_SPEED, speed);
    }

    /** 弹幕透明度，默认 0.9，可调 0.1~1.0，步长 0.1（与 NovaTV 一致） */
    public static float getAlpha() {
        return Hawk.get(HawkConfig.DANMU_ALPHA, 0.9f);
    }

    public static void setAlpha(float alpha) {
        Hawk.put(HawkConfig.DANMU_ALPHA, clampFloat(alpha, 0.1f, 1.0f));
    }

    /** 弹幕字号缩放，默认 0.8x（保留 NovaBox 原有默认值），可调范围 0.4x~2.4x，步长 0.1x（与 NovaTV 一致） */
    public static float getSizeScale() {
        return Hawk.get(HawkConfig.DANMU_SIZE_SCALE, 0.8f);
    }

    public static void setSizeScale(float scale) {
        Hawk.put(HawkConfig.DANMU_SIZE_SCALE, clampFloat(scale, 0.4f, 2.4f));
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
     * 默认 40，可调 0~300px，步长 20px（与 NovaTV 一致）。
     */
    public static int getTopMarginPx() {
        return Hawk.get(HawkConfig.DANMU_TOP_MARGIN_PX, 40);
    }

    public static void setTopMarginPx(int px) {
        Hawk.put(HawkConfig.DANMU_TOP_MARGIN_PX, clamp(px, 0, 300));
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

    /** 浮点数值 clamp，并四舍五入到 1 位小数，避免连续 +/-0.1 步长后出现浮点误差(如 1.2000001)。 */
    private static float clampFloat(float value, float min, float max) {
        float clamped = Math.max(min, Math.min(value, max));
        return Math.round(clamped * 10f) / 10f;
    }
}
