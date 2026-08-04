package com.mobile.novabox.util;

import android.app.Activity;
import android.content.pm.ActivityInfo;

import com.orhanobut.hawk.Hawk;

/**
 * 全屏播放方向策略统一入口（仅手机端生效，平板端始终保持横屏）。
 *
 * 策略值（{@link HawkConfig#FULLSCREEN_ORIENTATION}）：
 *   0 = 自动     — 横屏视频旋转横屏，竖屏视频不旋转（默认）
 *   1 = 竖屏     — 所有视频进全屏均不旋转屏幕方向
 *   2 = 横屏     — 所有视频进全屏均旋转到横屏（原有行为）
 *   3 = 传感器   — 交给系统传感器自动决定横/竖（SCREEN_ORIENTATION_SENSOR）
 *
 * ── 传感器策略说明 ──────────────────────────────────────────────────────────
 * 设置 SCREEN_ORIENTATION_SENSOR 后，系统会根据手机实际朝向实时旋转屏幕：
 *   手机竖拿 → 竖屏显示；手机横拿 → 横屏显示。
 * 退出全屏时恢复 PORTRAIT，让 Activity 回到竖屏初始状态。
 * ───────────────────────────────────────────────────────────────────────────
 */
public final class OrientationHelper {

    /** 策略：自动 */
    public static final int MODE_AUTO   = 0;
    /** 策略：始终竖屏 */
    public static final int MODE_PORT   = 1;
    /** 策略：始终横屏 */
    public static final int MODE_LAND   = 2;
    /** 策略：跟随传感器 */
    public static final int MODE_SENSOR = 3;

    private OrientationHelper() {}

    /** 读取当前配置的策略值。 */
    public static int getMode() {
        return Hawk.get(HawkConfig.FULLSCREEN_ORIENTATION, MODE_AUTO);
    }

    /** 获取策略名称（用于设置页显示）。 */
    public static String getModeName(int mode) {
        switch (mode) {
            case MODE_PORT:   return "竖屏";
            case MODE_LAND:   return "横屏";
            case MODE_SENSOR: return "跟随传感器";
            default:          return "自动";
        }
    }

    /**
     * 进入全屏时调用——根据策略和视频实际比例设置屏幕方向。
     * 平板端直接跳过（保持横屏分栏布局不变）。
     *
     * @param activity         当前 Activity
     * @param isLandscapeVideo 视频宽 > 高则为 true；直播传 true
     */
    public static void applyEnterFullscreen(Activity activity, boolean isLandscapeVideo) {
        if (PadUiHelper.isPad(activity)) return;

        switch (getMode()) {
            case MODE_PORT:
                // 竖屏策略：进全屏不旋转，保持竖屏
                break;

            case MODE_LAND:
                // 横屏策略：无论什么视频都旋转到横屏
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                break;

            case MODE_SENSOR:
                // 传感器策略：交给系统传感器，手机怎么拿就怎么转
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                break;

            case MODE_AUTO:
            default:
                // 自动策略：横屏视频旋转，竖屏视频不旋转
                if (isLandscapeVideo) {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                }
                break;
        }
    }

    /**
     * 退出全屏时调用——恢复屏幕方向到竖屏（如果进入时做过旋转的话）。
     * 平板端直接跳过。
     *
     * @param activity         当前 Activity
     * @param isLandscapeVideo 视频宽 > 高则为 true；直播传 true
     * @return true = 发起了方向旋转，调用方需等 onConfigurationChanged 后再还原布局；
     *         false = 没有发起旋转，调用方可直接还原布局
     */
    public static boolean applyExitFullscreen(Activity activity, boolean isLandscapeVideo) {
        if (PadUiHelper.isPad(activity)) return false;

        switch (getMode()) {
            case MODE_PORT:
                // 竖屏策略：进入时没旋转，退出时也不需要旋转
                return false;

            case MODE_LAND:
                // 横屏策略：进入时旋转了，退出时转回竖屏
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                return true;

            case MODE_SENSOR:
                // 传感器策略：退出时恢复竖屏，让 Activity 回到初始状态
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                return true;

            case MODE_AUTO:
            default:
                // 自动策略：只有横屏视频进入时旋转了，退出才需要转回
                if (isLandscapeVideo) {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    return true;
                }
                return false;
        }
    }

    /**
     * PlayActivity 专用——Activity 启动时决定初始方向。
     * 竖屏策略 → 竖屏启动；其余策略 → 横屏启动（原有行为）。
     */
    public static int getPlayActivityOrientation() {
        if (getMode() == MODE_PORT) {
            return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        }
        return ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
    }

    /**
     * 直播全屏按钮专用：根据策略决定点击全屏按钮时如何旋转。
     * 直播无视频比例概念，视为横屏视频。
     * 竖屏策略下不旋转（直播全屏仍在竖屏内展开 UI）。
     */
    public static void applyLiveEnterFullscreen(Activity activity) {
        if (PadUiHelper.isPad(activity)) return;
        // 直接复用通用逻辑，直播视为"横屏视频"
        applyEnterFullscreen(activity, true);
    }

    /**
     * 直播退出全屏：根据策略恢复方向。
     */
    public static boolean applyLiveExitFullscreen(Activity activity) {
        if (PadUiHelper.isPad(activity)) return false;
        return applyExitFullscreen(activity, true);
    }

    /**
     * 在线视频（DetailActivity）进入全屏时调用。
     * DetailActivity 不走 isLandscapeVideo 判断（视频比例未知），
     * 竖屏策略不旋转，其余策略旋转横屏。
     */
    public static void applyDetailEnterFullscreen(Activity activity) {
        if (PadUiHelper.isPad(activity)) return;

        switch (getMode()) {
            case MODE_PORT:
                // 竖屏策略：不旋转
                break;
            case MODE_SENSOR:
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                break;
            case MODE_LAND:
            case MODE_AUTO:
            default:
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;
        }
    }

    /**
     * 在线视频（DetailActivity）退出全屏时调用。
     * 返回 true 表示发起了旋转，需等 onConfigurationChanged 后还原布局。
     */
    public static boolean applyDetailExitFullscreen(Activity activity) {
        if (PadUiHelper.isPad(activity)) return false;

        switch (getMode()) {
            case MODE_PORT:
                return false;
            case MODE_LAND:
            case MODE_AUTO:
            case MODE_SENSOR:
            default:
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                return true;
        }
    }
}
