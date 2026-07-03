package com.mobile.novabox.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mobile.novabox.R;
import com.mobile.novabox.ui.adapter.SelectDialogAdapter;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SelectDialog<T> extends BaseDialog {

    public SelectDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_select);
    }

    public SelectDialog(@NonNull @NotNull Context context, int resId) {
        super(context);
        setContentView(resId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public void setTip(String tip) {
        TextView title = findViewById(R.id.title);
        if (title != null) title.setText(tip);
    }

    public void setAdapter(SelectDialogAdapter.SelectDialogInterface<T> sourceBeanSelectDialogInterface,
                           DiffUtil.ItemCallback<T> sourceBeanItemCallback,
                           List<T> data, int select) {
        SelectDialogAdapter<T> adapter = new SelectDialogAdapter<>(sourceBeanSelectDialogInterface, sourceBeanItemCallback);
        adapter.setData(data, select);

        RecyclerView rvList = findViewById(R.id.list);
        rvList.setLayoutManager(new LinearLayoutManager(getContext()));
        rvList.setAdapter(adapter);

        // 限制列表最大高度（通过 LayoutParams，兼容所有 RecyclerView 版本）
        // vs_410 在手机端=360dp，平板端=475dp，自动适配
        int maxHeightPx = getContext().getResources().getDimensionPixelSize(R.dimen.vs_410);
        rvList.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int measuredH = bottom - top;
            if (measuredH > maxHeightPx) {
                ViewGroup.LayoutParams lp = rvList.getLayoutParams();
                lp.height = maxHeightPx;
                rvList.setLayoutParams(lp);
            }
        });

        // 滚动到当前选中项
        rvList.post(() -> {
            if (select >= 3) {
                rvList.smoothScrollToPosition(select);
            }
        });

        // 取消按钮：关闭弹窗，不触发任何回调
        TextView btnCancel = findViewById(R.id.btnCancel);
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dismiss());
        }

        // 确认按钮：提交 pendingSelect 触发回调，再关闭
        TextView btnConfirm = findViewById(R.id.btnConfirm);
        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                adapter.confirmSelection();
                dismiss();
            });
        }
    }
}
