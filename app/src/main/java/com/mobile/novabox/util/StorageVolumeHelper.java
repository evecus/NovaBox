package com.mobile.novabox.util;

import android.content.Context;
import android.os.Build;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 存储卷根目录发现工具（本地视频 / 本地音频扫描共用）。
 *
 * 之前扫描本地文件系统时把根目录写死成 {@code /sdcard} / {@code /storage/emulated/0}
 * 这两个内部存储路径，导致外插 SD 卡、USB OTG 等外部存储完全扫描不到。
 *
 * 现在改为运行时动态发现"当前设备到底挂载了哪些存储卷"：
 * 1) 优先用 {@link StorageManager#getStorageVolumes()}（API 24+），这是系统推荐的规范
 *    API，直接返回所有已挂载卷（内部存储 + 每张 SD 卡 / U 盘）对应的 {@link File} 根目录。
 * 2) 叠加直接枚举 {@code /storage} 目录下的条目作为补充兜底：部分厂商 ROM 或 USB OTG
 *    转接的外部存储可能不会完整出现在 getStorageVolumes() 的结果里，但依然会在
 *    {@code /storage/<卷名>} 下挂载出一个可读目录。低于 API 24 的设备也完全依赖这条路径。
 * 3) 保留 {@code /storage/emulated/0}、{@code /sdcard} 作为最后兜底，兼容极少数
 *    上面两步都失败或返回空列表的机型。
 *
 * 最终通过 {@link File#getCanonicalPath()} 解析真实路径后再去重——{@code /sdcard} 在
 * 几乎所有 Android 设备上都是指向 {@code /storage/emulated/0} 的符号链接，两者字符串不同
 * 但物理上是同一个目录，不做规范化去重会导致该目录被完整扫描两遍、扫描结果重复。
 */
public class StorageVolumeHelper {

    private StorageVolumeHelper() {}

    /**
     * 发现当前设备上所有可扫描的存储卷根目录（内部存储 + SD 卡 + U 盘等），
     * 已按真实路径去重。
     */
    public static List<File> discoverRoots(Context context) {
        Set<String> canonicalSeen = new LinkedHashSet<>();
        // 用 LinkedHashSet 保证遍历顺序稳定（内部存储优先），同时通过
        // canonicalSeen 做真实路径去重后再决定是否保留某个候选根目录。
        java.util.List<File> result = new java.util.ArrayList<>();

        // 1) StorageManager.getStorageVolumes()：API 24+ 的规范做法。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                StorageManager sm = (StorageManager) context.getApplicationContext()
                        .getSystemService(Context.STORAGE_SERVICE);
                if (sm != null) {
                    List<StorageVolume> volumes = sm.getStorageVolumes();
                    for (StorageVolume volume : volumes) {
                        File dir = volumeDirectory(volume);
                        addIfNewRoot(dir, canonicalSeen, result);
                    }
                }
            } catch (Exception ignored) {
                // 个别厂商 ROM 上该调用可能抛异常，忽略即可，靠下面的兜底路径补齐。
            }
        }

        // 2) 直接枚举 /storage 目录下的条目作为补充，覆盖第 1 步可能漏掉的卷。
        //    跳过 self、emulated（emulated 下的 0 已经等价于内部存储主卷，
        //    走下面第 3 步的兜底路径单独处理）。
        try {
            File storageDir = new File("/storage");
            File[] entries = storageDir.listFiles();
            if (entries != null) {
                for (File entry : entries) {
                    String name = entry.getName();
                    if ("self".equals(name) || "emulated".equals(name)) continue;
                    if (entry.isDirectory()) {
                        addIfNewRoot(entry, canonicalSeen, result);
                    }
                }
            }
        } catch (Exception ignored) {
            // 无权限枚举 /storage 本身时不影响其它来源。
        }

        // 3) 兜底：经典内部存储路径，确保即使上面两步都失败也至少能扫内部存储。
        addIfNewRoot(new File("/storage/emulated/0"), canonicalSeen, result);
        addIfNewRoot(new File("/sdcard"), canonicalSeen, result);

        return result;
    }

    /**
     * 尝试从 {@link StorageVolume} 反射/兼容取出其对应的 {@link File} 根目录。
     * API 30+ 有官方的 {@code StorageVolume#getDirectory()}；更低版本上没有公开 API，
     * 退而用 {@code getPath()}（部分 ROM 上可能返回 null，交由调用方按 null 处理）。
     */
    private static File volumeDirectory(StorageVolume volume) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            File dir = volume.getDirectory();
            if (dir != null) return dir;
        }
        try {
            // getPath() 在部分系统版本上被标记为 hide API，但实测大多数机型仍可通过
            // 反射调用；失败时静默忽略，不影响其它发现路径。
            java.lang.reflect.Method m = StorageVolume.class.getMethod("getPath");
            Object path = m.invoke(volume);
            if (path instanceof String) return new File((String) path);
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 校验候选根目录存在且可读，解析真实路径后去重，去重通过则加入结果集。
     */
    private static void addIfNewRoot(File candidate, Set<String> canonicalSeen, List<File> result) {
        if (candidate == null) return;
        try {
            if (!candidate.exists() || !candidate.isDirectory()) return;
            String canonical;
            try {
                canonical = candidate.getCanonicalPath();
            } catch (Exception e) {
                canonical = candidate.getAbsolutePath();
            }
            if (canonicalSeen.add(canonical)) {
                result.add(candidate);
            }
        } catch (Exception ignored) {
            // 权限问题等异常场景下跳过该候选根目录，不影响其它根的发现。
        }
    }
}
