package com.mobile.novabox.ui.fragment;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.CountDownTimer;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.catvod.crawler.Spider;
import com.mobile.novabox.R;
import com.mobile.novabox.api.ApiConfig;
import com.mobile.novabox.base.App;
import com.mobile.novabox.base.BaseLazyFragment;
import com.mobile.novabox.bean.LiveChannelGroup;
import com.mobile.novabox.bean.LiveChannelItem;
import com.mobile.novabox.bean.LivePlayerManager;
import com.mobile.novabox.bean.LiveSettingGroup;
import com.mobile.novabox.bean.LiveSettingItem;
import com.mobile.novabox.player.controller.LiveController;
import com.mobile.novabox.ui.activity.MainActivity;
import com.mobile.novabox.ui.adapter.LiveChannelGroupAdapter;
import com.mobile.novabox.ui.adapter.LiveChannelItemAdapter;
import com.mobile.novabox.ui.adapter.LiveSettingGroupAdapter;
import com.mobile.novabox.ui.adapter.LiveSettingItemAdapter;
import com.mobile.novabox.ui.dialog.LivePasswordDialog;
import com.mobile.novabox.ui.widget.MainNavBar;
import com.mobile.novabox.util.DefaultConfig;
import com.mobile.novabox.util.FastClickCheckUtil;
import com.mobile.novabox.util.HawkConfig;
import com.mobile.novabox.util.LOG;
import com.mobile.novabox.util.PlayerHelper;
import com.mobile.novabox.util.live.TxtSubscribe;
import com.google.gson.JsonArray;

import com.google.gson.JsonObject;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import xyz.doikki.videoplayer.exo.ExoMediaSourceHelper;
import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.VideoView;

/**
 * 直播内容 Fragment(原 LivePlayActivity 转换而来)。
 * 由容器 MainActivity 以 Tab 形式承载:切走时暂停播放、切回继续,状态保留。
 * 全屏/沉浸式相关的窗口操作通过宿主 MainActivity 完成。
 */
public class LiveFragment extends BaseLazyFragment {
    public static Context context;
    private VideoView<xyz.doikki.videoplayer.player.AbstractPlayer> mVideoView;
    private View switchChannelSnapshotOverlay;
    private ImageView switchChannelSnapshotImage;
    private TextView tvTime;
    private TextView tvNetSpeed;
    private TextView tvResolution;
    private RecyclerView mChannelGroupView;
    private RecyclerView mLiveChannelView;
    private LiveChannelGroupAdapter liveChannelGroupAdapter;
    private LiveChannelItemAdapter liveChannelItemAdapter;

    private RecyclerView mSettingGroupView;
    private RecyclerView mSettingItemView;
    private LiveSettingGroupAdapter liveSettingGroupAdapter;
    private LiveSettingItemAdapter liveSettingItemAdapter;
    private List<LiveSettingGroup> liveSettingGroupList = new ArrayList<>();

    public static  int currentChannelGroupIndex = 0;
    private Handler mHandler = new Handler();
    private int resolutionInfoRetryCount = 0;
    private boolean resolutionInfoPending = false;
    // Pad 端横屏始终是横屏，用此标记区分"全屏模式"与"正常浏览模式"
    private boolean mIsPadFullscreen = false;
    // 竖屏/传感器策略下"不旋转全屏"标记
    private boolean mIsPortraitFullscreen = false;
    private static final int RESOLUTION_INFO_MAX_RETRY = 10;
    private static final long RESOLUTION_INFO_RETRY_DELAY = 300L;
    private static final long RESOLUTION_INFO_HIDE_DELAY = 3000L;

    private List<LiveChannelGroup> liveChannelGroupList = new ArrayList<>();
    private int currentLiveChannelIndex = -1;
    private int currentLiveLookBackIndex = -1;
    private int currentLiveChangeSourceTimes = 0;
    /** 本轮已尝试过的播放内核(0=EXO硬解 1=EXO软解 2=IJK硬解 3=IJK软解),失败时按序切换;播放成功/换源/换频道时清空 */
    private final Set<Integer> triedLivePlayerTypes = new HashSet<>();
    private LiveChannelItem currentLiveChannelItem = null;
    private String pendingLiveRefreshChannelName = null;
    private int pendingLiveRefreshSourceIndex = -1;
    private boolean refreshingLiveChannelList = false;
    private LivePlayerManager livePlayerManager = new LivePlayerManager();
    private ArrayList<Integer> channelGroupPasswordConfirmed = new ArrayList<>();

    private static LiveChannelItem  channel_Name = null;
    private CountDownTimer countDownTimer;
    private View ll_right_top_loading;
    private View ll_right_top_huikan;
    private TextView tv_right_top_channel_name;
    private TextView tv_right_top_epg_name;
    private View iv_circle_bg;
    private ImageView iv_back_bg;

    private boolean isSHIYI = false;
    private boolean isBack = false;
    private static final int postTimeout = 6000;

