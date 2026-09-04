package com.mobile.novabox.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.mobile.novabox.R;
import com.mobile.novabox.api.DanmakuApi;
import com.mobile.novabox.event.RefreshEvent;

import org.greenrobot.eventbus.EventBus;
import org.jetbrains.annotations.NotNull;

/**
 * 弹幕地址设置弹窗（从 DanmuFullSettingDialog 的"地址"分栏中独立出来，
 * 作为设置页里单独的"弹幕地址"设置项）。
 */
public class DanmuApiDialog extends BaseDialog {

    private EditText inputApi;
    private OnListener listener;

    public interface OnListener {
        void onChange();
    }

    public void setOnListener(OnListener listener) {
        this.listener = listener;
    }

    public DanmuApiDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_danmu_api);
        setCanceledOnTouchOutside(true);

        inputApi = findViewById(R.id.inputDanmuApi);
        TextView btnClose = findViewById(R.id.btnDanmuApiClose);
        if (btnClose != null) btnClose.setOnClickListener(v -> dismiss());

        initApiPanel();
    }

    @Override
    public void show() {
        super.show();
        // 与其它设置弹窗保持一致：手机窄屏尽量铺满，平板限制在舒适宽度内
        Context context = getContext();
        while (context instanceof ContextWrapper && !(context instanceof Activity)) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        if (context instanceof Activity) {
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            ((Activity) context).getWindowManager().getDefaultDisplay().getMetrics(dm);
            int maxWidthPx = (int) (dm.density * 420);
            int widthPx = Math.min(dm.widthPixels, maxWidthPx);
            getWindow().setLayout(widthPx, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void initApiPanel() {
        inputApi.setText(DanmakuApi.getDisplayApiUrl());
        String defaultApi = DanmakuApi.getDisplayApiUrl();
        inputApi.setHint(defaultApi.isEmpty() ? "请输入弹幕搜索地址" : defaultApi);
        findViewById(R.id.danmuApiDefault).setOnClickListener(v -> {
            DanmakuApi.setUseDefault(true);
            inputApi.setText("");
            notifyChanged();
        });
        findViewById(R.id.danmuApiSubmit).setOnClickListener(v -> saveApi(inputApi.getText().toString().trim()));
        inputApi.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                saveApi(inputApi.getText().toString().trim());
                return true;
            }
            return false;
        });
    }

    private void saveApi(String api) {
        DanmakuApi.setCustomApi(api);
        notifyChanged();
    }

    private void notifyChanged() {
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, false));
        if (listener != null) listener.onChange();
    }
}
