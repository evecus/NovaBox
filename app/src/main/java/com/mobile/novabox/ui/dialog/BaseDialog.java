package com.mobile.novabox.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.mobile.novabox.R;

import xyz.doikki.videoplayer.util.CutoutUtil;

public class BaseDialog extends Dialog {
    public BaseDialog(@NonNull Context context) {
        super(context, R.style.CustomDialogStyle);
    }

    public BaseDialog(Context context, int customDialogStyle) {
        super(context, customDialogStyle);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        CutoutUtil.adaptCutoutAboveAndroidP(this, true);//设置刘海
        super.onCreate(savedInstanceState);
    }

    @Override
    public void show() {
        if (isContextInvalid()) {
            return;
        }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        super.show();
        hideSysBar();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        // 手机竖屏适配：将弹窗宽度限制为屏幕短边的92%，高度自适应
        // Context 可能被 ContextThemeWrapper 包装，需要解包拿到真正的 Activity
        android.content.Context baseCtx = getContext();
        while (baseCtx instanceof android.content.ContextWrapper && !(baseCtx instanceof Activity)) {
            baseCtx = ((android.content.ContextWrapper) baseCtx).getBaseContext();
        }
        if (baseCtx instanceof Activity) {
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            ((Activity) baseCtx).getWindowManager().getDefaultDisplay().getMetrics(dm);
            int dialogWidth = (int) (Math.min(dm.widthPixels, dm.heightPixels) * 0.92f);
            getWindow().setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private boolean isContextInvalid() {
        Context context = getContext();
        if (!(context instanceof Activity)) {
            return false;
        }
        Activity activity = (Activity) context;
        return activity.isFinishing()
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed());
    }

    private void hideSysBar() {
        // 弹窗不强制隐藏导航栏，避免"再次滑动返回"的双击问题
        // 弹窗的系统栏显示状态跟随 Activity 即可
    }
}
