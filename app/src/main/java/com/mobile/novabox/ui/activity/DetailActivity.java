package com.mobile.novabox.ui.activity;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.content.ClipboardManager;
import android.content.ClipData;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentContainerView;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearSmoothScroller;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.mobile.novabox.R;
import com.mobile.novabox.api.ApiConfig;
import com.mobile.novabox.base.App;
import com.mobile.novabox.base.BaseActivity;
import com.mobile.novabox.bean.AbsXml;
import com.mobile.novabox.bean.Movie;
import com.mobile.novabox.bean.SourceBean;
import com.mobile.novabox.bean.VodInfo;
import com.mobile.novabox.cache.RoomDataManger;
import com.mobile.novabox.event.RefreshEvent;
import com.mobile.novabox.picasso.RoundTransformation;
import com.mobile.novabox.player.MyVideoView;
import com.mobile.novabox.ui.adapter.SeriesAdapter;
import com.mobile.novabox.ui.adapter.SeriesFlagAdapter;
import com.mobile.novabox.ui.dialog.DescDialog;
import com.mobile.novabox.ui.dialog.DownloadSelectDialog;
import com.mobile.novabox.ui.dialog.QuickSearchDialog;
import com.mobile.novabox.ui.fragment.PlayFragment;
import com.mobile.novabox.util.DefaultConfig;
import com.mobile.novabox.util.FastClickCheckUtil;
import com.mobile.novabox.util.HawkConfig;
import com.mobile.novabox.util.LOG;
import com.mobile.novabox.util.MD5;
import com.mobile.novabox.util.SearchHelper;
import com.mobile.novabox.util.SubtitleHelper;
import com.mobile.novabox.viewmodel.SourceViewModel;
import com.lzy.okgo.OkGo;
import com.orhanobut.hawk.Hawk;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.squareup.picasso.Picasso;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.jessyan.autosize.utils.AutoSizeUtils;

import android.graphics.Paint;
import android.os.Handler;
import android.widget.SeekBar;
import android.content.pm.ActivityInfo;
import android.view.WindowManager;
import android.os.Build;

/**
 * @author pj567
 * @date :2020/12/22
 * @description:
 */

public class DetailActivity extends BaseActivity {
    private LinearLayout llLayout;
    private FragmentContainerView llPlayerFragmentContainer;
    private View llPlayerFragmentContainerBlock;
    private View llPlayerPlace;
    // Mini player controls
    private FrameLayout playerAreaContainer;
    private LinearLayout miniControlsOverlay;
    private ImageView miniBackBtn;
    private ImageView miniPlayPauseBtn;
    private ImageView miniLockBtn;
    private ImageView miniFullscreenBtn;
    private SeekBar miniSeekBar;
    private TextView miniCurrentTime;
    private TextView miniTotalTime;
    private boolean miniControlsLocked = false;
    private boolean miniControlsVisible = false;
    private final Handler miniControlsHandler = new Handler();
    private final Runnable hideControlsRunnable = () -> hideMiniControls();
    private PlayFragment playFragment = null;
    private View thumbContainer;
    private ImageView ivThumb;
    private TextView tvName;
    private TextView tvYear;
    private TextView tvSite;
    private TextView tvArea;
    private TextView tvLang;
    private TextView tvType;
    private TextView tvActor;
    private TextView tvDirector;
    private TextView tvPlayUrl;
    private TextView tvPlay;
//    private TextView tvSort;
    private TextView tvDesc;
    private TextView tvSeriesSort;
    private TextView tvQuickSearch;
    private TextView tvCollect;
    private RecyclerView mGridViewFlag;
    private RecyclerView mGridView;
    private RecyclerView mSeriesGroupView;
    private LinearLayout mEmptyPlayList;
    private LinearLayout tvSeriesGroup;
    private SourceViewModel sourceViewModel;
    private Movie.Video mVideo;
    private VodInfo vodInfo;
    private SeriesFlagAdapter seriesFlagAdapter;
    private BaseQuickAdapter<String, BaseViewHolder> seriesGroupAdapter;
    private SeriesAdapter seriesAdapter;
    public String vodId;
    public String sourceKey;
    public String firstsourceKey;
    boolean seriesSelect = false;
    private View seriesFlagFocus = null;
    private boolean isReverse;
    private String preFlag="";
    private boolean firstReverse;
    private GridLayoutManager mGridViewLayoutMgr = null;
    private HashMap<String, String> mCheckSources = null;
    private final ArrayList<String> seriesGroupOptions = new ArrayList<>();
    private View currentSeriesGroupView;
    private int GroupCount;
    boolean showPreview = Hawk.get(HawkConfig.SHOW_PREVIEW, true);; // true 开启 false 关闭

    private LinearSmoothScroller smoothScroller;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_detail;
    }

    @Override
    protected void init() {
        EventBus.getDefault().register(this);
        initView();
        initViewModel();
        initData();
    }

    private void initView() {
        llLayout = findViewById(R.id.llLayout);
        llPlayerPlace = findViewById(R.id.previewPlayerPlace);
        llPlayerFragmentContainer = findViewById(R.id.previewPlayer);
        llPlayerFragmentContainerBlock = findViewById(R.id.previewPlayerBlock);
        applyPreviewRoundCorners();
        thumbContainer = findViewById(R.id.thumbContainer);
        ivThumb = findViewById(R.id.ivThumb);
        thumbContainer.setVisibility(!showPreview ? View.VISIBLE : View.GONE);
        llPlayerPlace.setVisibility(showPreview ? View.VISIBLE : View.GONE);
        ivThumb.setVisibility(!showPreview ? View.VISIBLE : View.GONE);
        tvName = findViewById(R.id.tvName);
        tvYear = findViewById(R.id.tvYear);
        tvSite = findViewById(R.id.tvSite);
        tvArea = findViewById(R.id.tvArea);
        tvLang = findViewById(R.id.tvLang);
        tvType = findViewById(R.id.tvType);
        tvActor = findViewById(R.id.tvActor);
        tvDirector = findViewById(R.id.tvDirector);
        tvPlayUrl = findViewById(R.id.tvPlayUrl);
        // 限制信息区域最大高度，超出时可手动滚动
        androidx.core.widget.NestedScrollView infoScrollView = findViewById(R.id.infoScrollView);
        if (infoScrollView != null) {
            int maxInfoHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.30);
            infoScrollView.post(() -> {
                if (infoScrollView.getHeight() > maxInfoHeight) {
                    android.view.ViewGroup.LayoutParams lp = infoScrollView.getLayoutParams();
                    lp.height = maxInfoHeight;
                    infoScrollView.setLayoutParams(lp);
                }
            });
        }
        tvPlay = findViewById(R.id.tvPlay);
//        tvSort = findViewById(R.id.tvSort);
        tvDesc = findViewById(R.id.tvDesc);
        tvSeriesSort = findViewById(R.id.mSeriesSortTv);
        tvCollect = findViewById(R.id.tvCollect);
        tvQuickSearch = findViewById(R.id.tvQuickSearch);
        mEmptyPlayList = findViewById(R.id.mEmptyPlaylist);
        mGridView = findViewById(R.id.mGridView);
        mGridView.setHasFixedSize(false);
        this.mGridViewLayoutMgr = new GridLayoutManager(this.mContext, com.mobile.novabox.util.PadUiHelper.getEpisodeSpanCount(this));
        mGridView.setLayoutManager(this.mGridViewLayoutMgr);
