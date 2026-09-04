package com.mobile.novabox.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.mobile.novabox.R;

/**
 * 首页 / 直播 / 我的 三个 Tab 共用的主导航栏。
 *
 * 结构:本组件只负责选中态高亮与点击事件上报;具体样式(手机版底部横条 /
 * 平板 sw600dp 左侧竖栏)由 view_main_nav_bar 的布局限定符各留一份,
 * 页面布局里不再重复手写导航栏。
 *
 * 用法(容器 MainActivity 持有本组件并接管路由):
 * <pre>
 * &lt;com.mobile.novabox.ui.widget.MainNavBar
 *     android:id="@+id/bottomNavLayout"
 *     app:navSelected="home|live|my" ... /&gt;
 *
 * navBar.setOnTabSelectListener(tab -&gt; switchToTab(tab));
 * navBar.setOnTabReselectListener(() -&gt; ...);   // 再次点击当前 tab
 * navBar.setSelectedTab(tab);                    // 容器切换后同步高亮
 * </pre>
 *
 * 点击不直接 startActivity:由容器在同一 Activity 内切换 Fragment,
 * 从而做到"导航栏纹丝不动,只有内容区切换动画"。
 */
public class MainNavBar extends FrameLayout {
    public static final int TAB_HOME = 0;
    public static final int TAB_LIVE = 1;
    public static final int TAB_MY = 2;

    private static final int COLOR_SELECTED = 0xFF0CADE2;
    private static final int COLOR_UNSELECTED = 0x80000000;
    private static final int COLOR_ICON_UNSELECTED = 0xFF000000;

    private int selectedTab = TAB_HOME;
    private Runnable reselectListener;
    private OnTabSelectListener tabSelectListener;

    /** Tab 点击(与当前选中项不同时)回调,由容器执行实际的 Tab 切换 */
    public interface OnTabSelectListener {
        void onTabSelect(int tab);
    }

    public MainNavBar(Context context) {
        this(context, null);
    }

    public MainNavBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MainNavBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // 同一个布局名,手机/平板各自的表达由资源限定符(view_main_nav_bar / -sw600dp)选择
        inflate(context, R.layout.view_main_nav_bar, this);
        if (attrs != null) {
            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.MainNavBar);
            selectedTab = ta.getInt(R.styleable.MainNavBar_navSelected, TAB_HOME);
            ta.recycle();
        }
        applySelection();
        wireClicks();
    }

    /** Tab 点击回调(容器接管路由) */
    public void setOnTabSelectListener(OnTabSelectListener listener) {
        this.tabSelectListener = listener;
    }

    /** 再次点击"当前已选中"的 tab 时回调(例如首页滚回顶部);不需要可不设置 */
    public void setOnTabReselectListener(Runnable listener) {
        this.reselectListener = listener;
    }

    public int getSelectedTab() {
        return selectedTab;
    }

    /** 容器完成 Tab 切换后调用,同步高亮(不改路由状态) */
    public void setSelectedTab(int tab) {
        if (tab < TAB_HOME || tab > TAB_MY) return;
        this.selectedTab = tab;
        applySelection();
    }

    private void wireClicks() {
        findViewById(R.id.navHome).setOnClickListener(v -> openTab(TAB_HOME));
        findViewById(R.id.navLive).setOnClickListener(v -> openTab(TAB_LIVE));
        findViewById(R.id.navSetting).setOnClickListener(v -> openTab(TAB_MY));
    }

    private void openTab(int tab) {
        if (tab == selectedTab) {
            if (reselectListener != null) reselectListener.run();
            return;
        }
        if (tabSelectListener != null) {
            tabSelectListener.onTabSelect(tab);
        }
    }

    private void applySelection() {
        bindItem(R.id.navHomeIcon, R.id.navHomeText, TAB_HOME == selectedTab);
        bindItem(R.id.navLiveIcon, R.id.navLiveText, TAB_LIVE == selectedTab);
        bindItem(R.id.navSettingIcon, R.id.navSettingText, TAB_MY == selectedTab);
    }

    private void bindItem(int iconId, int textId, boolean selected) {
        ImageView icon = findViewById(iconId);
        if (icon != null) {
            icon.setAlpha(selected ? 1.0f : 0.5f);
            icon.setColorFilter(selected ? COLOR_SELECTED : COLOR_ICON_UNSELECTED);
        }
        TextView text = findViewById(textId);
        if (text != null) {
            text.setTextColor(selected ? COLOR_SELECTED : COLOR_UNSELECTED);
        }
    }
}
