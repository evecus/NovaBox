package com.mobile.novabox.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mobile.novabox.R;
import com.mobile.novabox.bean.VodInfo;
import com.mobile.novabox.download.DownloadManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 详情页"下载"弹窗:
 * 1. 列出当前线路所有剧集,支持多选/全选/反选
 * 2. 点"开始下载":逐集调用 playerContent 解析直链,解析成功即加入下载队列
 * 3. 解析在后台线程池做(带超时),UI 显示进度
 */
public class DownloadSelectDialog extends Dialog {

    private final VodInfo vodInfo;
    private final String sourceKey;
    private final List<VodInfo.VodSeries> episodes;
    private final boolean[] selected;
    private final ExecutorService parsePool = Executors.newFixedThreadPool(3);

    private RecyclerView rvEpisodes;
    private TextView tvConfirm;
    private EpisodeAdapter adapter;
    private boolean resolving = false;

    public DownloadSelectDialog(Activity activity, VodInfo vodInfo, String sourceKey) {
        super(activity, R.style.CustomDialogStyle);
        this.vodInfo = vodInfo;
        this.sourceKey = sourceKey;
        List<VodInfo.VodSeries> list = vodInfo.seriesMap != null && vodInfo.playFlag != null
                ? vodInfo.seriesMap.get(vodInfo.playFlag) : null;
        this.episodes = list != null ? list : new ArrayList<>();
        this.selected = new boolean[episodes.size()];
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_download_select);
        setCanceledOnTouchOutside(true);

        Window window = getWindow();
        if (window != null) {
            // 自适应宽度:屏幕窄端×0.85 上限 600dp,避免小屏撑满 / 大屏过宽出现大片空白
            android.util.DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            int minScreenSidePx = Math.min(dm.widthPixels, dm.heightPixels);
            int widthPx = (int) (minScreenSidePx * 0.85f);
            int maxWidthPx = (int) (dm.density * 600);
            if (widthPx > maxWidthPx) widthPx = maxWidthPx;
            // 高度限制屏幕 85%,LinearLayout 内容可小于窗口(自然居中)、可大于窗口(NestedScrollView 滚动)
            int heightPx = (int) (dm.heightPixels * 0.85f);
            window.setLayout(widthPx, heightPx);
            window.setGravity(Gravity.CENTER);
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        TextView title = findViewById(R.id.tvDownloadTitle);
        String flagName = vodInfo.playFlag == null ? "" : vodInfo.playFlag;
        title.setText(vodInfo.name + " · " + flagName);

        TextView tvSelectAll = findViewById(R.id.tvSelectAll);
        TextView tvDeselectAll = findViewById(R.id.tvDeselectAll);
        TextView tvClose = findViewById(R.id.tvDownloadClose);
        tvSelectAll.setOnClickListener(v -> {
            for (int i = 0; i < selected.length; i++) selected[i] = true;
            adapter.notifyDataSetChanged();
            refreshConfirmText();
        });
        tvDeselectAll.setOnClickListener(v -> {
            for (int i = 0; i < selected.length; i++) selected[i] = !selected[i];
            adapter.notifyDataSetChanged();
            refreshConfirmText();
        });
        tvClose.setOnClickListener(v -> dismiss());
        findViewById(R.id.tvDownloadCancel).setOnClickListener(v -> dismiss());

        rvEpisodes = findViewById(R.id.rvEpisodes);
        // grid 布局:按屏幕宽度区间自适应列数(手机 4 列 / 大屏手机 5 / 平板 6-7 列)
        rvEpisodes.setLayoutManager(new GridLayoutManager(activity, calcSpanCount(activity)));
        adapter = new EpisodeAdapter();
        rvEpisodes.setAdapter(adapter);

        tvConfirm = findViewById(R.id.tvDownloadConfirm);
        refreshConfirmText();
        tvConfirm.setOnClickListener(v -> startDownload());
    }

    private void refreshConfirmText() {
        int count = 0;
        for (boolean b : selected) if (b) count++;
        tvConfirm.setText("开始下载(" + count + "集)");
    }

