package com.mobile.novabox.ui.activity;

import android.app.ActivityManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.mobile.novabox.R;
import com.mobile.novabox.base.BaseActivity;
import com.mobile.novabox.base.BaseLazyFragment;
import com.mobile.novabox.cast.CastProxyService;
import com.mobile.novabox.server.ControlManager;
import com.mobile.novabox.ui.fragment.HomeFragment;
import com.mobile.novabox.ui.fragment.LiveFragment;
import com.mobile.novabox.ui.fragment.MyFragment;
import com.mobile.novabox.ui.widget.MainNavBar;
import com.mobile.novabox.util.AppManager;

/**
 * 主容器(原 HomeActivity 的壳):底部/左侧导航栏 + 三个内容 Tab Fragment。
 *
 * - 导航栏常驻容器层,切换 Tab 时只对内容 Fragment 做淡入+轻微上移动画,
 *   导航栏纹丝不动,呈现真正的"底部 Tab 切换"效果。
 * - HomeFragment / LiveFragment / MyFragment 分别由原 HomeActivity /
 *   LivePlayActivity / MyActivity 转换而来,页面状态在 Tab 间保留
 *   (直播切走时自动暂停,切回继续)。
 *
 * 外部跳转约定(SettingActivity 等):
 *   extra "tab"(int,见 TAB_*)     → 启动后落到指定 Tab
 *   extra "useCache"(boolean)     → 传给 HomeFragment(改配置后带缓存重载)
 */
public class MainActivity extends BaseActivity {
    public static final int TAB_HOME = MainNavBar.TAB_HOME;
    public static final int TAB_LIVE = MainNavBar.TAB_LIVE;
    public static final int TAB_MY = MainNavBar.TAB_MY;

    private MainNavBar navBar;
    private FrameLayout fragmentContainer;

    private HomeFragment homeFragment;
    private LiveFragment liveFragment;
    private MyFragment myFragment;
    private int currentTab = -1;

