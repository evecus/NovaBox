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
import androidx.recyclerview.widget.LinearLayoutManager;
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
            rv.setLayoutManager(new LinearLayoutManager(activity));
            rv.setAdapter(new EpisodeAdapter());
        }
    }

    class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.VH> {

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setPadding(48, 28, 48, 28);
            tv.setTextSize(16);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new VH(tv);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            holder.tv.setText(items.get(position));
            boolean selected = position == currentIndex;
            holder.tv.setTextColor(selected ? 0xFF0CADE2 : 0xFF333333);
            holder.tv.setTextSize(selected ? 18 : 16);
            holder.tv.setBackgroundColor(selected ? 0x110CADE2 : Color.TRANSPARENT);
            holder.tv.setOnClickListener(v -> {
                if (listener != null) listener.onSelected(position);
                dismiss();
            });
        }

        @Override
        public int getItemCount() {
            return items == null ? 0 : items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tv;
            VH(TextView v) {
                super(v);
                tv = v;
            }
        }
    }
}
