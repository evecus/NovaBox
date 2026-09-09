package com.mobile.novabox.cast;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Android 13(API 33)起,应用需要动态申请 POST_NOTIFICATIONS 权限才能显示通知。
 * CastProxyService 依赖这条通知让用户感知"投屏代理正在后台运行",
 * 因此在启动该服务前调用这里检查/申请一次。
 *
 * 注意:即使用户拒绝了这个权限,前台服务本身仍然可以正常启动运行
 * (startForeground 不强制要求通知权限),只是用户看不到提示,
 * 所以这里的申请结果不阻塞 CastProxyService 的启动。
 */
public final class NotificationPermissionHelper {

    /** 用于 onRequestPermissionsResult 匹配的请求码 */
    public static final int REQUEST_CODE = 0x4E56; // 'NV'

    private NotificationPermissionHelper() {
    }

    public static boolean hasPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 从传入的 Context 里解包出真正的 Activity。
     * 调用方经常是从 Dialog.getContext() 拿到的 Context,这类 Context 常被
     * ContextThemeWrapper 等 ContextWrapper 包装了一层,直接做
     * "context instanceof Activity" 判断在这种情况下会失败 —— 这也是之前
     * 投屏后从未弹出过通知权限申请框的根本原因:请求代码其实从来没有真正执行到
     * ActivityCompat.requestPermissions 那一步,是被 instanceof 判断挡在了外面。
     */
    private static Activity unwrapActivity(Context context) {
        Context ctx = context;
        while (ctx instanceof ContextWrapper) {
            if (ctx instanceof Activity) return (Activity) ctx;
            ctx = ((ContextWrapper) ctx).getBaseContext();
        }
        return (ctx instanceof Activity) ? (Activity) ctx : null;
    }

    /**
     * 如果尚未授权且系统版本需要(Android 13+),发起运行时权限申请弹窗。
     * 申请结果不影响调用方的后续流程(CastProxyService 无论是否拿到权限都会启动),
     * 只是决定用户能不能看到那条"投屏代理运行中"的通知。
     *
     * @param context 会自动解包拿到真正的 Activity 才能弹出系统权限对话框;
     *                传入的 Context 链上找不到 Activity(理论上不应发生,兜底跳过申请)时直接返回。
     */
    public static void requestIfNeeded(Context context) {
        if (hasPermission(context)) return;
        Activity activity = unwrapActivity(context);
        if (activity == null || activity.isFinishing()) return;
        try {
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_CODE);
        } catch (Throwable ignored) {
            // 极少数定制系统/异常情况下请求失败,忽略即可,不影响投屏和代理本身
        }
    }
}
