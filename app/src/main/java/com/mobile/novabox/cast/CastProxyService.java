package com.mobile.novabox.cast;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.mobile.novabox.R;
import com.mobile.novabox.dlna.CastDevice;
import com.mobile.novabox.dlna.DLNACastService;
import com.mobile.novabox.server.ControlManager;
import com.mobile.novabox.ui.activity.MainActivity;
import com.mobile.novabox.util.LOG;

import org.fourthline.cling.android.AndroidUpnpService;

/**
 * 投屏代理保活服务(前台服务,类似音乐后台播放)。
 *
 * 背景:
 * 本机在线源很多返回的播放地址是 127.0.0.1:9978(App 内置的 RemoteServer 代理),
 * 投屏时会被 CastUrlResolver 转换成局域网地址(如 192.168.1.6:9978)推给电视播放。
 * 但 RemoteServer 只是跑在 App 主进程里的普通单例,并不是持久后台服务 ——
 * 一旦用户切到后台太久被系统回收、或者退出 App,代理跟着进程一起消失,
 * 电视端的播放就会中断。
 *
 * 解决方式:
 * 只要本次投屏用到了代理,就启动这个前台服务,常驻一条通知(类似音乐播放器
 * "正在播放"的通知),把代理的存活周期从"Activity/临时后台"提升到"前台服务",
 * 从而在用户离开 App 界面、锁屏之后依然保证 9978 端口可用。
 *
 * 自动收尾(避免代理"赖着不走"):
 * DLNA 投屏设备支持标准的 GetTransportInfo 查询,本服务每隔 {@link #POLL_INTERVAL_MS}
 * 主动问一次电视当前播放状态,如果电视已经 STOPPED/NO_MEDIA_PRESENT,或者连续多次
 * 查询失败(设备离线/关机/离开局域网),就判定投屏已结束,自动停止服务。
 * 由于 DLNA 协议本身、以及各品牌电视的实现程度参差不齐,这个判断不保证 100% 准确,
 * 因此额外加一个硬性兜底:无论轮询判断是否成功,最多保活 {@link #MAX_ALIVE_MS},
 * 到点强制停止,防止代理无限期占用后台资源和电量。
 * TVBox 类型投屏(自定义 HTTP 协议)没有标准状态查询接口,完全依赖这个硬性兜底。
 *
 * 用户也可以随时点通知里的"停止投屏代理"按钮手动结束。
 */
public class CastProxyService extends Service {

    private static final String CHANNEL_ID = "novabox_cast_proxy";
    private static final int NOTIFICATION_ID = 0x4E56; // 'NV'

    /** 轮询间隔:3 分钟查一次电视端播放状态 */
    private static final long POLL_INTERVAL_MS = 3 * 60 * 1000L;
    /** 硬性最大保活时长:3 小时,无论轮询是否判断出结果,到点强制停止 */
    private static final long MAX_ALIVE_MS = 3 * 60 * 60 * 1000L;
    /** 连续查询失败达到这个次数(约 3 次 * 3 分钟 = 9 分钟)才判定为设备离线/已退出,避免网络抖动误判 */
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    public static final String ACTION_START = "com.mobile.novabox.cast.action.START";
    public static final String ACTION_STOP = "com.mobile.novabox.cast.action.STOP";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_DEVICE_TYPE = "extra_device_type";
    public static final String EXTRA_DEVICE_ID = "extra_device_id";
    public static final String EXTRA_DEVICE_NAME = "extra_device_name";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = this::pollTransportState;
    private final Runnable maxAliveRunnable = this::onMaxAliveTimeout;

    private AndroidUpnpService upnpService;
    private boolean binding;
    private CastDevice pollDevice; // 仅 DLNA 设备可查状态;TVBox 设备为 null,只靠硬性超时兜底
    private int consecutiveFailures;

