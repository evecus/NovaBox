package com.mobile.novabox.player.controller;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.mobile.novabox.R;
import com.mobile.novabox.api.ApiConfig;
import com.mobile.novabox.base.App;
import com.mobile.novabox.bean.ParseBean;
import com.mobile.novabox.bean.SourceBean;
import com.mobile.novabox.server.ControlManager;
import com.mobile.novabox.server.RemoteServer;
import com.mobile.novabox.subtitle.widget.SimpleSubtitleView;
import com.mobile.novabox.ui.adapter.ParseAdapter;
import com.mobile.novabox.ui.dialog.EpisodeSelectDialog;
import com.mobile.novabox.ui.dialog.PlayerSelectDialog;
import com.mobile.novabox.ui.dialog.SpeedSelectDialog;
import com.mobile.novabox.util.HawkConfig;
import com.mobile.novabox.util.LOG;
import com.mobile.novabox.util.M3u8;
import com.mobile.novabox.util.PlayerHelper;
import com.mobile.novabox.util.PlayerSwitchUtil;
import com.mobile.novabox.util.SubtitleHelper;
import com.mobile.novabox.util.VideoParseRuler;
import com.mobile.novabox.util.thunder.Jianpian;
import com.mobile.novabox.util.thunder.Thunder;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.HttpHeaders;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xwalk.core.XWalkView;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import xyz.doikki.videoplayer.player.VideoView;

import static xyz.doikki.videoplayer.util.PlayerUtils.stringForTime;
import static xyz.doikki.videoplayer.util.PlayerUtils.seconds2Time;
import static xyz.doikki.videoplayer.util.PlayerUtils.safeTimeMs;

