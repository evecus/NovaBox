package com.mobile.novabox.util;

import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/**
 * Pad 端 UI 辅助工具类
 *
 * <p>功能：
 * <ul>
 *   <li>检测当前设备是否为平板（最小宽度 ≥ 600dp）</li>
 *   <li>根据屏幕宽度动态计算最佳网格列数（首页/搜索/收藏/历史）</li>
 *   <li>检测当前是否处于横屏状态</li>
 * </ul>
 *
 * <p>用法示例（在 Activity/Fragment 中）：
 * <pre>
 *   int cols = PadUiHelper.getVodGridSpanCount(this);
 *   mRecyclerView.setLayoutManager(new GridLayoutManager(this, cols));
 * </pre>
 */
public final class PadUiHelper {

    private PadUiHelper() {}

    // 最小宽度阈值（dp），与 layout-sw600dp 保持一致
    private static final int PAD_MIN_WIDTH_DP = 600;

    /**
     * 判断当前设备是否为平板（sw ≥ 600dp）。
     */
    public static boolean isPad(Context context) {
        Configuration config = context.getResources().getConfiguration();
        return config.smallestScreenWidthDp >= PAD_MIN_WIDTH_DP;
    }

    /**
     * 判断当前是否处于横屏模式。
     */
    public static boolean isLandscape(Context context) {
        int orientation = context.getResources().getConfiguration().orientation;
        return orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    /**
     * 获取当前屏幕宽度（dp）。
     */
    public static int getScreenWidthDp(Context context) {
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            wm.getDefaultDisplay().getMetrics(dm);
        } else {
            dm = context.getResources().getDisplayMetrics();
        }
        float density = dm.density;
        return (int) (dm.widthPixels / density);
    }

    // ── 各页面推荐列数 ──────────────────────────────────────────────────────────

    /**
     * 首页影片网格推荐列数。
     * 手机竖屏：3列；Pad 横屏：5~7列（依宽度自适应）。
     */
    public static int getVodGridSpanCount(Context context) {
        if (!isPad(context)) return 3;           // 手机保持原逻辑
        int widthDp = getScreenWidthDp(context);
        // 去掉左侧导航栏（~72dp）后的可用宽度，每列约 160dp
        int availableDp = widthDp - 72;
        int cols = availableDp / 160;
        return Math.max(4, Math.min(cols, 8));   // 限制在 4~8 列
    }

    /**
     * 搜索结果页推荐列数。
     * 手机：3列；Pad（右栏约 75%）：4~6列。
     */
    public static int getSearchResultSpanCount(Context context) {
        if (!isPad(context)) return 3;
        int widthDp = getScreenWidthDp(context);
        // 右侧结果栏约 75% 宽度，每项至少 220dp 保证信息显示完整
        int availableDp = (int) (widthDp * 0.75f);
        int cols = availableDp / 220;
        return Math.max(2, Math.min(cols, 4));
    }

    /**
     * 收藏 / 历史 页推荐列数。
     * 手机：3列；Pad：5~7列。
     */
    public static int getCollectGridSpanCount(Context context) {
        if (!isPad(context)) return 3;
        int widthDp = getScreenWidthDp(context);
        int cols = widthDp / 160;
        return Math.max(4, Math.min(cols, 8));
    }

    /**
     * 详情页集数网格列数。
     * 手机：4列；Pad：固定 4 列。
     */
    public static int getEpisodeSpanCount(Context context) {
        return 4;
    }

    /**
     * 直播页频道网格推荐列数（分组/频道/线路各自为 1 列，此方法返回分组总列数）。
     * 直播页在 Pad 上已改为固定三列（分组·频道·线路），此方法仅供扩展使用。
     */
    public static int getLiveChannelSpanCount(Context context) {
        return isPad(context) ? 1 : 1;
    }
}
