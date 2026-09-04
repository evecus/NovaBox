package com.mobile.novabox.ui.fragment;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mobile.novabox.R;
import com.mobile.novabox.api.ApiConfig;
import com.mobile.novabox.base.BaseLazyFragment;
import com.mobile.novabox.bean.AbsSortXml;
import com.mobile.novabox.bean.MovieSort;
import com.mobile.novabox.bean.SourceBean;
import com.mobile.novabox.event.RefreshEvent;
import com.mobile.novabox.ui.activity.CollectActivity;
import com.mobile.novabox.ui.activity.DetailActivity;
import com.mobile.novabox.ui.activity.HistoryActivity;
import com.mobile.novabox.ui.activity.MainActivity;
import com.mobile.novabox.ui.activity.SearchActivity;
import com.mobile.novabox.ui.activity.SettingActivity;
import com.mobile.novabox.ui.adapter.HomePageAdapter;
import com.mobile.novabox.ui.adapter.SortAdapter;
import com.mobile.novabox.ui.tv.widget.DefaultTransformer;
import com.mobile.novabox.ui.tv.widget.FixedSpeedScroller;
import com.mobile.novabox.ui.tv.widget.NoScrollViewPager;
import com.mobile.novabox.util.DefaultConfig;
import com.mobile.novabox.util.FastClickCheckUtil;
import com.mobile.novabox.util.HawkConfig;
import com.mobile.novabox.util.LOG;
import com.mobile.novabox.viewmodel.SourceViewModel;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 首页内容 Fragment(原 HomeActivity 转换而来)。
 * 由容器 MainActivity 以 Tab 形式承载;底部/左侧导航栏在容器层。
 */
public class HomeFragment extends BaseLazyFragment {
    private TextView tvName;
    private RecyclerView mGridView;
    private NoScrollViewPager mViewPager;
    private SourceViewModel sourceViewModel;
    private SortAdapter sortAdapter;
    private HomePageAdapter pageAdapter;
    private View currentView;
    private final List<BaseLazyFragment> fragments = new ArrayList<>();
    private boolean sortChange = false;
    private int currentSelected = 0;
    private int sortFocused = 0;
    public View sortFocusView = null;
    private final Handler mHandler = new Handler();
    private boolean eventBusRegistered = false;

