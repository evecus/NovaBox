package com.mobile.novabox.ui.fragment;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import com.mobile.novabox.R;
import com.mobile.novabox.api.ApiConfig;
import com.mobile.novabox.api.DanmakuApi;
import com.mobile.novabox.base.BaseActivity;
import com.mobile.novabox.base.BaseLazyFragment;
import com.mobile.novabox.ui.activity.ConfigManagerActivity;
import com.mobile.novabox.ui.activity.LiveSourceActivity;
import com.mobile.novabox.ui.activity.SettingActivity;
import com.mobile.novabox.ui.adapter.SelectDialogAdapter;
import com.mobile.novabox.ui.dialog.DanmuApiDialog;
import com.mobile.novabox.ui.dialog.DanmuFullSettingDialog;
import com.mobile.novabox.ui.dialog.SelectDialog;
import com.mobile.novabox.ui.dialog.XWalkInitDialog;
import com.mobile.novabox.util.FastClickCheckUtil;
import com.mobile.novabox.util.FileUtils;
import com.mobile.novabox.util.HawkConfig;
import com.mobile.novabox.util.HistoryHelper;
import com.mobile.novabox.util.LOG;
import com.mobile.novabox.util.OkGoHelper;
import com.mobile.novabox.util.PlayerHelper;
import com.orhanobut.hawk.Hawk;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class ModelSettingFragment extends BaseLazyFragment {
    private static final int REQUEST_PICK_WALLPAPER = 1002;
    private TextView tvDebugOpen;
    private TextView tvParseWebView;
    private TextView tvPlay;
    private TextView tvLivePlay;
    private TextView tvDanmuApi;
    private TextView tvRender;
    private View llApi;
    private View llApiLine;
    private TextView tvApi;
    private TextView tvApiLine;
    private TextView tvDns;
    private TextView tvHomeRec;
    private TextView tvm3u8AdText;
    private TextView tvAutoSwitchLineText;
    private TextView tvIjkCachePlay;
    private TextView tvSearchThread;
    private TextView tvFullscreenOrientation;

    public static ModelSettingFragment newInstance() {
        return new ModelSettingFragment().setArguments();
    }

    public ModelSettingFragment setArguments() {
        return this;
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_model;
    }

    @Override
    protected void init() {
        tvm3u8AdText = findViewById(R.id.m3u8AdText);
        tvm3u8AdText.setText(Hawk.get(HawkConfig.M3U8_PURIFY, false) ? "开启" : "关闭");
        // 设置隐藏项的默认值
        Hawk.put(HawkConfig.DEFAULT_LOAD_LIVE, false);       // 下次进入: 点播
        Hawk.put(HawkConfig.HOME_REC_STYLE, false);          // 首页多行: 否
        Hawk.put(HawkConfig.FAST_SEARCH_MODE, true);         // 聚合搜索: 开启
        Hawk.put(HawkConfig.HISTORY_NUM, 0);                 // 历史记录: 无上限
        Hawk.put(HawkConfig.PLAY_SCALE, 0);                  // 画面缩放: 默认
        Hawk.put(HawkConfig.SHOW_PREVIEW, true);             // 窗口预览: 开启
        tvAutoSwitchLineText = findViewById(R.id.autoSwitchLineText);
        tvAutoSwitchLineText.setText(Hawk.get(HawkConfig.AUTO_SWITCH_LINE, true) ? "开启" : "关闭");
        tvDebugOpen = findViewById(R.id.tvDebugOpen);
        tvParseWebView = findViewById(R.id.tvParseWebView);
        tvPlay = findViewById(R.id.tvPlay);
        tvLivePlay = findViewById(R.id.tvLivePlay);
        tvDanmuApi = findViewById(R.id.tvDanmuApi);
        tvRender = findViewById(R.id.tvRenderType);
        llApi = findViewById(R.id.llApi);
        llApiLine = findViewById(R.id.llApiLine);
        tvApi = findViewById(R.id.tvApi);
        tvApiLine = findViewById(R.id.tvApiLine);
        tvDns = findViewById(R.id.tvDns);
        tvHomeRec = findViewById(R.id.tvHomeRec);
        tvIjkCachePlay = findViewById(R.id.tvIjkCachePlay);
        tvSearchThread = findViewById(R.id.tvSearchThread);
        tvFullscreenOrientation = findViewById(R.id.tvFullscreenOrientation);
        tvDebugOpen.setText(Hawk.get(HawkConfig.DEBUG_OPEN, false) ? "已打开" : "已关闭");
        tvParseWebView.setText(Hawk.get(HawkConfig.PARSE_WEBVIEW, true) ? "系统自带" : "XWalkView");
        refreshApiUrlLabel();
        findAndRefreshApiLineLabel();

        tvDns.setText(OkGoHelper.dnsHttpsList.get(Hawk.get(HawkConfig.DOH_URL, 0)));
        tvHomeRec.setText(getHomeRecName(Hawk.get(HawkConfig.HOME_REC, 0)));
        tvPlay.setText(PlayerHelper.getPlayerName(Hawk.get(HawkConfig.PLAY_TYPE, 0)));
        // 用 safeGetInt 兼容历史脏数据(LIVE_PLAY_TYPE 曾被误存成 String)，避免 onViewCreated 中
        // 直接 Hawk.get(key,int) 抛 ClassCastException 导致设置页崩溃回到首页
        tvLivePlay.setText(PlayerHelper.getPlayerName(com.mobile.novabox.api.ApiConfig.safeGetInt(HawkConfig.LIVE_PLAY_TYPE, 2)));
        refreshDanmuApiLabel();
        tvRender.setText(PlayerHelper.getRenderName(Hawk.get(HawkConfig.PLAY_RENDER, 0)));
        tvIjkCachePlay.setText(Hawk.get(HawkConfig.IJK_CACHE_PLAY, false) ? "开启" : "关闭");
        refreshSearchThreadLabel();
        // 全屏播放方向：仅手机端显示，平板端隐藏
        android.view.View llFullscreenOrientationRow = findViewById(R.id.llFullscreenOrientation);
        if (com.mobile.novabox.util.PadUiHelper.isPad(mContext)) {
            if (llFullscreenOrientationRow != null) llFullscreenOrientationRow.setVisibility(android.view.View.GONE);
        } else {
            if (llFullscreenOrientationRow != null) llFullscreenOrientationRow.setVisibility(android.view.View.VISIBLE);
            if (tvFullscreenOrientation != null) {
                tvFullscreenOrientation.setText(
                    com.mobile.novabox.util.OrientationHelper.getModeName(
                        com.mobile.novabox.util.OrientationHelper.getMode()));
            }
        }
        findViewById(R.id.llDebug).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Hawk.put(HawkConfig.DEBUG_OPEN, !Hawk.get(HawkConfig.DEBUG_OPEN, false));
                tvDebugOpen.setText(Hawk.get(HawkConfig.DEBUG_OPEN, false) ? "已打开" : "已关闭");
            }
        });
        findViewById(R.id.llParseWebVew).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                boolean useSystem = !Hawk.get(HawkConfig.PARSE_WEBVIEW, true);
                Hawk.put(HawkConfig.PARSE_WEBVIEW, useSystem);
                tvParseWebView.setText(Hawk.get(HawkConfig.PARSE_WEBVIEW, true) ? "系统自带" : "XWalkView");
                if (!useSystem) {
                    Toast.makeText(mContext, "注意: XWalkView只适用于部分低Android版本，Android5.0以上推荐使用系统自带", Toast.LENGTH_LONG).show();
                    XWalkInitDialog dialog = new XWalkInitDialog(mContext);
                    dialog.setOnListener(new XWalkInitDialog.OnListener() {
                        @Override
                        public void onchange() {
                        }
                    });
                    dialog.show();
                }
            }
        });
        findViewById(R.id.llWp).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                startActivityForResult(intent, REQUEST_PICK_WALLPAPER);
            }
        });
        findViewById(R.id.llWpRecovery).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                File wp = new File(requireActivity().getFilesDir().getAbsolutePath() + "/wp");
                if (wp.exists())
                    wp.delete();
                ((BaseActivity) requireActivity()).changeWallpaper(true);
            }
        });
        findViewById(R.id.llDns).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int dohUrl = Hawk.get(HawkConfig.DOH_URL, 0);

                SelectDialog<String> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择安全DNS");
                dialog.setShowFooter(false); // 隐藏底部取消/确认,只用右上角 ✕ 关闭
                dialog.setInstantApply(true); // 点选即生效
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<String>() {
                    @Override
                    public void click(String value, int pos) {
                        tvDns.setText(OkGoHelper.dnsHttpsList.get(pos));
                        Hawk.put(HawkConfig.DOH_URL, pos);
//                        String url = OkGoHelper.getDohUrl(pos);
//                        OkGoHelper.dnsOverHttps.setUrl(url.isEmpty() ? null : HttpUrl.get(url));
                        OkGoHelper.reloadDns();
                        IjkMediaPlayer.toggleDotPort(pos > 0);
                    }

                    @Override
                    public String getDisplay(String val) {
                        return val;
                    }
                }, new DiffUtil.ItemCallback<String>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                        return oldItem.equals(newItem);
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                        return oldItem.equals(newItem);
                    }
                }, OkGoHelper.dnsHttpsList, dohUrl);
                dialog.show();
            }
        });
        findViewById(R.id.llSearchThread).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                com.mobile.novabox.ui.dialog.SearchThreadDialog dialog = new com.mobile.novabox.ui.dialog.SearchThreadDialog(mActivity);
                dialog.setOnDismissListener(d -> refreshSearchThreadLabel());
                dialog.show();
            }
        });
        findViewById(R.id.llApi).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Intent intent = new Intent(mContext, ConfigManagerActivity.class);
                startActivity(intent);
            }
        });

        // 直播地址
        findViewById(R.id.llLiveSource).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                Intent intent = new Intent(mContext, LiveSourceActivity.class);
                startActivity(intent);
            }
        });

        // 线路选择 - 两栏弹窗
        llApiLine.setVisibility(View.VISIBLE);
        if (llApiLine != null) llApiLine.setVisibility(View.VISIBLE);
        findAndRefreshApiLineLabel();

        findViewById(R.id.llApiLine).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                showRouteSelectDialog();
            }
        });


        findViewById(R.id.llPlay).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int playerType = Hawk.get(HawkConfig.PLAY_TYPE, 0);
                int defaultPos = 0;
                ArrayList<Integer> players = PlayerHelper.getExistPlayerTypes();
                ArrayList<Integer> renders = new ArrayList<>();
                for(int p = 0; p<players.size(); p++) {
                    renders.add(p);
                    if (players.get(p) == playerType) {
                        defaultPos = p;
                    }
                }
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择默认视频播放器");
                dialog.setShowFooter(false);
                dialog.setInstantApply(true);
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Integer thisPlayerType = players.get(pos);
                        Hawk.put(HawkConfig.PLAY_TYPE, thisPlayerType);
                        tvPlay.setText(PlayerHelper.getPlayerName(thisPlayerType));
                        PlayerHelper.init();
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        Integer playerType = players.get(val);
                        return PlayerHelper.getPlayerName(playerType);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, renders, defaultPos);
                dialog.show();
            }
        });

        // 直播播放器：独立于视频播放器(点播)的播放内核选择，默认 IJK硬解
        findViewById(R.id.llLivePlay).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                // 兼容历史脏数据(LIVE_PLAY_TYPE 曾被误存成 String)，避免点击设置项时崩溃
                int playerType = com.mobile.novabox.api.ApiConfig.safeGetInt(HawkConfig.LIVE_PLAY_TYPE, 2);
                int defaultPos = 0;
                ArrayList<Integer> players = PlayerHelper.getExistPlayerTypes();
                ArrayList<Integer> renders = new ArrayList<>();
                for(int p = 0; p<players.size(); p++) {
                    renders.add(p);
                    if (players.get(p) == playerType) {
                        defaultPos = p;
                    }
                }
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择默认直播播放器");
                dialog.setShowFooter(false);
                dialog.setInstantApply(true);
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Integer thisPlayerType = players.get(pos);
                        Hawk.put(HawkConfig.LIVE_PLAY_TYPE, thisPlayerType);
                        tvLivePlay.setText(PlayerHelper.getPlayerName(thisPlayerType));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        Integer playerType = players.get(val);
                        return PlayerHelper.getPlayerName(playerType);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, renders, defaultPos);
                dialog.show();
            }
        });

        // 弹幕地址：从弹幕设置弹窗中独立出来的单独设置项
        findViewById(R.id.llDanmuApi).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                DanmuApiDialog dialog = new DanmuApiDialog(mActivity);
                dialog.setOnListener(new DanmuApiDialog.OnListener() {
                    @Override
                    public void onChange() {
                        refreshDanmuApiLabel();
                    }
                });
                dialog.show();
            }
        });
        findViewById(R.id.llRender).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.PLAY_RENDER, 0);
                ArrayList<Integer> renders = new ArrayList<>();
                renders.add(0);
                renders.add(1);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择默认渲染方式");
                dialog.setShowFooter(false);
                dialog.setInstantApply(true);
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.PLAY_RENDER, value);
                        tvRender.setText(PlayerHelper.getRenderName(value));
                        PlayerHelper.init();
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return PlayerHelper.getRenderName(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, renders, defaultPos);
                dialog.show();
            }
        });
        findViewById(R.id.llHomeRec).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                int defaultPos = Hawk.get(HawkConfig.HOME_REC, 0);
                ArrayList<Integer> types = new ArrayList<>();
                types.add(0);
                types.add(1);
                types.add(2);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择首页列表数据");
                dialog.setShowFooter(false);
                dialog.setInstantApply(true);
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.HOME_REC, value);
                        tvHomeRec.setText(getHomeRecName(value));
                    }

                    @Override
                    public String getDisplay(Integer val) {
                        return getHomeRecName(val);
                    }
                }, new DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                        return oldItem.intValue() == newItem.intValue();
                    }
                }, types, defaultPos);
                dialog.show();
            }
        });
        SettingActivity.callback = new SettingActivity.DevModeCallback() {
            @Override
            public void onChange() {
                findViewById(R.id.llDebug).setVisibility(View.VISIBLE);
            }
        };

        findViewById(R.id.m3u8Ad).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                boolean is_purify=Hawk.get(HawkConfig.M3U8_PURIFY, false);
                Hawk.put(HawkConfig.M3U8_PURIFY, !is_purify);
                tvm3u8AdText.setText(!is_purify ? "开启" : "关闭");
            }
        });
        findViewById(R.id.danmuFullSetting).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                // 设置页没有弹幕 View，传 null 即可（弹幕开关只做持久化 + 事件广播）
                DanmuFullSettingDialog dialog = new DanmuFullSettingDialog(mActivity);
                dialog.show();
            }
        });
        findViewById(R.id.autoSwitchLine).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                boolean enable = !Hawk.get(HawkConfig.AUTO_SWITCH_LINE, true);
                Hawk.put(HawkConfig.AUTO_SWITCH_LINE, enable);
                tvAutoSwitchLineText.setText(enable ? "开启" : "关闭");
            }
        });
        // 全屏播放方向选择
        if (llFullscreenOrientationRow != null && !com.mobile.novabox.util.PadUiHelper.isPad(mContext)) {
            llFullscreenOrientationRow.setOnClickListener(v -> {
                com.mobile.novabox.util.FastClickCheckUtil.check(v);
                int cur = com.mobile.novabox.util.OrientationHelper.getMode();
                java.util.ArrayList<Integer> modes = new java.util.ArrayList<>();
                modes.add(com.mobile.novabox.util.OrientationHelper.MODE_AUTO);
                modes.add(com.mobile.novabox.util.OrientationHelper.MODE_PORT);
                modes.add(com.mobile.novabox.util.OrientationHelper.MODE_LAND);
                modes.add(com.mobile.novabox.util.OrientationHelper.MODE_SENSOR);
                SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
                dialog.setTip("请选择全屏播放方向");
                dialog.setShowFooter(false);
                dialog.setInstantApply(true);
                dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
                    @Override
                    public void click(Integer value, int pos) {
                        Hawk.put(HawkConfig.FULLSCREEN_ORIENTATION, modes.get(pos));
                        if (tvFullscreenOrientation != null) {
                            tvFullscreenOrientation.setText(
                                com.mobile.novabox.util.OrientationHelper.getModeName(modes.get(pos)));
                        }
                    }
                    @Override
                    public String getDisplay(Integer val) {
                        return com.mobile.novabox.util.OrientationHelper.getModeName(modes.get(val));
                    }
                }, new androidx.recyclerview.widget.DiffUtil.ItemCallback<Integer>() {
                    @Override
                    public boolean areItemsTheSame(@androidx.annotation.NonNull Integer o, @androidx.annotation.NonNull Integer n) {
                        return o.intValue() == n.intValue();
                    }
                    @Override
                    public boolean areContentsTheSame(@androidx.annotation.NonNull Integer o, @androidx.annotation.NonNull Integer n) {
                        return o.intValue() == n.intValue();
                    }
                }, modes, modes.indexOf(cur));
                dialog.show();
            });
        }
        findViewById(R.id.llIjkCachePlay).setOnClickListener((view -> onClickIjkCachePlay(view)));
        findViewById(R.id.llClearCache).setOnClickListener((view -> onClickClearCache(view)));
    }


    private void restartAppAfterCacheCleared() {
        Toast.makeText(mContext, "缓存已清空,即将重启到主页!", Toast.LENGTH_LONG).show();
        com.mobile.novabox.base.App.restartApp(2500);
    }

    private void refreshDanmuApiLabel() {
        if (tvDanmuApi == null) return;
        String label = DanmakuApi.getDisplayApiUrl();
        tvDanmuApi.setText(label.isEmpty() ? "默认" : label);
    }

    private void refreshApiUrlLabel() {
        if (tvApi == null) return;
        String current = Hawk.get(HawkConfig.API_URL, "");
        String label = "";
        ArrayList<String> vodConfigs = Hawk.get(HawkConfig.VOD_CONFIG_LIST, new ArrayList<String>());
        outer:
        for (String entry : vodConfigs) {
            List<String[]> routes = ConfigManagerActivity.getRoutes(entry);
            for (String[] route : routes) {
                if (current.equals(route[1])) {
                    label = ConfigManagerActivity.getEntryName(entry);
                    break outer;
                }
            }
        }
        tvApi.setText(label.isEmpty() ? "" : label);
    }

    private void refreshApiLineText() {
        if (tvApiLine == null) return;
        findAndRefreshApiLineLabel();
    }

    private void findAndRefreshApiLineLabel() {
        if (tvApiLine == null) return;
        // Always show line selection; show current selected route name if any
        if (llApiLine != null) llApiLine.setVisibility(View.VISIBLE);
        String current = Hawk.get(HawkConfig.API_URL, "");
        String label = "";
        ArrayList<String> vodConfigs = Hawk.get(HawkConfig.VOD_CONFIG_LIST, new ArrayList<String>());
        outer:
        for (String entry : vodConfigs) {
            List<String[]> routes = ConfigManagerActivity.getRoutes(entry);
            for (String[] route : routes) {
                if (current.equals(route[1])) {
                    label = ConfigManagerActivity.getEntryName(entry);
                    if (routes.size() > 1) {
                        label += " · " + route[0];
                    }
                    break outer;
                }
            }
        }
        tvApiLine.setText(label);
    }


    // 线路选择弹窗已抽到 com.mobile.novabox.ui.dialog.RouteSelectDialog,与首页顶部栏"线路"按钮共用。
    private void showRouteSelectDialog() {
        com.mobile.novabox.ui.dialog.RouteSelectDialog.show(mActivity, new com.mobile.novabox.ui.dialog.RouteSelectDialog.OnRouteSelectedListener() {
            @Override
            public void onSelected(String url) {
                String oldApi = Hawk.get(HawkConfig.API_URL, "");
                Hawk.put(HawkConfig.API_URL, url);
                HistoryHelper.setApiHistory(url);
                tvApi.setText(url);
                refreshApiUrlLabel();
                findAndRefreshApiLineLabel();
                if (!oldApi.equals(url)) {
                    // 统一调用 App.restartApp:杀进程 + 重新启动,保证 ApiConfig/PlayerHelper/Hawk 等
                    // ApplicationContext 单例全部重新初始化,新配置立即生效。
                    Toast.makeText(mContext, "配置已切换,即将自动重启应用!", Toast.LENGTH_SHORT).show();
                    com.mobile.novabox.base.App.restartApp(2500);
                }
            }
            @Override
            public void onCancel() { /* no-op */ }
        });
    }

    private void updateApiRowWeight(boolean showLine) {
        // 手机版单列布局，无需调整weight
    }

    private void onClickIjkCachePlay(View v) {
        FastClickCheckUtil.check(v);
        Hawk.put(HawkConfig.IJK_CACHE_PLAY, !Hawk.get(HawkConfig.IJK_CACHE_PLAY, false));
        tvIjkCachePlay.setText(Hawk.get(HawkConfig.IJK_CACHE_PLAY, false) ? "开启" : "关闭");
    }

    /** 刷新"搜索线程数"设置项右侧展示的当前值，格式："上限18 / 每批6"，默认配置时额外标注"(默认)" */
    private void refreshSearchThreadLabel() {
        if (tvSearchThread == null) return;
        int maxThread = com.mobile.novabox.util.SearchThreadHelper.getMaxThreadCount();
        int batch = com.mobile.novabox.util.SearchThreadHelper.getBatchCount();
        String suffix = com.mobile.novabox.util.SearchThreadHelper.isDefault() ? "(默认)" : "";
        tvSearchThread.setText("上限" + maxThread + " / 每批" + batch + suffix);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_PICK_WALLPAPER) {
            if (resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null) return;
            Uri uri = data.getData();
            new Thread(() -> {
                try {
                    InputStream input = mContext.getContentResolver().openInputStream(uri);
                    if (input == null) return;
                    File dest = new File(requireActivity().getFilesDir().getAbsolutePath(), "wp");
                    FileOutputStream output = new FileOutputStream(dest);
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = input.read(buffer)) != -1) output.write(buffer, 0, len);
                    input.close();
                    output.close();
                    if (mActivity != null) {
                        mActivity.runOnUiThread(() -> {
                            ((BaseActivity) requireActivity()).changeWallpaper(true);
                            Toast.makeText(mContext, "壁纸已设置", Toast.LENGTH_SHORT).show();
                        });
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                    if (mActivity != null) {
                        mActivity.runOnUiThread(() -> Toast.makeText(mContext, "设置壁纸失败", Toast.LENGTH_SHORT).show());
                    }
                }
            }).start();
            return;
        }
    }

    private void onClickClearCache(View v) {
        FastClickCheckUtil.check(v);
        String cachePath = FileUtils.getCachePath();
        File cacheDir = new File(cachePath);
        String cspCachePath = FileUtils.getFilePath()+"/csp/";
        File cspCacheDir = new File(cspCachePath);
        ApiConfig.get().clearSpiderCache();
        new Thread(() -> {
            try {
                if(cacheDir.exists())FileUtils.cleanDirectory(cacheDir);
                if(cspCacheDir.exists()){
                    FileUtils.cleanDirectory(cspCacheDir);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (mActivity != null) {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            restartAppAfterCacheCleared();
                        }
                    });
                }
            }
        }).start();
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        SettingActivity.callback = null;
    }

    String getHomeRecName(int type) {
        if (type == 1) {
            return "站点推荐";
        } else if (type == 2) {
            return "观看历史";
        } else {
            return "豆瓣热播";
        }
    }

    String getSearchView(int type) {
        if (type == 0) {
            return "文字列表";
        } else {
            return "缩略图";
        }
    }
}
