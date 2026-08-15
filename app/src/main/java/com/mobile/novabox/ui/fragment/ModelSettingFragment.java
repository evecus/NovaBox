package com.mobile.novabox.ui.fragment;

import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.os.Handler;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import com.mobile.novabox.R;
import com.mobile.novabox.api.ApiConfig;
import com.mobile.novabox.base.BaseActivity;
import com.mobile.novabox.base.BaseLazyFragment;
import com.mobile.novabox.bean.SourceBean;
import com.mobile.novabox.event.RefreshEvent;
import com.mobile.novabox.player.thirdparty.RemoteTVBox;
import com.mobile.novabox.ui.activity.HomeActivity;
import com.mobile.novabox.ui.activity.LocalFileActivity;
import com.mobile.novabox.ui.activity.ConfigManagerActivity;
import com.mobile.novabox.ui.activity.LiveSourceActivity;
import com.mobile.novabox.ui.activity.SettingActivity;
import com.mobile.novabox.ui.adapter.SelectDialogAdapter;
import com.mobile.novabox.ui.dialog.ApiDialog;
import com.mobile.novabox.ui.dialog.DanmuFullSettingDialog;
import com.mobile.novabox.ui.dialog.SearchRemoteTvDialog;
import com.mobile.novabox.ui.dialog.SelectDialog;
import com.mobile.novabox.ui.dialog.XWalkInitDialog;
import com.mobile.novabox.util.FastClickCheckUtil;
import com.mobile.novabox.util.FileUtils;
import com.mobile.novabox.util.HawkConfig;
import com.mobile.novabox.util.HistoryHelper;
import com.mobile.novabox.util.LOG;
import com.mobile.novabox.util.OkGoHelper;
import com.mobile.novabox.util.PlayerHelper;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.FileCallback;
import com.lzy.okgo.model.Progress;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;