    boolean useCacheConfig = false;

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_home;
    }

    /** 由容器在启动/跳回时注入(SettingActivity 改配置后带 useCache 返回) */
    public void setUseCacheConfig(boolean useCache) {
        this.useCacheConfig = useCache;
    }

    /** 已初始化过的首页按新配置重载(等效原"重建 HomeActivity"流程) */
    public void reloadWithCache() {
        if (!isViewCreated) {
            // 尚未初始化:init() 首次可见时自然会用最新 useCacheConfig 走流程
            return;
        }
        dataInitOk = false;
        jarInitOk = false;
        dismissHomeDialogs();
        initData();
    }

    @Override
    protected void init() {
        EventBus.getDefault().register(this);
        eventBusRegistered = true;
        // LAN server disabled for mobile: ControlManager.get().startServer();
        initView();
        initViewModel();
        initData();
    }

    private void initView() {
        this.tvName = findViewById(R.id.tvName);
        this.mGridView = findViewById(R.id.mGridView);
        this.mViewPager = findViewById(R.id.mViewPager);
        this.sortAdapter = new SortAdapter();
        this.mGridView.setLayoutManager(new LinearLayoutManager(this.mContext, LinearLayoutManager.HORIZONTAL, false));
        this.mGridView.setAdapter(this.sortAdapter);
        this.mGridView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateSortSelection(currentSelected);
            }
        });
        sortAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                mGridView.post(() -> updateSortSelection(currentSelected));
            }
        });
        // 分类 tab 点击切换页面
        sortAdapter.setOnItemClickListener((adapter, view, position) -> {
            if (position < 0 || position >= fragments.size()) return;
            sortFocused = position;
            updateSortSelection(position);
            if (sortFocused != currentSelected) {
                currentSelected = sortFocused;
                mViewPager.setCurrentItem(sortFocused, false);
                changeTop(sortFocused != 0);
                BaseLazyFragment baseLazyFragment = fragments.get(sortFocused);
                if (baseLazyFragment instanceof GridFragment && ((GridFragment) baseLazyFragment).shouldReloadOnSelect()) {
                    ((GridFragment) baseLazyFragment).forceRefresh();
                }
            }
        });

        // 分类 tab 长按弹出筛选框（排序/地区等）。
        // 只有长按"当前正在显示的那个 tab"才生效。
        sortAdapter.setOnItemLongClickListener((adapter, view, position) -> {
            if (position < 0 || position >= fragments.size()) return false;
            if (position != currentSelected) return false;
            BaseLazyFragment baseLazyFragment = fragments.get(position);
            if (baseLazyFragment instanceof GridFragment) {
                ((GridFragment) baseLazyFragment).showFilter();
                return true;
            }
            return false;
        });

        // 站源切换按钮
        findViewById(R.id.btnSiteSwitch).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            showSiteSwitch();
        });

        // 线路选择按钮(复用设置界面的线路选择弹窗)
        findViewById(R.id.btnRoute).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            showRouteSelect();
        });

        // 搜索框
        findViewById(R.id.btnSearch).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            jumpActivity(SearchActivity.class);
        });

        // 收藏按钮
        findViewById(R.id.btnCollect).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            jumpActivity(CollectActivity.class);
        });

        // 历史按钮
        findViewById(R.id.btnHistory).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            jumpActivity(HistoryActivity.class);
        });

        setLoadSir(findViewById(R.id.contentLayout));
    }

    /** 导航栏再次点击"首页":回到第一个 tab 顶部(容器调用) */
    public void scrollToTop() {
        mGridView.scrollToPosition(0);
        if (currentSelected != 0) {
            sortFocused = 0;
            currentSelected = 0;
            mViewPager.setCurrentItem(0, false);
            changeTop(false);
            updateSortSelection(0);
        }
    }

    private boolean skipNextUpdate = false;

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.sortResult.observe(this, new Observer<AbsSortXml>() {
            @Override
            public void onChanged(AbsSortXml absXml) {
                if (skipNextUpdate) {
                    skipNextUpdate = false;
                    return;
                }
                showSuccess();
                if (absXml != null && absXml.classes != null && absXml.classes.sortList != null) {
                    sortAdapter.setNewData(DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), absXml.classes.sortList, true));
                } else {
                    sortAdapter.setNewData(DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), new ArrayList<>(), true));
                }
                initViewPager(absXml);
                // 不再用站源名称覆盖按钮文字，固定显示"站源切换"
                tvName.clearAnimation();
            }
        });
    }

    private boolean dataInitOk = false;
    private boolean jarInitOk = false;
    private com.mobile.novabox.ui.dialog.TipDialog mConfigErrorDialog;

    private void initData() {
        if (dataInitOk && jarInitOk) {
            sourceViewModel.getSort(ApiConfig.get().getHomeSourceBean().getKey());
            if (hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                LOG.e("有");
            } else {
                LOG.e("无");
            }
            if (!useCacheConfig && Hawk.get(HawkConfig.DEFAULT_LOAD_LIVE, false)) {
                ((MainActivity) mActivity).switchToTab(MainActivity.TAB_LIVE, false);
            }
            return;
        }
        tvNameAnimation();
        showLoading();
        if (dataInitOk && !jarInitOk) {
            if (!ApiConfig.get().getSpider().isEmpty()) {
                ApiConfig.get().loadJar(useCacheConfig, ApiConfig.get().getSpider(), new ApiConfig.LoadConfigCallback() {
                    @Override
                    public void success() {
                        jarInitOk = true;
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                initData();
                            }
                        }, 50);
                    }

                    @Override
                    public void notice(String msg) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(mActivity, msg, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void error(String msg) {
                        jarInitOk = true;
                        dataInitOk = true;
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(mActivity, msg + " jar load err", Toast.LENGTH_SHORT).show();
                                initData();
                            }
                        }, 50);
                    }
                });
            }
            return;
        }
        ApiConfig.get().loadConfig(useCacheConfig, new ApiConfig.LoadConfigCallback() {
            @Override
            public void notice(String msg) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(mActivity, msg, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void success() {
                dataInitOk = true;
                if (ApiConfig.get().getSpider().isEmpty()) {
                    jarInitOk = true;
                }
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        initData();
                    }
                }, 50);
            }

            @Override
            public void error(String msg) {
                if (msg.equalsIgnoreCase("-1")) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            dataInitOk = true;
                            jarInitOk = true;
                            initData();
                        }
                    });
                    return;
                }
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isActivityUnavailable()) {
                            return;
                        }
                        if (mConfigErrorDialog == null)
                            mConfigErrorDialog = new com.mobile.novabox.ui.dialog.TipDialog(mActivity, msg, "重试", "取消", new com.mobile.novabox.ui.dialog.TipDialog.OnListener() {
                                @Override
                                public void left() {
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            dismissConfigErrorDialog();
                                            initData();
                                        }
                                    });
                                }

                                @Override
                                public void right() {
                                    dataInitOk = true;
                                    jarInitOk = true;
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            dismissConfigErrorDialog();
                                            initData();
                                        }
                                    });
                                }

                                @Override
                                public void cancel() {
                                    dataInitOk = true;
                                    jarInitOk = true;
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            dismissConfigErrorDialog();
                                            initData();
                                        }
                                    });
                                }
                            });
                        if (!mConfigErrorDialog.isShowing())
                            mConfigErrorDialog.show();
                    }
                });
            }
        }, mActivity);
    }

    /**
     * phone: highlight the active tab by selection state, not TV remote focus.
     */
    private void updateSortSelection(int selectedPosition) {
        if (mGridView == null || mGridView.getLayoutManager() == null) return;
        LinearLayoutManager lm = (LinearLayoutManager) mGridView.getLayoutManager();
        int first = lm.findFirstVisibleItemPosition();
        int last = lm.findLastVisibleItemPosition();
        for (int i = first; i <= last; i++) {
            if (i < 0) continue;
            View child = lm.findViewByPosition(i);
            if (child != null) {
                boolean sel = (i == selectedPosition);
                child.setSelected(sel);
                // 代码绘制圆角背景，确保在所有系统版本上圆角生效
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                bg.setCornerRadius(60f);
                bg.setColor(sel ? 0xBD0CADE2 : android.graphics.Color.TRANSPARENT);
                child.setBackground(bg);
            }
        }
    }

    private void initViewPager(AbsSortXml absXml) {
        if (sortAdapter.getData().size() > 0) {
            for (MovieSort.SortData data : sortAdapter.getData()) {
                if (data.id.equals("my0")) {
                    if (Hawk.get(HawkConfig.HOME_REC, 0) == 1 && absXml != null && absXml.videoList != null && absXml.videoList.size() > 0) {
                        fragments.add(com.mobile.novabox.ui.fragment.UserFragment.newInstance(absXml.videoList));
                    } else {
                        fragments.add(com.mobile.novabox.ui.fragment.UserFragment.newInstance(null));
                    }
                } else {
                    fragments.add(GridFragment.newInstance(data));
                }
            }
            pageAdapter = new HomePageAdapter(getChildFragmentManager(), fragments);
            try {
                Field field = androidx.viewpager.widget.ViewPager.class.getDeclaredField("mScroller");
                field.setAccessible(true);
                FixedSpeedScroller scroller = new FixedSpeedScroller(mContext, new AccelerateInterpolator());
                field.set(mViewPager, scroller);
                scroller.setmDuration(300);
            } catch (Exception e) {
            }
            mViewPager.setPageTransformer(true, new DefaultTransformer());
            mViewPager.setAdapter(pageAdapter);
            mViewPager.setCurrentItem(currentSelected, false);
        }
    }

    /**
     * 返回键处理(由容器 MainActivity 调用)。
     * @return true 表示本 Fragment 已消费(打断加载/恢复列表状态等);
     *         false 表示可以退出应用(容器执行二次确认退出)。
     */
    public boolean handleBack() {
        // 打断加载
        if (isLoading()) {
            refreshEmpty();
            return true;
        }
        // 如果处于 VOD 删除模式，则退出该模式并刷新界面
        if (HawkConfig.hotVodDelete) {
            HawkConfig.hotVodDelete = false;
            com.mobile.novabox.ui.fragment.UserFragment.homeHotVodAdapter.notifyDataSetChanged();
            return true;
        }

        // 检查 fragments 状态
        if (this.fragments.size() <= 0 || this.sortFocused >= this.fragments.size() || this.sortFocused < 0) {
            return false;
        }

        BaseLazyFragment baseLazyFragment = this.fragments.get(this.sortFocused);
        if (baseLazyFragment instanceof GridFragment) {
            GridFragment grid = (GridFragment) baseLazyFragment;
            // 如果当前 Fragment 能恢复之前保存的 UI 状态，则直接返回
            if (grid.restoreView()) {
                return true;
            }
            // 如果 sortFocusView 存在且没有获取焦点，则请求焦点
            if (this.sortFocusView != null && !this.sortFocusView.isFocused()) {
                this.sortFocusView.requestFocus();
                return true;
            }
            // 如果当前不是第一个界面，则将列表设置到第一项
            else if (this.sortFocused != 0) {
                this.mGridView.scrollToPosition(0);
                return true;
            } else {
                return false;
            }
        } else if (baseLazyFragment instanceof com.mobile.novabox.ui.fragment.UserFragment && com.mobile.novabox.ui.fragment.UserFragment.tvHotList.canScrollVertically(-1)) {
            // 如果 UserFragment 列表可以向上滚动，则滚动到顶部
            com.mobile.novabox.ui.fragment.UserFragment.tvHotList.scrollToPosition(0);
            this.mGridView.scrollToPosition(0);
            return true;
        } else {
            return false;
        }
    }

    /** MENU 键处理(由容器转发):长按进设置,短按弹站源切换。返回是否消费。 */
    public boolean onMenuKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_MENU) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            menuKeyDownTime = System.currentTimeMillis();
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            long pressDuration = System.currentTimeMillis() - menuKeyDownTime;
            if (pressDuration >= LONG_PRESS_THRESHOLD) {
                jumpActivity(SettingActivity.class);
            } else {
                showSiteSwitch();
            }
        }
        return true;
    }

    private long menuKeyDownTime = 0;
    private static final long LONG_PRESS_THRESHOLD = 2000; // 设置长按的阈值，单位是毫秒

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_PUSH_URL) {
            if (ApiConfig.get().getSource("push_agent") != null) {
                Intent newIntent = new Intent(mContext, DetailActivity.class);
                newIntent.putExtra("id", (String) event.obj);
                newIntent.putExtra("sourceKey", "push_agent");
                newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                mActivity.startActivity(newIntent);
            }
        } else if (event.type == RefreshEvent.TYPE_FILTER_CHANGE) {
            if (currentView != null) {
                showFilterIcon((int) event.obj);
            }
        }
    }

    private void showFilterIcon(int count) {
        boolean visible = count > 0;
        currentView.findViewById(R.id.tvFilterColor).setVisibility(visible ? View.VISIBLE : View.GONE);
        currentView.findViewById(R.id.tvFilter).setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    private void changeTop(boolean hide) {
        // 手机版：顶部导航栏在所有分类页面下保持常驻显示，不做隐藏动画
    }

    @Override
    public void onDestroy() {
        dismissHomeDialogs();
        mHandler.removeCallbacksAndMessages(null);
        unregisterEventBus();
        super.onDestroy();
    }

    private void unregisterEventBus() {
        if (eventBusRegistered) {
            EventBus.getDefault().unregister(this);
            eventBusRegistered = false;
        }
    }

    private android.app.Dialog mSiteSwitchDialog;

    void showSiteSwitch() {
        if (isActivityUnavailable()) return;
        List<SourceBean> sites = ApiConfig.get().getSwitchSourceBeanList();
        if (sites.isEmpty()) return;
        int currentSelect = sites.indexOf(ApiConfig.get().getHomeSourceBean());
        if (currentSelect < 0) currentSelect = 0;

        android.app.Dialog dialog = new android.app.Dialog(mActivity, R.style.CustomDialogStyleDim);
        dialog.setContentView(R.layout.dialog_site_switch);
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            int maxH = (int) (getResources().getDisplayMetrics().heightPixels * 0.55f);
            window.setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.88f),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(android.view.Gravity.CENTER);
        }

        // 关闭按钮
        dialog.findViewById(R.id.ivClose).setOnClickListener(v -> dialog.dismiss());

        RecyclerView rv = dialog.findViewById(R.id.list);
        rv.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(mContext, 2));
        final int[] selectedIdx = {currentSelect};

        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(mActivity);
        rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
                android.view.View v = inflater.inflate(R.layout.item_site_switch, parent, false);
                return new RecyclerView.ViewHolder(v) {
                };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, @SuppressLint("RecyclerView") int position) {
                android.widget.TextView tvName = holder.itemView.findViewById(R.id.tvName);
                android.widget.ImageView ivCheck = holder.itemView.findViewById(R.id.ivCheck);
                SourceBean bean = sites.get(position);
                tvName.setText(bean.getName());
                boolean isSelected = (position == selectedIdx[0]);
                ivCheck.setImageResource(isSelected ? R.drawable.icon_radio_selected : R.drawable.icon_radio_unselect);
                tvName.setTextColor(isSelected ? 0xff02f8e1 : 0xFF000000);
                holder.itemView.setOnClickListener(v -> {
                    if (position == selectedIdx[0]) {
                        dialog.dismiss();
                        return;
                    }
                    selectedIdx[0] = position;
                    notifyDataSetChanged();
                    dialog.dismiss();
                    ApiConfig.get().setSourceBean(bean);
                    refreshHome();
                });
            }

            @Override
            public int getItemCount() {
                return sites.size();
            }
        });

        rv.post(() -> rv.scrollToPosition(Math.max(0, selectedIdx[0] - 2)));
        mSiteSwitchDialog = dialog;
        dialog.show();
    }

    private void showRouteSelect() {
        // 复用设置页的线路选择弹窗,与用户在设置里看到的一致。
        // 选完后切换 API_URL,保存历史,并杀进程重启 App 让 ApiConfig/PlayerHelper/Hawk 等单例重新加载。
        com.mobile.novabox.ui.dialog.RouteSelectDialog.show(mActivity, new com.mobile.novabox.ui.dialog.RouteSelectDialog.OnRouteSelectedListener() {
            @Override
            public void onSelected(String url) {
                String oldApi = Hawk.get(HawkConfig.API_URL, "");
                Hawk.put(HawkConfig.API_URL, url);
                com.mobile.novabox.util.HistoryHelper.setApiHistory(url);
                if (!oldApi.equals(url)) {
                    android.widget.Toast.makeText(mActivity, "配置已切换,即将自动重启应用!", android.widget.Toast.LENGTH_SHORT).show();
                    com.mobile.novabox.base.App.restartApp(2500);
                }
            }

            @Override
            public void onCancel() { /* no-op */ }
        });
    }

    /** 站源切换后整任务重启容器(携带 useCache),等效原 HomeActivity 的 CLEAR_TASK 自重建 */
    private void refreshHome() {
        if (Thread.currentThread() != android.os.Looper.getMainLooper().getThread()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    refreshHome();
                }
            });
            return;
        }
        if (isActivityUnavailable()) {
            return;
        }
        dismissHomeDialogs();
        Intent intent = new Intent(mContext.getApplicationContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Bundle bundle = new Bundle();
        bundle.putBoolean("useCache", true);
        intent.putExtras(bundle);
        mActivity.startActivity(intent);
    }

    private boolean isActivityUnavailable() {
        return !isAdded() || mActivity == null || mActivity.isFinishing() || mActivity.isDestroyed();
    }

    private void dismissHomeDialogs() {
        dismissConfigErrorDialog();
        dismissSiteSwitchDialog();
    }

    private void dismissConfigErrorDialog() {
        if (mConfigErrorDialog != null) {
            if (mConfigErrorDialog.isShowing()) {
                mConfigErrorDialog.dismiss();
            }
            mConfigErrorDialog = null;
        }
    }

    private void dismissSiteSwitchDialog() {
        if (mSiteSwitchDialog != null) {
            if (mSiteSwitchDialog.isShowing()) {
                mSiteSwitchDialog.dismiss();
            }
            mSiteSwitchDialog = null;
        }
    }

    private void refreshEmpty() {
        skipNextUpdate = true;
        showSuccess();
        sortAdapter.setNewData(DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), new ArrayList<>(), true));
        initViewPager(null);
        tvName.clearAnimation();
    }

    private void tvNameAnimation() {
        AlphaAnimation blinkAnimation = new AlphaAnimation(0.0f, 1.0f);
        blinkAnimation.setDuration(500);
        blinkAnimation.setStartOffset(20);
        blinkAnimation.setRepeatMode(Animation.REVERSE);
        blinkAnimation.setRepeatCount(Animation.INFINITE);
        tvName.startAnimation(blinkAnimation);
    }
}
