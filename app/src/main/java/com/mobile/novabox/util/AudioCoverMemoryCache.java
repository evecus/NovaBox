package com.mobile.novabox.util;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import com.mobile.novabox.bean.LocalAudioFile;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 音乐封面的"内存态、分批增量加载"策略。
 *
 * 背景：封面数据本身直接嵌在音乐文件的 ID3/FLAC/OGG 标签里，读取成本很低，没有必要
 * 落盘缓存占用本地存储空间——统一改为只在内存里持有封面 Bitmap，Activity 销毁或列表
 * 重建后这份内存数据会丢失，下次展示时按需重新读取文件即可，代价很小。
 *
 * 加载节奏：不是"先读前 200 首、后面全部懒加载"，而是分批读取——第一批读取当前列表
 * 的前 {@link #BATCH_SIZE}（默认 200）首；用户往下滚动，看到了还没读取封面的位置时，
 * 再读取下一批 BATCH_SIZE 首（即 200~400、400~600……），每次都是整批增量，不是逐条
 * 懒加载，减少频繁的小额 I/O。
 *
 * 切换分类 / 排序方式后，"前 200"的定义会跟着变化（比如按标题排序的前 200 首，和按
 * 艺术家排序的前 200 首通常是不同的歌），因此每次分类或排序变化都需要调用 {@link #reset()}
 * 并重新从当前列表顺序的开头开始计算。
 */
public class AudioCoverMemoryCache {

    public static final int BATCH_SIZE = 200;

    public interface OnProgress {
        /** 每完成一首歌的封面加载（或判定无封面）后回调一次，供上层节流刷新列表。 */
        void onProgress();
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 当前已经"确保加载过"的批次数（已加载到第几个 BATCH_SIZE）。 */
    private volatile int loadedBatches = 0;
    private volatile boolean loading = false;
    private volatile boolean disposed = false;

    public void dispose() {
        disposed = true;
        executor.shutdownNow();
    }

    /**
     * 切换分类 / 排序方式，或重新扫描后调用：清零批次计数，让"前 200"按新的列表顺序
     * 重新计算，而不是延续旧顺序下已加载的位置。
     */
    public void reset() {
        loadedBatches = 0;
    }

    /**
     * 确保 orderedList 的前 BATCH_SIZE 首封面已加载（或正在加载）。
     * 用于列表首次展示 / 分类排序切换后的初始加载。
     */
    public void ensureFirstBatch(List<LocalAudioFile> orderedList, OnProgress onProgress) {
        ensureBatchesUpTo(1, orderedList, onProgress);
    }

    /**
     * 根据滚动位置增量加载：当用户看到了索引 visibleIndex 但该位置还没有封面数据时
     * 调用。内部按 BATCH_SIZE 取整，换算出需要覆盖到第几批，只在还没加载到那一批时
     * 才触发新的加载（200 → 400 → 600…）。
     */
    public void ensureVisible(int visibleIndex, List<LocalAudioFile> orderedList, OnProgress onProgress) {
        int neededBatch = (visibleIndex / BATCH_SIZE) + 1;
        ensureBatchesUpTo(neededBatch, orderedList, onProgress);
    }

    /**
     * 确保已加载到第 targetBatch 批（每批 BATCH_SIZE 首）。
     * 例如 targetBatch=1 → 加载前 200 首；targetBatch=2 → 加载 200~400 范围内还没
     * 加载的部分（累计前 400 首），以此类推。
     */
    public void ensureBatchesUpTo(int targetBatch, List<LocalAudioFile> orderedList, OnProgress onProgress) {
        if (disposed || orderedList == null || orderedList.isEmpty()) return;
        if (targetBatch <= loadedBatches) return;
        if (loading) return;
        loading = true;

        // 拷贝一份快照供后台线程使用，避免遍历过程中原列表被主线程改动。
        final List<LocalAudioFile> snapshot = new java.util.ArrayList<>(orderedList);
        final int startBatch = loadedBatches;

        executor.execute(() -> {
            try {
                int currentBatch = startBatch;
                while (!disposed && currentBatch < targetBatch) {
                    int start = currentBatch * BATCH_SIZE;
                    int end = Math.min((currentBatch + 1) * BATCH_SIZE, snapshot.size());
                    if (start >= snapshot.size()) {
                        // 列表本身比目标批次短，直接视为已加载完。
                        currentBatch = targetBatch;
                        break;
                    }
                    for (int i = start; i < end; i++) {
                        if (disposed) break;
                        LocalAudioFile item = snapshot.get(i);
                        if (item.coverLoaded) continue; // 已经加载过（含"确认无封面"的情况）

                        try {
                            AudioMetadataLoader.Metadata meta = AudioMetadataLoader.loadLocal(item.path);
                            Bitmap cover = meta != null ? meta.cover : null;
                            item.coverBitmap = cover;
                            item.coverLoaded = true;
                        } catch (Exception ignored) {
                            // 单首读取失败不影响其余歌曲继续加载，标记为"已尝试过"避免反复重试。
                            item.coverLoaded = true;
                        }
                        if (onProgress != null) {
                            mainHandler.post(onProgress::onProgress);
                        }
                    }
                    currentBatch++;
                }
                final int finalBatch = currentBatch;
                loadedBatches = Math.max(loadedBatches, finalBatch);
            } finally {
                loading = false;
            }
        });
    }
}
