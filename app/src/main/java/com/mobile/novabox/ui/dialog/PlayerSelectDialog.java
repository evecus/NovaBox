package com.mobile.novabox.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.mobile.novabox.R;

/**
 * 播放器切换弹窗:4 档(EXO 硬/软、IJK 硬/软)。
 * 会话级,不持久化(换视频恢复默认)。
 */
public class PlayerSelectDialog extends Dialog {

    public interface OnSelectListener {
        void onSelected(int playerType);
    }

    public PlayerSelectDialog(@NonNull Activity activity, int currentType, OnSelectListener listener) {
        super(activity, R.style.CustomDialogStyle);
        setContentView(R.layout.dlg_player_select);
        Window window = getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }

        int[][] rows = {
                {R.id.ll_player_exo_hard, R.id.iv_player_exo_hard, 0},
                {R.id.ll_player_exo_soft, R.id.iv_player_exo_soft, 1},
                {R.id.ll_player_ijk_hard, R.id.iv_player_ijk_hard, 2},
                {R.id.ll_player_ijk_soft, R.id.iv_player_ijk_soft, 3}
        };
        for (int[] row : rows) {
            android.view.View ll = findViewById(row[0]);
            android.widget.ImageView check = findViewById(row[1]);
            final int type = row[2];
            if (ll == null || check == null) continue;
            check.setVisibility(type == currentType ? android.view.View.VISIBLE : android.view.View.GONE);
            ll.setOnClickListener(v -> {
                if (listener != null) listener.onSelected(type);
                dismiss();
            });
        }
    }
}