public class VodController extends BaseController {
    public VodController(@NonNull @NotNull Context context) {
        super(context);
        mHandlerCallback = new HandlerCallback() {
            @Override
            public void callback(Message msg) {
                switch (msg.what) {
                    case 1000: { // seek 刷新
                        break;
                    }
                    case 1001: { // seek 关闭
                        break;
                    }
                    case 1002: { // 显示底部菜单(仅真全屏时生效:点击屏幕显示完整 UI)
                        // 小屏预览模式下自身控件条不显示,统一由 miniControlsOverlay 负责
                        if (mIsPreviewMode) break;
                        if (mBottomRoot != null) mBottomRoot.setVisibility(VISIBLE);
                        mTopRoot1.setVisibility(VISIBLE);
                        // 顶部剧集信息等(mPlayTitle 已删)
                        // 本 app 无 TV 版,返回图标在手机/平板全屏时都显示
                        backBtn.setVisibility(VISIBLE);
                        showLockView();
                        // 全屏时:点屏幕显示完整 UI
                        syncUiByFullscreen();
                        break;
                    }
                    case 1003: { // 隐藏底部菜单(仅真全屏时生效)
                        if (mIsPreviewMode) break;
                        if (mBottomRoot != null) mBottomRoot.setVisibility(GONE);
                        mTopRoot1.setVisibility(GONE);
                        backBtn.setVisibility(INVISIBLE);
                        break;
                    }
                    case 1004: { // 设置速度
                        if (isInPlaybackState()) {
                            try {
                                float speed = (float) mPlayerConfig.getDouble("sp");
                                mControlWrapper.setSpeed(speed);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        } else
                            mHandler.sendEmptyMessageDelayed(1004, 100);
                        break;
                    }
                }
            }
        };
    }

    SeekBar mSeekBar;
    TextView mCurrentTime;
    TextView mTotalTime;
    boolean mIsDragging;
    ImageView mLockView;
    private ImageView mBtnPlayPause;
    private TextView mTimeInfo;          // 全屏:00:00 / 00:00
    private TextView mCurrTime;          // 小屏左侧时间
    private ImageView mEnterFullscreen;  // 小屏末尾"进入全屏"图标
    private ImageView mExitFullscreen;   // 全屏末尾"退出全屏"图标
    LinearLayout mBottomRoot;
    LinearLayout mTopRoot1;
    LinearLayout mParseRoot;
    RecyclerView mGridParseView;
    TextView mPlayTitle1;
    TextView mVideoSize;
    public SimpleSubtitleView mSubtitleView;
    private View backBtn;//返回键
    private boolean isClickBackBtn;
    private boolean hasDanmu = false;

    LockRunnable lockRunnable = new LockRunnable();
    private boolean isLock = false;
    Handler myHandle;
    Runnable myRunnable;
    int myHandleSeconds = 10000;//闲置多少毫秒秒关闭底栏  默认6秒

    /**
     * 是否处于"小屏预览"模式。小屏预览下,界面控制条(返回/锁定/进度条/播放按钮等)
     * 完全由 Activity 级别的 miniControlsOverlay 负责显示,VodController 自身的
     * bottom_container/tv_top_l_container 等控件条一律隐藏、不响应点击/手势显隐,
     * 避免和 miniControlsOverlay 同时出现两套进度条/按钮的问题。
     * 只有在真正进入全屏(由 Activity 的 toggleFullPreview 驱动)时才为 false,
     * 此时 VodController 自身控件条才是唯一生效的控制 UI。
     */
    private boolean mIsPreviewMode = true;

    /**
     * 由外部(PlayFragment/DetailActivity)在真正进入/退出全屏时调用,
     * 用来切换"谁负责显示控制条"。
     */
    public void setPreviewMode(boolean isPreviewMode) {
        mIsPreviewMode = isPreviewMode;
        if (mIsPreviewMode) {
            // 小屏预览:自身控件条整体隐藏,不常驻显示,也不响应单击显隐
            myHandle.removeCallbacks(myRunnable);
            mHandler.removeMessages(1002);
            mHandler.removeMessages(1003);
            if (mBottomRoot != null) mBottomRoot.setVisibility(GONE);
            if (mTopRoot1 != null) mTopRoot1.setVisibility(GONE);
            if (backBtn != null) backBtn.setVisibility(INVISIBLE);
            if (mLockView != null) mLockView.setVisibility(INVISIBLE);
        } else {
            // 真全屏:默认先隐藏,等待用户点击屏幕后再显示(见 onSingleTapConfirmed)
            if (mBottomRoot != null) mBottomRoot.setVisibility(GONE);
            syncUiByFullscreen();
        }
    }

    public boolean isPreviewMode() {
        return mIsPreviewMode;
    }

    int videoPlayState = 0;

    private final Runnable myRunnable2 = new Runnable() {
        @SuppressLint("SetTextI18n")
        @Override
        public void run() {
            // 系统时间 / 右上角网速已删除(tv_top_r_container 整块移除),
            // 中部 loading 指示器下方的网速数字(tv_play_load_net_speed)也已删除,仅保留转圈动画。
            int[] mVideoSizes = mControlWrapper.getVideoSize();
            String width = Integer.toString(mVideoSizes[0]);
            String height = Integer.toString(mVideoSizes[1]);
            // 只在有效分辨率时更新，避免播放器未上报时覆盖掉已由 onVideoSizeChanged 写入的正确值
            if (mVideoSizes[0] > 0 && mVideoSizes[1] > 0) {
                mVideoSize.setText("[ " + width + " x " + height + " ]");
            }

            mHandler.postDelayed(this, 1000);
        }
    };
    
    private void showLockView() {
        // 本 app 无 TV 版,锁图标在手机/平板全屏时都显示
        mLockView.setVisibility(VISIBLE);
        mHandler.removeCallbacks(lockRunnable);
        mHandler.postDelayed(lockRunnable, 3000);
    }

    @Override
    protected void initView() {
        super.initView();
        mCurrentTime = null; // 原 curr_time 已合并到 fs_time_info
        mTotalTime = null;   // 原 total_time 已合并到 fs_time_info
        mTimeInfo = findViewById(R.id.fs_time_info);
        mCurrTime = findViewById(R.id.fs_curr_time);
        mTotalTime = findViewById(R.id.fs_total_time);
        mEnterFullscreen = findViewById(R.id.fs_enter_fullscreen);
        mExitFullscreen = findViewById(R.id.fs_exit_fullscreen);
        // 小屏模式下自身控件条完全不显示,进入/退出全屏统一交给外部(Activity)的
        // miniControlsOverlay / toggleFullPreview 处理,这里不再重复提供入口。
        if (mEnterFullscreen != null) {
            mEnterFullscreen.setVisibility(GONE);
            mEnterFullscreen.setOnClickListener(null);
        }
        if (mExitFullscreen != null) {
            mExitFullscreen.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    // 真正的"退出全屏"由 Activity 执行(还原小屏预览布局),
                    // 这里只负责把事件转发出去,不再调用库自身的 stopFullScreen()。
                    if (listener != null) listener.requestExitFullscreen();
                }
            });
        }
        mPlayTitle1 = findViewById(R.id.tv_info_name1);
        mSeekBar = findViewById(R.id.seekBar);
        mBottomRoot = findViewById(R.id.bottom_container);
        mTopRoot1 = findViewById(R.id.tv_top_l_container);
        // 初始状态:小屏预览模式,自身控件条一律不显示(常驻由 miniControlsOverlay 负责)
        if (mBottomRoot != null) mBottomRoot.setVisibility(GONE);
        if (mTopRoot1 != null) mTopRoot1.setVisibility(GONE);
        mParseRoot = findViewById(R.id.parse_root);
        mGridParseView = findViewById(R.id.mGridParseView);
        mVideoSize = findViewById(R.id.tv_videosize);
        mSubtitleView = findViewById(R.id.subtitle_view);
        backBtn = findViewById(R.id.tv_back);
        mBtnPlayPause = findViewById(R.id.btn_play_pause);
        if (mBtnPlayPause != null) {
            mBtnPlayPause.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mControlWrapper.isPlaying()) {
                        mControlWrapper.pause();
                        mBtnPlayPause.setImageResource(R.drawable.icon_play_mini);
                    } else {
                        mControlWrapper.start();
                        mBtnPlayPause.setImageResource(R.drawable.icon_pause);
                    }
                }
            });
        }

        // ── 功能按钮行:下一集/弹幕/播放器切换/倍速/选集 ──
        View btnNextEp = findViewById(R.id.btn_next_episode);
        if (btnNextEp != null) {
            btnNextEp.setOnClickListener(v -> {
                if (listener != null) listener.playNext(true);
            });
        }

        mFunctionRow = findViewById(R.id.fs_function_row);

        View btnDanmuSetting = findViewById(R.id.btn_danmu_setting);
        if (btnDanmuSetting != null) {
            // 弹幕按钮只在在线视频显示(默认 gone,由外部按场景显示)
            btnDanmuSetting.setOnClickListener(v -> {
                if (listener != null) listener.showDanmuSetting();
            });
        }

        View btnPlayerSelect = findViewById(R.id.btn_player_select);
        if (btnPlayerSelect != null) {
            btnPlayerSelect.setOnClickListener(v -> {
                Activity a = (Activity) getContext();
                int cur = 0;
                try {
                    if (mPlayerConfig != null) cur = mPlayerConfig.optInt("pl", 0);
                } catch (Throwable ignore) {}
                new PlayerSelectDialog(a, cur, type -> {
                    currentPlayerType = type;
                    try {
                        if (mPlayerConfig == null) mPlayerConfig = new JSONObject();
                        mPlayerConfig.put("pl", type);
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                    if (listener != null) listener.onPlayerSelected(type);
                    showSpeedIndicator(typeName(type));
                }).show();
            });
        }

        View btnSpeedSelect = findViewById(R.id.btn_speed_select);
        if (btnSpeedSelect != null) {
            btnSpeedSelect.setOnClickListener(v -> {
                Activity a = (Activity) getContext();
                new SpeedSelectDialog(a, currentSpeed, speed -> {
                    currentSpeed = speed;
                    try {
                        if (mPlayerConfig == null) mPlayerConfig = new JSONObject();
                        mPlayerConfig.put("sp", (double) speed);
                        mControlWrapper.setSpeed(speed);
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                    showSpeedIndicator(speed + "x");
                }).show();
            });
        }

        View btnEpisodeSelect = findViewById(R.id.btn_episode_select);
        if (btnEpisodeSelect != null) {
            btnEpisodeSelect.setOnClickListener(v -> {
                if (listener != null) listener.selectEpisode();
            });
        }
        backBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getContext() instanceof Activity) {
                    isClickBackBtn = true;
                    ((Activity) getContext()).onBackPressed();
                }
            }
        });
        mLockView = findViewById(R.id.tv_lock);
        mLockView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                isLock = !isLock;
                mLockView.setImageResource(isLock ? R.drawable.icon_lock : R.drawable.icon_unlock);
                if (isLock) {
                    Message obtain = Message.obtain();
                    obtain.what = 1003;//隐藏底部菜单
                    mHandler.sendMessage(obtain);
                    // 锁定时整个按钮行隐藏(避免占位不可点)
                    if (mFunctionRow != null) mFunctionRow.setVisibility(View.GONE);
                    if (mTimeInfo != null) mTimeInfo.setVisibility(View.GONE);
                } else {
                    // 解锁时恢复按钮行(根据 isFullScreen 决定显示哪个)
                    syncUiByFullscreen();
                }
                showLockView();
            }
        });
        View rootView = findViewById(R.id.rootView);
        rootView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isLock) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        showLockView();
                    }
                }
                return isLock;
            }
        });

        initSubtitleInfo();

        myHandle = new Handler();
        myRunnable = new Runnable() {
            @Override
            public void run() {
                hideBottom();
            }
        };

        // myRunnable2 启动:注意不能用 mHandler——initView 是在 BaseVideoController 构造链里被调用的,
        // 此时父类的 mHandler 字段还没初始化(null),直接 mHandler.post 会 NPE(闪退)。
        // 用本类在 initView 内新建的 myHandle(本行之前已创建,非 null)替代。
        myHandle.post(myRunnable2);

        mGridParseView.setLayoutManager(new LinearLayoutManager(getContext(), 0, false));
        ParseAdapter parseAdapter = new ParseAdapter();
        parseAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                ParseBean parseBean = parseAdapter.getItem(position);
                // 当前默认解析需要刷新
                int currentDefault = parseAdapter.getData().indexOf(ApiConfig.get().getDefaultParse());
                parseAdapter.notifyItemChanged(currentDefault);
                ApiConfig.get().setDefaultParse(parseBean);
                parseAdapter.notifyItemChanged(position);
                listener.changeParse(parseBean);
                hideBottom();
            }
        });
        mGridParseView.setAdapter(parseAdapter);
        parseAdapter.setNewData(ApiConfig.get().getParseBeanList());

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }

                long duration = mControlWrapper.getDuration();
                long newPosition = (duration * progress) / seekBar.getMax();
                if (mCurrTime != null)
                    mCurrTime.setText(stringForTime(safeTimeMs(newPosition)));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mIsDragging = true;
                mControlWrapper.stopProgress();
                mControlWrapper.stopFadeOut();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                myHandle.removeCallbacks(myRunnable);
                myHandle.postDelayed(myRunnable, myHandleSeconds);
                long duration = mControlWrapper.getDuration();
                long newPosition = (duration * seekBar.getProgress()) / seekBar.getMax();
                mControlWrapper.seekTo(newPosition);
                mIsDragging = false;
                mControlWrapper.startProgress();
                mControlWrapper.startFadeOut();
            }
        });
    }

    void initSubtitleInfo() {
        int subtitleTextSize = SubtitleHelper.getTextSize(mActivity);
        mSubtitleView.setTextSize(subtitleTextSize);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.player_vod_control_view;
    }

    public void showParse(boolean userJxList) {
        mParseRoot.setVisibility(userJxList ? VISIBLE : GONE);
    }

    private JSONObject mPlayerConfig = null;

    public void setPlayerConfig(JSONObject playerCfg) {
        this.mPlayerConfig = playerCfg;
        updatePlayerCfgView();
    }

    void updatePlayerCfgView() {
        // 播放器/解码/音轨等按钮已删除(play_btn_group 整组移除),无需再同步/归一化配置(统一 4 档新编码)
    }

    public void setTitle(String playTitleInfo) {
        // mPlayTitle(tv_info_name)已随 tv_pause_container 删除,改用顶部 mPlayTitle1
        if (mPlayTitle1 != null) mPlayTitle1.setText(playTitleInfo);
    }

    public void setUrlTitle(String playTitleInfo) {
        if (mPlayTitle1 != null) mPlayTitle1.setText(playTitleInfo);
    }

    public void resetSpeed() {
        skipEnd = true;
        mHandler.removeMessages(1004);
        mHandler.sendEmptyMessageDelayed(1004, 100);
    }

    public void setHasDanmu(boolean hasDanmu) {
        this.hasDanmu = hasDanmu;
    }

    public interface VodControlListener {
        void playNext(boolean rmProgress);

        void playPre();

        void prepared();

        void changeParse(ParseBean pb);

        void updatePlayerCfg();

        void replay(boolean replay);

        void errReplay();

        void selectSubtitle();

        void selectAudioTrack();

        void showDanmuSetting();

        void searchDanmuUi(boolean longClick);

        void startPlayUrl(String url, HashMap<String, String> headers);

        void setAllowSwitchPlayer(boolean isAllow);

        /** 选集按钮点击:由 Activity 弹 EpisodeSelectDialog */
        void selectEpisode();

        /** 播放器切换(4 档):由 Activity 执行实际的切换 + 重放 */
        void onPlayerSelected(int newType);

        /** 全屏状态下点击"退出全屏"按钮:由 Activity 执行真正的退出全屏(恢复小屏预览布局) */
        void requestExitFullscreen();
    }

    public void setListener(VodControlListener listener) {
        this.listener = listener;
    }

    private VodControlListener listener;

    /** 会话级:当前倍速/播放器类型(换视频重置,不持久化) */
    private float currentSpeed = 1.0f;
    private int currentPlayerType = 0;

    /** 弹幕设置按钮(仅在线视频显示) */
    private View mBtnDanmuSetting;

    /** 功能按钮行(下一集/弹幕/播放器切换/倍速/选集,仅全屏时显示) */
    private View mFunctionRow;

    /** 控制弹幕按钮显隐(在线视频调用显示) */
    public void setDanmuButtonVisible(boolean visible) {
        if (mBtnDanmuSetting == null) {
            mBtnDanmuSetting = findViewById(R.id.btn_danmu_setting);
        }
        if (mBtnDanmuSetting != null) {
            mBtnDanmuSetting.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private boolean skipEnd = true;

    @SuppressLint("SetTextI18n")
    @Override
    protected void setProgress(int duration, int position) {

        if (mIsDragging) {
            return;
        }
        super.setProgress(duration, position);
        // 进度刷新时同步小屏/全屏 UI 状态
        syncUiByFullscreen();
        if (skipEnd && position != 0 && duration != 0) {
            int et = 0;
            try {
                et = mPlayerConfig.getInt("et");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (et > 0 && position + (et * 1000) >= duration) {
                skipEnd = false;
                listener.playNext(true);
            }
        }
        // 时间显示:全屏 fs_time_info("00:00 / 00:00") + 小屏左/右时间
        String curStr = stringForTime(position);
        String durStr = stringForTime(duration);
        if (mTimeInfo != null) {
            mTimeInfo.setText(curStr + " / " + durStr);
        }
        if (mCurrTime != null) {
            mCurrTime.setText(curStr);
        }
        if (mTotalTime != null) {
            mTotalTime.setText(durStr);
        }
        // seekTime(右上角进度时间)已被删除
        if (duration > 0) {
            mSeekBar.setEnabled(true);
            int pos = (int) (position * 1.0 / duration * mSeekBar.getMax());
            mSeekBar.setProgress(pos);
        } else {
            mSeekBar.setEnabled(false);
        }
        int percent = mControlWrapper.getBufferedPercentage();
        if (percent >= 95) {
            mSeekBar.setSecondaryProgress(mSeekBar.getMax());
        } else {
            mSeekBar.setSecondaryProgress(percent * 10);
        }
    }

    @Override
    protected void updateSeekUI(int curr, int seekTo, int duration) {
        super.updateSeekUI(curr, seekTo, duration);
        mHandler.sendEmptyMessage(1000);
        mHandler.removeMessages(1001);
        mHandler.sendEmptyMessageDelayed(1001, 1000);
    }

    @Override
    protected void onPlayStateChanged(int playState) {
        super.onPlayStateChanged(playState);
        videoPlayState = playState;
        switch (playState) {
            case VideoView.STATE_IDLE:
                break;
            case VideoView.STATE_PLAYING:
                startProgress();
                if (mBtnPlayPause != null) mBtnPlayPause.setImageResource(R.drawable.icon_pause);
                // 视频开始播放后稍等一下再读分辨率，此时播放器已上报真实尺寸
                myHandle.postDelayed(() -> {
                    if (mVideoSize != null && mControlWrapper != null) {
                        int[] sz = mControlWrapper.getVideoSize();
                        if (sz[0] > 0 && sz[1] > 0) {
                            mVideoSize.setText("[ " + sz[0] + " x " + sz[1] + " ]");
                        }
                    }
                }, 500);
                break;
            case VideoView.STATE_PAUSED:
                if (mBtnPlayPause != null) mBtnPlayPause.setImageResource(R.drawable.icon_play_mini);
                mTopRoot1.setVisibility(GONE);
                // tv_top_r_container / mPlayTitle(tv_info_name) 已删除
                break;
            case VideoView.STATE_ERROR:
                listener.errReplay();
                break;
            case VideoView.STATE_PREPARED:
                listener.prepared();
                break;
            case VideoView.STATE_BUFFERED:
                break;
            case VideoView.STATE_PREPARING:
            case VideoView.STATE_BUFFERING:
                break;
            case VideoView.STATE_PLAYBACK_COMPLETED:
                listener.playNext(true);
                break;
        }
    }


    boolean isBottomVisible() {
        return mBottomRoot.getVisibility() == VISIBLE;
    }

    void showBottom() {
        mHandler.removeMessages(1003);
        mHandler.sendEmptyMessage(1002);
    }

    void hideBottom() {
        mHandler.removeMessages(1002);
        mHandler.sendEmptyMessage(1003);
    }


    private boolean fromLongPress;
    private float speed_old = 1.0f;

    private void speedPlayStart(){
        fromLongPress = true;
        try {
            speed_old = (float) mPlayerConfig.getDouble("sp");
            float speed = 3.0f;
            mPlayerConfig.put("sp", speed);
            updatePlayerCfgView();
            listener.updatePlayerCfg();
            mControlWrapper.setSpeed(speed);
            findViewById(R.id.play_speed_3_container).setVisibility(View.VISIBLE);
        } catch (JSONException f) {
            f.printStackTrace();
        }
    }
    private void speedPlayEnd(){
        if (fromLongPress) {
            fromLongPress =false;
            try {
                float speed = speed_old;
                mPlayerConfig.put("sp", speed);
                updatePlayerCfgView();
                listener.updatePlayerCfg();
                mControlWrapper.setSpeed(speed);
            } catch (JSONException f) {
                f.printStackTrace();
            }
            findViewById(R.id.play_speed_3_container).setVisibility(View.GONE);
        }
    }

    /** 短暂显示倍速/播放器切换浮标(复用 play_speed_3_container) */
    private void showSpeedIndicator(String text) {
        try {
            TextView tv = findViewById(R.id.tv_speed_3);
            if (tv != null) tv.setText(text);
            View container = findViewById(R.id.play_speed_3_container);
            if (container != null) {
                container.setVisibility(View.VISIBLE);
                container.removeCallbacks(null);
                container.postDelayed(() -> container.setVisibility(View.GONE), 1500);
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    /**
     * 同步"真全屏" UI 显隐(全屏时显示完整 UI:时间行 + 按钮行 + 退出全屏图标)。
     * 真全屏与否现在完全由外部 Activity 通过 setPreviewMode() 驱动的
     * mIsPreviewMode 决定,不再依赖库自身的 mControlWrapper.isFullScreen()
     * (因为已不再调用 startFullScreen()/stopFullScreen())。
     * 小屏预览模式下该方法不生效,所有这些控件在 setPreviewMode(true) 时已被强制隐藏。
     */
    private void syncUiByFullscreen() {
        if (mIsPreviewMode) return;
        boolean fs = true; // 走到这里说明处于真全屏(mIsPreviewMode == false)
        // 全屏:显示时间行 + 按钮行 + 退出全屏图标;小屏专属的左侧时间/总时长/进入全屏图标本控件已不再使用
        if (mFunctionRow != null) {
            mFunctionRow.setVisibility(fs ? View.VISIBLE : View.GONE);
        }
        if (mTimeInfo != null) {
            mTimeInfo.setVisibility(fs ? View.VISIBLE : View.GONE);
        }
        if (mExitFullscreen != null) {
            mExitFullscreen.setVisibility(fs ? View.VISIBLE : View.GONE);
        }
        if (mCurrTime != null) {
            mCurrTime.setVisibility(View.GONE);
        }
        if (mTotalTime != null) {
            mTotalTime.setVisibility(View.GONE);
        }
        if (mEnterFullscreen != null) {
            mEnterFullscreen.setVisibility(View.GONE);
        }
    }

    private String typeName(int type) {
        switch (type) {
            case 0: return "EXO 硬解";
            case 1: return "EXO 软解";
            case 2: return "IJK 硬解";
            case 3: return "IJK 软解";
            default: return "";
        }
    }

    @Override
    public void onLongPress(MotionEvent e) {
        if (videoPlayState!=VideoView.STATE_PAUSED) {
            speedPlayStart();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) {
            speedPlayEnd();
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        // 小屏预览模式:点击交给外层 previewPlayerBlock/miniControlsOverlay 处理,
        // 自身不响应单击显隐,避免出现两套进度条。
        if (mIsPreviewMode) {
            return false;
        }
        myHandle.removeCallbacks(myRunnable);
        if (!isBottomVisible()) {
            showBottom();
            // 闲置计时关闭
            myHandle.postDelayed(myRunnable, myHandleSeconds);
        } else {
            hideBottom();
        }
        return true;
    }
    
    private class LockRunnable implements Runnable {
        @Override
        public void run() {
            mLockView.setVisibility(INVISIBLE);
        }
    }
    
    @Override
    public boolean onBackPressed() {
        if (isClickBackBtn) {
            isClickBackBtn = false;
            if (isBottomVisible()) {
                hideBottom();
            }
            return false;
        }
        if (super.onBackPressed()) {
            return true;
        }
        if (isBottomVisible()) {
            hideBottom();
            return true;
        }
        return false;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mHandler.removeCallbacks(myRunnable2);
    }


    //尝试去bom
    public String getWebPlayUrlIfNeeded(String webPlayUrl) {
        if (webPlayUrl != null && !webPlayUrl.contains("127.0.0.1:9978") &&  webPlayUrl.contains(".m3u8")) {
            try {
                String urlEncode = URLEncoder.encode(webPlayUrl, "UTF-8");
                LOG.i("echo-BOM-------");
                return ControlManager.get().getAddress(true) + "proxy?go=bom&url=" + urlEncode;
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        return webPlayUrl;
    }

    public String encodeUrl(String url) {
        try {
            return URLEncoder.encode(url, "UTF-8");
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * 自动切换播放内核(播放失败时由上层调用)。
     * 按固定顺序 0→1→2→3 尝试除当前外的其余内核,已尝试过的记录在 triedPlayerTypes 中。
     *
     * @param triedPlayerTypes 已尝试过的内核档位集合(切内核失败后重试时传入,内部会累加)
     * @return false 表示已切换到下一个内核,调用方应使用当前 URL 重新播放;
     *         true  表示其余三个内核都已试过(或配置不支持),调用方应降级处理(如切换线路)
     */
    public boolean switchPlayer(java.util.Set<Integer> triedPlayerTypes) {
        try {
            int playerType = mPlayerConfig.getInt("pl");
            int p_type = PlayerSwitchUtil.nextPlayerType(playerType, triedPlayerTypes);
            if (p_type < 0) {
                LOG.i("echo-switchPlayer: all player types tried, skip");
                return true;
            }
            LOG.i("echo-switchPlayer: " + playerType + " -> " + p_type);
            mPlayerConfig.put("pl", p_type);
            mPlayerConfig.put("ijk", PlayerSwitchUtil.ijkCodeFor(p_type));
            updatePlayerCfgView();
            listener.updatePlayerCfg();
        }catch (Exception e){
            LOG.i("echo-switchPlayer error: " + e.getMessage());
            return true;
        }
        return false;
    }

    public void playM3u8(final String url, final HashMap<String, String> headers) {
        if(url.contains("url=")){
            listener.startPlayUrl(url, headers);
            return;
        }
        OkGo.getInstance().cancelTag("m3u8-1");
        OkGo.getInstance().cancelTag("m3u8-2");
        final HttpHeaders okGoHeaders = new HttpHeaders();
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                okGoHeaders.put(entry.getKey(), entry.getValue());
            }
        }
        OkGo.<String>get(url)
                .tag("m3u8-1")
                .headers(okGoHeaders)
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        String content = response.body();
                        if (!content.startsWith("#EXTM3U")) {
                            listener.startPlayUrl(url, headers);
                            return;
                        }
                        String forwardUrl = extractForwardUrl(url, content);
                        if (forwardUrl.isEmpty()) {
                            LOG.i("echo-m3u81-to-play");
                            processM3u8Content(url, content, headers);
                        } else {
                            fetchAndProcessForwardUrl(forwardUrl, headers, okGoHeaders, url);
                        }
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body().string();
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        LOG.e("echo-m3u8请求错误1: " + response.getException());
                        listener.startPlayUrl(url, headers);
                    }
                });
    }

    private String extractForwardUrl(String baseUrl, String content) {
        String[] lines = content.split("\\r?\\n",50);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                // 只需要找接下来的几行
                for (int j = i + 1; j < lines.length; j++) {
                    String targetLine = lines[j].trim();
                    if (targetLine.isEmpty()) continue;
                    if (isValidM3u8Line(targetLine)) {
                        return resolveForwardUrl(baseUrl, targetLine);
                    }
                }
            }
        }
        return "";
    }

    private boolean isValidM3u8Line(String line) {
        return !line.startsWith("#") && (line.endsWith(".m3u8") || line.contains(".m3u8?"));
    }

    private void processM3u8Content(String url, String content, HashMap<String, String> headers) {
        String basePath = getBasePath(url);
        RemoteServer.m3u8Content = M3u8.purify(basePath, content);
        if (RemoteServer.m3u8Content == null || M3u8.currentAdCount==0) {
            LOG.i("echo-m3u8内容解析：未检测到广告");
            listener.startPlayUrl(url, headers);
        } else {
            listener.startPlayUrl(ControlManager.get().getAddress(true) + "proxyM3u8", headers);
            Toast.makeText(getContext(), "已移除视频广告 "+M3u8.currentAdCount+" 条", Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchAndProcessForwardUrl(final String forwardUrl, final HashMap<String, String> headers,
                                           HttpHeaders okGoHeaders, final String fallbackUrl) {
        OkGo.<String>get(forwardUrl)
                .tag("m3u8-2")
                .headers(okGoHeaders)
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        String content = response.body();
                        LOG.i("echo-m3u82-to-play");
                        processM3u8Content(forwardUrl, content, headers);
                    }
                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body().string();
                    }
                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        LOG.e("echo-重定向 m3u8 请求错误: " + response.getException());
                        listener.startPlayUrl(fallbackUrl, headers);
                    }
                });
    }

    private String getBasePath(String url) {
        int ilast = url.lastIndexOf('/');
        return url.substring(0, ilast + 1);
    }

    private String resolveForwardUrl(String baseUrl, String line) {
        try {
            // 使用 URL 构造器自动解析相对路径
            URL base = new URL(baseUrl);
            URL resolved = new URL(base, line);
            return resolved.toString();
        } catch (MalformedURLException e) {
            // 出现异常时可以记录日志，并返回原始 line
            LOG.e("echo-resolveForwardUrl异常: " + e.getMessage());
            return line;
        }
    }

    public String firstUrlByArray(String url)
    {
        try {
            JSONArray urlArray = new JSONArray(url);
            for (int i = 0; i < urlArray.length(); i++) {
                String item = urlArray.getString(i);
                if (item.contains("http")) {
                    url = item;
                    break; // 找到第一个立即终止循环
                }
            }
        } catch (JSONException e) {
        }
        return url;
    }

    public void evaluateScript(SourceBean sourceBean,String url, WebView web_view, XWalkView xWalk_view){
        String clickSelector = sourceBean.getClickSelector().trim();
        clickSelector=clickSelector.isEmpty()?VideoParseRuler.getHostScript(url):clickSelector;
        if (!clickSelector.isEmpty()) {
            String selector;
            if (clickSelector.contains(";") && !clickSelector.endsWith(";")) {
                String[] parts = clickSelector.split(";", 2);
                if (!url.contains(parts[0])) {
                    return;
                }
                selector = parts[1].trim();
            } else {
                selector = clickSelector.trim();
            }
            // 构造点击的 JS 代码
            String js = selector;
//            if(!selector.contains("click()"))js+=".click();";
            LOG.i("echo-javascript:" + js);
            if(web_view!=null){
                //4.4以上才支持这种写法
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    web_view.evaluateJavascript(js, null);
                } else {
                    web_view.loadUrl("javascript:" + js);
                }
            }
            if(xWalk_view!=null){
                //4.0+开始全部支持这种写法
                xWalk_view.evaluateJavascript(js, null);
            }
        }
    }

    public void stopOther()
    {
        Thunder.stop(false);//停止磁力下载
        Jianpian.finish();//停止p2p下载
        App.getInstance().setDashData(null);
    }
}
