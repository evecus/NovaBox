package com.mobile.novabox.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 本地视频 / 本地音频页面的“分类”“排序”选择持久化存储。
 *
 * 之前这些状态只存在于 Activity 的内存变量里，重新进入页面后会恢复成默认值。
 * 这里用 SharedPreferences 把上次选择的分类和排序方式落盘，下次进入页面直接读取。
 */
public class LocalMediaPrefs {

    private static final String PREFS_NAME = "local_media_prefs";

    // ── 本地视频 ──────────────────────────────────────────────────────────
    private static final String KEY_VIDEO_CATEGORY    = "video_category";
    private static final String KEY_VIDEO_SORT_VIDEO   = "video_sort_video";
    private static final String KEY_VIDEO_SORT_FOLDER  = "video_sort_folder";

    // ── 本地音频 ──────────────────────────────────────────────────────────
    private static final String KEY_AUDIO_CATEGORY     = "audio_category";
    private static final String KEY_AUDIO_SORT_SONG    = "audio_sort_song";
    private static final String KEY_AUDIO_SORT_GROUP   = "audio_sort_group";

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ── 视频页面 ──────────────────────────────────────────────────────────

    public static void saveVideoCategory(Context context, int category) {
        prefs(context).edit().putInt(KEY_VIDEO_CATEGORY, category).apply();
    }

    public static int loadVideoCategory(Context context, int defaultValue) {
        return prefs(context).getInt(KEY_VIDEO_CATEGORY, defaultValue);
    }

    public static void saveVideoSortVideo(Context context, int sort) {
        prefs(context).edit().putInt(KEY_VIDEO_SORT_VIDEO, sort).apply();
    }

    public static int loadVideoSortVideo(Context context, int defaultValue) {
        return prefs(context).getInt(KEY_VIDEO_SORT_VIDEO, defaultValue);
    }

    public static void saveVideoSortFolder(Context context, int sort) {
        prefs(context).edit().putInt(KEY_VIDEO_SORT_FOLDER, sort).apply();
    }

    public static int loadVideoSortFolder(Context context, int defaultValue) {
        return prefs(context).getInt(KEY_VIDEO_SORT_FOLDER, defaultValue);
    }

    // ── 音频页面 ──────────────────────────────────────────────────────────

    public static void saveAudioCategory(Context context, int category) {
        prefs(context).edit().putInt(KEY_AUDIO_CATEGORY, category).apply();
    }

    public static int loadAudioCategory(Context context, int defaultValue) {
        return prefs(context).getInt(KEY_AUDIO_CATEGORY, defaultValue);
    }

    public static void saveAudioSortSong(Context context, int sort) {
        prefs(context).edit().putInt(KEY_AUDIO_SORT_SONG, sort).apply();
    }

    public static int loadAudioSortSong(Context context, int defaultValue) {
        return prefs(context).getInt(KEY_AUDIO_SORT_SONG, defaultValue);
    }

    public static void saveAudioSortGroup(Context context, int sort) {
        prefs(context).edit().putInt(KEY_AUDIO_SORT_GROUP, sort).apply();
    }

    public static int loadAudioSortGroup(Context context, int defaultValue) {
        return prefs(context).getInt(KEY_AUDIO_SORT_GROUP, defaultValue);
    }
}
