package com.mobile.novabox.ui.activity;

import android.Manifest;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mobile.novabox.R;
import com.mobile.novabox.base.BaseActivity;
import com.mobile.novabox.bean.LocalAudioFile;
import com.mobile.novabox.util.PadUiHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalAudioActivity extends BaseActivity {

    // 分类常量
    public static final int CAT_SONG   = 0;
    public static final int CAT_ALBUM  = 1;
    public static final int CAT_ARTIST = 2;
    public static final int CAT_FOLDER = 3;

    // 排序常量（歌曲）
    public static final int SORT_SONG_TITLE_ASC   = 0;
    public static final int SORT_SONG_TITLE_DESC  = 1;
    public static final int SORT_SONG_ARTIST_ASC  = 2;
    public static final int SORT_SONG_ARTIST_DESC = 3;
    public static final int SORT_SONG_TIME_ASC    = 4;
    public static final int SORT_SONG_TIME_DESC   = 5;
    // 排序常量（分组）
    public static final int SORT_GROUP_NAME_ASC   = 10;
    public static final int SORT_GROUP_NAME_DESC  = 11;
    public static final int SORT_GROUP_TIME_ASC   = 12;
    public static final int SORT_GROUP_TIME_DESC  = 13;

    private static final int REQUEST_STORAGE = 102;
    private static final String[] AUDIO_EXTS = {
            ".mp3", ".flac", ".aac", ".wav", ".ogg", ".m4a", ".wma", ".opus", ".ape"
    };

    private RecyclerView rvList;
    private int currentCategory = CAT_SONG;
    private int currentSortSong   = SORT_SONG_TITLE_ASC;
    private int currentSortGroup  = SORT_GROUP_NAME_ASC;

    private List<LocalAudioFile> allFiles = new ArrayList<>();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected int getLayoutResID() { return R.layout.activity_local_audio; }

    @Override
    protected void init() {
        findViewById(R.id.ivBack).setOnClickListener(v -> finish());

        rvList = findViewById(R.id.rvList);
        rvList.setLayoutManager(new LinearLayoutManager(this));

        findViewById(R.id.tvRefresh).setOnClickListener(v -> {
            Toast.makeText(this, "正在扫描本地音乐...", Toast.LENGTH_SHORT).show();
            scanAudio();
        });
        findViewById(R.id.tvCategory).setOnClickListener(v -> showCategoryDialog());
        findViewById(R.id.tvSort).setOnClickListener(v -> showSortDialog());

        checkPermissionAndScan();
    }

    // ─── 权限 ──────────────────────────────────────────────────────────────────

    private void checkPermissionAndScan() {
        String perm = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{perm}, REQUEST_STORAGE);
        } else {
            scanAudio();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE) {
            scanAudio();
        }
    }

    // ─── 扫描 ──────────────────────────────────────────────────────────────────

    private void scanAudio() {
        if (executor.isShutdown()) executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<LocalAudioFile> files = doScan();
            mainHandler.post(() -> {
                allFiles = files;
                refreshList();
                if (files.isEmpty())
                    Toast.makeText(this, "未找到本地音乐", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private List<LocalAudioFile> doScan() {
        List<LocalAudioFile> list = new ArrayList<>();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] proj = {
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.SIZE
        };
        ContentResolver cr = getContentResolver();
        try (Cursor c = cr.query(uri, proj, null, null,
                MediaStore.Audio.Media.DATE_MODIFIED + " DESC")) {
            if (c != null) {
                int iData   = c.getColumnIndex(MediaStore.Audio.Media.DATA);
                int iTitle  = c.getColumnIndex(MediaStore.Audio.Media.TITLE);
                int iArtist = c.getColumnIndex(MediaStore.Audio.Media.ARTIST);
                int iAlbum  = c.getColumnIndex(MediaStore.Audio.Media.ALBUM);
                int iMod    = c.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED);
                int iSize   = c.getColumnIndex(MediaStore.Audio.Media.SIZE);
                while (c.moveToNext()) {
                    String path = iData >= 0 ? c.getString(iData) : null;
                    if (path == null || path.isEmpty()) continue;
                    if (!isAudioFile(path)) continue;
                    LocalAudioFile f = new LocalAudioFile();
                    f.path       = path;
                    f.title      = iTitle  >= 0 ? c.getString(iTitle)  : null;
                    f.artist     = iArtist >= 0 ? c.getString(iArtist) : null;
                    f.album      = iAlbum  >= 0 ? c.getString(iAlbum)  : null;
                    f.modified   = iMod    >= 0 ? c.getLong(iMod) * 1000L : 0;
                    f.size       = iSize   >= 0 ? c.getLong(iSize) : 0;
                    f.folderPath = new File(path).getParent();
                    // 清理 MediaStore 默认填充的 <unknown>
                    if (f.artist != null && f.artist.contains("<unknown>")) f.artist = "";
                    if (f.album  != null && f.album.contains("<unknown>"))  f.album  = "";
                    if (f.title  == null || f.title.isEmpty())
                        f.title = stripExt(new File(path).getName());
                    list.add(f);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }

        // fallback: file system scan
        if (list.isEmpty()) {
            for (String root : new String[]{"/sdcard", "/storage/emulated/0"}) {
                File dir = new File(root);
                if (!dir.exists() || !dir.canRead()) continue;
                scanFs(dir, list, 0);
            }
        }
        return list;
    }

    private void scanFs(File dir, List<LocalAudioFile> list, int depth) {
        if (depth > 8) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                String n = f.getName();
                if (n.startsWith(".") || n.equals("Android")) continue;
                scanFs(f, list, depth + 1);
            } else if (isAudioFile(f.getName())) {
                LocalAudioFile af = new LocalAudioFile();
                af.path       = f.getAbsolutePath();
                af.title      = stripExt(f.getName());
                af.modified   = f.lastModified();
                af.folderPath = f.getParent();
                list.add(af);
            }
        }
    }

    private boolean isAudioFile(String path) {
        String lower = path.toLowerCase();
        for (String e : AUDIO_EXTS) if (lower.endsWith(e)) return true;
        return false;
    }

    private String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    // ─── 列表刷新 ──────────────────────────────────────────────────────────────

    void refreshList() {
        if (currentCategory == CAT_SONG) {
            List<LocalAudioFile> sorted = new ArrayList<>(allFiles);
            sortSongs(sorted, currentSortSong);
            rvList.setAdapter(new SongAdapter(sorted));
        } else {
            Map<String, List<LocalAudioFile>> groups = buildGroups();
            List<Map.Entry<String, List<LocalAudioFile>>> entries = new ArrayList<>(groups.entrySet());
            sortGroups(entries, currentSortGroup);
            rvList.setAdapter(new GroupAdapter(entries));
        }
    }

    private Map<String, List<LocalAudioFile>> buildGroups() {
        Map<String, List<LocalAudioFile>> map = new LinkedHashMap<>();
        for (LocalAudioFile f : allFiles) {
            if (currentCategory == CAT_ARTIST) {
                // 拆分多艺术家，将歌曲同时放入每个艺术家目录
                List<String> artists = splitArtists(f.artist);
                for (String artist : artists) {
                    if (!map.containsKey(artist)) map.put(artist, new ArrayList<>());
                    map.get(artist).add(f);
                }
            } else {
                String key;
                switch (currentCategory) {
                    case CAT_ALBUM:
                        key = (f.album != null && !f.album.isEmpty()) ? f.album : "未知专辑";
                        break;
                    case CAT_FOLDER:
                        key = f.folderPath != null ? f.folderPath : "/";
                        break;
                    default: key = ""; break;
                }
                if (!map.containsKey(key)) map.put(key, new ArrayList<>());
                map.get(key).add(f);
            }
        }
        return map;
    }

    /**
     * 将艺术家字段按常见分隔符拆分，返回去重后的单个艺术家列表。
     * 支持：/ 、 , & × · feat. ft. vs. x（大小写均可）
     */
    private List<String> splitArtists(String artist) {
        List<String> result = new ArrayList<>();
        if (artist == null || artist.isEmpty()) {
            result.add("未知艺术家");
            return result;
        }
        // 先把 feat./ft./vs./×/· 等替换成统一分隔符 |，再按 | / , & 、 拆分
        String normalized = artist
                .replaceAll("(?i)\\bfeat\\.?\\s*", "|")
                .replaceAll("(?i)\\bft\\.?\\s*",   "|")
                .replaceAll("(?i)\\bvs\\.?\\s*",   "|")
                .replaceAll("[/,&×·、;；]",         "|");
        String[] parts = normalized.split("\\|");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && !result.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        if (result.isEmpty()) result.add("未知艺术家");
        return result;
    }

    private void sortSongs(List<LocalAudioFile> list, int sort) {
        Comparator<LocalAudioFile> cmp;
        switch (sort) {
            case SORT_SONG_TITLE_DESC:  cmp = (a, b) -> b.title.compareToIgnoreCase(a.title);   break;
            case SORT_SONG_ARTIST_ASC:  cmp = (a, b) -> safeStr(a.artist).compareToIgnoreCase(safeStr(b.artist)); break;
            case SORT_SONG_ARTIST_DESC: cmp = (a, b) -> safeStr(b.artist).compareToIgnoreCase(safeStr(a.artist)); break;
            case SORT_SONG_TIME_ASC:    cmp = (a, b) -> Long.compare(a.modified, b.modified);   break;
            case SORT_SONG_TIME_DESC:   cmp = (a, b) -> Long.compare(b.modified, a.modified);   break;
            default:                    cmp = (a, b) -> a.title.compareToIgnoreCase(b.title);    break;
        }
        Collections.sort(list, cmp);
    }

    private void sortGroups(List<Map.Entry<String, List<LocalAudioFile>>> entries, int sort) {
        Comparator<Map.Entry<String, List<LocalAudioFile>>> cmp;
        switch (sort) {
            case SORT_GROUP_NAME_DESC:  cmp = (a, b) -> b.getKey().compareToIgnoreCase(a.getKey()); break;
            case SORT_GROUP_TIME_ASC:   cmp = (a, b) -> Long.compare(minTime(a.getValue()), minTime(b.getValue())); break;
            case SORT_GROUP_TIME_DESC:  cmp = (a, b) -> Long.compare(maxTime(b.getValue()), maxTime(a.getValue())); break;
            default:                    cmp = (a, b) -> a.getKey().compareToIgnoreCase(b.getKey()); break;
        }
        Collections.sort(entries, cmp);
    }

    private long minTime(List<LocalAudioFile> l) { long t = Long.MAX_VALUE; for (LocalAudioFile f : l) if (f.modified < t) t = f.modified; return t; }
    private long maxTime(List<LocalAudioFile> l) { long t = 0; for (LocalAudioFile f : l) if (f.modified > t) t = f.modified; return t; }
    private String safeStr(String s) { return s != null ? s : ""; }

    // ─── 弹窗：分类 ───────────────────────────────────────────────────────────

    private void showCategoryDialog() {
        showOptionDialog("选择分类",
                new String[]{"歌曲", "专辑", "艺术家", "文件夹"},
                currentCategory,
                idx -> {
                    currentCategory = idx;
                    refreshList();
                });
    }

    // ─── 弹窗：排序（根目录） ─────────────────────────────────────────────────

    void showSortDialog() {
        if (currentCategory == CAT_SONG) {
            showOptionDialog("歌曲排序",
                    new String[]{"歌曲名升序", "歌曲名降序", "艺术家升序", "艺术家降序", "修改时间升序", "修改时间降序"},
                    currentSortSong,
                    idx -> { currentSortSong = idx; refreshList(); });
        } else {
            showOptionDialog("目录排序",
                    new String[]{"名称升序", "名称降序", "修改时间升序", "修改时间降序"},
                    currentSortGroup - 10,
                    idx -> { currentSortGroup = idx + 10; refreshList(); });
        }
    }

    private void showOptionDialog(String title, String[] options, int selected,
                                  OnPickListener listener) {
        Dialog dlg = new Dialog(this, R.style.CustomDialogStyle);
        View root = LayoutInflater.from(this).inflate(R.layout.dialog_local_audio_option, null);
        dlg.setContentView(root);
        dlg.setCanceledOnTouchOutside(true);

        ((TextView) root.findViewById(R.id.tvDialogTitle)).setText(title);
        RadioGroup rg = root.findViewById(R.id.rgOptions);
        for (int i = 0; i < options.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(options[i]);
            rb.setTextColor(0xFF000000);
            rb.setTextSize(15f);
            rb.setPadding(8, 20, 8, 20);
            rb.setId(i);
            if (i == selected) rb.setChecked(true);
            rg.addView(rb);
        }
        rg.setOnCheckedChangeListener((group, checkedId) -> {
            listener.onPick(checkedId);
            dlg.dismiss();
        });

        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int w = (int) (Math.min(dm.widthPixels, dm.heightPixels) * 0.8f);
        if (dlg.getWindow() != null) {
            dlg.getWindow().setLayout(w, WindowManager.LayoutParams.WRAP_CONTENT);
            dlg.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dlg.show();
    }

    interface OnPickListener { void onPick(int index); }

    // ─── Adapter：歌曲列表 ────────────────────────────────────────────────────

    class SongAdapter extends RecyclerView.Adapter<SongAdapter.VH> {
        private final List<LocalAudioFile> data;
        SongAdapter(List<LocalAudioFile> data) { this.data = data; }

        class VH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvArtist;
            VH(View v) {
                super(v);
                tvTitle  = v.findViewById(R.id.tvSongTitle);
                tvArtist = v.findViewById(R.id.tvSongArtist);
            }
        }
        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(LocalAudioActivity.this)
                    .inflate(R.layout.item_audio_song, p, false));
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            LocalAudioFile f = data.get(pos);
            h.tvTitle.setText(f.title);
            String artist = f.artist != null && !f.artist.isEmpty() ? f.artist : "";
            h.tvArtist.setVisibility(artist.isEmpty() ? View.GONE : View.VISIBLE);
            h.tvArtist.setText(artist);
            h.itemView.setOnClickListener(v -> {
                // 播放，列表 = 当前排序后全部歌曲
                playSong(data, pos);
            });
        }
        @Override public int getItemCount() { return data.size(); }
    }

    // ─── Adapter：分组列表 ────────────────────────────────────────────────────

    class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.VH> {
        private final List<Map.Entry<String, List<LocalAudioFile>>> data;
        GroupAdapter(List<Map.Entry<String, List<LocalAudioFile>>> data) { this.data = data; }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvCount;
            VH(View v) {
                super(v);
                tvName  = v.findViewById(R.id.tvGroupName);
                tvCount = v.findViewById(R.id.tvGroupCount);
            }
        }
        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(LocalAudioActivity.this)
                    .inflate(R.layout.item_audio_group, p, false));
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            Map.Entry<String, List<LocalAudioFile>> entry = data.get(pos);
            // 文件夹模式显示最后一段路径名；其他显示原名
            String displayName = entry.getKey();
            if (currentCategory == CAT_FOLDER) {
                File f = new File(displayName);
                displayName = f.getName().isEmpty() ? displayName : f.getName();
            }
            h.tvName.setText(displayName);
            h.tvCount.setText(entry.getValue().size() + " 首");
            h.itemView.setOnClickListener(v -> {
                Bundle bundle = new Bundle();
                bundle.putString("dirTitle", h.tvName.getText().toString());
                bundle.putInt("sortSong", currentSortSong);
                // 把歌曲列表序列化成路径数组传递
                List<LocalAudioFile> songs = entry.getValue();
                String[] paths = new String[songs.size()];
                for (int i = 0; i < songs.size(); i++) paths[i] = songs.get(i).path;
                bundle.putStringArray("songPaths", paths);
                jumpActivity(LocalAudioDirActivity.class, bundle);
            });
        }
        @Override public int getItemCount() { return data.size(); }
    }

    // ─── 播放 ──────────────────────────────────────────────────────────────────

    void playSong(List<LocalAudioFile> playlist, int index) {
        LocalAudioFile song = playlist.get(index);
        Bundle b = new Bundle();
        b.putString("path", song.path);
        b.putString("name", new File(song.path).getName());
        String[] paths = new String[playlist.size()];
        for (int i = 0; i < playlist.size(); i++) paths[i] = playlist.get(i).path;
        b.putStringArray("playlistPaths", paths);
        b.putInt("playlistIndex", index);
        b.putBoolean("isLocal", true);
        jumpActivity(LocalAudioPlayerActivity.class, b);
    }

    public List<LocalAudioFile> getAllFiles() { return allFiles; }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