//        mGridView.setLayoutManager(new LinearLayoutManager(this.mContext, LinearLayoutManager.HORIZONTAL, false));

        smoothScroller = new LinearSmoothScroller(mContext) {
            @Override
            protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
                return 100f / displayMetrics.densityDpi;
            }
            @Override
            public PointF computeScrollVectorForPosition(int targetPosition) {
                return mGridViewLayoutMgr.computeScrollVectorForPosition(targetPosition);
            }
        };

        seriesAdapter = new SeriesAdapter(this.mGridViewLayoutMgr);
        mGridView.setAdapter(seriesAdapter);
        mGridViewFlag = findViewById(R.id.mGridViewFlag);
        mGridViewFlag.setHasFixedSize(true);
        mGridViewFlag.setLayoutManager(new LinearLayoutManager(this.mContext, LinearLayoutManager.HORIZONTAL, false));
        seriesFlagAdapter = new SeriesFlagAdapter();
        mGridViewFlag.setAdapter(seriesFlagAdapter);
        seriesFlagAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                if (vodInfo == null || vodInfo.seriesFlags == null || position < 0 || position >= vodInfo.seriesFlags.size()) return;
                FastClickCheckUtil.check(view);
                String newFlag = vodInfo.seriesFlags.get(position).name;
                if (newFlag.equals(vodInfo.playFlag)) return;
                // update selected state
                for (VodInfo.VodSeriesFlag f : vodInfo.seriesFlags) {
                    f.selected = f.name.equals(newFlag);
                }
                seriesFlagAdapter.notifyDataSetChanged();
                mGridViewFlag.scrollToPosition(position);
                // reset episode selection
                for (java.util.List<VodInfo.VodSeries> seriesList : vodInfo.seriesMap.values()) {
                    if (seriesList == null) continue;
                    for (VodInfo.VodSeries s : seriesList) s.selected = false;
                }
                vodInfo.playFlag = newFlag;
                vodInfo.playIndex = 0;
                java.util.List<VodInfo.VodSeries> newList = vodInfo.seriesMap.get(newFlag);
                if (newList != null && !newList.isEmpty()) {
                    newList.get(0).selected = true;
                    setTextShow(tvPlayUrl, "播放地址：", newList.get(0).url);
                }
                refreshList();
                jumpToPlay();
            }
        });
        isReverse = false;
        firstReverse = false;
        preFlag = "";
        if (showPreview) {
            playFragment = new PlayFragment();
            getSupportFragmentManager().beginTransaction().add(R.id.previewPlayer, playFragment).commitNowAllowingStateLoss();
            if (tvPlay != null) tvPlay.setText("全屏");
            // VodController 内的"退出全屏"按钮点击后转发到这里,统一走 toggleFullPreview 退出真全屏
            playFragment.setExitFullscreenRequestCallback(() -> {
                if (fullWindows) {
                    toggleFullPreview();
                }
            });
        }
        llPlayerFragmentContainerBlock.setFocusable(showPreview);

        // Setup player area container (visible when preview player is active)
        playerAreaContainer = findViewById(R.id.playerAreaContainer);
        miniControlsOverlay = findViewById(R.id.miniControlsOverlay);
        miniBackBtn = findViewById(R.id.miniBackBtn);
        miniPlayPauseBtn = findViewById(R.id.miniPlayPauseBtn);
        miniLockBtn = findViewById(R.id.miniLockBtn);
        miniFullscreenBtn = findViewById(R.id.miniFullscreenBtn);
        miniSeekBar = findViewById(R.id.miniSeekBar);
        miniCurrentTime = findViewById(R.id.miniCurrentTime);
        miniTotalTime = findViewById(R.id.miniTotalTime);

        if (showPreview) {
            // Show player area at 16:9 aspect ratio
            playerAreaContainer.post(() -> {
                applyPlayerAreaSize();
                playerAreaContainer.setVisibility(View.VISIBLE);
            });
            // Hide thumb in topLayout when player is active
            thumbContainer.setVisibility(View.GONE);
            llPlayerPlace.setVisibility(View.GONE);
            ivThumb.setVisibility(View.GONE);

            // Tap on player area toggles mini controls
            llPlayerFragmentContainerBlock.setOnClickListener(v -> {
                if (miniControlsLocked) return;
                if (miniControlsVisible) {
                    hideMiniControls();
                } else {
                    showMiniControls();
                }
            });

            miniBackBtn.setOnClickListener(v -> {
                if (fullWindows) {
                    if (playFragment.onBackPressed()) return;
                    toggleFullPreview();
                    List<VodInfo.VodSeries> list = vodInfo != null ? vodInfo.seriesMap.get(vodInfo.playFlag) : null;
                    if (list != null) tvSeriesGroup.setVisibility(View.GONE); // 分组按钮已禁用
                    mGridView.requestFocus();
                } else {
                    onBackPressed();
                }
            });

            miniPlayPauseBtn.setOnClickListener(v -> {
                if (playFragment != null && playFragment.getPlayer() != null) {
                    if (playFragment.getPlayer().isPlaying()) {
                        playFragment.getPlayer().pause();
                        miniPlayPauseBtn.setImageResource(R.drawable.icon_play_mini);
                    } else {
                        playFragment.getPlayer().start();
                        miniPlayPauseBtn.setImageResource(R.drawable.icon_pause);
                    }
                    scheduleHideMiniControls();
                }
            });

            miniLockBtn.setOnClickListener(v -> {
                miniControlsLocked = !miniControlsLocked;
                miniLockBtn.setImageResource(miniControlsLocked ? R.drawable.icon_lock : R.drawable.icon_unlock);
                if (miniControlsLocked) {
                    // Only show lock button, hide rest of overlay
                    miniBackBtn.setVisibility(View.GONE);
                    miniPlayPauseBtn.setVisibility(View.GONE);
                    miniFullscreenBtn.setVisibility(View.GONE);
                    miniSeekBar.setVisibility(View.GONE);
                    miniCurrentTime.setVisibility(View.GONE);
                    miniTotalTime.setVisibility(View.GONE);
                    miniControlsHandler.removeCallbacks(hideControlsRunnable);
                } else {
                    miniBackBtn.setVisibility(View.VISIBLE);
                    miniPlayPauseBtn.setVisibility(View.VISIBLE);
                    miniFullscreenBtn.setVisibility(View.VISIBLE);
                    miniSeekBar.setVisibility(View.VISIBLE);
                    miniCurrentTime.setVisibility(View.VISIBLE);
                    miniTotalTime.setVisibility(View.VISIBLE);
                    scheduleHideMiniControls();
                }
            });

            miniFullscreenBtn.setOnClickListener(v -> {
                hideMiniControls();
                toggleFullPreview();
                if (firstReverse) {
                    jumpToPlay();
                    firstReverse = false;
                }
            });

            miniSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && playFragment != null && playFragment.getPlayer() != null) {
                        long duration = playFragment.getPlayer().getDuration();
                        long pos = duration * progress / 1000;
                        miniCurrentTime.setText(formatTime(pos));
                    }
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    if (playFragment != null && playFragment.getPlayer() != null) {
                        long duration = playFragment.getPlayer().getDuration();
                        long pos = duration * seekBar.getProgress() / 1000;
                        playFragment.getPlayer().seekTo(pos);
                    }
                    scheduleHideMiniControls();
                }
            });

            // Start seekbar update loop
            startMiniSeekBarUpdater();
        }

        mSeriesGroupView = findViewById(R.id.mSeriesGroupView);
        tvSeriesGroup = findViewById(R.id.mSeriesGroupTv);
        mSeriesGroupView.setHasFixedSize(true);
        mSeriesGroupView.setLayoutManager(new LinearLayoutManager(this.mContext, LinearLayoutManager.HORIZONTAL, false));
        seriesGroupAdapter = new BaseQuickAdapter<String, BaseViewHolder>(R.layout.item_series_group, seriesGroupOptions) {
            @Override
            protected void convert(BaseViewHolder helper, String item) {
                TextView tvSeries = helper.getView(R.id.tvSeriesGroup);
                tvSeries.setText(item);
                if (helper.getLayoutPosition() == getData().size() - 1) {
                    helper.itemView.setId(View.generateViewId());
                    helper.itemView.setNextFocusRightId(helper.itemView.getId());
                }else {
                    helper.itemView.setNextFocusRightId(View.NO_ID);
                }
            }
        };
        mSeriesGroupView.setAdapter(seriesGroupAdapter);

        if (tvPlay != null) tvPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                if (showPreview) {
                    toggleFullPreview();
                    if (firstReverse) {
                        jumpToPlay();
                        firstReverse = false;
                    }
                } else {
                    jumpToPlay();
                }
            }
        });

        tvQuickSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startQuickSearch();
                QuickSearchDialog quickSearchDialog = new QuickSearchDialog(DetailActivity.this);
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH, quickSearchData));
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_WORD, quickSearchWord));
                quickSearchDialog.show();
                if (pauseRunnable != null && pauseRunnable.size() > 0) {
                    searchExecutorService = Executors.newFixedThreadPool(5);
                    for (Runnable runnable : pauseRunnable) {
                        searchExecutorService.execute(runnable);
                    }
                    pauseRunnable.clear();
                    pauseRunnable = null;
                }
                quickSearchDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        try {
                            if (searchExecutorService != null) {
                                pauseRunnable = searchExecutorService.shutdownNow();
                                searchExecutorService = null;
                            }
                        } catch (Throwable th) {
                            th.printStackTrace();
                        }
                    }
                });
            }
        });
        tvCollect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = tvCollect.getText().toString();
                if ("加入收藏".equals(text)) {
                    RoomDataManger.insertVodCollect(sourceKey, vodInfo);
                    Toast.makeText(DetailActivity.this, "已加入收藏夹", Toast.LENGTH_SHORT).show();
                    tvCollect.setText("取消收藏");
                } else {
                    RoomDataManger.deleteVodCollect(sourceKey, vodInfo);
                    Toast.makeText(DetailActivity.this, "已移除收藏夹", Toast.LENGTH_SHORT).show();
                    tvCollect.setText("加入收藏");
                }
            }
        });
        tvPlayUrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //获取剪切板管理器
                ClipboardManager cm = (ClipboardManager)getSystemService(mContext.CLIPBOARD_SERVICE);
                //设置内容到剪切板
                cm.setPrimaryClip(ClipData.newPlainText(null, tvPlayUrl.getText().toString().replace("播放地址：","")));
                Toast.makeText(DetailActivity.this, "已复制", Toast.LENGTH_SHORT).show();
            }
        });

        // 下载按钮:弹选集弹窗
        TextView tvDownload = findViewById(R.id.tvDownload);
        if (tvDownload != null) {
            tvDownload.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (vodInfo == null || vodInfo.seriesMap == null || vodInfo.playFlag == null) {
                        Toast.makeText(DetailActivity.this, "暂无可下载剧集", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    new DownloadSelectDialog(DetailActivity.this, vodInfo, sourceKey).show();
                }
            });
        }

        // 投屏按钮:用默认选中的剧集,先 playerContent 解析直链,再弹设备搜索弹窗
        TextView tvCast = findViewById(R.id.tvCast);
        if (tvCast != null) {
            tvCast.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openCastDialog();
                }
            });
        }


        tvSeriesSort.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onClick(View v) {
                if (vodInfo != null && vodInfo.seriesMap.size() > 0) {
                    vodInfo.reverseSort = !vodInfo.reverseSort;
                    isReverse = !isReverse;
                    tvSeriesSort.setText(isReverse?"倒序":"正序");
                    vodInfo.reverse();
                    vodInfo.playIndex=(vodInfo.seriesMap.get(vodInfo.playFlag).size()-1)-vodInfo.playIndex;
                    firstReverse = !firstReverse;
                    setSeriesGroupOptions();
                    seriesAdapter.notifyDataSetChanged();

                    customSeriesScrollPos(vodInfo.playIndex);
                    if(currentSeriesGroupView != null) {
                        TextView txtView = currentSeriesGroupView.findViewById(R.id.tvSeriesGroup);
                        txtView.setTextColor(Color.WHITE);
                    }
                }
            }
        });
        tvDesc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        FastClickCheckUtil.check(v);
                        DescDialog dialog = new DescDialog(mContext);
                        dialog.setDescribe(removeHtmlTag(mVideo.des));
                        dialog.show();
                    }
                });
            }
        });

