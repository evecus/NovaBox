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
     *
     * 说明：220dp/列、上限6列时卡片偏大；这里把单列估算宽度调到190dp，
     * 上限提到7列，让卡片比之前紧凑一档，同时仍保留网格间距，不会像最初
     * 160dp/8列那版一样过于密集。
     */
    public static int getVodGridSpanCount(Context context) {
        if (!isPad(context)) return 3;           // 手机保持原逻辑
        int widthDp = getScreenWidthDp(context);
        // 去掉左侧导航栏（~72dp）后的可用宽度，每列约 190dp
        int availableDp = widthDp - 72;
        int cols = availableDp / 190;
        return Math.max(5, Math.min(cols, 7));   // 限制在 5~7 列
    }

    /**
     * 首页影片网格的项间距（dp），配合 GridSpacingItemDecoration 使用。
     * 手机端保持原有零间距不变，仅 Pad 端加间距避免卡片贴在一起。
     */
    public static int getVodGridItemSpacingDp(Context context) {
        return isPad(context) ? 14 : 0;
    }

    /**
     * 搜索结果页推荐列数。
     * 手机：3列；Pad（右栏约 75%）：2~3列。
     *
     * 说明：之前每项按 220dp 估算，在 Pad 宽屏下会算出 4 列，
     * 而 item_fast_search_row 内容（封面+片名+来源+备注）在窄列中被压缩，
     * 导致卡片显得又小又密集。这里把单项估算宽度提高到 340dp，
     * 并把列数上限收紧到 3，让每张卡片有足够宽度显示完整信息、间距也更舒展。
     */
    public static int getSearchResultSpanCount(Context context) {
        if (!isPad(context)) return 3;
        int widthDp = getScreenWidthDp(context);
        // 右侧结果栏约 75% 宽度，每项至少 340dp 保证封面、标题、标签不拥挤
        int availableDp = (int) (widthDp * 0.75f);
        int cols = availableDp / 340;
        return Math.max(2, Math.min(cols, 3));
    }

    /**
     * 搜索结果网格的项间距（dp），配合 GridSpacingItemDecoration 使用。
     * 手机：8dp；Pad：16dp，避免卡片贴在一起。
     */
    public static int getSearchResultItemSpacingDp(Context context) {
        return isPad(context) ? 16 : 8;
    }

    /**
     * 收藏 / 历史 页推荐列数。
     * 手机：3列；Pad：4~6列。
     *
     * 说明：之前每列按 160dp 估算，在 Pad 宽屏下会算出 7~8 列，
     * 导致卡片（复用首页的 item_grid 布局）被压得很小、很密集。
     * 这里把单列估算宽度提高到 220dp，并把列数上限从 8 收紧到 6，
     * 与首页 getVodGridSpanCount 保持一致的视觉密度。
     */
    public static int getCollectGridSpanCount(Context context) {
        if (!isPad(context)) return 3;
        int widthDp = getScreenWidthDp(context);
        int cols = widthDp / 220;
        return Math.max(4, Math.min(cols, 6));
    }

    /**
     * 收藏 / 历史页网格的项间距（dp），配合 GridSpacingItemDecoration 使用。
     * 手机端保持原有零间距不变，仅 Pad 端加间距避免卡片贴在一起。
     */
    public static int getCollectGridItemSpacingDp(Context context) {
        return isPad(context) ? 14 : 0;
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
