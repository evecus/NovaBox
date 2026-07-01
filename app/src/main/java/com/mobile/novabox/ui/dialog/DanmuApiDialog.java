package com.mobile.novabox.ui.dialog;

import android.content.Context;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.mobile.novabox.R;
import com.mobile.novabox.api.DanmakuApi;
import com.mobile.novabox.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import org.jetbrains.annotations.NotNull;

public class DanmuApiDialog extends BaseDialog {
    private EditText input;
    private OnListener listener;

    public DanmuApiDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_danmu_api);
        setCanceledOnTouchOutside(false);
        input = findViewById(R.id.input);
        input.setText(Hawk.get(HawkConfig.DANMU_API, ""));
        input.setHint(getDefaultApi());

        // 取消：关闭弹窗，不做任何修改
        findViewById(R.id.inputCancel).setOnClickListener(v -> dismiss());
        // 默认：重置为默认地址
        findViewById(R.id.inputDefault).setOnClickListener(v -> saveDefault());
        // 确认：保存输入内容
        findViewById(R.id.inputSubmit).setOnClickListener(v -> save(input.getText().toString().trim()));

        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    save(input.getText().toString().trim());
                    return true;
                }
                return false;
            }
        });
    }

    private String getDefaultApi() {
        String api = DanmakuApi.getDisplayApiUrl();
        return api.isEmpty() ? "请输入弹幕搜索地址" : api;
    }

    private void save(String api) {
        DanmakuApi.setCustomApi(api);
        if (listener != null) listener.onChange(api);
        dismiss();
    }

    private void saveDefault() {
        DanmakuApi.setUseDefault(true);
        if (listener != null) listener.onChange("");
        dismiss();
    }

    public void setOnListener(OnListener listener) {
        this.listener = listener;
    }

    public interface OnListener {
        void onChange(String api);
    }
}
