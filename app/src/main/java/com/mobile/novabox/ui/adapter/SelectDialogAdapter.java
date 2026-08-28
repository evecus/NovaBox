package com.mobile.novabox.ui.adapter;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.mobile.novabox.R;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SelectDialogAdapter<T> extends RecyclerView.Adapter<SelectDialogAdapter.SelectViewHolder> {

    class SelectViewHolder extends RecyclerView.ViewHolder {
        public SelectViewHolder(@NonNull @NotNull View itemView) {
            super(itemView);
        }
    }

    public interface SelectDialogInterface<T> {
        void click(T value, int pos);
        String getDisplay(T val);
    }

    public static DiffUtil.ItemCallback<String> stringDiff = new DiffUtil.ItemCallback<String>() {
        @Override
        public boolean areItemsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
            return oldItem.equals(newItem);
        }

        @Override
        public boolean areContentsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
            return oldItem.equals(newItem);
        }
    };

    private ArrayList<T> data = new ArrayList<>();

    /** 当前已保存生效的选中位置（初始值，来自外部） */
    private int select = 0;

    /** 用户在弹窗内临时点选的位置，尚未确认 */
    private int pendingSelect = -1;

    private SelectDialogInterface dialogInterface;

    /**
     * 是否"点选即生效":true 时点击 item 立刻触发回调并把已选项同步到 select(高亮);
     * 注意:即使生效也不自动关闭弹窗,关闭由外部 ✕ 按钮或调用方决定。
     * null/false 时维持"先选-再确认"两步模式(用于不想即时生效的场景)。
     */
    private boolean instantApply;

    public SelectDialogAdapter(SelectDialogInterface dialogInterface, DiffUtil.ItemCallback diffCallback) {
        this.dialogInterface = dialogInterface;
    }

    /** 切换即时生效模式(参见 instantApply 字段注释) */
    public void setInstantApply(boolean instant) {
        this.instantApply = instant;
    }

    public void setData(List<T> newData, int defaultSelect) {
        data.clear();
        data.addAll(newData);
        select = defaultSelect;
        pendingSelect = defaultSelect; // 初始临时选中与当前一致
        notifyDataSetChanged();
    }

    /** 返回当前临时选中的位置，供 SelectDialog 的"确认"按钮使用 */
    public int getPendingSelect() {
        return pendingSelect;
    }

    /** 返回临时选中的数据项 */
    public T getPendingItem() {
        if (pendingSelect >= 0 && pendingSelect < data.size()) {
            return data.get(pendingSelect);
        }
        return null;
    }

    /** 确认：将 pendingSelect 提交为正式 select，并触发回调 */
    public void confirmSelection() {
        if (pendingSelect >= 0 && pendingSelect < data.size()) {
            int oldSelect = select;
            select = pendingSelect;
            dialogInterface.click(data.get(select), select);
            if (oldSelect != select) {
                notifyItemChanged(oldSelect);
                notifyItemChanged(select);
            }
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @Override
    public SelectDialogAdapter.SelectViewHolder onCreateViewHolder(@NonNull @NotNull ViewGroup parent, int viewType) {
        return new SelectDialogAdapter.SelectViewHolder(
                LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dialog_select, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull @NotNull SelectDialogAdapter.SelectViewHolder holder,
                                 @SuppressLint("RecyclerView") int position) {
        T value = data.get(position);
        String name = dialogInterface.getDisplay(value);
        TextView view = holder.itemView.findViewById(R.id.tvName);

        // 高亮临时选中项
        if (position == pendingSelect) {
            view.setTextColor(0xff02f8e1);
            view.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        } else {
            view.setTextColor(0xff333333);
            view.setTypeface(Typeface.defaultFromStyle(Typeface.NORMAL));
        }
        view.setText(name);

        holder.itemView.setOnClickListener(v -> {
            if (position == pendingSelect) return;
            int old = pendingSelect;
            pendingSelect = position;
            notifyItemChanged(old);
            notifyItemChanged(pendingSelect);
            // 注意:instantApply 模式只同步生效 + 触发回调,不自动关闭弹窗
            // —— 关闭由右上角 ✕ 按钮或外部 dismiss 调用负责,用户可继续浏览/调整
            if (instantApply) {
                int oldSel = select;
                select = position;
                dialogInterface.click(value, position);
                if (oldSel != position) {
                    notifyItemChanged(oldSel);
                    notifyItemChanged(position);
                }
            }
        });
    }
}
