package com.mobile.novabox.util;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;

import com.orhanobut.hawk.Hawk;

/**
 * 全屏播放方向策略统一入口（仅手机端生效，平板端始终保持横屏）。
 *
 * 策略值（{@link HawkConfig#FULLSCREEN_ORIENTATION}）：
 *   0 = 自动   — 横屏视频旋转到横屏，竖屏视频不旋转（默认）
 *   1 = 竖屏   — 所有视频进全屏均不旋转屏幕方向
 *   2 = 横屏   — 所有视频进全屏均旋转到横屏（原有行为）
 *   3 = 传感器 — 当前手机朝向决定是否旋转（手机竖放→不旋转，横放→旋转）
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

    /**
     * 读取当前配置的策略值。
     */
    public static int getMode() {
        return Hawk.get(HawkConfig.FULLSCREEN_ORIENTATION, MODE_AUTO);
    }

    /**
     * 获取策略名称（用于设置页显示）。
     */
    public static String getModeName(int mode) {
        switch (mode) {
            case MODE_PORT:   return "竖屏";
            case MODE_LAND:   return "横屏";
            case MODE_SENSOR: return "跟随传感器";
            default:          return "自动";
        }
    }

    /**
     * 进入全屏时调用——根据策略和视频实际比例决定是否旋转屏幕。
     * 平板端直接跳过（保持横屏分栏布局不变）。
     *
     * @param activity       当前 Activity
     * @param isLandscapeVideo 视频宽 > 高则为 true
     */
    public static void applyEnterFullscreen(Activity activity, boolean isLandscapeVideo) {
        if (PadUiHelper.isPad(activity)) return; // 平板端不处理

        int mode = getMode();
        switch (mode) {
            case MODE_PORT:
                // 竖屏策略：进全屏不旋转，保持竖屏
                break;

            case MODE_LAND:
                // 横屏策略：无论什么视频都旋转到横屏
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                break;

            case MODE_SENSOR:
                // 传感器策略：手机当前是横屏就旋转，竖屏就不旋转
                int currentOrientation = activity.getResources().getConfiguration().orientation;
                if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                }
                // 手机竖拿：不强制旋转
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
     * 退出全屏时调用——根据进入时是否旋转了方向来决定退出时是否要转回来。
     * 平板端直接跳过。
     *
     * @param activity         当前 Activity
     * @param isLandscapeVideo 视频宽 > 高则为 true
     * @return 是否需要等待 onConfigurationChanged 回调（即是否发起了旋转）
     */
    public static boolean applyExitFullscreen(Activity activity, boolean isLandscapeVideo) {
        if (PadUiHelper.isPad(activity)) return false; // 平板端不处理

        int mode = getMode();
        switch (mode) {
            case MODE_PORT:
                // 竖屏策略：进入时没转，退出时也不需要转
                return false;

            case MODE_LAND:
                // 横屏策略：进入时转了横屏，退出时转回竖屏
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                return true;

            case MODE_SENSOR:
                // 传感器策略：看进入时是否旋转（即当前是否在横屏状态）
                int currentOrientation = activity.getResources().getConfiguration().orientation;
                if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    return true;
                }
                return false;

            case MODE_AUTO:
            default:
                // 自动策略：只有横屏视频进入时转了方向，退出才需要转回
                if (isLandscapeVideo) {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    return true;
                }
                return false;
        }
    }

    /**
     * PlayActivity / PlayFragment 专用——初始化时决定应锁定的方向。
     * PlayActivity 代表"点击集数直接全屏播放"场景，视频比例未知，
     * 固定用横屏策略的超集：
     *   竖屏策略 → 保持竖屏启动（由 BaseActivity 默认竖屏即可，不额外设置）
     *   横屏/自动/传感器 → 横屏启动（维持原逻辑）
     *
     * @return 要设置的方向常量；返回 -1 表示不需要改变（由父类默认处理）
     */
    public static int getPlayActivityOrientation() {
        int mode = getMode();
        if (mode == MODE_PORT) {
            // 竖屏策略：点击集数后也保持竖屏
            return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        }
        // 其他策略：横屏（原有行为）
        return ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
    }

    /**
     * VodController / PlayFragment 中"横竖屏切换"按钮专用。
     * 当用户主动点击切换时，直接在横/竖之间 toggle，忽略全局策略。
     *
     * @param activity 当前 Activity
     */
    public static void toggleLandscapePortrait(Activity activity) {
        int cur = activity.getRequestedOrientation();
        if (cur == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                || cur == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                || cur == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        } else {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
    }

    /**
     * 直播全屏按钮专用：根据策略决定点击全屏按钮时是否旋转。
     * 直播没有"视频比例"概念，视为横屏视频处理。
     *
     * @param activity 当前 Activity
     */
    public static void applyLiveEnterFullscreen(Activity activity) {
        if (PadUiHelper.isPad(activity)) return;

        int mode = getMode();
        if (mode == MODE_PORT) {
            // 竖屏策略：直播全屏也不旋转
        } else {
            // 横屏/自动/传感器：直播全屏旋转横屏
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
    }
}