    private void startDownload() {
        if (resolving) return;
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < selected.length; i++) if (selected[i]) indices.add(i);
        if (indices.isEmpty()) {
            Toast.makeText(getContext(), "请先选择剧集", Toast.LENGTH_SHORT).show();
            return;
        }
        // Android 10 及以下:写公共 Download 目录需要存储权限(Android 11+ 走 MediaStore 无需)
        if (android.os.Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (!com.hjq.permissions.XXPermissions.isGranted(getContext(), com.hjq.permissions.Permission.Group.STORAGE)) {
                Toast.makeText(getContext(), "请先授予存储权限", Toast.LENGTH_SHORT).show();
                com.hjq.permissions.XXPermissions.with(getContext())
                        .permission(com.hjq.permissions.Permission.Group.STORAGE)
                        .request(new com.hjq.permissions.OnPermissionCallback() {
                            @Override
                            public void onGranted(java.util.List<String> permissions, boolean all) {
                                if (all) startDownloadWithIndices(indices);
                            }

                            @Override
                            public void onDenied(java.util.List<String> permissions, boolean never) {
                                Toast.makeText(getContext(), "存储权限被拒绝,无法下载", Toast.LENGTH_SHORT).show();
                            }
                        });
                return;
            }
        }
        startDownloadWithIndices(indices);
    }

    private void startDownloadWithIndices(List<Integer> indices) {
        resolving = true;
        tvConfirm.setText("正在解析...");
        Toast.makeText(getContext(), "开始解析 " + indices.size() + " 集,解析后自动下载", Toast.LENGTH_SHORT).show();
        final int total = indices.size();
        final int[] done = {0};
        parsePool.execute(() -> {
            for (Integer idx : indices) {
                VodInfo.VodSeries series = episodes.get(idx);
                if (series == null || series.url == null) continue;
                String resolved = resolvePlayUrl(series.url);
                if (resolved != null && !resolved.isEmpty()) {
                    DownloadManager.get().addDownload(
                            sourceKey, vodInfo.name, series.name, vodInfo.playFlag,
                            series.url, resolved, refererOf(series.url));
                }
                done[0]++;
                final int cur = done[0];
                rvEpisodes.post(() -> tvConfirm.setText("解析中 " + cur + "/" + total));
            }
            rvEpisodes.post(() -> {
                resolving = false;
                tvConfirm.setText("开始下载(" + countSelected() + "集)");
                Toast.makeText(getContext(), "下载已加入队列,可到\"我的-下载管理\"查看", Toast.LENGTH_LONG).show();
                dismiss();
            });
        });
    }

    private int countSelected() {
        int c = 0;
        for (boolean b : selected) if (b) c++;
        return c;
    }

    /**
     * 按屏幕宽度(dp)选 grid 列数,平衡手机 4 列 / 平板 6-7 列,
     * 避免一行只显示一个剧集(老逻辑的痛点)。
     */
    private static int calcSpanCount(Activity activity) {
        android.util.DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int widthDp = (int) (dm.widthPixels / dm.density);
        if (widthDp >= 1024) return 7; // 大屏平板
        if (widthDp >= 720) return 6;  // 平板 / 横屏大手机
        if (widthDp >= 480) return 5;  // 大屏手机 / 小平板
        return 4;                       // 普通手机
    }

    private String refererOf(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String host = uri.getHost();
            if (host != null && !host.isEmpty()) return uri.getScheme() + "://" + host + "/";
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 复用播放链路:sp.playerContent(flag, url, vipFlags) 解析直链(复用 CastUrlResolver) */
    private String resolvePlayUrl(String url) {
        com.mobile.novabox.cast.CastUrlResolver.ResolveResult r =
                com.mobile.novabox.cast.CastUrlResolver.resolvePlayUrl(sourceKey, vodInfo.playFlag, url);
        return r == null ? null : r.url;
    }

    @Override
    public void dismiss() {
        super.dismiss();
        parsePool.shutdownNow();
    }

    // ─── 剧集列表 adapter(多选 grid,每项自带 state_checked 切换背景/文字色) ───

    class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.VH> {

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View item = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_download_episode, parent, false);
            return new VH(item);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            VodInfo.VodSeries series = episodes.get(position);
            String name = series != null && series.name != null ? series.name : ("第" + (position + 1) + "集");
            holder.tvName.setText(name);
            boolean sel = selected[position];
            holder.itemView.setSelected(sel);
            // 右上角 ✓ 角标只在选中态显示(visibility 不能用 selector 自动切,需手动)
            holder.tvCheck.setVisibility(sel ? View.VISIBLE : View.GONE);
            holder.itemView.setOnClickListener(v -> {
                selected[position] = !selected[position];
                boolean newSel = selected[position];
                holder.itemView.setSelected(newSel);
                holder.tvCheck.setVisibility(newSel ? View.VISIBLE : View.GONE);
                refreshConfirmText();
            });
        }

        @Override
        public int getItemCount() {
            return episodes.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView tvName;
            final TextView tvCheck;

            VH(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvEpisodeName);
                tvCheck = itemView.findViewById(R.id.tvEpisodeCheck);
            }
        }
    }
}