// phone: TV item listener removed - use adapter click callbacks
// phone: TV item listener removed - use adapter click callbacks
        seriesAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                if (vodInfo != null && vodInfo.seriesMap.get(vodInfo.playFlag).size() > 0) {
                    boolean reload = false;
                    boolean isAllowFull = false;
                    for (int j = 0; j < vodInfo.seriesMap.get(vodInfo.playFlag).size(); j++) {
                        seriesAdapter.getData().get(j).selected = false;
                        seriesAdapter.notifyItemChanged(j);
                    }
                    //解决倒叙不刷新
                    if (vodInfo.playIndex != position) {
                        seriesAdapter.getData().get(position).selected = true;
                        seriesAdapter.notifyItemChanged(position);
                        vodInfo.playIndex = position;

                        reload = true;
                    }
                    //解决当前集不刷新的BUG
                    if (!preFlag.isEmpty() && !vodInfo.playFlag.equals(preFlag)) {
                        reload = true;
                        isAllowFull = true;
                    }
                    boolean isCurrentPlaying = !showPreview || isCurrentPreviewPlaying(position);
                    if (showPreview && !isCurrentPlaying) {
                        reload = true;
                        isAllowFull = true;
                    }

                    seriesAdapter.getData().get(vodInfo.playIndex).selected = true;
                    seriesAdapter.notifyItemChanged(vodInfo.playIndex);
                    //选集全屏 想选集不全屏的注释下面一行
                    if (showPreview && !fullWindows && isCurrentPlaying && !isAllowFull && playFragment.getPlayer().isPlaying())toggleFullPreview();
                    if (!showPreview || reload) {
                        jumpToPlay();
                        firstReverse=false;
                    }
                }
            }
        });

