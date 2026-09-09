package com.mobile.novabox.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.mobile.novabox.R;

/**
 * 倍速选择弹窗:垂直列表 0.5x ~ 2.0x,当前速度高亮。
 * 会话级,不持久化。
 */
public class SpeedSelectDialog extends Dialog {

    public interface OnSelectListener {
        void onSelected(float speed);
    }

    private static final float[] SPEEDS = {2.0f, 1.75f, 1.5f, 1.25f, 1.0f, 0.75f, 0.5f};
    private static final int[] ROW_IDS = {
            R.id.ll_speed_2_0, R.id.ll_speed_1_75, R.id.ll_speed_1_5, R.id.ll_speed_1_25,
            R.id.ll_speed_1_0, R.id.ll_speed_0_75, R.id.ll_speed_0_5
    };

    public SpeedSelectDialog(@NonNull Activity activity, float currentSpeed, OnSelectListener listener) {
        super(activity, R.style.CustomDialogStyle);
        setContentView(R.layout.dlg_speed_select);
        Window window = getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }

        for (int i = 0; i < SPEEDS.length; i++) {
            final float speed = SPEEDS[i];
            android.widget.LinearLayout row = findViewById(ROW_IDS[i]);
            if (row == null) continue;
            row.setBackgroundColor(Math.abs(speed - currentSpeed) < 0.01f ? 0x66FFFFFF : Color.TRANSPARENT);
            row.setOnClickListener(v -> {
                if (listener != null && Math.abs(speed - currentSpeed) >= 0.01f) listener.onSelected(speed);
                dismiss();
            });
        }
    }
}