    private final ServiceConnection upnpConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            binding = false;
            upnpService = (AndroidUpnpService) service;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            upnpService = null;
            binding = false;
        }
    };

    /**
     * 启动(或更新)投屏代理保活服务。
     * @param title      当前投屏的标题,用于通知栏展示,可为空
     * @param pollDevice 用于轮询查询播放状态的设备;传 null 表示不支持状态查询(如 TVBox),
     *                   此时只依赖硬性最大保活时长兜底
     */
    public static void start(Context context, String title, @Nullable CastDevice pollDevice) {
        try {
            Intent intent = new Intent(context, CastProxyService.class);
            intent.setAction(ACTION_START);
            intent.putExtra(EXTRA_TITLE, title == null ? "" : title);
            if (pollDevice != null) {
                intent.putExtra(EXTRA_DEVICE_TYPE, pollDevice.getType());
                intent.putExtra(EXTRA_DEVICE_ID, pollDevice.getId());
                intent.putExtra(EXTRA_DEVICE_NAME, pollDevice.getName());
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Throwable th) {
            LOG.e("CastProxyService start failed: " + th.getMessage());
        }
    }

    public static void stop(Context context) {
        try {
            context.stopService(new Intent(context, CastProxyService.class));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannelIfNeeded();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // 保证代理服务器已经启动(通常投屏前已经在跑了,这里是兜底)
        try {
            ControlManager.get().startServer();
        } catch (Throwable th) {
            LOG.e("CastProxyService ensure RemoteServer failed: " + th.getMessage());
        }

        String title = intent == null ? "" : intent.getStringExtra(EXTRA_TITLE);
        startForeground(NOTIFICATION_ID, buildNotification(title, null));

        // 每次 start 都重置轮询目标和计时:处理"投屏中途切换设备/重新投屏"的情况
        mainHandler.removeCallbacks(pollRunnable);
        mainHandler.removeCallbacks(maxAliveRunnable);
        consecutiveFailures = 0;

        int deviceType = intent == null ? -1 : intent.getIntExtra(EXTRA_DEVICE_TYPE, -1);
        String deviceId = intent == null ? null : intent.getStringExtra(EXTRA_DEVICE_ID);
        String deviceName = intent == null ? null : intent.getStringExtra(EXTRA_DEVICE_NAME);
        pollDevice = (deviceType >= 0 && deviceId != null) ? new CastDevice(deviceType, deviceId, deviceName) : null;

        if (pollDevice != null && pollDevice.getType() == CastDevice.TYPE_DLNA) {
            ensureUpnpBound();
            mainHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
        } else {
            // TVBox 或未知类型设备没有标准状态查询接口,完全依赖硬性超时兜底
            LOG.i("CastProxyService: device type " + deviceType + " has no state query support, relying on max-alive timeout only");
        }
        mainHandler.postDelayed(maxAliveRunnable, MAX_ALIVE_MS);

        // 不要求系统在被杀后自动重建:代理是否需要存在取决于投屏是否还在进行,
        // 交由用户/App 显式控制更合适,避免"自动复活"造成用户困惑。
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacks(pollRunnable);
        mainHandler.removeCallbacks(maxAliveRunnable);
        releaseUpnpBound();
        super.onDestroy();
    }

    // ---------------- 轮询判断投屏是否已结束 ----------------

    private void ensureUpnpBound() {
        if (upnpService != null || binding) return;
        try {
            binding = bindService(new Intent(this, DLNACastService.class), upnpConnection, Context.BIND_AUTO_CREATE);
        } catch (Throwable th) {
            LOG.e("CastProxyService bind DLNACastService failed: " + th.getMessage());
        }
    }

    private void releaseUpnpBound() {
        try {
            if (binding || upnpService != null) unbindService(upnpConnection);
        } catch (Throwable ignored) {
        }
        upnpService = null;
        binding = false;
    }

    private void pollTransportState() {
        if (pollDevice == null || upnpService == null) {
            // 服务还没绑定上(比如刚启动),先跳过这一轮,下次再试
            schedulePollAgain();
            return;
        }
        com.mobile.novabox.dlna.DLNACastManagerBridge.queryTransportState(upnpService, pollDevice, state -> {
            if (state == null) {
                consecutiveFailures++;
                LOG.i("CastProxyService poll: query failed (" + consecutiveFailures + "/" + MAX_CONSECUTIVE_FAILURES + ")");
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    LOG.i("CastProxyService: device unreachable for " + consecutiveFailures + " polls, stopping");
                    stopSelf();
                    return;
                }
            } else {
                consecutiveFailures = 0;
                LOG.i("CastProxyService poll: state=" + state);
                if ("STOPPED".equals(state) || "NO_MEDIA_PRESENT".equals(state)) {
                    LOG.i("CastProxyService: playback stopped on remote device, stopping proxy");
                    stopSelf();
                    return;
                }
            }
            schedulePollAgain();
        });
    }

    private void schedulePollAgain() {
        mainHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
    }

    private void onMaxAliveTimeout() {
        LOG.i("CastProxyService: reached max alive time (" + MAX_ALIVE_MS + "ms), force stop");
        stopSelf();
    }

    // ---------------- 通知 ----------------

    private void createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "投屏代理服务", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("投屏播放时保持本机代理在后台运行,防止投屏中断");
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification(String title, String extraLine) {
        String content = (title == null || title.isEmpty())
                ? "正在为投屏提供代理服务,退出 App 可能导致播放中断"
                : "正在投屏:" + title;
        if (extraLine != null && !extraLine.isEmpty()) {
            content = content + "\n" + extraLine;
        }

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, openIntent, piFlags);

        Intent stopIntent = new Intent(this, CastProxyService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, piFlags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.app_icon)
                .setContentTitle("投屏代理运行中")
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(contentIntent)
                .addAction(0, "停止投屏代理", stopPendingIntent)
                .build();
    }
}
