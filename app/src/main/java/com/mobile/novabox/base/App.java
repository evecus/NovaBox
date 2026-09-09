package com.mobile.novabox.base;

import android.app.Activity;
import androidx.multidex.MultiDexApplication;

import com.mobile.novabox.bean.VodInfo;
import com.mobile.novabox.callback.EmptyCallback;
import com.mobile.novabox.callback.LoadingCallback;
import com.mobile.novabox.data.AppDataManager;
import com.mobile.novabox.server.ControlManager;
import com.mobile.novabox.util.AppManager;
import com.mobile.novabox.util.EpgUtil;
import com.mobile.novabox.util.FileUtils;
import com.mobile.novabox.util.HawkConfig;
import com.mobile.novabox.util.LOG;
import com.mobile.novabox.util.OkGoHelper;
import com.mobile.novabox.util.PlayerHelper;
import com.kingja.loadsir.core.LoadSir;
import com.orhanobut.hawk.Hawk;
import com.p2p.P2PClass;
import com.whl.quickjs.android.QuickJSLoader;
import com.github.catvod.crawler.JsLoader;

import me.jessyan.autosize.AutoSizeConfig;
import me.jessyan.autosize.unit.Subunits;

/**
 * @author pj567
 * @date :2020/12/17
 * @description:
 */
public class App extends MultiDexApplication {
    private static App instance;

    private static P2PClass p;
    public static String burl;
    private static String dashData;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        initParams();
        // OKGo
        OkGoHelper.init(); //台标获取
        EpgUtil.init();
        // 初始化Web服务器
        ControlManager.init(this);
        //初始化数据库
        AppDataManager.init();
        LoadSir.beginBuilder()
                .addCallback(new EmptyCallback())
                .addCallback(new LoadingCallback())
                .commit();
        AutoSizeConfig.getInstance().setCustomFragment(true).getUnitsManager()
                .setSupportDP(false)
                .setSupportSP(false)
                .setSupportSubunits(Subunits.MM);
        PlayerHelper.init();
        QuickJSLoader.init();
        FileUtils.cleanPlayerCache();
    }

    private void initParams() {
        // Hawk
        Hawk.init(this).build();
        Hawk.put(HawkConfig.DEBUG_OPEN, false);
        if (!Hawk.contains(HawkConfig.PLAY_TYPE)) {
            // 首次安装默认 4 档中的 EXO硬解(0):0=EXO硬解 1=EXO软解 2=IJK硬解 3=IJK软解
            Hawk.put(HawkConfig.PLAY_TYPE, 0);
        }
        if (!Hawk.contains(HawkConfig.LIVE_PLAY_TYPE)) {
            // 直播播放器默认单独使用 IJK硬解(2),与视频播放器(点播)默认值互不影响
            Hawk.put(HawkConfig.LIVE_PLAY_TYPE, 2);
        }
    }

    public static App getInstance() {
        return instance;
    }

    /**
     * 完全重启应用(杀掉进程):配置切换、缓存清理等需要重新加载全局状态时使用。
     * 与只 startActivity(FLAG_ACTIVITY_CLEAR_TASK) 不同,这里会调 System.exit(0),
     * 强制清空 ApplicationContext 单例(ApiConfig/Hawk/PlayerHelper 等),保证新配置生效。
     * delayMs 默认 2500,期间会显示 toast,让用户感知到"正在重启"。
     */
    public static void restartApp(int delayMs) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (instance == null) return;
                android.content.Intent intent = instance.getPackageManager().getLaunchIntentForPackage(instance.getPackageName());
                if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    instance.startActivity(intent);
                    System.exit(0);
                }
            }
        }, delayMs);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        JsLoader.destroy();
    }


    private VodInfo vodInfo;
    public void setVodInfo(VodInfo vodinfo){
        this.vodInfo = vodinfo;
    }
    public VodInfo getVodInfo(){
        return this.vodInfo;
    }

    public static P2PClass getp2p() {
        try {
            if (p == null) {
                p = new P2PClass(FileUtils.getExternalCachePath());
            }
            return p;
        } catch (Exception e) {
            LOG.e(e.toString());
            return null;
        }
    }

    public Activity getCurrentActivity() {
        return AppManager.getInstance().currentActivity();
    }

    public void setDashData(String data) {
        dashData = data;
    }
    public String getDashData() {
        return dashData;
    }
}