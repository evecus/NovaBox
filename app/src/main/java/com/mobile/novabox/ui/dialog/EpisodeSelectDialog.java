package com.mobile.novabox.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mobile.novabox.R;

import java.util.List;

/**
 * 选集弹窗(普通弹窗,非全屏):三场景共用(在线/本地/OpenList)。
 * 传入 items + currentIndex + 标题;点击回调 index。
 */
public class EpisodeSelectDialog extends Dialog {

    public interface OnSelectListener {
        void onSelected(int index);
    }

    private final List<String> items;
    private final int currentIndex;
    private final OnSelectListener listener;

    public EpisodeSelectDialog(@NonNull Activity activity, String title, List<String> items,
                               int currentIndex, OnSelectListener listener) {
        super(activity, R.style.CustomDialogStyle);
        this.items = items;
        this.currentIndex = currentIndex;
        this.listener = listener;
        setContentView(R.layout.dlg_episode_select);
        Window window = getWindow();
        if (window != null) {
            // 普通弹窗:宽度 80% 屏幕,高度自适应;居中显示(不再占满全屏)
            int width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.8f);
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#F2FFFFFF")));
            window.setGravity(Gravity.CENTER);
        }

        TextView tvTitle = findViewById(R.id.tv_episode_title);
        if (tvTitle != null) tvTitle.setText(title == null ? "选集" : title);
        findViewById(R.id.tv_episode_close).setOnClickListener(v -> dismiss());

        RecyclerView rv = findViewById(R.id.rv_episode_list);
        if (rv != null) {
            // grid 布局:按屏幕宽度 + 最长集名长度动态决定列数
            rv.setLayoutManager(new GridLayoutManager(activity, calcSpanCount(activity, items)));
            rv.setAdapter(new EpisodeAdapter());
        }
    }

    /** 按弹窗宽度 + 最长集名字符数自适应列数：名字越长列数越少 */
    private static int calcSpanCount(Activity activity, List<String> items) {
        android.util.DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int widthDp = (int) (dm.widthPixels / dm.density);

        int maxLen = 0;
        if (items != null) {
            for (String s : items) {
                if (s != null && s.length() > maxLen) maxLen = s.length();
            }
        }

        int spanByName;
        if (maxLen <= 3) {
            spanByName = widthDp >= 720 ? 7 : widthDp >= 480 ? 6 : 5;
        } else if (maxLen <= 6) {
            spanByName = widthDp >= 720 ? 6 : widthDp >= 480 ? 5 : 4;
        } else if (maxLen <= 10) {
            spanByName = widthDp >= 720 ? 4 : widthDp >= 480 ? 3 : 2;
        } else if (maxLen <= 16) {
            spanByName = widthDp >= 720 ? 3 : 2;
        } else {
            spanByName = 1;
        }
        return Math.max(1, spanByName);
    }

    class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.VH> {

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View item = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_download_episode, parent, false);
            return new VH(item);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            holder.tvName.setText(items.get(position));
            boolean selected = position == currentIndex;
            holder.itemView.setSelected(selected);
            holder.tvName.setTextSize(selected ? 16 : 14);
            // 右上角 ✓ 角标联动选中态
            holder.tvCheck.setVisibility(selected ? View.VISIBLE : View.GONE);
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onSelected(position);
                dismiss();
            });
        }

        @Override
        public int getItemCount() {
            return items == null ? 0 : items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView tvName;
            final TextView tvCheck;

            VH(View v) {
                super(v);
                tvName = v.findViewById(R.id.tvEpisodeName);
                tvCheck = v.findViewById(R.id.tvEpisodeCheck);
            }
        }
    }
}