    private long mExitTime = 0;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_main;
    }

    @Override
    protected void init() {
        navBar = findViewById(R.id.bottomNavLayout);
        fragmentContainer = findViewById(R.id.fragment_container);

        navBar.setOnTabSelectListener(tab -> switchToTab(tab, true));
        navBar.setOnTabReselectListener(() -> {
            if (currentTab == TAB_HOME && homeFragment != null) {
                homeFragment.scrollToTop();
            }
        });

        // 处理外部跳转意图(SettingActivity 改配置后带 tab/useCache 返回)
        handleNavigationIntent(getIntent());

        int initialTab = currentTab < 0 ? readInitialTab() : currentTab;
        switchToTab(initialTab, false);
    }

    private int readInitialTab() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("tab")) {
            return intent.getIntExtra("tab", TAB_HOME);
        }
        return TAB_HOME;
    }

    private void handleNavigationIntent(Intent intent) {
        if (intent == null || intent.getExtras() == null) return;
        Bundle extras = intent.getExtras();
        if (extras.containsKey("tab")) {
            currentTab = extras.getInt("tab", TAB_HOME);
        }
        boolean useCache = extras.getBoolean("useCache", false);
        if (useCache) {
            if (homeFragment != null) {
                homeFragment.setUseCacheConfig(true);
                homeFragment.reloadWithCache();
            }
            pendingHomeUseCache = true;
        }
    }

    /** 首个 Fragment 尚未创建时缓存 useCache 标记,创建后补传 */
    private boolean pendingHomeUseCache = false;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        int prevTab = currentTab;
        handleNavigationIntent(intent);
        // 外部(如 SettingActivity)要求落到其他 Tab 时执行实际切换
        if (currentTab != prevTab) {
            switchToTab(currentTab, true);
        }
    }

    /**
     * 切换 Tab。show/hide 保留各 Fragment 状态(直播切走自动暂停,切回继续);
     * 切换后对新内容做淡入+轻微上移动画,导航栏不动。
     */
    public void switchToTab(int tab, boolean animate) {
        if (tab == currentTab && findFragmentByTab(tab) != null) return;
        BaseLazyFragment target = obtainFragment(tab);
        if (target == null) return;
        BaseLazyFragment current = currentTab >= 0 ? findFragmentByTab(currentTab) : null;

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        if (current != null && current != target) {
            ft.hide(current);
        }
        if (target.isAdded()) {
            ft.show(target);
        } else {
            ft.add(R.id.fragment_container, target);
        }
        ft.commitAllowingStateLoss();

        navBar.setSelectedTab(tab);
        currentTab = tab;
        if (animate) {
            animateIn(target);
        }
    }

    private BaseLazyFragment findFragmentByTab(int tab) {
        switch (tab) {
            case TAB_HOME: return homeFragment;
            case TAB_LIVE: return liveFragment;
            case TAB_MY: return myFragment;
            default: return null;
        }
    }

    private BaseLazyFragment obtainFragment(int tab) {
        switch (tab) {
            case TAB_HOME:
                if (homeFragment == null) {
                    homeFragment = new HomeFragment();
                    if (pendingHomeUseCache) {
                        homeFragment.setUseCacheConfig(true);
                        pendingHomeUseCache = false;
                    }
                }
                return homeFragment;
            case TAB_LIVE:
                if (liveFragment == null) liveFragment = new LiveFragment();
                return liveFragment;
            case TAB_MY:
                if (myFragment == null) myFragment = new MyFragment();
                return myFragment;
            default:
                return null;
        }
    }

    /** 内容区切换动画:淡入 + 轻微上移(手机)/右移(平板);导航栏不参与 */
    private void animateIn(BaseLazyFragment fragment) {
        View view = fragment.getView();
        if (view == null) {
            // 视图尚未创建(commit 异步),下一帧再试一次
            fragmentContainer.post(() -> {
                View v = fragment.getView();
                if (v != null) runInAnimation(v);
            });
            return;
        }
        runInAnimation(view);
    }

    private void runInAnimation(View view) {
        float density = getResources().getDisplayMetrics().density;
        boolean pad = com.mobile.novabox.util.PadUiHelper.isPad(this);
        view.setAlpha(0f);
        if (pad) {
            view.setTranslationX(32f * density);
        } else {
            view.setTranslationY(24f * density);
        }
        view.animate()
                .alpha(1f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(220L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    private BaseLazyFragment currentFragment() {
        return currentTab >= 0 ? findFragmentByTab(currentTab) : null;
    }

    @Override
    public void onBackPressed() {
        BaseLazyFragment current = currentFragment();
        // 直播 Tab:全屏退出/关浮层等由 Fragment 消费;否则回首页 Tab
        if (current instanceof LiveFragment) {
            if (((LiveFragment) current).handleBack()) return;
            switchToTab(TAB_HOME, true);
            return;
        }
        // 我的 Tab:返回即回首页 Tab
        if (current instanceof MyFragment) {
            switchToTab(TAB_HOME, true);
            return;
        }
        // 首页 Tab:Fragment 先处理(打断加载/恢复列表);未消费则二次确认退出
        if (current instanceof HomeFragment) {
            if (!((HomeFragment) current).handleBack()) {
                doExit();
            }
            return;
        }
        doExit();
    }

    /** 原 HomeActivity 的二次返回退出逻辑,移到容器层 */
    private void doExit() {
        if (System.currentTimeMillis() - mExitTime < 2000) {
            ControlManager.get().stopServer();
            // 用户主动退出App:代理已被停掉,若之前因投屏启动了保活服务,同步停止
            CastProxyService.stop(this);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                if (activityManager != null) {
                    for (ActivityManager.AppTask appTask : activityManager.getAppTasks()) {
                        appTask.finishAndRemoveTask();
                    }
                } else {
                    finishAndRemoveTask();
                }
            } else {
                AppManager.getInstance().finishAllActivity();
                finish();
            }
        } else {
            mExitTime = System.currentTimeMillis();
            Toast.makeText(mContext, "再按一次返回键退出应用", Toast.LENGTH_SHORT).show();
        }
    }

    /** 直播全屏切换时由 LiveFragment 调用:隐藏/显示导航栏 */
    public void setNavVisible(boolean visible) {
        if (navBar != null) {
            navBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    /** 直播竖屏状态栏 padding 由窗口层承担:全屏时清除,退出时恢复 */
    public void setContentPaddingEnabled(boolean enabled) {
        if (enabled) {
            restoreStatusBarPadding();
        } else {
            clearStatusBarPadding();
        }
    }

    /** 供 LiveFragment 退出全屏后恢复设备默认方向 */
    public void enforceDeviceOrientation() {
        enforceOrientationForDevice();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 转发给当前 Fragment(如"我的"页备份弹窗的 SAF 选择结果)
        Fragment current = currentFragment();
        if (current != null) {
            current.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (liveFragment != null) {
            liveFragment.onOrientationChanged(newConfig);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (liveFragment != null && currentTab == TAB_LIVE) {
            liveFragment.onWindowFocusChangedCompat(hasFocus);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            ControlManager.get().stopServer();
            CastProxyService.stop(this);
        }
    }

    @Nullable
    public HomeFragment getHomeFragment() {
        return homeFragment;
    }
}