import org.greenrobot.eventbus.EventBus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import okhttp3.HttpUrl;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class ModelSettingFragment extends BaseLazyFragment {
    private static final int REQUEST_LOCAL_CONFIG = 1001;
    private static final int REQUEST_PICK_WALLPAPER = 1002;
    private TextView tvDebugOpen;
    private TextView tvParseWebView;
    private TextView tvPlay;
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
    private TextView tvFullscreenOrientation;
    private ApiDialog apiDialog;
    private boolean selectLocalLive;

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
        tvRender = findViewById(R.id.tvRenderType);
        llApi = findViewById(R.id.llApi);
        llApiLine = findViewById(R.id.llApiLine);
        tvApi = findViewById(R.id.tvApi);
        tvApiLine = findViewById(R.id.tvApiLine);
        tvDns = findViewById(R.id.tvDns);
        tvHomeRec = findViewById(R.id.tvHomeRec);
        tvIjkCachePlay = findViewById(R.id.tvIjkCachePlay);
        tvFullscreenOrientation = findViewById(R.id.tvFullscreenOrientation);
        tvDebugOpen.setText(Hawk.get(HawkConfig.DEBUG_OPEN, false) ? "已打开" : "已关闭");
        tvParseWebView.setText(Hawk.get(HawkConfig.PARSE_WEBVIEW, true) ? "系统自带" : "XWalkView");
        refreshApiUrlLabel();
        findAndRefreshApiLineLabel();

        tvDns.setText(OkGoHelper.dnsHttpsList.get(Hawk.get(HawkConfig.DOH_URL, 0)));
        tvHomeRec.setText(getHomeRecName(Hawk.get(HawkConfig.HOME_REC, 0)));
        tvPlay.setText(PlayerHelper.getPlayerName(Hawk.get(HawkConfig.PLAY_TYPE, 2)));
        tvRender.setText(PlayerHelper.getRenderName(Hawk.get(HawkConfig.PLAY_RENDER, 0)));
        tvIjkCachePlay.setText(Hawk.get(HawkConfig.IJK_CACHE_PLAY, false) ? "开启" : "关闭");
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
                int playerType = Hawk.get(HawkConfig.PLAY_TYPE, 2);
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
                dialog.setTip("请选择默认播放器");
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

    private void restartAppAfterConfigChanged() {
        Toast.makeText(mContext, "配置已切换,即将自动重启应用!", Toast.LENGTH_SHORT).show();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                restartApp();
            }
        }, 2500);
    }

    private void restartAppAfterCacheCleared() {
        Toast.makeText(mContext, "缓存已清空,即将重启到主页!", Toast.LENGTH_LONG).show();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                restartApp();
            }
        }, 2500);
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
                    restartAppAfterConfigChanged();
                }
            }
            @Override
            public void onCancel() { /* no-op */ }
        });
    }

    private void updateApiRowWeight(boolean showLine) {
        // 手机版单列布局，无需调整weight
    }

    private void restartApp() {
        if (mContext == null) return;
        Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(mContext.getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            System.exit(0);
        }
    }

    private void onClickIjkCachePlay(View v) {
        FastClickCheckUtil.check(v);
        Hawk.put(HawkConfig.IJK_CACHE_PLAY, !Hawk.get(HawkConfig.IJK_CACHE_PLAY, false));
        tvIjkCachePlay.setText(Hawk.get(HawkConfig.IJK_CACHE_PLAY, false) ? "开启" : "关闭");
    }

    private void openLocalConfig(boolean live) {
        selectLocalLive = live;
        if (!XXPermissions.isGranted(mContext, Permission.Group.STORAGE)) {
            Toast.makeText(getContext(), "请选择文件前需要先授予存储权限", Toast.LENGTH_SHORT).show();
            XXPermissions.with(mActivity)
                    .permission(Permission.Group.STORAGE)
                    .request(new OnPermissionCallback() {
                        @Override
                        public void onGranted(List<String> permissions, boolean all) {
                            if (all) {
                                Toast.makeText(getContext(), "已获得存储权限", Toast.LENGTH_SHORT).show();
                                openLocalFileActivity(selectLocalLive);
                            }
                        }

                        @Override
                        public void onDenied(List<String> permissions, boolean never) {
                            if (never) {
                                Toast.makeText(getContext(), "获取存储权限失败,请在系统设置中开启", Toast.LENGTH_SHORT).show();
                                XXPermissions.startPermissionActivity(mActivity, permissions);
                            } else {
                                Toast.makeText(getContext(), "获取存储权限失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
            return;
        }
        openLocalFileActivity(live);
    }

    private void openLocalFileActivity(boolean live) {
        Intent intent = new Intent(mContext, LocalFileActivity.class);
        intent.putExtra(LocalFileActivity.EXTRA_LIVE, live);
        startActivityForResult(intent, REQUEST_LOCAL_CONFIG);
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

        if (requestCode != REQUEST_LOCAL_CONFIG || resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        String api = localConfigToApi(data.getData());
        if (api == null || api.isEmpty()) {
            Toast.makeText(getContext(), "读取本地配置失败", Toast.LENGTH_SHORT).show();
            return;
        }
        if (apiDialog != null) {
            apiDialog.setLocalApi(api, selectLocalLive);
        }
    }

    private String localConfigToApi(Uri uri) {
        String path = getPathFromUri(uri);
        if (path == null || path.isEmpty()) {
            path = copyUriToLocalConfig(uri);
        }
        if (path == null || path.isEmpty()) {
            return "";
        }
        String storageRoot = Environment.getExternalStorageDirectory().getAbsolutePath();
        if (path.startsWith(storageRoot)) {
            return "clan://localhost/" + path.substring(storageRoot.length()).replaceFirst("^/+", "");
        }
        path = copyUriToLocalConfig(uri);
        if (path != null && path.startsWith(storageRoot)) {
            return "clan://localhost/" + path.substring(storageRoot.length()).replaceFirst("^/+", "");
        }
        return "";
    }

    private String getPathFromUri(Uri uri) {
        try {
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                return uri.getPath();
            }
            if (DocumentsContract.isDocumentUri(mContext, uri)) {
                String docId = DocumentsContract.getDocumentId(uri);
                if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
                    String[] split = docId.split(":");
                    if (split.length > 1 && "primary".equalsIgnoreCase(split[0])) {
                        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + split[1];
                    }
                }
                if ("com.android.providers.downloads.documents".equals(uri.getAuthority()) && docId.startsWith("raw:")) {
                    return docId.substring(4);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String copyUriToLocalConfig(Uri uri) {
        InputStream input = null;
        FileOutputStream output = null;
        try {
            input = mContext.getContentResolver().openInputStream(uri);
            if (input == null) return "";
            File dir = new File(FileUtils.getExternalCachePath(), "config");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, getDisplayName(uri));
            output = new FileOutputStream(file);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
            return file.getAbsolutePath();
        } catch (Throwable th) {
            th.printStackTrace();
            return "";
        } finally {
            try {
                if (output != null) output.close();
            } catch (Throwable ignored) {
            }
            try {
                if (input != null) input.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private String getDisplayName(Uri uri) {
        String name = "local_config.json";
        Cursor cursor = null;
        try {
            cursor = mContext.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String displayName = cursor.getString(index);
                    if (displayName != null && !displayName.isEmpty()) {
                        name = displayName;
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return name;
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


    public static SearchRemoteTvDialog loadingSearchRemoteTvDialog;
    public static List<String> remoteTvHostList;
    public static boolean foundRemoteTv;

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