// phone: TV item listener removed - use adapter click callbacks
        tvSeriesSort.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                tvSeriesSort.setTextColor(mContext.getResources().getColor(R.color.color_02F8E1));
                if (vodInfo != null && Objects.requireNonNull(vodInfo.seriesMap.get(vodInfo.playFlag)).size() > 0) {
                    int firstVisible = mGridViewLayoutMgr.findFirstVisibleItemPosition();
                    int lastVisible = mGridViewLayoutMgr.findLastVisibleItemPosition();
                    if (vodInfo.playIndex < firstVisible || vodInfo.playIndex > lastVisible) {
                        customSeriesScrollPos(vodInfo.playIndex);
                    }
                }
            } else {
                tvSeriesSort.setTextColor(Color.WHITE);
            }
        });
        seriesGroupAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                TextView newTxtView = view.findViewById(R.id.tvSeriesGroup);
                newTxtView.setTextColor(mContext.getResources().getColor(R.color.color_02F8E1));
                if (vodInfo != null && vodInfo.seriesMap.get(vodInfo.playFlag).size() > 0) {
                    int listSize = vodInfo.seriesMap.get(vodInfo.playFlag).size();
                    int targetPos = position * GroupCount;
                    // 如果是最后一个分组，滚动到末尾让该分组从顶部开始显示
                    int totalGroups = (listSize + GroupCount - 1) / GroupCount;
                    if (position == totalGroups - 1) {
                        // 末尾分组：先滚到底部，再回到该分组起始位置
                        mGridViewLayoutMgr.scrollToPositionWithOffset(listSize - 1, 0);
                        mGridView.post(() -> mGridViewLayoutMgr.scrollToPositionWithOffset(targetPos, 0));
                    } else {
                        mGridViewLayoutMgr.scrollToPositionWithOffset(targetPos, 0);
                    }
                }
                if(currentSeriesGroupView != null) {
                    TextView txtView = currentSeriesGroupView.findViewById(R.id.tvSeriesGroup);
                    txtView.setTextColor(Color.WHITE);
                }
                currentSeriesGroupView = view;
                currentSeriesGroupView.isSelected();
            }
        });

        if(showPreview){
            llPlayerFragmentContainerBlock.requestFocus();
        }else {
            if (tvPlay != null) tvPlay.requestFocus();
        }
        setLoadSir(llLayout);
    }

    //解决类似海贼王的超长动漫 焦点滚动失败的问题
    void customSeriesScrollPos(int targetPos)
    {
        mGridViewLayoutMgr.scrollToPositionWithOffset(targetPos>10?targetPos - 10:0, 0);
        mGridView.postDelayed(() -> {
            this.smoothScroller.setTargetPosition(targetPos);
            mGridViewLayoutMgr.startSmoothScroll(smoothScroller);
            mGridView.smoothScrollToPosition(targetPos);
        }, 50);
    }

    private void initCheckedSourcesForSearch() {
        mCheckSources = SearchHelper.getSourcesForSearch();
    }

    private List<Runnable> pauseRunnable = null;

    private void jumpToPlay() {
        if (vodInfo != null && vodInfo.seriesMap.get(vodInfo.playFlag).size() > 0) {
            preFlag = vodInfo.playFlag;
            //更新播放地址
            setTextShow(tvPlayUrl, "播放地址：", vodInfo.seriesMap.get(vodInfo.playFlag).get(vodInfo.playIndex).url);
            Bundle bundle = new Bundle();
            //保存历史
            insertVod(firstsourceKey, vodInfo);
        //   insertVod(sourceKey, vodInfo);
            bundle.putString("sourceKey", sourceKey);
//            bundle.putSerializable("VodInfo", vodInfo);
            App.getInstance().setVodInfo(vodInfo);
            if (showPreview) {
                if (previewVodInfo == null) {
                    try {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        ObjectOutputStream oos = new ObjectOutputStream(bos);
                        oos.writeObject(vodInfo);
                        oos.flush();
                        oos.close();
                        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
                        previewVodInfo = (VodInfo) ois.readObject();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (previewVodInfo != null) {
                    previewVodInfo.playerCfg = vodInfo.playerCfg;
                    previewVodInfo.playFlag = vodInfo.playFlag;
                    previewVodInfo.playIndex = vodInfo.playIndex;
                    previewVodInfo.seriesMap = vodInfo.seriesMap;
//                    bundle.putSerializable("VodInfo", previewVodInfo);
                    App.getInstance().setVodInfo(previewVodInfo);
                }
                playFragment.setData(bundle);
            } else {
                jumpActivity(PlayActivity.class, bundle);
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    void refreshList() {
        if (vodInfo.seriesMap.get(vodInfo.playFlag).size() <= vodInfo.playIndex) {
            vodInfo.playIndex = 0;
        }

        if (vodInfo.seriesMap.get(vodInfo.playFlag) != null) {
            boolean canSelect = true;
            for (int j = 0; j < vodInfo.seriesMap.get(vodInfo.playFlag).size(); j++) {
                if(vodInfo.seriesMap.get(vodInfo.playFlag).get(j).selected){
                    canSelect = false;
                    break;
                }
            }
            if(canSelect)vodInfo.seriesMap.get(vodInfo.playFlag).get(vodInfo.playIndex).selected = true;
        }

        Paint pFont = new Paint();
        Rect rect = new Rect();

        List<VodInfo.VodSeries> list = vodInfo.seriesMap.get(vodInfo.playFlag);
        int listSize = list.size();
        mGridViewLayoutMgr.setSpanCount(com.mobile.novabox.util.PadUiHelper.getEpisodeSpanCount(this));
        seriesAdapter.setNewData(vodInfo.seriesMap.get(vodInfo.playFlag));

        setSeriesGroupOptions();

        mGridView.postDelayed(new Runnable() {
            @Override
            public void run() {
//                mGridView.smoothScrollToPosition(vodInfo.playIndex);
                customSeriesScrollPos(vodInfo.playIndex);
            }
        }, 100);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void setSeriesGroupOptions(){
        List<VodInfo.VodSeries> list = vodInfo.seriesMap.get(vodInfo.playFlag);
        int listSize = list.size();
        int offset = mGridViewLayoutMgr.getSpanCount();
        seriesGroupOptions.clear();
        // Phone: fixed 12 episodes per group
        GroupCount = 12;
        if(listSize>100 && listSize<=400)GroupCount=60;
        if(listSize>400)GroupCount=120;
        if(listSize > 1) {
            tvSeriesGroup.setVisibility(View.GONE); // 分组按钮已禁用
            int remainedOptionSize = listSize % GroupCount;
            int optionSize = listSize / GroupCount;

            for(int i = 0; i < optionSize; i++) {
                if(vodInfo.reverseSort)
//                    seriesGroupOptions.add(String.format("%d - %d", i * GroupCount + GroupCount, i * GroupCount + 1));
                    seriesGroupOptions.add(String.format("%d - %d", listSize - (i * GroupCount + 1)+1, listSize - (i * GroupCount + GroupCount)+1));
                else
                    seriesGroupOptions.add(String.format("%d - %d", i * GroupCount + 1, i * GroupCount + GroupCount));
            }
            if(remainedOptionSize > 0) {
                if(vodInfo.reverseSort)
//                    seriesGroupOptions.add(String.format("%d - %d", optionSize * GroupCount + remainedOptionSize, optionSize * GroupCount + 1));
                    seriesGroupOptions.add(String.format("%d - %d", listSize - (optionSize * GroupCount + 1)+1, listSize - (optionSize * GroupCount + remainedOptionSize)+1));
                else
                    seriesGroupOptions.add(String.format("%d - %d", optionSize * GroupCount + 1, optionSize * GroupCount + remainedOptionSize));
            }
//            if(vodInfo.reverseSort) Collections.reverse(seriesGroupOptions);

            seriesGroupAdapter.notifyDataSetChanged();
        }else {
            tvSeriesGroup.setVisibility(View.GONE);
        }
    }

    private void setTextShow(TextView view, String tag, String info) {
        if (info == null || info.trim().isEmpty()) {
            view.setVisibility(View.GONE);
            return;
        }
        view.setVisibility(View.VISIBLE);
        view.setText(Html.fromHtml(getHtml(tag, info)));
    }

    private String removeHtmlTag(String info) {
        if (info == null)
            return "";
        return info.replaceAll("\\<.*?\\>", "").replaceAll("\\s", "");
    }

    private void applyPreviewRoundCorners() {
        // No rounded corners on full-width player area
    }

    private void setPreviewRoundClip(boolean enable) {
        // No-op: full-width player has no round clip
    }


    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.detailResult.observe(this, new Observer<AbsXml>() {
            @Override
            public void onChanged(AbsXml absXml) {
                if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
                    showSuccess();

                    mVideo = absXml.movie.videoList.get(0);
                    mVideo.id = vodId;
                    if (TextUtils.isEmpty(mVideo.name))mVideo.name = vod_name;
                    if (TextUtils.isEmpty(mVideo.name))mVideo.name = "TVBox";
                    vodInfo = new VodInfo();
                    if((mVideo.pic==null || mVideo.pic.isEmpty()) && !vod_picture.isEmpty()){
                        mVideo.pic=vod_picture;
                    }
                    vodInfo.setVideo(mVideo);
                    vodInfo.sourceKey = mVideo.sourceKey;
                    sourceKey = mVideo.sourceKey;

                    tvName.setText(mVideo.name);
                    setTextShow(tvSite, "来源：", ApiConfig.get().getSource(firstsourceKey).getName());
                    setTextShow(tvYear, "年份：", mVideo.year == 0 ? "" : String.valueOf(mVideo.year));
                    setTextShow(tvArea, "地区：", mVideo.area);
                    setTextShow(tvLang, "语言：", mVideo.lang);
                    if (!firstsourceKey.equals(sourceKey)) {
                    	setTextShow(tvType, "类型：", "[" + ApiConfig.get().getSource(sourceKey).getName() + "] 解析");
                    } else {
                    	setTextShow(tvType, "类型：", mVideo.type);
                    }
                    setTextShow(tvActor, "演员：", mVideo.actor);
                    setTextShow(tvDirector, "导演：", mVideo.director);
                    if (!TextUtils.isEmpty(mVideo.pic)) {
                        Picasso.get()
                                .load(DefaultConfig.checkReplaceProxy(mVideo.pic))
                                .transform(new RoundTransformation(MD5.string2MD5(mVideo.pic))
                                        .centerCorp(true)
                                        .override(AutoSizeUtils.mm2px(mContext, 300), AutoSizeUtils.mm2px(mContext, 400))
                                        .roundRadius(AutoSizeUtils.mm2px(mContext, 10), RoundTransformation.RoundType.ALL))
                                .placeholder(R.drawable.img_loading_placeholder)
                                .noFade()
                                .error(R.drawable.img_loading_placeholder)
                                .into(ivThumb);
                    } else {
                        ivThumb.setImageResource(R.drawable.img_loading_placeholder);
                    }

                    if (vodInfo.seriesMap != null && vodInfo.seriesMap.size() > 0) {
                        mGridViewFlag.setVisibility(View.VISIBLE);
                        mGridView.setVisibility(View.VISIBLE);
                        if (tvPlay != null) tvPlay.setVisibility(View.VISIBLE);
                        mEmptyPlayList.setVisibility(View.GONE);

                        VodInfo vodInfoRecord = RoomDataManger.getVodInfo(sourceKey, vodId);
                        // 读取历史记录
                        if (vodInfoRecord != null) {
                            vodInfo.playIndex = Math.max(vodInfoRecord.playIndex, 0);
                            vodInfo.playFlag = vodInfoRecord.playFlag;
                            vodInfo.playerCfg = vodInfoRecord.playerCfg;
                            vodInfo.reverseSort = vodInfoRecord.reverseSort;
                        } else {
                            vodInfo.playIndex = 0;
                            vodInfo.playFlag = null;
                            vodInfo.playerCfg = "";
                            vodInfo.reverseSort = false;
                        }

                        if (vodInfo.reverseSort) {
                            vodInfo.reverse();
                        }

                        if (vodInfo.playFlag == null || !vodInfo.seriesMap.containsKey(vodInfo.playFlag))
                            vodInfo.playFlag = (String) vodInfo.seriesMap.keySet().toArray()[0];

                        int flagScrollTo = 0;
                        for (int j = 0; j < vodInfo.seriesFlags.size(); j++) {
                            VodInfo.VodSeriesFlag flag = vodInfo.seriesFlags.get(j);
                            if (flag.name.equals(vodInfo.playFlag)) {
                                flagScrollTo = j;
                                flag.selected = true;
                            } else
                                flag.selected = false;
                        }
                        //设置播放地址
                        setTextShow(tvPlayUrl, "播放地址：", vodInfo.seriesMap.get(vodInfo.playFlag).get(0).url);
                        seriesFlagAdapter.setNewData(vodInfo.seriesFlags);
                        mGridViewFlag.scrollToPosition(flagScrollTo);

                        refreshList();
                        if (showPreview) {
                            jumpToPlay();
                            llPlayerFragmentContainer.setVisibility(View.VISIBLE);
                            llPlayerFragmentContainerBlock.setVisibility(View.VISIBLE);
                            if (playerAreaContainer != null) playerAreaContainer.setVisibility(View.VISIBLE);
                            toggleSubtitleTextSize();
                        }
                        // startQuickSearch();
                    } else {
                        mGridViewFlag.setVisibility(View.GONE);
                        mGridView.setVisibility(View.GONE);
                        tvSeriesGroup.setVisibility(View.GONE);
                        if (tvPlay != null) tvPlay.setVisibility(View.GONE);
                        mEmptyPlayList.setVisibility(View.VISIBLE);
                    }
                } else {
                    showEmpty();
                    llPlayerFragmentContainer.setVisibility(View.GONE);
                    llPlayerFragmentContainerBlock.setVisibility(View.GONE);
                    if (playerAreaContainer != null) playerAreaContainer.setVisibility(View.GONE);
                }
            }
        });
    }

    private String getHtml(String label, String content) {
        if (content == null) {
            content = "";
        }
        return label + "<font color=\"#000000\">" + content + "</font>";
    }

    private String  vod_picture="";
    private String  vod_name="";
    private void initData() {
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            Bundle bundle = intent.getExtras();
            vod_name=bundle.getString("title", "");
            vod_picture=bundle.getString("picture", "");
            loadDetail(bundle.getString("id", null), bundle.getString("sourceKey", ""));
        }
    }

    private void loadDetail(String vid, String key) {
        if (vid != null) {
            vodId = vid;
            sourceKey = key;
            firstsourceKey = key;
            showLoading();
            sourceViewModel.getDetail(sourceKey, vodId);
            boolean isVodCollect = RoomDataManger.isVodCollect(sourceKey, vodId);
            if (isVodCollect) {
                tvCollect.setText("取消收藏");
            } else {
                tvCollect.setText("加入收藏");
            }
        }
    }


    private boolean isFirstLoad = true;
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_REFRESH) {
            if (event.obj != null) {
                if (event.obj instanceof VodInfo) {
                    syncPlayingVodInfo((VodInfo) event.obj);
                } else if (event.obj instanceof Integer) {
                    int index = (int) event.obj;
                    for (int j = 0; j < Objects.requireNonNull(vodInfo.seriesMap.get(vodInfo.playFlag)).size(); j++) {
                        seriesAdapter.getData().get(j).selected = false;
                        seriesAdapter.notifyItemChanged(j);
                    }
                    seriesAdapter.getData().get(index).selected = true;
                    seriesAdapter.notifyItemChanged(index);
                    if(!isFirstLoad)mGridView.scrollToPosition(index);
                    vodInfo.playIndex = index;
                    //保存历史
                    insertVod(firstsourceKey, vodInfo);
                    isFirstLoad = false;
                } else if (event.obj instanceof JSONObject) {
                    vodInfo.playerCfg = event.obj.toString();
                    //保存历史
                    insertVod(firstsourceKey, vodInfo);
                } else if (event.obj instanceof String) {
                    String url = event.obj.toString();
                    //设置更新播放地址
                    setTvPlayUrl(url);
                }

            }
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_SELECT) {
            if (event.obj != null) {
                Movie.Video video = (Movie.Video) event.obj;
                vod_name = video.name;
                vod_picture = video.pic;
                loadDetail(video.id, video.sourceKey);
            }
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_WORD_CHANGE) {
            if (event.obj != null) {
                String word = (String) event.obj;
                switchSearchWord(word);
            }
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_RESULT) {
            try {
                searchData(event.obj == null ? null : (AbsXml) event.obj);
            } catch (Exception e) {
                searchData(null);
            }
        }
    }

    private String searchTitle = "";
    private boolean hadQuickStart = false;
    private final List<Movie.Video> quickSearchData = new ArrayList<>();
    private final List<String> quickSearchWord = new ArrayList<>();
    private ExecutorService searchExecutorService = null;

    private void switchSearchWord(String word) {
        OkGo.getInstance().cancelTag("quick_search");
        quickSearchData.clear();
        searchTitle = word;
        searchResult();
    }

    private void startQuickSearch() {
        initCheckedSourcesForSearch();
        if (hadQuickStart)
            return;
        hadQuickStart = true;
        OkGo.getInstance().cancelTag("quick_search");
        quickSearchWord.clear();
        searchTitle = mVideo.name;
        quickSearchData.clear();
        quickSearchWord.addAll(SearchHelper.splitWords(searchTitle));
        // 分词
//        OkGo.<String>get("http://api.pullword.com/get.php?source=" + URLEncoder.encode(searchTitle) + "&param1=0&param2=0&json=1")
//                .tag("fenci")
//                .execute(new AbsCallback<String>() {
//                    @Override
//                    public String convertResponse(okhttp3.Response response) throws Throwable {
//                        if (response.body() != null) {
//                            return response.body().string();
//                        } else {
//                            throw new IllegalStateException("网络请求错误");
//                        }
//                    }
//
//                    @Override
//                    public void onSuccess(Response<String> response) {
//                        String json = response.body();
//                        try {
//                            for (JsonElement je : new Gson().fromJson(json, JsonArray.class)) {
//                                quickSearchWord.add(je.getAsJsonObject().get("t").getAsString());
//                            }
//                        } catch (Throwable th) {
//                            th.printStackTrace();
//                        }
//                        List<String> words = new ArrayList<>(new HashSet<>(quickSearchWord));
//                        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_WORD, words));
//                    }
//
//                    @Override
//                    public void onError(Response<String> response) {super.onError(response);}
//                });

        searchResult();
    }

    private void searchResult() {
        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        searchExecutorService = Executors.newFixedThreadPool(5);
        List<SourceBean> searchRequestList = new ArrayList<>();
        searchRequestList.addAll(ApiConfig.get().getSourceBeanList());
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        searchRequestList.remove(home);
        searchRequestList.add(0, home);

        ArrayList<String> siteKey = new ArrayList<>();
        for (SourceBean bean : searchRequestList) {
            if (!bean.isSearchable() || !bean.isQuickSearch()) {
                continue;
            }
            if (mCheckSources != null && !mCheckSources.containsKey(bean.getKey())) {
                continue;
            }
            siteKey.add(bean.getKey());
        }
        for (String key : siteKey) {
            searchExecutorService.execute(new Runnable() {
                @Override
                public void run() {
                    sourceViewModel.getQuickSearch(key, searchTitle);
                }
            });
        }
    }

    private void searchData(AbsXml absXml) {
        if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
            List<Movie.Video> data = new ArrayList<>();
            for (Movie.Video video : absXml.movie.videoList) {
                // 去除当前相同的影片
                if (video.sourceKey.equals(sourceKey) && video.id.equals(vodId))
                    continue;
                data.add(video);
            }
            quickSearchData.addAll(data);
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH, data));
        }
    }

    private void syncPlayingVodInfo(VodInfo playingVodInfo) {
        if (playingVodInfo == null || vodInfo == null || vodInfo.seriesMap == null) {
            return;
        }
        String newFlag = playingVodInfo.playFlag;
        if (TextUtils.isEmpty(newFlag) || !vodInfo.seriesMap.containsKey(newFlag)) {
            return;
        }
        List<VodInfo.VodSeries> newSeriesList = vodInfo.seriesMap.get(newFlag);
        if (newSeriesList == null || newSeriesList.isEmpty()) {
            return;
        }

        VodInfo.VodSeries playingSeries = getPlayingSeries(playingVodInfo, newFlag);
        int newIndex = findSameEpisodeIndex(playingSeries, newSeriesList, playingVodInfo.playIndex);
        vodInfo.playFlag = newFlag;
        vodInfo.playIndex = newIndex;
        if (playingVodInfo.playerCfg != null) {
            vodInfo.playerCfg = playingVodInfo.playerCfg;
        }

        for (VodInfo.VodSeriesFlag flag : vodInfo.seriesFlags) {
            flag.selected = flag.name.equals(newFlag);
        }
        for (List<VodInfo.VodSeries> seriesList : vodInfo.seriesMap.values()) {
            if (seriesList == null) {
                continue;
            }
            for (VodInfo.VodSeries series : seriesList) {
                series.selected = false;
            }
        }
        newSeriesList.get(newIndex).selected = true;

        seriesFlagAdapter.notifyDataSetChanged();
        refreshList();
        setTvPlayUrl(newSeriesList.get(newIndex).url);

        int flagIndex = -1;
        for (int i = 0; i < vodInfo.seriesFlags.size(); i++) {
            if (vodInfo.seriesFlags.get(i).name.equals(newFlag)) {
                flagIndex = i;
                break;
            }
        }
        if (flagIndex >= 0) {
            mGridViewFlag.scrollToPosition(flagIndex);
            if (mGridViewFlag.hasFocus()) {
                mGridViewFlag.scrollToPosition(flagIndex);
            }
        }
        if (!isFirstLoad && mGridView.hasFocus()) {
            mGridView.scrollToPosition(newIndex);
        }

        insertVod(firstsourceKey, vodInfo);
        isFirstLoad = false;
    }

    private VodInfo.VodSeries getPlayingSeries(VodInfo playingVodInfo, String flag) {
        if (playingVodInfo == null || playingVodInfo.seriesMap == null || TextUtils.isEmpty(flag)) {
            return null;
        }
        List<VodInfo.VodSeries> playingList = playingVodInfo.seriesMap.get(flag);
        if (playingList == null || playingList.isEmpty()) {
            return null;
        }
        int safeIndex = Math.max(0, Math.min(playingVodInfo.playIndex, playingList.size() - 1));
        return playingList.get(safeIndex);
    }

    private boolean isCurrentPreviewPlaying(int position) {
        if (!showPreview || previewVodInfo == null || vodInfo == null || vodInfo.seriesMap == null || TextUtils.isEmpty(vodInfo.playFlag)) {
            return false;
        }
        if (!TextUtils.equals(vodInfo.playFlag, previewVodInfo.playFlag) || previewVodInfo.playIndex != position) {
            return false;
        }
        List<VodInfo.VodSeries> currentList = vodInfo.seriesMap.get(vodInfo.playFlag);
        if (currentList == null || position < 0 || position >= currentList.size()) {
            return false;
        }
        VodInfo.VodSeries currentSeries = currentList.get(position);
        VodInfo.VodSeries previewSeries = getPlayingSeries(previewVodInfo, previewVodInfo.playFlag);
        return currentSeries != null && previewSeries != null && TextUtils.equals(currentSeries.url, previewSeries.url);
    }

    private int findSameEpisodeIndex(VodInfo.VodSeries currentSeries, List<VodInfo.VodSeries> targetList, int fallbackIndex) {
        if (targetList == null || targetList.isEmpty()) {
            return 0;
        }
        if (currentSeries != null && !TextUtils.isEmpty(currentSeries.name)) {
            String currentName = normalizeEpisodeName(currentSeries.name);
            for (int i = 0; i < targetList.size(); i++) {
                VodInfo.VodSeries targetSeries = targetList.get(i);
                if (targetSeries != null && currentName.equals(normalizeEpisodeName(targetSeries.name))) {
                    return i;
                }
            }
            int currentEpisode = extractEpisodeNumber(currentSeries.name);
            if (currentEpisode >= 0) {
                for (int i = 0; i < targetList.size(); i++) {
                    VodInfo.VodSeries targetSeries = targetList.get(i);
                    if (targetSeries != null && extractEpisodeNumber(targetSeries.name) == currentEpisode) {
                        return i;
                    }
                }
            }
        }
        return Math.max(0, Math.min(fallbackIndex, targetList.size() - 1));
    }

    private String normalizeEpisodeName(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .replaceAll("[\\[\\]【】()（）]", "")
                .replace("第", "")
                .replace("集", "")
                .replace("话", "")
                .replace("期", "");
    }

    private int extractEpisodeNumber(String name) {
        if (name == null) {
            return -1;
        }
        Matcher episodeMatcher = Pattern.compile("(?:第)?(\\d+)(?:集|话|期|$)").matcher(name);
        if (episodeMatcher.find()) {
            try {
                return Integer.parseInt(episodeMatcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        Matcher matcher = Pattern.compile("\\d+").matcher(name);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group());
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    private void insertVod(String sourceKey, VodInfo vodInfo) {
        try {
            vodInfo.playNote = vodInfo.seriesMap.get(vodInfo.playFlag).get(vodInfo.playIndex).name;
        } catch (Throwable th) {
            vodInfo.playNote = "";
        }
        RoomDataManger.insertVodRecord(sourceKey, vodInfo);
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_HISTORY_REFRESH));
    }

    // ===================== Mini player controls =====================

    private void showMiniControls() {
        if (miniControlsLocked) return;
        miniControlsOverlay.setVisibility(View.VISIBLE);
        miniControlsVisible = true;
        scheduleHideMiniControls();
    }

    private void hideMiniControls() {
        if (miniControlsLocked) return;
        miniControlsOverlay.setVisibility(View.GONE);
        miniControlsVisible = false;
    }

    private void scheduleHideMiniControls() {
        miniControlsHandler.removeCallbacks(hideControlsRunnable);
        miniControlsHandler.postDelayed(hideControlsRunnable, 3000);
    }

    private final Handler miniSeekBarHandler = new Handler();
    private final Runnable miniSeekBarUpdater = new Runnable() {
        @Override
        public void run() {
            if (showPreview && playFragment != null && playFragment.getPlayer() != null) {
                long duration = playFragment.getPlayer().getDuration();
                long pos = playFragment.getPlayer().getCurrentPosition();
                if (duration > 0) {
                    miniSeekBar.setProgress((int) (pos * 1000 / duration));
                    miniCurrentTime.setText(formatTime(pos));
                    miniTotalTime.setText(formatTime(duration));
                }
                boolean playing = playFragment.getPlayer().isPlaying();
                miniPlayPauseBtn.setImageResource(playing ? R.drawable.icon_pause : R.drawable.icon_play_mini);
            }
            miniSeekBarHandler.postDelayed(this, 500);
        }
    };

    private void startMiniSeekBarUpdater() {
        miniSeekBarHandler.postDelayed(miniSeekBarUpdater, 500);
    }

    private String formatTime(long ms) {
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes >= 60) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", minutes / 60, minutes % 60, seconds);
        }
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    // ================================================================

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // 通知权限(投屏代理保活服务用)的申请结果不需要特殊处理:
        // CastProxyService 无论是否拿到通知权限都会正常启动运行,
        // 拒绝只影响用户能不能看到那条"投屏代理运行中"的提示。
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        miniSeekBarHandler.removeCallbacksAndMessages(null);
        miniControlsHandler.removeCallbacksAndMessages(null);
        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        OkGo.getInstance().cancelTag("fenci");
        OkGo.getInstance().cancelTag("detail");
        OkGo.getInstance().cancelTag("quick_search");
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onBackPressed() {
        if (fullWindows) {
            if (playFragment.onBackPressed())
                return;
            toggleFullPreview();
            // 分组行可见性统一由 toggleFullPreview() 内部控制(强制 GONE,"1-12""13-20" 分组分页已禁用)
            // —— 之前这里独立按 list.size()>1 重新显示 tvSeriesGroup,会盖掉 toggleFullPreview 里的 GONE,
            // 导致退出全屏后"1-12""13-16"这类分组行再次跳出来。
            mGridView.requestFocus();
            return;
        }
        if (seriesSelect) {
            if (seriesFlagFocus != null && !seriesFlagFocus.isFocused()) {
                seriesFlagFocus.requestFocus();
                return;
            }
        }
        if(showPreview && playFragment!=null){
            playFragment.setPlayTitle(false);
            playFragment.setExitingPreview(true);
        }
        super.onBackPressed();
    }

    // preview
    VodInfo previewVodInfo = null;
    boolean fullWindows = false;
    // 保存小屏时 fragment 容器的宽高值（整数，避免保存引用导致意外修改）
    int windowsPreviewWidth = ViewGroup.LayoutParams.MATCH_PARENT;
    int windowsPreviewHeight = ViewGroup.LayoutParams.MATCH_PARENT;
    // 保存 playerAreaContainer 在 LinearLayout 中的原始位置，用于退出全屏时还原
    ViewGroup playerAreaOriginalParent = null;
    int playerAreaOriginalIndex = -1;
    ViewGroup.LayoutParams playerAreaOriginalLp = null;

    void toggleFullPreview() {
        // 首次进全屏前，保存小屏状态的宽高（值而非引用）
        if (!fullWindows) {
            ViewGroup.LayoutParams cur = llPlayerFragmentContainer.getLayoutParams();
            windowsPreviewWidth = cur.width;
            windowsPreviewHeight = cur.height;
        }
        fullWindows = !fullWindows;
        if (playFragment != null) {
            playFragment.setAutoSwitchLineEnabled(!fullWindows);
            // 真全屏时把控制条职责交给 VodController 自身;小屏时交还给 miniControlsOverlay
            playFragment.setControllerFullscreenMode(fullWindows);
        }

        if (fullWindows) {
            // ---- 进入横屏全屏 ----
            // 平板端已处于横屏分栏布局，不需要也不应该强制旋转屏幕方向，
            // 否则退出全屏时的 setRequestedOrientation(PORTRAIT) 会破坏分栏布局。
            // 根据手机端全屏方向策略决定是否旋转（平板端跳过）
            com.mobile.novabox.util.OrientationHelper.applyDetailEnterFullscreen(DetailActivity.this);
            // 隐藏系统 UI
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getWindow().getInsetsController().hide(
                    android.view.WindowInsets.Type.statusBars() |
                    android.view.WindowInsets.Type.navigationBars());
            } else {
                getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            }

            // 将 playerAreaContainer 从 LinearLayout 中脱离，直接挂到 decorView 根布局
            // 这样才能真正覆盖整个屏幕，避免受 LinearLayout 高度约束
            if (playerAreaContainer != null) {
                playerAreaOriginalParent = (ViewGroup) playerAreaContainer.getParent();
                playerAreaOriginalIndex = playerAreaOriginalParent.indexOfChild(playerAreaContainer);
                playerAreaOriginalLp = playerAreaContainer.getLayoutParams();
                playerAreaOriginalParent.removeView(playerAreaContainer);

                FrameLayout decorRoot = (FrameLayout) getWindow().getDecorView();
                FrameLayout.LayoutParams fullLp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                decorRoot.addView(playerAreaContainer, fullLp);
            }

            // 播放器 fragment 容器铺满
            llPlayerFragmentContainer.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            setPreviewRoundClip(false);
            llPlayerFragmentContainerBlock.setVisibility(View.GONE);
            mGridView.setVisibility(View.GONE);
            mGridViewFlag.setVisibility(View.GONE);
            tvSeriesGroup.setVisibility(View.GONE);
            if (miniControlsOverlay != null) miniControlsOverlay.setVisibility(View.GONE);

        } else {
            // ---- 退出全屏，恢复小屏 ----
            // 平板端不操作屏幕方向（始终保持横屏分栏），只恢复 View 层布局；
            // 手机端才切回竖屏。
            // 根据手机端全屏方向策略恢复方向（平板端跳过）
            com.mobile.novabox.util.OrientationHelper.applyDetailExitFullscreen(DetailActivity.this);
            // 恢复系统 UI
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getWindow().getInsetsController().show(
                    android.view.WindowInsets.Type.statusBars() |
                    android.view.WindowInsets.Type.navigationBars());
            } else {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }

            // 将 playerAreaContainer 从 decorView 移回原来的父布局
            if (playerAreaContainer != null && playerAreaOriginalParent != null) {
                FrameLayout decorRoot = (FrameLayout) getWindow().getDecorView();
                decorRoot.removeView(playerAreaContainer);
                playerAreaOriginalParent.addView(playerAreaContainer, playerAreaOriginalIndex, playerAreaOriginalLp);

                // 不再依赖“进全屏前保存的旧 LayoutParams”，因为它在多次进出全屏、
                // 或者 Activity 因 screenLayout 变化不再重建之后，可能已经不能准确
                // 反映当前分栏/屏幕宽度，从而导致播放区域高度错误（画面悬浮、四周留黑边）。
                // 统一改为退出全屏后重新按当前宽度计算一次 16:9 高度（与首次进入播放页
                // 使用完全相同的计算方式，见 applyPlayerAreaSize()），确保两处逻辑一致。
                playerAreaContainer.post(this::applyPlayerAreaSize);
            }

            // 内部播放 Fragment 容器始终铺满 playerAreaContainer，与 playerAreaContainer 的尺寸保持同步，
            // 避免两个视图分别用不同时机/不同数据源设置尺寸造成的瞬时错位
            llPlayerFragmentContainer.setLayoutParams(
                    new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            setPreviewRoundClip(true);
            llPlayerFragmentContainerBlock.setVisibility(View.VISIBLE);
            mGridView.setVisibility(View.VISIBLE);
            mGridViewFlag.setVisibility(View.VISIBLE);
            // 分组按钮已禁用(与 setSeriesGroupOptions() 中的开关保持一致)，
            // 之前这里独立按 list.size()>1 重新计算可见性，导致退出全屏后
            // "1-12""13-16"这类分组分页行被错误地重新显示出来。
            tvSeriesGroup.setVisibility(View.GONE);
            // 恢复 mini 控制条的可见性逻辑（进入全屏时被强制隐藏），保持与首次进入播放页一致
            if (miniControlsOverlay != null) {
                showMiniControls();
            }

            // 由于 Activity 不再因横竖屏切换而重建（configChanges 已声明 screenLayout 等），
            // playerAreaContainer 被从 decorView 移回原父布局后，部分设备/播放器内核
            // （尤其是 Pad 端左右分栏布局）不会自动触发一次完整的重新测量/布局，
            // 导致播放画面仍按退出前的尺寸渲染，出现画面缩小悬浮、周围大片黑边的问题。
            // 这里显式强制这条视图链重新测量布局，并在下一帧结束后再触发一次，
            // 确保播放器内核（TextureView/SurfaceView）拿到的是恢复后的真实尺寸。
            forceRelayoutPlayerArea();
        }
        toggleSubtitleTextSize();
    }

    /**
     * 统一计算并应用 playerAreaContainer 的宽高（16:9），供首次进入播放页
     * 和退出全屏恢复小屏两处场景共用同一套计算逻辑，避免两处结果不一致
     * 导致的画面悬浮/留黑边问题。
     * Pad 端播放器位于左栏（约屏幕宽度的 73%），手机端铺满整个宽度。
     */
    private void applyPlayerAreaSize() {
        if (playerAreaContainer == null) return;
        int screenW = playerAreaContainer.getRootView().getWidth();
        int w = com.mobile.novabox.util.PadUiHelper.isPad(mContext)
                ? (int) (screenW * 0.73f) : screenW;
        if (w <= 0) return;
        int h = w * 9 / 16;
        ViewGroup.LayoutParams lp = playerAreaContainer.getLayoutParams();
        if (lp == null) return;
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.height = h;
        playerAreaContainer.setLayoutParams(lp);
        playerAreaOriginalLp = lp;
    }

    /**
     * 强制播放器容器链重新测量布局，修复退出全屏后（Activity 未重建的情况下）
     * 播放画面未按恢复后的容器尺寸重新铺满、画面悬浮缩小在中间的问题。
     *
     * 单纯调用 requestLayout() 只能让容器（ViewGroup）本身重新走一遍测量流程，
     * 但播放内核（TextureView/SurfaceView）在被从 decorView 摘下再挂回原布局后，
     * 其内部渲染 Surface 的尺寸缓存不一定会跟着刷新——它是在 SurfaceTexture 的
     * onSurfaceTextureSizeChanged 回调里才会真正更新绘制尺寸，而这个回调只在
     * View 的实际测量尺寸发生变化时才会触发。如果测量前后数值恰好一致
     * （或者内核没有正确监听到这次测量），画面就会停留在退出全屏前的（悬浮/居中）状态。
     *
     * 这里改用更强的做法：先把播放视图整体隐藏再显示（GONE -> VISIBLE），
     * 这会强制 View 树完全脱离当前的测量缓存，下一次显示时必定重新走一次完整的
     * measure/layout，从而让播放内核的渲染 Surface 拿到正确的新尺寸。
     */
    private void forceRelayoutPlayerArea() {
        relayoutPlayerAreaOnce();
        if (playerAreaContainer != null) {
            playerAreaContainer.post(this::relayoutPlayerAreaOnce);
            playerAreaContainer.postDelayed(this::relayoutPlayerAreaOnce, 150);
        }
    }

    private void relayoutPlayerAreaOnce() {
        if (fullWindows) return; // 已经又切回全屏，跳过过期的延迟回调
        applyPlayerAreaSize();
        if (playerAreaContainer != null) {
            playerAreaContainer.requestLayout();
        }
        if (llPlayerFragmentContainer != null) {
            llPlayerFragmentContainer.requestLayout();
        }
        MyVideoView player = (playFragment != null) ? playFragment.getPlayer() : null;
        if (player != null) {
            // 平板端不切换屏幕方向，View 挂回原位后尺寸变化可预期，
            // requestLayout() 就能让播放内核拿到正确尺寸，无需 GONE/VISIBLE，
            // 避免产生一帧空白画面（闪屏）。
            // 手机端仍用 GONE->VISIBLE 强制刷新（方向切换后内核缓存可能未更新）。
            if (com.mobile.novabox.util.PadUiHelper.isPad(mContext)) {
                player.requestLayout();
            } else {
                // GONE -> VISIBLE 强制播放内核（TextureView/SurfaceView）丢弃旧的测量缓存，
                // 下一帧必定重新measure，从而按当前容器尺寸重新铺满画面。
                player.setVisibility(View.GONE);
                player.requestLayout();
                player.post(() -> {
                    player.setVisibility(View.VISIBLE);
                    player.requestLayout();
                });
            }
        }
    }

    void toggleSubtitleTextSize() {
        int subtitleTextSize  = SubtitleHelper.getTextSize(this);
        if (!fullWindows) {
            subtitleTextSize *= 0.6;
        }
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SUBTITLE_SIZE_CHANGE, subtitleTextSize));
    }

    private void setTvPlayUrl(String url)
    {
        setTextShow(tvPlayUrl, "播放地址：", url);
    }

    /**
     * 详情页投屏:取默认选中的剧集 → playerContent 解析直链 → 弹设备搜索弹窗
     * (解析在后台线程做,完成前显示"正在解析...")
     */
    private void openCastDialog() {
        if (vodInfo == null || vodInfo.seriesMap == null || vodInfo.playFlag == null) {
            Toast.makeText(this, "暂无可投屏剧集", Toast.LENGTH_SHORT).show();
            return;
        }
        final java.util.List<VodInfo.VodSeries> seriesList = vodInfo.seriesMap.get(vodInfo.playFlag);
        if (seriesList == null || seriesList.isEmpty()) {
            Toast.makeText(this, "暂无可投屏剧集", Toast.LENGTH_SHORT).show();
            return;
        }
        final VodInfo.VodSeries series = seriesList.get(vodInfo.playIndex < seriesList.size() ? vodInfo.playIndex : 0);
        if (series == null || series.url == null) {
            Toast.makeText(this, "暂无可投屏剧集", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "正在解析直链...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            com.mobile.novabox.cast.CastUrlResolver.ResolveResult r =
                    com.mobile.novabox.cast.CastUrlResolver.resolvePlayUrl(sourceKey, vodInfo.playFlag, series.url);
            runOnUiThread(() -> {
                if (r == null || r.url == null || r.url.isEmpty()) {
                    Toast.makeText(this, "解析失败,无法投屏", Toast.LENGTH_SHORT).show();
                    return;
                }
                com.mobile.novabox.cast.CastUrlResolver.CastResolveResult cr =
                        com.mobile.novabox.cast.CastUrlResolver.resolveWithProxyFlag(r.url, r.headers);
                String castUrl = cr.url;
                String title = (vodInfo.name == null ? "" : vodInfo.name) + (series.name == null ? "" : " " + series.name);
                HashMap<String, String> headers = new HashMap<>();
                if (r.headers != null) headers.putAll(r.headers);
                com.mobile.novabox.dlna.CastVideo video = new com.mobile.novabox.dlna.CastVideo(castUrl, title, headers, 0);
                com.mobile.novabox.ui.dialog.CastDeviceDialog dialog =
                        new com.mobile.novabox.ui.dialog.CastDeviceDialog(DetailActivity.this, video);
                // 本次投屏地址依赖本机代理(127.0.0.1:9978 转换而来):
                // 一旦 App 被系统回收/退出,代理消失会导致电视端播放中断,
                // 因此投屏成功后需要启动前台服务 CastProxyService 保活。
                dialog.setUsesLocalProxy(cr.usesLocalProxy);
                dialog.show();
            });
        }).start();
    }
}