    // 手机端新增控件
    private RecyclerView mLiveSourceView;
    private LiveSettingItemAdapter liveSourceAdapter;
    private View liveControlOverlay;
    private View liveSettingsDialogOverlay;
    private TextView tvMobileChannelName;
    private ImageView ivSettingsBtn;
    // iv_overlay_settings_btn 已从布局移除
    private ImageView ivBackBtn;
    private ImageView ivRefreshBtn;
    private ImageView ivFullscreenBtn;
    private ImageView ivPlayPauseCenter;
    private TextView tvSettingsClose;
    private android.os.CountDownTimer controlOverlayTimer;


    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_live;
    }

    @Override
    protected void init() {
        context = mActivity;

        setLoadSir(findViewById(R.id.live_channel_area));
        mVideoView = findViewById(R.id.mVideoView);
        switchChannelSnapshotOverlay = findViewById(R.id.switchChannelSnapshotOverlay);
        switchChannelSnapshotImage = findViewById(R.id.switchChannelSnapshotImage);

        mChannelGroupView = findViewById(R.id.mGroupGridView);
        mLiveChannelView = findViewById(R.id.mChannelGridView);
        mSettingGroupView = findViewById(R.id.mSettingGroupView);
        mSettingItemView = findViewById(R.id.mSettingItemView);
        tvTime = findViewById(R.id.tvTime);
        tvNetSpeed = findViewById(R.id.tvNetSpeed);
        tvResolution = findViewById(R.id.tvResolution);

        tv_right_top_channel_name = (TextView)findViewById(R.id.tv_right_top_channel_name);
        tv_right_top_epg_name = (TextView)findViewById(R.id.tv_right_top_epg_name);
        iv_circle_bg = findViewById(R.id.iv_circle_bg);
        iv_back_bg = (ImageView) findViewById(R.id.iv_back_bg);
        ll_right_top_loading = findViewById(R.id.ll_right_top_loading);
        ll_right_top_huikan = findViewById(R.id.ll_right_top_huikan);
        // ProgressBar自带旋转动画，无需手动设置objectAnimator

        initVideoView();
        initChannelGroupView();
        initLiveChannelView();
        initSettingGroupView();
        initSettingItemView();
        initLiveChannelList();
        initLiveSettingGroupList();
        initMobileUI();
        Hawk.put(HawkConfig.PLAYER_IS_LIVE,true);
    }
    // 切台后短暂显示右上角"频道名+加载圈"悬浮条，几秒后自动隐藏
    @SuppressLint("SetTextI18n")
    private void updateRightTopChannelInfo() {
        if (isSHIYI || channel_Name == null || channel_Name.getChannelName() == null) {
            return;
        }
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        ll_right_top_loading.setVisibility(View.VISIBLE);
        countDownTimer = new CountDownTimer(postTimeout, 1000) {
            public void onTick(long j) {
            }
            public void onFinish() {
                ll_right_top_loading.setVisibility(View.GONE);
                ll_right_top_huikan.setVisibility(View.GONE);
            }
        };
        countDownTimer.start();
        tv_right_top_channel_name.setText(channel_Name.getChannelName());
        tv_right_top_epg_name.setText(channel_Name.getChannelName());
    }

    /**
     * 返回键处理(由容器 MainActivity 调用)。
     * @return true 表示已消费(退出全屏/关浮层/回上一源等);
     *         false 表示切换回首页 Tab。
     */
    public boolean handleBack() {
        // Pad 端：横屏全屏模式下返回 = 退出全屏；非全屏走下面普通逻辑
        if (com.mobile.novabox.util.PadUiHelper.isPad(mActivity) && isLandscape()) {
            if (mIsPadFullscreen) {
                exitFullscreenMode();
                return true;
            }
            // 非全屏，回首页 Tab
        } else if (!com.mobile.novabox.util.PadUiHelper.isPad(mActivity) && (isLandscape() || mIsPortraitFullscreen)) {
            exitFullscreenMode();
            return true;
        }
        // 关闭设置弹窗
        if (liveSettingsDialogOverlay != null && liveSettingsDialogOverlay.getVisibility() == View.VISIBLE) {
            hideMobileSettingsDialog();
            return true;
        }
        // 关闭控制浮层
        if (liveControlOverlay != null && liveControlOverlay.getVisibility() == View.VISIBLE) {
            hideControlOverlay();
            return true;
        }
        if(isBack){
            isBack= false;
            playPreSource();
            return true;
        }
        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
        mHandler.removeCallbacks(mUpdateNetSpeedRun);
        return false;
    }

    /** Fragment 真正 Resume(容器 Tab 切回/应用回前台):恢复播放,横屏全屏时重申沉浸式 */
    @Override
    protected void onFragmentResume() {
        // 横屏全屏时重新应用沉浸式UI（pad非全屏不应用）
        boolean shouldImmersive = isLandscape() &&
            (!com.mobile.novabox.util.PadUiHelper.isPad(mActivity) || mIsPadFullscreen);
        if (shouldImmersive) {
            mActivity.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
        }
        if (mVideoView != null) {
            mVideoView.resume();
        }
    }

    /** Fragment 真正 Pause(容器 Tab 切走/应用退后台):暂停播放 */
    @Override
    protected void onFragmentPause() {
        if (mVideoView != null) {
            mVideoView.pause();
        }
    }

    @Override
    public void onDestroy() {
        Hawk.put(HawkConfig.PLAYER_IS_LIVE, false);
        hideSwitchChannelSnapshot();
        if (controlOverlayTimer != null) controlOverlayTimer.cancel();
        if (mVideoView != null) {
            mVideoView.release();
            mVideoView = null;
        }
        mHandler.removeCallbacks(mUpdateResolutionInfoRun);
        mHandler.removeCallbacks(mHideResolutionInfoRun);
        super.onDestroy();
    }

    /** 旋转回调(容器 onConfigurationChanged 转发) */
    public void onOrientationChanged(Configuration newConfig) {
        if (newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            // 旋转到横屏：进入全屏，同时清除竖屏全屏标记（横屏全屏接管）
            mIsPortraitFullscreen = false;
            enterFullscreenMode();
        } else {
            // 旋转到竖屏
            if (mIsPortraitFullscreen) {
                // 竖屏全屏状态下传感器旋回竖屏，保持全屏不退出
                return;
            }
            if (!mIsPadFullscreen) {
                // 已经不在全屏（exitFullscreenMode 刚执行完），忽略此次回调
                return;
            }
            exitFullscreenMode();
        }
    }

    private void enterFullscreenMode() {
        mIsPadFullscreen = true;
        // 注意：不在此处清除 mIsPortraitFullscreen，由调用方负责设置，
        // 否则竖屏全屏点击全屏按钮后标记被错误清除，返回键无法退出全屏。
        // 沉浸式全屏（手机/Pad通用）
        mActivity.getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );
        // 隐藏信息栏、频道列表外层容器（wrapper）和容器层的导航栏
        View infoBar = findViewById(R.id.live_info_bar);
        View channelAreaWrapper = findViewById(R.id.live_channel_area_wrapper);
        if (infoBar != null) infoBar.setVisibility(View.GONE);
        if (channelAreaWrapper != null) channelAreaWrapper.setVisibility(View.GONE);
        ((MainActivity) mActivity).setNavVisible(false);
        // 清除窗口层的状态栏 padding(全屏)
        ((MainActivity) mActivity).setContentPaddingEnabled(false);
        // 播放容器撑满：手机竖向LL中用 weight=0 + height=MATCH_PARENT 独占空间
        View playerContainer = findViewById(R.id.live_player_container);
        if (playerContainer != null) {
            android.widget.LinearLayout.LayoutParams lp =
                (android.widget.LinearLayout.LayoutParams) playerContainer.getLayoutParams();
            lp.weight = 0;
            lp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
            playerContainer.setLayoutParams(lp);
            playerContainer.requestLayout();
        }
        // 去掉主布局 padding
        View mainLayout = findViewById(R.id.live_main_content);
        if (mainLayout != null) mainLayout.setPadding(0, 0, 0, 0);
        // VideoView 重新布局后刷新画面，避免黑屏
        if (mVideoView != null) {
            mVideoView.requestLayout();
        }
        // 切换全屏按钮图标为"退出全屏"
        if (ivFullscreenBtn != null) {
            ivFullscreenBtn.setImageResource(R.drawable.icon_exit_fullscreen);
        }
        // 确保控制浮层隐藏
        hideControlOverlay();
        hideMobileSettingsDialog();
    }

    private void exitFullscreenMode() {
        mIsPadFullscreen = false;
        mIsPortraitFullscreen = false;
        // 恢复系统UI：用与常规状态一致的 flags，
        // 而不是 SYSTEM_UI_FLAG_VISIBLE（VISIBLE=0 会清掉 LAYOUT_FULLSCREEN 等标志，
        // 导致退出全屏后状态栏图标/时间消失或显示异常）
        int uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            uiFlags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        mActivity.getWindow().getDecorView().setSystemUiVisibility(uiFlags);
        // 恢复窗口层的状态栏 padding + 显示容器层导航栏
        ((MainActivity) mActivity).setContentPaddingEnabled(true);
        ((MainActivity) mActivity).setNavVisible(true);
        // 显示信息栏、频道列表外层容器
        View infoBar = findViewById(R.id.live_info_bar);
        View channelAreaWrapper = findViewById(R.id.live_channel_area_wrapper);
        if (infoBar != null) infoBar.setVisibility(View.VISIBLE);
        if (channelAreaWrapper != null) channelAreaWrapper.setVisibility(View.VISIBLE);
        // 播放容器恢复原始尺寸
        View playerContainer = findViewById(R.id.live_player_container);
        if (playerContainer != null) {
            android.widget.LinearLayout.LayoutParams lp =
                (android.widget.LinearLayout.LayoutParams) playerContainer.getLayoutParams();
            // Pad横向LL：width=0,weight=72；手机竖向LL：width=MATCH_PARENT,height=0,weight=2
            if (com.mobile.novabox.util.PadUiHelper.isPad(mActivity)) {
                lp.weight = 72;
                lp.width = 0;
                lp.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
            } else {
                lp.weight = 2;
                lp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
                lp.height = 0;
            }
            playerContainer.setLayoutParams(lp);
            playerContainer.requestLayout();
        }
        // VideoView 重新布局后刷新画面，避免退出全屏后黑屏
        if (mVideoView != null) {
            mVideoView.requestLayout();
        }
        // 切换全屏按钮图标回"进入全屏"
        if (ivFullscreenBtn != null) {
            ivFullscreenBtn.setImageResource(R.drawable.icon_fullscreen);
        }
        // 退出全屏后恢复到设备默认方向（Pad=横屏，手机=竖屏）
        ((MainActivity) mActivity).enforceDeviceOrientation();
    }

    /** 窗口重新获焦(容器转发):横屏全屏时重申沉浸式(pad非全屏跳过) */
    public void onWindowFocusChangedCompat(boolean hasFocus) {
        boolean shouldImmersive = hasFocus && isLandscape() &&
            (!com.mobile.novabox.util.PadUiHelper.isPad(mActivity) || mIsPadFullscreen);
        if (shouldImmersive) {
            mActivity.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
        }
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation
            == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }


    private JsonObject catchup=null;
    private Boolean hasCatchup=false;
    private void initLiveObj(){
        catchup = null;
        hasCatchup = false;
        int position=ApiConfig.getLiveGroupIndex();
        JsonArray live_groups=Hawk.get(HawkConfig.LIVE_GROUP_LIST,new JsonArray());
        if (live_groups == null || live_groups.size() == 0 || position < 0 || position >= live_groups.size()) {
            return;
        }
        JsonObject livesOBJ = live_groups.get(position).getAsJsonObject();
        String type = livesOBJ.has("type")?livesOBJ.get("type").getAsString():"0";

        if(livesOBJ.has("catchup")){
            catchup = livesOBJ.getAsJsonObject("catchup");
            LOG.i("echo-catchup :"+ catchup.toString());
            hasCatchup=true;
        }
        if(type.equals("3")){
            String py_jar="";
            if(livesOBJ.has("jar")){
                py_jar=livesOBJ.has("jar")?livesOBJ.get("jar").getAsString():"";

            }else if(livesOBJ.has("api")){
                py_jar=livesOBJ.has("api")?livesOBJ.get("api").getAsString():"";
                String ext="";
                if(livesOBJ.has("ext") && (livesOBJ.get("ext").isJsonObject() || livesOBJ.get("ext").isJsonArray())){
                    ext=livesOBJ.get("ext").toString();
                }else {
                    ext= DefaultConfig.safeJsonString(livesOBJ, "ext", "");
                }
                LOG.i("echo-ext:"+ext);
                if(!ext.isEmpty())py_jar=py_jar+"?extend="+ext;
            }
            ApiConfig.get().setLiveJar(py_jar);
        }
    }

    private HashMap<String,String> liveWebHeader()
    {
        return Hawk.get(HawkConfig.LIVE_WEB_HEADER);
    }

    private HashMap<String, String> liveChannelHeader() {
        if (currentLiveChannelItem == null) return liveWebHeader();
        HashMap<String, String> header = new HashMap<>();
        HashMap<String, String> liveHeader = liveWebHeader();
        if (liveHeader != null) header.putAll(liveHeader);
        if (currentLiveChannelItem.getHeaders() != null) {
            header.putAll(currentLiveChannelItem.getHeaders());
        }
        if (!currentLiveChannelItem.getChannelFormat().isEmpty()) {
            header.put(ExoMediaSourceHelper.HEADER_FORMAT, currentLiveChannelItem.getChannelFormat());
        }
        if (header.isEmpty()) return null;
        return header;
    }

    private boolean currentChannelHasCatchup() {
        return currentLiveChannelItem != null && currentLiveChannelItem.hasCatchup();
    }

    private void showSwitchChannelSnapshot() {
        if (switchChannelSnapshotImage != null && mVideoView != null) {
            Bitmap bitmap = null;
            try {
                bitmap = mVideoView.doScreenShot();
            } catch (Throwable ignored) {
            }
            if (bitmap != null) {
                switchChannelSnapshotImage.setImageBitmap(bitmap);
                switchChannelSnapshotImage.setVisibility(View.VISIBLE);
            } else {
                switchChannelSnapshotImage.setImageBitmap(null);
                switchChannelSnapshotImage.setVisibility(View.GONE);
            }
        }
        if (switchChannelSnapshotOverlay != null) {
            switchChannelSnapshotOverlay.setVisibility(View.VISIBLE);
        }
    }

    private void hideSwitchChannelSnapshot() {
        if (switchChannelSnapshotOverlay != null) {
            switchChannelSnapshotOverlay.setVisibility(View.GONE);
        }
        if (switchChannelSnapshotImage != null) {
            switchChannelSnapshotImage.setImageBitmap(null);
            switchChannelSnapshotImage.setVisibility(View.GONE);
        }
    }

    private boolean playChannel(int channelGroupIndex, int liveChannelIndex, boolean changeSource) {
        // 换源(changeSource=true)时若 currentLiveChannelItem 为空(如频道列表被清空后异常时序下触发换源)，
        // 原先直接调用 currentLiveChannelItem.getSourceNum() 会抛 NullPointerException，这里先判空。
        if ((channelGroupIndex == currentChannelGroupIndex && liveChannelIndex == currentLiveChannelIndex && !changeSource)
                || (changeSource && currentLiveChannelItem != null && currentLiveChannelItem.getSourceNum() == 1)) {
            return true;
        }
        if (changeSource && currentLiveChannelItem == null) {
            // 换源但当前没有有效频道，直接返回，避免后续访问 currentLiveChannelItem 造成崩溃
            return false;
        }
        boolean showPreviousFrame = currentLiveChannelItem != null && mVideoView != null && mVideoView.isPlaying();
        triedLivePlayerTypes.clear();
        if (!changeSource) {
            currentChannelGroupIndex = channelGroupIndex;
            currentLiveChannelIndex = liveChannelIndex;
            currentLiveChannelItem = getLiveChannels(currentChannelGroupIndex).get(currentLiveChannelIndex);
            Hawk.put(HawkConfig.LIVE_CHANNEL, currentLiveChannelItem.getChannelName());
            livePlayerManager.getLiveChannelPlayer(mVideoView, currentLiveChannelItem.getChannelName());
        }

        channel_Name = currentLiveChannelItem;
        currentLiveLookBackIndex=-1;
        isSHIYI=false;
        isBack = false;
        if(hasCatchup || currentChannelHasCatchup() || currentLiveChannelItem.getUrl().contains("PLTV/") || currentLiveChannelItem.getUrl().contains("TVOD/")){
            currentLiveChannelItem.setinclude_back(true);
        }else {
            currentLiveChannelItem.setinclude_back(false);
        }
        updateRightTopChannelInfo();
        // 更新手机端UI
        updateMobileChannelName(currentLiveChannelItem.getChannelName());
        updateMobileSourceList();
        // 同步刷新主列表三列高亮
        liveChannelGroupAdapter.setSelectedGroupIndex(currentChannelGroupIndex);
        liveChannelItemAdapter.setSelectedChannelIndex(currentLiveChannelIndex);
        if (liveSourceAdapter != null && currentLiveChannelItem != null) {
            int srcIdx = currentLiveChannelItem.getSourceIndex();
            if (srcIdx >= 0 && srcIdx < liveSourceAdapter.getData().size()) {
                liveSourceAdapter.selectItem(srcIdx, true, false);
            }
        }
        ll_right_top_huikan.setVisibility(View.GONE);
        if(mVideoView!=null){
            if(liveChannelHeader()!=null)LOG.i("echo-"+liveChannelHeader().toString());
            if (showPreviousFrame) {
                showSwitchChannelSnapshot();
            } else {
                hideSwitchChannelSnapshot();
            }
            mVideoView.release();
            mVideoView.setUrl(currentLiveChannelItem.getUrl(),liveChannelHeader());
            mVideoView.start();
            showResolutionAfterChannelSwitch();
        }
        return true;
    }

    public void playPreSource() {
        if (!isCurrentLiveChannelValid()) return;
        currentLiveChannelItem.preSource();
        playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
    }

    public void playNextSource() {
        if (!isCurrentLiveChannelValid()) return;
        currentLiveChannelItem.nextSource();
        playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
    }



    private void initVideoView() {
        LiveController controller = new LiveController(mActivity);
        controller.setListener(new LiveController.LiveControlListener() {
            @Override
            public boolean singleTap() {
                toggleControlOverlay();
                return true;
            }


            @Override
            public void longPress() {
                if (isLandscape()) return; // 横屏全屏时不响应长按
                showMobileSettingsDialog();
            }

            @Override
            public void playStateChanged(int playState) {
                mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
                switch (playState) {
                    case VideoView.STATE_IDLE:
                        // 空闲状态：播放器处于空闲，尚未开始播放。一般不需要自动换源。
                    case VideoView.STATE_PAUSED:
                        // 暂停状态：播放被暂停，通常是用户操作，不触发自动换源
                        break;
                    case VideoView.STATE_PREPARED:
                        // 准备就绪：播放器已经加载好媒体数据，但尚未开始播放。
                    case VideoView.STATE_BUFFERED:
                    case VideoView.STATE_PLAYING:
                        // 播放状态：当播放器缓冲完成或正在正常播放时，表明当前源是可用的，
                        hideSwitchChannelSnapshot();
                        if (resolutionInfoPending) {
                            resolutionInfoRetryCount = 0;
                            mHandler.removeCallbacks(mUpdateResolutionInfoRun);
                            mHandler.post(mUpdateResolutionInfoRun);
                        }
                        currentLiveChangeSourceTimes = 0;
                        triedLivePlayerTypes.clear();
                        break;
                    case VideoView.STATE_ERROR:
                    case VideoView.STATE_PLAYBACK_COMPLETED:
                        // 错误或播放结束状态：播放器遇到错误或播放完毕时，
                        // 启动自动换源任务，等待3秒后尝试切换至备选源
                        hideSwitchChannelSnapshot();
                        mHandler.postDelayed(mConnectTimeoutChangeSourceRun, 3500);
                        break;
                    case VideoView.STATE_PREPARING:
                    case VideoView.STATE_BUFFERING:
                        // 正在准备或缓冲状态：表示当前源正在加载中
                        mHandler.postDelayed(mConnectTimeoutChangeSourceRun, (ApiConfig.getLiveConnectTimeoutIndex() + 1) * 5000L);
                        break;
                    default:
                        LOG.i("echo-Unexpected live_play state: " + playState);
                        break;
                }
            }

            @Override
            public void changeSource(int direction) {
                if (direction > 0)
                    playNextSource();
                else
                    playPreSource();
            }
        });
        controller.setCanChangePosition(false);
        controller.setEnableInNormal(true);
        controller.setGestureEnabled(true);
        controller.setDoubleTapTogglePlayEnabled(false);
        mVideoView.setVideoController(controller);
        mVideoView.setProgressManager(null);
    }

    private boolean switchLivePlayerAndReplay() {
        if (currentLiveChannelItem == null || mVideoView == null) {
            return false;
        }
        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
        // 网络原因访问不了播放地址(IO/超时/服务器不可达):切换播放内核无意义,直接换源/换频道
        if (mVideoView.getLastErrorType() == AbstractPlayer.PlayerEventListener.ERROR_TYPE_NETWORK) {
            LOG.i("echo-liveAutoRetry network error, skip player switch, change source");
            triedLivePlayerTypes.clear();
            return false;
        }
        mVideoView.release();
        // 按固定顺序 0→1→2→3 逐个尝试其余内核,每次失败切换下一个并重播当前源
        if (!livePlayerManager.switchLivePlayer(mVideoView, currentLiveChannelItem.getChannelName(), triedLivePlayerTypes)) {
            LOG.i("echo-liveAutoRetry all player types tried, change source");
            triedLivePlayerTypes.clear();
            return false;
        }
        LOG.i("echo-liveAutoRetry switch player and replay current url");
        mVideoView.setUrl(currentLiveChannelItem.getUrl(), liveChannelHeader());
        mVideoView.start();
        return true;
    }

    private Runnable mConnectTimeoutChangeSourceRun = new Runnable() {
        @Override
        public void run() {
            if (switchLivePlayerAndReplay()) {
                return;
            }
            currentLiveChangeSourceTimes++;
            if (currentLiveChannelItem.getSourceNum() == currentLiveChangeSourceTimes) {
                currentLiveChangeSourceTimes = 0;
                Integer[] groupChannelIndex = getNextChannel(Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false) ? -1 : 1);
                playChannel(groupChannelIndex[0], groupChannelIndex[1], false);
            } else {
                playNextSource();
            }
        }
    };

    private void initChannelGroupView() {
        mChannelGroupView.setHasFixedSize(true);
        mChannelGroupView.setLayoutManager(new LinearLayoutManager(this.mContext, 1, false));

        liveChannelGroupAdapter = new LiveChannelGroupAdapter();
        mChannelGroupView.setAdapter(liveChannelGroupAdapter);

        liveChannelGroupAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                selectChannelGroup(position, false, -1);
            }
        });
    }

    private void selectChannelGroup(int groupIndex, boolean focus, int liveChannelIndex) {
        if (focus) {
            liveChannelGroupAdapter.setFocusedGroupIndex(groupIndex);
            liveChannelItemAdapter.setFocusedChannelIndex(-1);
        }
        if ((groupIndex > -1 && groupIndex != liveChannelGroupAdapter.getSelectedGroupIndex()) || isNeedInputPassword(groupIndex)) {
            liveChannelGroupAdapter.setSelectedGroupIndex(groupIndex);
            if (isNeedInputPassword(groupIndex)) {
                showPasswordDialog(groupIndex, liveChannelIndex);
                return;
            }
            if (focus && liveChannelIndex < 0) {
                loadChannelGroupData(groupIndex);
            } else {
                loadChannelGroupDataAndPlay(groupIndex, liveChannelIndex);
            }
        }
    }

    private void initLiveChannelView() {
        // 先初始化 adapter（Pad 端也需要它），再判断 RecyclerView 是否存在
        liveChannelItemAdapter = new LiveChannelItemAdapter();
        if (mLiveChannelView == null) return; // Pad 布局中没有 mChannelGridView
        mLiveChannelView.setHasFixedSize(true);
        mLiveChannelView.setLayoutManager(new LinearLayoutManager(this.mContext, 1, false));
        mLiveChannelView.setAdapter(liveChannelItemAdapter);

        liveChannelItemAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                liveChannelItemAdapter.setSelectedChannelIndex(position);
                clickLiveChannel(position);
            }
        });
    }

    private void clickLiveChannel(int position) {
        playChannel(liveChannelGroupAdapter.getSelectedGroupIndex(), position, false);
    }

    private void initSettingGroupView() {
        mSettingGroupView.setHasFixedSize(true);
        mSettingGroupView.setLayoutManager(new LinearLayoutManager(this.mContext, 1, false));

        liveSettingGroupAdapter = new LiveSettingGroupAdapter();
        mSettingGroupView.setAdapter(liveSettingGroupAdapter);

        //手机/模拟器
        liveSettingGroupAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                selectVisibleSettingGroup(position, false);
            }
        });
    }

    private void selectVisibleSettingGroup(int position, boolean focus) {
        if (position < 0 || position >= liveSettingGroupAdapter.getData().size()) return;
        selectSettingGroup(liveSettingGroupAdapter.getData().get(position).getGroupIndex(), focus);
    }

    private void selectSettingGroup(int position, boolean focus) {
        if (focus) {
            liveSettingGroupAdapter.setFocusedGroupIndex(position);
            liveSettingItemAdapter.setFocusedItemIndex(-1);
        }
        if (position == liveSettingGroupAdapter.getSelectedGroupIndex() || position < 0 || position >= liveSettingGroupList.size())
            return;

        liveSettingGroupAdapter.setSelectedGroupIndex(position);
        liveSettingItemAdapter.setNewData(liveSettingGroupList.get(position).getLiveSettingItems());

        switch (position) {
            case 0:
                if (currentLiveChannelItem != null
                        && currentLiveChannelItem.getSourceIndex() >= 0
                        && currentLiveChannelItem.getSourceIndex() < liveSettingItemAdapter.getData().size()) {
                    liveSettingItemAdapter.selectItem(currentLiveChannelItem.getSourceIndex(), true, false);
                }
                break;
            case 1:
                liveSettingItemAdapter.selectItem(livePlayerManager.getLivePlayerScale(), true, true);
                break;
            case 2:
                liveSettingItemAdapter.selectItem(livePlayerManager.getLivePlayerType(), true, true);
                break;
            case 5:
                liveSettingItemAdapter.selectItem(getCurrentLiveApiHistoryIndex(), true, true);
                break;
        }
        int scrollToPosition = liveSettingItemAdapter.getSelectedItemIndex();
        if (scrollToPosition < 0) scrollToPosition = 0;
        mSettingItemView.scrollToPosition(scrollToPosition);
    }

    private void initSettingItemView() {
        mSettingItemView.setHasFixedSize(true);
        mSettingItemView.setLayoutManager(new LinearLayoutManager(this.mContext, 1, false));

        liveSettingItemAdapter = new LiveSettingItemAdapter();
        mSettingItemView.setAdapter(liveSettingItemAdapter);

        //手机/模拟器
        liveSettingItemAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                clickSettingItem(position);
            }
        });
    }

    // 手机端线路列表适配器
    private void initMobileUI() {
        // 线路列表
        mLiveSourceView = findViewById(R.id.mLiveSourceView);
        mLiveSourceView.setHasFixedSize(true);
        mLiveSourceView.setLayoutManager(new LinearLayoutManager(this.mContext, LinearLayoutManager.VERTICAL, false));
        liveSourceAdapter = new LiveSettingItemAdapter();
        mLiveSourceView.setAdapter(liveSourceAdapter);
        liveSourceAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                if (currentLiveChannelItem != null && position >= 0 && position < currentLiveChannelItem.getSourceNum()) {
                    liveSourceAdapter.selectItem(position, true, false);
                    playChannelBySource(position);
                }
            }
        });

        // 信息栏 - 频道名
        tvMobileChannelName = findViewById(R.id.tv_mobile_channel_name);

        // 设置按钮
        ivSettingsBtn = findViewById(R.id.iv_settings_btn);
        liveSettingsDialogOverlay = findViewById(R.id.live_settings_dialog_overlay);
        tvSettingsClose = findViewById(R.id.tv_settings_close);

        ivSettingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMobileSettingsDialog();
            }
        });

        // 底部/左侧导航栏在容器 MainActivity 层;全屏显隐经 setNavVisible 控制

        tvSettingsClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideMobileSettingsDialog();
            }
        });

        liveSettingsDialogOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideMobileSettingsDialog();
            }
        });
        // 防止点击弹窗内容关闭弹窗
        try {
            View dialogContent = ((android.view.ViewGroup) liveSettingsDialogOverlay).getChildAt(0);
            if (dialogContent != null) {
                dialogContent.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // 拦截点击，不关闭弹窗
                    }
                });
            }
        } catch (Exception e) { /* ignore */ }

        // 播放控制浮层
        liveControlOverlay = findViewById(R.id.live_control_overlay);
        ivBackBtn = findViewById(R.id.iv_back_btn);
        ivRefreshBtn = findViewById(R.id.iv_refresh_btn);
        ivFullscreenBtn = findViewById(R.id.iv_fullscreen_btn);
        // iv_overlay_settings_btn 已从布局移除，此处跳过
        ivPlayPauseCenter = findViewById(R.id.iv_play_pause_center);

        ivBackBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isLandscape() || mIsPortraitFullscreen) {
                    // 全屏时（横屏或竖屏全屏）退出全屏
                    exitFullscreenMode();
                } else {
                    // 等效原 finish():回到首页 Tab
                    ((MainActivity) mActivity).switchToTab(MainNavBar.TAB_HOME, true);
                }
            }
        });

        ivRefreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentLiveChannelItem != null) {
                    hideControlOverlay();
                    refreshChannel();
                }
            }
        });

        ivFullscreenBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isPad = com.mobile.novabox.util.PadUiHelper.isPad(mActivity);
                // 已在全屏：点击退出全屏
                // 平板：需要 mIsPadFullscreen=true 才算全屏（平板常态就是横屏，不能单靠 isLandscape 判断）
                // 手机：isLandscape 或 mIsPortraitFullscreen 即为全屏
                boolean alreadyFullscreen = isPad
                        ? mIsPadFullscreen
                        : (isLandscape() || mIsPortraitFullscreen);
                if (alreadyFullscreen) {
                    exitFullscreenMode();
                    hideControlOverlay();
                    return;
                }
                // 未全屏：进入全屏
                if (isPad) {
                    enterFullscreenMode();
                } else {
                    int _mode = com.mobile.novabox.util.OrientationHelper.getMode();
                    if (_mode == com.mobile.novabox.util.OrientationHelper.MODE_PORT) {
                        // 竖屏策略：不旋转，直接展开竖屏全屏UI
                        mIsPortraitFullscreen = true;
                        enterFullscreenMode();
                    } else if (_mode == com.mobile.novabox.util.OrientationHelper.MODE_SENSOR) {
                        // 传感器策略：先直接展开全屏UI，再解锁传感器
                        mIsPortraitFullscreen = true;
                        enterFullscreenMode();
                        mActivity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                    } else {
                        // 横屏/自动策略：旋转横屏，等 onOrientationChanged 触发 enterFullscreenMode
                        mActivity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                    }
                }
                hideControlOverlay();
            }
        });

        // 设置按钮已从视频控制浮层移除

        ivPlayPauseCenter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mVideoView.isPlaying()) {
                    mVideoView.pause();
                    ivPlayPauseCenter.setImageResource(R.drawable.icon_play);
                } else {
                    mVideoView.start();
                    ivPlayPauseCenter.setImageResource(R.drawable.icon_pause);
                }
                scheduleHideControlOverlay();
            }
        });

        controlOverlayTimer = new android.os.CountDownTimer(4000, 4000) {
            @Override
            public void onTick(long millisUntilFinished) {}
            @Override
            public void onFinish() {
                hideControlOverlay();
            }
        };
    }

    private void toggleControlOverlay() {
        if (liveControlOverlay == null) return;
        if (liveControlOverlay.getVisibility() == View.VISIBLE) {
            hideControlOverlay();
        } else {
            showControlOverlay();
        }
    }

    private void showControlOverlay() {
        if (liveControlOverlay == null) return;
        // 竖屏小屏时隐藏返回按钮，横屏全屏时显示
        if (ivBackBtn != null) {
            ivBackBtn.setVisibility((isLandscape() || mIsPortraitFullscreen) ? View.VISIBLE : View.GONE);
        }
        liveControlOverlay.setVisibility(View.VISIBLE);
        scheduleHideControlOverlay();
    }

    private void hideControlOverlay() {
        if (liveControlOverlay == null) return;
        liveControlOverlay.setVisibility(View.GONE);
        if (controlOverlayTimer != null) controlOverlayTimer.cancel();
    }

    private void scheduleHideControlOverlay() {
        if (controlOverlayTimer != null) {
            controlOverlayTimer.cancel();
            controlOverlayTimer.start();
        }
    }

    private void showMobileSettingsDialog() {
        if (liveSettingsDialogOverlay == null) return;
        ApiConfig.get().refreshLiveApiHistoryItems();
        loadCurrentSourceList();
        // 只展示非"线路选择"的设置分组
        ArrayList<LiveSettingGroup> visibleGroups = new ArrayList<>();
        for (LiveSettingGroup group : liveSettingGroupList) {
            if (group == null) continue;
            if (group.getGroupIndex() == 0) continue; // 跳过线路选择
            if (!group.getLiveSettingItems().isEmpty()) visibleGroups.add(group);
        }
        liveSettingGroupAdapter.setNewData(visibleGroups);
        // 默认选择第一个可见分组
        if (!visibleGroups.isEmpty()) {
            selectSettingGroup(visibleGroups.get(0).getGroupIndex(), false);
        }
        // 横屏时弹窗更宽、列表区高度更小
        try {
            android.view.ViewGroup dialogContent = (android.view.ViewGroup)
                ((android.view.ViewGroup) liveSettingsDialogOverlay).getChildAt(0);
            if (dialogContent != null) {
                android.view.ViewGroup.LayoutParams lp = dialogContent.getLayoutParams();
                float density = getResources().getDisplayMetrics().density;
                if (isLandscape()) {
                    lp.width = (int) (480 * density);
                } else {
                    lp.width = (int) (320 * density);
                }
                dialogContent.setLayoutParams(lp);
                // 调整列表区高度:容纳 6 个分组项 + 6 个选项,每项 ~45dp,合计 ~290dp;加 30dp 余量 = 320dp。
                View listArea = dialogContent.getChildAt(2); // 分割线下方的列表LinearLayout
                if (listArea != null) {
                    android.view.ViewGroup.LayoutParams llp = listArea.getLayoutParams();
                    llp.height = (int) ((isLandscape() ? 220 : 320) * density);
                    listArea.setLayoutParams(llp);
                }
            }
        } catch (Exception e) { /* ignore */ }
        liveSettingsDialogOverlay.setVisibility(View.VISIBLE);
    }

    private void hideMobileSettingsDialog() {
        if (liveSettingsDialogOverlay == null) return;
        liveSettingsDialogOverlay.setVisibility(View.GONE);
    }

    // 更新手机端线路列表
    private void updateMobileSourceList() {
        if (liveSourceAdapter == null || mLiveSourceView == null) return;
        ArrayList<LiveSettingItem> sourceItems = new ArrayList<>();
        if (currentLiveChannelItem != null && currentLiveChannelItem.getChannelSourceNames() != null) {
            ArrayList<String> names = currentLiveChannelItem.getChannelSourceNames();
            for (int i = 0; i < names.size(); i++) {
                LiveSettingItem item = new LiveSettingItem();
                item.setItemIndex(i);
                item.setItemName(names.get(i));
                sourceItems.add(item);
            }
        }
        liveSourceAdapter.setNewData(sourceItems);
        if (currentLiveChannelItem != null) {
            int idx = currentLiveChannelItem.getSourceIndex();
            if (idx >= 0 && idx < sourceItems.size()) {
                liveSourceAdapter.selectItem(idx, true, false);
            }
        }
    }

    // 切换到指定线路播放
    private void playChannelBySource(int sourceIndex) {
        if (currentLiveChannelItem == null) return;
        currentLiveChannelItem.setSourceIndex(sourceIndex);
        playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
    }

    // 刷新当前频道
    private void refreshChannel() {
        if (currentLiveChannelItem != null) {
            playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
        }
    }

    // 更新手机端频道名显示
    private void updateMobileChannelName(String name) {
        if (tvMobileChannelName != null) {
            tvMobileChannelName.setText(name != null ? name : "请选择频道");
        }
    }

    private void clickSettingItem(int position) {
        int settingGroupIndex = liveSettingGroupAdapter.getSelectedGroupIndex();
        if (settingGroupIndex >= 0 && settingGroupIndex < 3 && !isCurrentLiveChannelValid()) {
            return;
        }
        if (settingGroupIndex < 4) {
            if (position == liveSettingItemAdapter.getSelectedItemIndex())
                return;
            liveSettingItemAdapter.selectItem(position, true, true);
        }
        switch (settingGroupIndex) {
            case 0://线路切换
                if (position < 0 || position >= currentLiveChannelItem.getSourceNum()) break;
                currentLiveChannelItem.setSourceIndex(position);
                playChannel(currentChannelGroupIndex, currentLiveChannelIndex,true);
                break;
            case 1://画面比例
                livePlayerManager.changeLivePlayerScale(mVideoView, position, currentLiveChannelItem.getChannelName());
                break;
            case 2://播放解码
                mVideoView.release();
                livePlayerManager.changeLivePlayerType(mVideoView, position, currentLiveChannelItem.getChannelName());
                mVideoView.setUrl(currentLiveChannelItem.getUrl(),liveChannelHeader());
                mVideoView.start();
                break;
            case 3://超时换源
                Hawk.put(HawkConfig.LIVE_CONNECT_TIMEOUT, position);
                break;
            case 4://偏好设置
                boolean select = false;
                switch (position) {
                    case 0:
                        select = !Hawk.get(HawkConfig.LIVE_SHOW_TIME, false);
                        Hawk.put(HawkConfig.LIVE_SHOW_TIME, select);
                        showTime();
                        break;
                    case 1:
                        select = !Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false);
                        Hawk.put(HawkConfig.LIVE_SHOW_NET_SPEED, select);
                        showNetSpeed();
                        break;
                    case 2:
                        select = !Hawk.get(HawkConfig.LIVE_SHOW_RESOLUTION, false);
                        Hawk.put(HawkConfig.LIVE_SHOW_RESOLUTION, select);
                        showResolutionSetting();
                        break;
                    case 3:
                        select = !Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false);
                        Hawk.put(HawkConfig.LIVE_CHANNEL_REVERSE, select);
                        break;
                    case 4:
                        select = !Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false);
                        Hawk.put(HawkConfig.LIVE_CROSS_GROUP, select);
                        break;
                }
                liveSettingItemAdapter.selectItem(position, select, false);
                break;
            case 5://多源切换（直播地址列表在上 + 线路选择 lives 数组在下）
                List<LiveSettingItem> multiItems = liveSettingGroupList.get(5).getLiveSettingItems();
                if (position < 0 || position >= multiItems.size()) break;
                LiveSettingItem multiItem = multiItems.get(position);
                if (multiItem.getItemGroup() == 1) {
                    // 线路选择来源：加载点播 JSON 内嵌 lives 数组中的该项
                    JsonArray liveGroups = Hawk.get(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
                    int livesIndex = multiItem.getItemSourceIndex();
                    if (liveGroups == null || livesIndex < 0 || livesIndex >= liveGroups.size()) break;
                    String currentLiveApiUrlL = Hawk.get(HawkConfig.LIVE_API_URL, "");
                    if (currentLiveApiUrlL.isEmpty() && livesIndex == ApiConfig.getLiveGroupIndex()) {
                        liveSettingItemAdapter.selectItem(position, true, true);
                        break;
                    }
                    String currentChannelNameL = getPreferredLiveRefreshChannelName();
                    int currentSourceIndexL = getPreferredLiveRefreshSourceIndex();
                    liveSettingItemAdapter.selectItem(position, true, true);
                    // 切换到点播 lives 源：清空独立直播地址，保证高亮与回切正确
                    Hawk.put(HawkConfig.LIVE_API_URL, "");
                    ApiConfig.setLiveGroupIndex(livesIndex);
                    JsonObject livesOBJ = liveGroups.get(livesIndex).getAsJsonObject();
                    ApiConfig.get().loadLiveApi(livesOBJ);
                    if (ApiConfig.get().getChannelGroupList().isEmpty()) {
                        if (mVideoView != null) mVideoView.release();
                        setEmptyLiveChannelList(false);
                    } else {
                        refreshLiveChannelListAndPlay(currentChannelNameL, currentSourceIndexL);
                    }
                    hideMobileSettingsDialog();
                    break;
                }
                // 直播地址来源：切换为独立直播源
                String liveUrl = multiItem.getItemUrl();
                if (liveUrl.isEmpty()) break;
                String oldLiveApiM = Hawk.get(HawkConfig.LIVE_API_URL, "");
                if (liveUrl.equals(oldLiveApiM)) {
                    liveSettingItemAdapter.selectItem(position, true, true);
                    break;
                }
                String currentChannelNameM = getPreferredLiveRefreshChannelName();
                int currentSourceIndexM = getPreferredLiveRefreshSourceIndex();
                liveSettingItemAdapter.selectItem(position, true, true);
                Hawk.put(HawkConfig.LIVE_API_URL, liveUrl);
                ApiConfig.get().refreshLiveApiHistoryItems();
                ApiConfig.get().loadLiveConfig(false, new ApiConfig.LoadConfigCallback() {
                    @Override
                    public void success() {
                        mHandler.post(() -> refreshLiveChannelListAndPlay(currentChannelNameM, currentSourceIndexM));
                    }
                    @Override
                    public void error(String msg) {
                        mHandler.post(() -> {
                            if (mVideoView != null) mVideoView.release();
                            setEmptyLiveChannelList(false);
                            Toast.makeText(mActivity, msg, Toast.LENGTH_SHORT).show();
                        });
                    }
                    @Override
                    public void notice(String msg) {
                        mHandler.post(() -> Toast.makeText(mActivity, msg, Toast.LENGTH_SHORT).show());
                    }
                });
                // 多源切换：立即关闭弹窗
                hideMobileSettingsDialog();
                break;
        }
    }

    private String getPreferredLiveRefreshChannelName() {
        if (currentLiveChannelItem != null) return currentLiveChannelItem.getChannelName();
        return Hawk.get(HawkConfig.LIVE_CHANNEL, "");
    }

    private int getPreferredLiveRefreshSourceIndex() {
        if (currentLiveChannelItem != null) return currentLiveChannelItem.getSourceIndex();
        return -1;
    }

    private void refreshLiveChannelListAndPlay(String channelName, int sourceIndex) {
        refreshingLiveChannelList = true;
        pendingLiveRefreshChannelName = channelName;
        pendingLiveRefreshSourceIndex = sourceIndex;
        currentLiveLookBackIndex = -1;
        currentLiveChangeSourceTimes = 0;
        triedLivePlayerTypes.clear();
        channelGroupPasswordConfirmed.clear();
        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
        hideSwitchChannelSnapshot();
        if (liveChannelGroupAdapter != null) {
            liveChannelGroupAdapter.setFocusedGroupIndex(-1);
            liveChannelGroupAdapter.setSelectedGroupIndex(-1);
        }
        if (liveChannelItemAdapter != null) {
            liveChannelItemAdapter.setFocusedChannelIndex(-1);
            liveChannelItemAdapter.setSelectedChannelIndex(-1);
            liveChannelItemAdapter.setNewData(new ArrayList<LiveChannelItem>());
        }
        initLiveChannelList();
        initLiveSettingGroupList();
    }

    private int getCurrentLiveApiHistoryIndex() {
        if (liveSettingGroupList.size() < 6) return -1;
        String current = Hawk.get(HawkConfig.LIVE_API_URL, "");
        boolean usingVodConfigLives = current.isEmpty();
        int currentLiveGroupIndex = ApiConfig.getLiveGroupIndex();
        List<LiveSettingItem> items = liveSettingGroupList.get(5).getLiveSettingItems();
        for (int i = 0; i < items.size(); i++) {
            LiveSettingItem item = items.get(i);
            if (item.getItemGroup() == 1) {
                // 线路选择(lives)来源：仅在未启用独立直播源时按 live_group_index 匹配
                if (usingVodConfigLives && currentLiveGroupIndex == item.getItemSourceIndex()) return i;
            } else {
                // 直播地址来源：按 LIVE_API_URL 匹配
                String url = item.getItemUrl();
                if (url.isEmpty()) url = item.getItemName();
                if (!current.isEmpty() && current.equals(url)) return i;
            }
        }
        return -1;
    }

    private void initLiveChannelList() {
        // 检查是否配置了直播源
        String liveApiUrl = Hawk.get(HawkConfig.LIVE_API_URL, "");
        List<LiveChannelGroup> list = ApiConfig.get().getChannelGroupList();
        if (liveApiUrl.isEmpty() && list.isEmpty()) {
            // 未配置独立直播源：优先使用点播 JSON 内嵌 lives 数组
            JsonArray livesGroups = Hawk.get(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
            if (livesGroups == null || livesGroups.size() == 0) {
                // 点播 JSON 也未提供 lives 数组，友好提示用户
                showNoLiveSourceTip();
                return;
            }
            int liveGroupIndex = ApiConfig.getLiveGroupIndex();
            if (liveGroupIndex < 0 || liveGroupIndex >= livesGroups.size()) liveGroupIndex = 0;
            JsonObject livesOBJ = livesGroups.get(liveGroupIndex).getAsJsonObject();
            ApiConfig.get().loadLiveApi(livesOBJ);
            list = ApiConfig.get().getChannelGroupList();
        }
        if (list.isEmpty()) {
            // 有配置但频道列表为空（首次进入或未加载）：主动请求直播源
            if (!refreshingLiveChannelList) showLoading();
            ApiConfig.get().loadLiveConfig(false, new ApiConfig.LoadConfigCallback() {
                @Override
                public void success() {
                    mHandler.post(() -> {
                        List<LiveChannelGroup> loaded = ApiConfig.get().getChannelGroupList();
                        if (loaded.isEmpty()) {
                            setEmptyLiveChannelList(false);
                            return;
                        }
                        initLiveObj();
                        if (loaded.size() == 1 && loaded.get(0).getGroupName().startsWith("http://127.0.0.1")) {
                            loadProxyLives(loaded.get(0).getGroupName());
                        } else {
                            liveChannelGroupList.clear();
                            liveChannelGroupList.addAll(loaded);
                            showSuccess();
                            initLiveState();
                        }
                    });
                }
                @Override
                public void error(String msg) {
                    mHandler.post(() -> {
                        setEmptyLiveChannelList(false);
                        if (msg != null && !msg.equals("-1")) {
                            Toast.makeText(mActivity, msg, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                @Override
                public void notice(String msg) {
                    mHandler.post(() -> Toast.makeText(mActivity, msg, Toast.LENGTH_SHORT).show());
                }
            });
            return;
        }
        initLiveObj();
        if (list.size() == 1 && list.get(0).getGroupName().startsWith("http://127.0.0.1")) {
            loadProxyLives(list.get(0).getGroupName());
        } else {
            liveChannelGroupList.clear();
            liveChannelGroupList.addAll(list);
            showSuccess();
            initLiveState();
        }
    }

    public void loadProxyLives(String url) {
        try {
            Uri parsedUrl = Uri.parse(url);
            url = new String(Base64.decode(parsedUrl.getQueryParameter("ext"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8");
        } catch (Throwable th) {
            if (!url.startsWith("http://127.0.0.1")) {
                setEmptyLiveChannelList();
                return;
            }
        }
        if (!isValidLiveProxyUrl(url)) {
            setEmptyLiveChannelList();
            return;
        }
        if (!refreshingLiveChannelList) {
            showLoading();
        }

        LOG.i("echo-live-url:"+url);

        if(url.contains(".py") || url.contains(".js")){
            if ((url.contains(".py") || url.contains(".js")) && !hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                // 权限不足时，直接设置默认播放列表
                Toast.makeText(App.getInstance(), "该源需要存储权限", Toast.LENGTH_SHORT).show();
                setEmptyLiveChannelList();
                return;
            }
            String finalUrl = url;
            Runnable waitResponse = new Runnable() {
                @Override
                public void run() {
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    Future<String> future = executor.submit(new Callable<String>() {
                        @Override
                        public String call() {
                            Spider sp = ApiConfig.get().getLiveCSP(finalUrl);
                            String json=sp.liveContent(finalUrl);
                            return json;
                        }
                    });
                    String sortJson = null;
                    try {
                        sortJson = future.get(ApiConfig.get().getLiveConnectTimeoutSeconds(), TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        e.printStackTrace();
                        future.cancel(true);
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    } finally {
                        if (sortJson==null || sortJson.isEmpty()) {
                            // 频道列表为空时，使用默认播放列表
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    setEmptyLiveChannelList();
                                }
                            });
                            return;
                        }
                        JsonArray livesArray = TxtSubscribe.parseToJsonArray(sortJson);

                        ApiConfig.get().loadLives(livesArray);
                        List<LiveChannelGroup> list = ApiConfig.get().getChannelGroupList();
                        if (list.isEmpty()) {
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    setEmptyLiveChannelList();
                                }
                            });
                            return;
                        }
                        liveChannelGroupList.clear();
                        liveChannelGroupList.addAll(list);

                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                LiveFragment.this.showSuccess();
                                initLiveState();
                            }
                        });
                        try {
                            executor.shutdown();
                        } catch (Throwable th) {
                            th.printStackTrace();
                        }
                    }
                }
            };
            Executors.newSingleThreadExecutor().execute(waitResponse);
        }else {
            OkGo.<String>get(url).execute(new AbsCallback<String>() {

                @Override
                public String convertResponse(okhttp3.Response response) throws Throwable {
                    assert response.body() != null;
                    return response.body().string();
                }

                @Override
                public void onSuccess(Response<String> response) {
                    JsonArray livesArray = TxtSubscribe.parseToJsonArray(response.body());

                    ApiConfig.get().loadLives(livesArray);
                    List<LiveChannelGroup> list = ApiConfig.get().getChannelGroupList();
                    if (list.isEmpty()) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                setEmptyLiveChannelList();
                            }
                        });
                        return;
                    }
                    liveChannelGroupList.clear();
                    liveChannelGroupList.addAll(list);

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            LiveFragment.this.showSuccess();
                            initLiveState();
                        }
                    });
                }

                @Override
                public void onError(Response<String> response) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            setEmptyLiveChannelList();
                        }
                    });
                }
            });
        }
    }

    private boolean isValidLiveProxyUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String lowerUrl = url.trim().toLowerCase(Locale.US);
        return lowerUrl.startsWith("http://")
                || lowerUrl.startsWith("https://")
                || lowerUrl.startsWith("rtsp://")
                || lowerUrl.startsWith("rtmp://")
                || lowerUrl.startsWith("rtp://");
    }

    private void initLiveState() {
        refreshingLiveChannelList = false;
        String lastChannelName = pendingLiveRefreshChannelName == null ? Hawk.get(HawkConfig.LIVE_CHANNEL, "") : pendingLiveRefreshChannelName;
        int sourceIndex = pendingLiveRefreshSourceIndex;
        pendingLiveRefreshChannelName = null;
        pendingLiveRefreshSourceIndex = -1;

        int lastChannelGroupIndex = -1;
        int lastLiveChannelIndex = -1;
        LiveChannelItem lastLiveChannelItem = null;
        for (LiveChannelGroup liveChannelGroup : liveChannelGroupList) {
            ArrayList<LiveChannelItem> groupChannels = liveChannelGroup.getLiveChannels();
            if (groupChannels == null || groupChannels.isEmpty()) {
                continue;
            }
            for (LiveChannelItem liveChannelItem : groupChannels) {
                if (liveChannelItem.getChannelName().equals(lastChannelName)) {
                    lastChannelGroupIndex = liveChannelGroup.getGroupIndex();
                    lastLiveChannelIndex = liveChannelItem.getChannelIndex();
                    lastLiveChannelItem = liveChannelItem;
                    break;
                }
            }
            if (lastChannelGroupIndex != -1) break;
        }
        if (lastChannelGroupIndex == -1) {
            lastChannelGroupIndex = getFirstNoPasswordChannelGroup();
            if (lastChannelGroupIndex == -1)
                lastChannelGroupIndex = 0;
            lastLiveChannelIndex = 0;
        }
        if (lastLiveChannelItem != null && sourceIndex >= 0 && lastLiveChannelItem.getSourceNum() > 0) {
            lastLiveChannelItem.setSourceIndex(Math.min(sourceIndex, lastLiveChannelItem.getSourceNum() - 1));
        }

        livePlayerManager.init(mVideoView);
        showTime();
        showNetSpeed();

        liveChannelGroupAdapter.setNewData(liveChannelGroupList);
        currentLiveChannelIndex = -1;
        selectChannelGroup(lastChannelGroupIndex, false, lastLiveChannelIndex);

        // 初次进入直接显示三列列表，高亮当前分组/频道/源
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                refreshAndShowChannelListWithHighlight();
            }
        }, 120);
    }

    /**
     * 刷新三列列表（分组、频道、源）并高亮当前播放项，立即显示不做动画。
     */
    private void refreshAndShowChannelListWithHighlight() {
        if (liveChannelGroupList.isEmpty()) return;
        // 刷新频道列表数据
        List<LiveChannelItem> channels = getLiveChannels(currentChannelGroupIndex);
        liveChannelItemAdapter.setNewData(channels);
        // 高亮分组
        liveChannelGroupAdapter.setSelectedGroupIndex(currentChannelGroupIndex);
        liveChannelGroupAdapter.setFocusedGroupIndex(-1);
        // 高亮频道
        liveChannelItemAdapter.setSelectedChannelIndex(currentLiveChannelIndex);
        liveChannelItemAdapter.setFocusedChannelIndex(-1);
        // 刷新源列表
        loadCurrentSourceList();
        updateMobileSourceList();
        // 高亮源
        if (currentLiveChannelItem != null) {
            int srcIdx = currentLiveChannelItem.getSourceIndex();
            if (srcIdx >= 0 && liveSourceAdapter != null && srcIdx < liveSourceAdapter.getData().size()) {
                liveSourceAdapter.selectItem(srcIdx, true, false);
            }
        }
        // 滚动定位
        if (currentChannelGroupIndex >= 0)
            mChannelGroupView.scrollToPosition(currentChannelGroupIndex);
        if (currentLiveChannelIndex >= 0 && mLiveChannelView != null)
            mLiveChannelView.scrollToPosition(currentLiveChannelIndex);
    }


    private void initLiveSettingGroupList() {
        liveSettingGroupList=ApiConfig.get().getLiveSettingGroupList();
        if (liveSettingGroupList.size() < 6) return;
        liveSettingGroupList.get(3).getLiveSettingItems().get(ApiConfig.getLiveConnectTimeoutIndex()).setItemSelected(true);
        liveSettingGroupList.get(4).getLiveSettingItems().get(0).setItemSelected(Hawk.get(HawkConfig.LIVE_SHOW_TIME, false));
        liveSettingGroupList.get(4).getLiveSettingItems().get(1).setItemSelected(Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false));
        liveSettingGroupList.get(4).getLiveSettingItems().get(2).setItemSelected(Hawk.get(HawkConfig.LIVE_SHOW_RESOLUTION, false));
        liveSettingGroupList.get(4).getLiveSettingItems().get(3).setItemSelected(Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false));
        liveSettingGroupList.get(4).getLiveSettingItems().get(4).setItemSelected(Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false));
        // 多源切换（group[5]）：直播地址来源按 LIVE_API_URL 标记；线路选择来源按 live_group_index 标记
        String currentLiveApiUrl = Hawk.get(HawkConfig.LIVE_API_URL, "");
        boolean usingVodConfigLives = currentLiveApiUrl.isEmpty();
        int currentLiveGroupIndex = ApiConfig.getLiveGroupIndex();
        List<LiveSettingItem> multiSettingItems = liveSettingGroupList.get(5).getLiveSettingItems();
        for (int i = 0; i < multiSettingItems.size(); i++) {
            LiveSettingItem item = multiSettingItems.get(i);
            if (item.getItemGroup() == 1) {
                // 线路选择(lives)来源：仅在未启用独立直播源时按 live_group_index 匹配
                item.setItemSelected(usingVodConfigLives && currentLiveGroupIndex == item.getItemSourceIndex());
            } else {
                // 直播地址来源：按 LIVE_API_URL 标记
                String itemUrl = item.getItemUrl();
                item.setItemSelected(!currentLiveApiUrl.isEmpty() && currentLiveApiUrl.equals(itemUrl));
            }
        }
    }

    private void loadCurrentSourceList() {
        ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
        if (currentLiveChannelItem != null && currentLiveChannelItem.getChannelSourceNames() != null) {
            ArrayList<String> currentSourceNames = currentLiveChannelItem.getChannelSourceNames();
            for (int j = 0; j < currentSourceNames.size(); j++) {
                LiveSettingItem liveSettingItem = new LiveSettingItem();
                liveSettingItem.setItemIndex(j);
                liveSettingItem.setItemName(currentSourceNames.get(j));
                liveSettingItemList.add(liveSettingItem);
            }
        }
        liveSettingGroupList.get(0).setLiveSettingItems(liveSettingItemList);
    }

    private void showResolutionAfterChannelSwitch() {
        resolutionInfoPending = true;
        resolutionInfoRetryCount = 0;
        if (tvResolution != null) {
            tvResolution.setText("");
            tvResolution.setVisibility(View.GONE);
        }
        mHandler.removeCallbacks(mHideResolutionInfoRun);
        mHandler.removeCallbacks(mUpdateResolutionInfoRun);
        mHandler.postDelayed(mUpdateResolutionInfoRun, RESOLUTION_INFO_RETRY_DELAY);
    }

    private void showResolutionSetting() {
        mHandler.removeCallbacks(mHideResolutionInfoRun);
        mHandler.removeCallbacks(mUpdateResolutionInfoRun);
        if (Hawk.get(HawkConfig.LIVE_SHOW_RESOLUTION, false)) {
            resolutionInfoPending = true;
            resolutionInfoRetryCount = 0;
            if (tvResolution != null) {
                tvResolution.setVisibility(View.GONE);
                mHandler.postDelayed(mUpdateResolutionInfoRun, RESOLUTION_INFO_RETRY_DELAY);
            }
        } else {
            showResolutionAfterChannelSwitch();
        }
    }

    private final Runnable mHideResolutionInfoRun = new Runnable() {
        @Override
        public void run() {
            if (tvResolution != null) {
                tvResolution.setVisibility(View.GONE);
            }
        }
    };

    private final Runnable mUpdateResolutionInfoRun = new Runnable() {
        @Override
        public void run() {
            if (tvResolution == null || mVideoView == null) {
                return;
            }
            if (mVideoView.getCurrentPlayState() != VideoView.STATE_PREPARED
                    && mVideoView.getCurrentPlayState() != VideoView.STATE_BUFFERED
                    && mVideoView.getCurrentPlayState() != VideoView.STATE_PLAYING) {
                retryOrHideResolutionInfo();
                return;
            }
            int[] videoSize = mVideoView.getVideoSize();
            if (videoSize != null && videoSize.length >= 2 && videoSize[0] > 0 && videoSize[1] > 0) {
                updateResolutionText(videoSize[0], videoSize[1]);
                return;
            }
            retryOrHideResolutionInfo();
        }
    };

    private void updateResolutionText(int width, int height) {
        resolutionInfoPending = false;
        tvResolution.setText("[ " + width + "x" + height + " ]");
        tvResolution.setVisibility(View.VISIBLE);
        mHandler.removeCallbacks(mHideResolutionInfoRun);
        if (!Hawk.get(HawkConfig.LIVE_SHOW_RESOLUTION, false)) {
            mHandler.postDelayed(mHideResolutionInfoRun, RESOLUTION_INFO_HIDE_DELAY);
        }
    }

    private void retryOrHideResolutionInfo() {
        if (resolutionInfoPending && resolutionInfoRetryCount++ < RESOLUTION_INFO_MAX_RETRY) {
            mHandler.postDelayed(mUpdateResolutionInfoRun, RESOLUTION_INFO_RETRY_DELAY);
        } else {
            tvResolution.setVisibility(View.GONE);
        }
    }

    void showTime() {
        if (Hawk.get(HawkConfig.LIVE_SHOW_TIME, false)) {
            mHandler.post(mUpdateTimeRun);
            tvTime.setVisibility(View.VISIBLE);
        } else {
            mHandler.removeCallbacks(mUpdateTimeRun);
            tvTime.setVisibility(View.GONE);
        }
    }

    private Runnable mUpdateTimeRun = new Runnable() {
        @Override
        public void run() {
            Date day=new Date();
            @SuppressLint("SimpleDateFormat") SimpleDateFormat df = new SimpleDateFormat("hh:mm a");
            tvTime.setText(df.format(day));
            mHandler.postDelayed(this, 1000);
        }
    };

    private void showNetSpeed() {
        if (Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false)) {
            mHandler.post(mUpdateNetSpeedRun);
            tvNetSpeed.setVisibility(View.VISIBLE);
        } else {
            mHandler.removeCallbacks(mUpdateNetSpeedRun);
            tvNetSpeed.setVisibility(View.GONE);
        }
    }

    private Runnable mUpdateNetSpeedRun = new Runnable() {
        @Override
        public void run() {
            if (mVideoView == null) return;
            String speed = PlayerHelper.getDisplaySpeedBps(mVideoView.getTcpSpeed(), true);
            tvNetSpeed.setText(speed);
            mHandler.postDelayed(this, 1000);
        }
    };

    private void showPasswordDialog(int groupIndex, int liveChannelIndex) {
        LivePasswordDialog dialog = new LivePasswordDialog(mActivity);
        dialog.setOnListener(new LivePasswordDialog.OnListener() {
            @Override
            public void onChange(String password) {
                if (password.equals(liveChannelGroupList.get(groupIndex).getGroupPassword())) {
                    channelGroupPasswordConfirmed.add(groupIndex);
                    loadChannelGroupDataAndPlay(groupIndex, liveChannelIndex);
                } else {
                    Toast.makeText(App.getInstance(), "密码错误", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancel() {
            }
        });
        dialog.show();
    }

    private void loadChannelGroupDataAndPlay(int groupIndex, int liveChannelIndex) {
        loadChannelGroupData(groupIndex);

        if (liveChannelIndex > -1) {
            clickLiveChannel(liveChannelIndex);
            mChannelGroupView.scrollToPosition(groupIndex);
            if (mLiveChannelView != null) mLiveChannelView.scrollToPosition(liveChannelIndex);
        }
    }

    private void loadChannelGroupData(int groupIndex) {
        liveChannelItemAdapter.setNewData(getLiveChannels(groupIndex));
        if (groupIndex == currentChannelGroupIndex) {
            if (currentLiveChannelIndex > -1 && mLiveChannelView != null)
                mLiveChannelView.scrollToPosition(currentLiveChannelIndex);
            liveChannelItemAdapter.setSelectedChannelIndex(currentLiveChannelIndex);
        }
        else {
            if (mLiveChannelView != null) mLiveChannelView.scrollToPosition(0);
            liveChannelItemAdapter.setSelectedChannelIndex(-1);
        }
    }

    private boolean isNeedInputPassword(int groupIndex) {
        return !liveChannelGroupList.get(groupIndex).getGroupPassword().isEmpty()
                && !isPasswordConfirmed(groupIndex);
    }

    private boolean isPasswordConfirmed(int groupIndex) {
        for (Integer confirmedNum : channelGroupPasswordConfirmed) {
            if (confirmedNum == groupIndex)
                return true;
        }
        return false;
    }

    private ArrayList<LiveChannelItem> getLiveChannels(int groupIndex) {
        if (!isNeedInputPassword(groupIndex)) {
            return liveChannelGroupList.get(groupIndex).getLiveChannels();
        } else {
            return new ArrayList<>();
        }
    }

    private Integer[] getNextChannel(int direction) {
        int channelGroupIndex = currentChannelGroupIndex;
        int liveChannelIndex = currentLiveChannelIndex;

        //跨选分组模式下跳过加密频道分组（超时换源）
        if (direction > 0) {
            liveChannelIndex++;
            if (liveChannelIndex >= getLiveChannels(channelGroupIndex).size()) {
                liveChannelIndex = 0;
                if (Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false)) {
                    do {
                        channelGroupIndex++;
                        if (channelGroupIndex >= liveChannelGroupList.size())
                            channelGroupIndex = 0;
                    } while (!liveChannelGroupList.get(channelGroupIndex).getGroupPassword().isEmpty() || channelGroupIndex == currentChannelGroupIndex);
                }
            }
        } else {
            liveChannelIndex--;
            if (liveChannelIndex < 0) {
                if (Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false)) {
                    do {
                        channelGroupIndex--;
                        if (channelGroupIndex < 0)
                            channelGroupIndex = liveChannelGroupList.size() - 1;
                    } while (!liveChannelGroupList.get(channelGroupIndex).getGroupPassword().isEmpty() || channelGroupIndex == currentChannelGroupIndex);
                }
                liveChannelIndex = getLiveChannels(channelGroupIndex).size() - 1;
            }
        }

        Integer[] groupChannelIndex = new Integer[2];
        groupChannelIndex[0] = channelGroupIndex;
        groupChannelIndex[1] = liveChannelIndex;

        return groupChannelIndex;
    }

    private int getFirstNoPasswordChannelGroup() {
        for (LiveChannelGroup liveChannelGroup : liveChannelGroupList) {
            if (liveChannelGroup.getGroupPassword().isEmpty())
                return liveChannelGroup.getGroupIndex();
        }
        return -1;
    }

    private boolean isCurrentLiveChannelValid() {
        if (currentLiveChannelItem == null) {
            Toast.makeText(App.getInstance(), "请先选择频道", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    /**
     * 未配置直播源时，显示友好提示，引导用户去设置页面配置
     */
    private void showNoLiveSourceTip() {
        clearLiveChannelList(false);
        showSuccess();
        // 在视频区域中央显示提示文字
        mHandler.post(() -> {
            try {
                // 通过 Toast 提示用户
                Toast.makeText(mContext, "请先到【设置 → 直播地址】中添加并选中一个直播源", Toast.LENGTH_LONG).show();
            } catch (Exception ignored) {}
        });
        updateMobileChannelName("未配置直播源");
    }

    /**
     * 当播放列表为空或加载失败时，设置一个默认的播放列表，保证播放界面不会崩溃
     */
    private void clearLiveChannelList(boolean releasePlayer) {
        refreshingLiveChannelList = false;
        pendingLiveRefreshChannelName = null;
        pendingLiveRefreshSourceIndex = -1;
        currentLiveChannelItem = null;
        currentLiveChannelIndex = -1;
        currentLiveLookBackIndex = -1;
        currentLiveChangeSourceTimes = 0;
        liveChannelGroupList.clear();
        ApiConfig.get().getChannelGroupList().clear();
        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
        hideSwitchChannelSnapshot();
        if (releasePlayer && mVideoView != null) mVideoView.release();
        showSuccess();
        if (liveChannelGroupAdapter != null) {
            liveChannelGroupAdapter.setFocusedGroupIndex(-1);
            liveChannelGroupAdapter.setSelectedGroupIndex(-1);
            liveChannelGroupAdapter.setNewData(liveChannelGroupList);
        }
        if (liveChannelItemAdapter != null) {
            liveChannelItemAdapter.setFocusedChannelIndex(-1);
            liveChannelItemAdapter.setSelectedChannelIndex(-1);
            liveChannelItemAdapter.setNewData(new ArrayList<LiveChannelItem>());
        }
    }

    private void setEmptyLiveChannelList() {
        setEmptyLiveChannelList(true);
    }

    private void setEmptyLiveChannelList(boolean releasePlayer) {
        clearLiveChannelList(releasePlayer);
    }
}
