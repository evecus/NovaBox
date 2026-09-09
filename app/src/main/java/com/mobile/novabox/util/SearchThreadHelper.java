package com.mobile.novabox.util;

import android.os.Build;

import com.orhanobut.hawk.Hawk;

/**
 * 搜索并发参数（用户可在 设置-其他-搜索线程数 里调整）。
 *
 * 涉及两个数值,含义不同,搜索速度主要由 MAX_THREAD_COUNT 决定:
 *
 * - batchCount(每批并发数):  SearchActivity/FastSearchActivity 每次只往线程池里
 *   "投递" batchCount 个源站的搜索任务,一个源完成/超时后才补下一个进来,
 *   是"投递节奏"的控制阀,不是真正能同时跑的线程数上限。
 *
 * - maxThreadCount(线程池上限): 真正决定能同时执行多少个源站搜索请求的硬上限。
 *   线程池用的是 SynchronousQueue(不排队,来一个任务就必须立刻有空闲线程接,
 *   接不住就新建线程,直到封顶),因此这个值才是决定"搜索有多快"的关键瓶颈。
 *   batchCount 大于它没有意义 —— 多出来的任务会被线程池拒绝,又被
 *   SearchActivity 的重试逻辑放回队列等下一轮,相当于白提交。
 *
 * 为了避免用户调出"batchCount > maxThreadCount"这种无效组合,写入时会自动
 * 把 batchCount 收敛到不超过 maxThreadCount。
 *
 * 未做过任何调整时,使用与原来硬编码完全一致的默认值:
 * batchCount 默认 6;maxThreadCount 默认 Android 11+ 为 18,以下为 12。
 */
public final class SearchThreadHelper {

    /** 每批并发数：可调范围 */
    public static final int BATCH_MIN = 2;
    public static final int BATCH_MAX = 30;
    public static final int BATCH_STEP = 2;

    /** 线程池上限：可调范围 */
    public static final int MAX_THREAD_MIN = 4;
    public static final int MAX_THREAD_MAX = 40;
    public static final int MAX_THREAD_STEP = 2;

    private SearchThreadHelper() {
    }

    /** 系统默认的线程池上限：Android 11(API 30)+ 为 18,以下为 12,与旧版硬编码保持一致 */
    public static int defaultMaxThreadCount() {
        return Build.VERSION.SDK_INT >= 30 ? 18 : 12;
    }

    /** 每批并发数默认值：6,与旧版硬编码保持一致 */
    public static int defaultBatchCount() {
        return 6;
    }

    public static int getMaxThreadCount() {
        int value = Hawk.get(HawkConfig.SEARCH_MAX_THREAD_COUNT, defaultMaxThreadCount());
        return clamp(value, MAX_THREAD_MIN, MAX_THREAD_MAX);
    }

    public static int getBatchCount() {
        int value = Hawk.get(HawkConfig.SEARCH_THREAD_COUNT, defaultBatchCount());
        value = clamp(value, BATCH_MIN, BATCH_MAX);
        // 每批并发数不该超过线程池上限,否则超出部分提交到线程池会被直接拒绝,
        // 等下一轮重试,等于白白多绕一圈,没有任何加速效果。
        return Math.min(value, getMaxThreadCount());
    }

    public static void setMaxThreadCount(int value) {
        value = clamp(value, MAX_THREAD_MIN, MAX_THREAD_MAX);
        Hawk.put(HawkConfig.SEARCH_MAX_THREAD_COUNT, value);
        // 上限下调后，如果之前保存的批次数超过了新上限，一并收敛，避免出现无效组合。
        int batch = Hawk.get(HawkConfig.SEARCH_THREAD_COUNT, defaultBatchCount());
        if (batch > value) {
            Hawk.put(HawkConfig.SEARCH_THREAD_COUNT, value);
        }
    }

    public static void setBatchCount(int value) {
        value = clamp(value, BATCH_MIN, BATCH_MAX);
        value = Math.min(value, getMaxThreadCount());
        Hawk.put(HawkConfig.SEARCH_THREAD_COUNT, value);
    }

    /** 恢复默认值（两个都还原为系统推荐值） */
    public static void resetToDefault() {
        Hawk.put(HawkConfig.SEARCH_MAX_THREAD_COUNT, defaultMaxThreadCount());
        Hawk.put(HawkConfig.SEARCH_THREAD_COUNT, defaultBatchCount());
    }

    /** 是否为默认配置（用于设置页展示"默认"字样） */
    public static boolean isDefault() {
        return getMaxThreadCount() == defaultMaxThreadCount() && getBatchCount() == defaultBatchCount();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
