package com.mobile.novabox.ui.activity;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mobile.novabox.R;
import com.mobile.novabox.base.BaseActivity;
import com.mobile.novabox.bean.LocalAudioFile;
import com.mobile.novabox.picasso.RoundTransformation;
import com.mobile.novabox.util.MediaCoverCache;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class LocalAudioDirActivity extends BaseActivity {

    private List<LocalAudioFile> songs = new ArrayList<>();
    private RecyclerView rvSongs;
    private int sortMode = LocalAudioActivity.SORT_SONG_TITLE_ASC;

    @Override
    protected int getLayoutResID() { return R.layout.activity_local_audio_dir; }

    @Override
    protected void init() {
        Bundle bundle = getIntent().getExtras();
        String title = bundle != null ? bundle.getString("dirTitle", "音乐") : "音乐";
        sortMode = bundle != null ? bundle.getInt("sortSong", LocalAudioActivity.SORT_SONG_TITLE_ASC) : LocalAudioActivity.SORT_SONG_TITLE_ASC;
        String[] paths = bundle != null ? bundle.getStringArray("songPaths") : null;

        ((TextView) findViewById(R.id.tvDirTitle)).setText(title);
        findViewById(R.id.ivBack).setOnClickListener(v -> finish());
        findViewById(R.id.tvSort).setOnClickListener(v -> showSortDialog());

        // 重建 LocalAudioFile 列表（路径数组传递）
        if (paths != null) {
            for (String p : paths) {
                LocalAudioFile f = new LocalAudioFile();
                f.path  = p;
                f.title = stripExt(new java.io.File(p).getName());
                f.modified = new java.io.File(p).lastModified();
                songs.add(f);
            }
        }

        rvSongs = findViewById(R.id.rvSongs);
        rvSongs.setLayoutManager(new LinearLayoutManager(this));
        refreshList();
    }

    private void refreshList() {
        List<LocalAudioFile> sorted = new ArrayList<>(songs);
        sortSongs(sorted, sortMode);
        rvSongs.setAdapter(new SongAdapter(sorted));
    }

    private void sortSongs(List<LocalAudioFile> list, int sort) {
        Comparator<LocalAudioFile> cmp;
        switch (sort) {
            case LocalAudioActivity.SORT_SONG_TITLE_DESC:  cmp = (a, b) -> b.title.compareToIgnoreCase(a.title); break;
            case LocalAudioActivity.SORT_SONG_ARTIST_ASC:  cmp = (a, b) -> safe(a.artist).compareToIgnoreCase(safe(b.artist)); break;
            case LocalAudioActivity.SORT_SONG_ARTIST_DESC: cmp = (a, b) -> safe(b.artist).compareToIgnoreCase(safe(a.artist)); break;
            case LocalAudioActivity.SORT_SONG_TIME_ASC:    cmp = (a, b) -> Long.compare(a.modified, b.modified); break;
            case LocalAudioActivity.SORT_SONG_TIME_DESC:   cmp = (a, b) -> Long.compare(b.modified, a.modified); break;
            default:                                        cmp = (a, b) -> a.title.compareToIgnoreCase(b.title); break;
        }
        Collections.sort(list, cmp);
    }

    private String safe(String s) { return s != null ? s : ""; }

    private void showSortDialog() {
        Dialog dlg = new Dialog(this, R.style.CustomDialogStyle);
        View root = LayoutInflater.from(this).inflate(R.layout.dialog_local_audio_option, null);
        dlg.setContentView(root);
        dlg.setCanceledOnTouchOutside(true);

        ((TextView) root.findViewById(R.id.tvDialogTitle)).setText("歌曲排序");
        RadioGroup rg = root.findViewById(R.id.rgOptions);
        String[] opts = {"歌曲名升序", "歌曲名降序", "艺术家升序", "艺术家降序", "修改时间升序", "修改时间降序"};
        for (int i = 0; i < opts.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(opts[i]);
            rb.setTextColor(0xFF000000);
            rb.setTextSize(15f);
            rb.setPadding(8, 20, 8, 20);
            rb.setId(i);
            if (i == sortMode) rb.setChecked(true);
            rg.addView(rb);
        }
        rg.setOnCheckedChangeListener((group, checkedId) -> {
            sortMode = checkedId;
            refreshList();
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

    private String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    class SongAdapter extends RecyclerView.Adapter<SongAdapter.VH> {
        private final List<LocalAudioFile> data;
        SongAdapter(List<LocalAudioFile> data) { this.data = data; }

        class VH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvArtist;
            ImageView ivCover;
            VH(View v) {
                super(v);
                tvTitle  = v.findViewById(R.id.tvSongTitle);
                tvArtist = v.findViewById(R.id.tvSongArtist);
                ivCover  = v.findViewById(R.id.ivSongCover);
            }
        }
        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(LocalAudioDirActivity.this)
                    .inflate(R.layout.item_audio_song, p, false));
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            LocalAudioFile f = data.get(pos);
            h.tvTitle.setText(f.title);
            String artist = f.artist != null && !f.artist.isEmpty() ? f.artist : "";
            h.tvArtist.setVisibility(artist.isEmpty() ? View.GONE : View.VISIBLE);
            h.tvArtist.setText(artist);
            bindCover(h.ivCover, f);
            h.itemView.setOnClickListener(v -> playSong(data, pos));
        }
        @Override public int getItemCount() { return data.size(); }
    }

    // ─── 封面绑定 ──────────────────────────────────────────────────────────

    private void bindCover(ImageView iv, LocalAudioFile f) {
        File cover = MediaCoverCache.peekAudioCover(this, f.path, f.modified);
        if (cover != null) {
            iv.setPadding(0, 0, 0, 0);
            Picasso.get()
                    .load(cover)
                    .transform(new RoundTransformation(cover.getAbsolutePath())
                            .centerCorp(true)
                            .override(dp(40), dp(40))
                            .roundRadius(dp(6), RoundTransformation.RoundType.ALL))
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
                    .noFade()
                    .into(iv);
        } else {
            int pad = dp(8);
            iv.setPadding(pad, pad, pad, pad);
            iv.setImageResource(R.drawable.ic_music_note);
        }
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private void playSong(List<LocalAudioFile> playlist, int index) {
        LocalAudioFile song = playlist.get(index);
        Bundle b = new Bundle();
        b.putString("path", song.path);
        b.putString("name", new java.io.File(song.path).getName());
        // 播放列表路径
        String[] paths = new String[playlist.size()];
        for (int i = 0; i < playlist.size(); i++) paths[i] = playlist.get(i).path;
        b.putStringArray("playlistPaths", paths);
        b.putInt("playlistIndex", index);
        b.putBoolean("isLocal", true);
        jumpActivity(LocalAudioPlayerActivity.class, b);
    }
}
