package com.mobile.novabox.util;

import android.app.Activity;
import android.content.Context;

import com.mobile.novabox.api.ApiConfig;
import com.mobile.novabox.bean.IJKCode;
import com.mobile.novabox.player.ExoMediaPlayerFactory;
import com.mobile.novabox.player.IjkMediaPlayer;
import com.mobile.novabox.player.render.SurfaceRenderViewFactory;
import com.mobile.novabox.player.thirdparty.Kodi;
import com.mobile.novabox.player.thirdparty.MXPlayer;
import com.mobile.novabox.player.thirdparty.ReexPlayer;
import com.mobile.novabox.player.thirdparty.RemoteTVBox;
import com.mobile.novabox.player.thirdparty.VlcPlayer;
import com.orhanobut.hawk.Hawk;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;

import tv.danmaku.ijk.media.player.IjkLibLoader;
import xyz.doikki.videoplayer.player.PlayerFactory;
import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.render.RenderViewFactory;
import xyz.doikki.videoplayer.render.TextureRenderViewFactory;

public class PlayerHelper {
    /** 播放器类型:0=EXO硬解,1=EXO软解,2=IJK硬解,3=IJK软解,10+=外部播放器 */
    public static final int PLAY_TYPE_EXO_HW = 0;
    public static final int PLAY_TYPE_EXO_SW = 1;
    public static final int PLAY_TYPE_IJK_HW = 2;
    public static final int PLAY_TYPE_IJK_SW = 3;

    public static void updateCfg(VideoView videoView, JSONObject playerCfg) {
        updateCfg(videoView,playerCfg,-1);
    }
    public static void updateCfg(VideoView videoView, JSONObject playerCfg,int forcePlayerType) {
        int playerType = Hawk.get(HawkConfig.PLAY_TYPE, PLAY_TYPE_IJK_HW);
        int renderType = Hawk.get(HawkConfig.PLAY_RENDER, 0);
        String ijkCode = Hawk.get(HawkConfig.IJK_CODEC, "硬解码");
        int scale = Hawk.get(HawkConfig.PLAY_SCALE, 0);
        try {
            playerType = playerCfg.getInt("pl");
            renderType = playerCfg.getInt("pr");
            ijkCode = playerCfg.getString("ijk");
            scale = playerCfg.getInt("sc");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if(forcePlayerType>=0)playerType = forcePlayerType;
        // PLAY_TYPE 统一为 4 档新编码(0=EXO硬解,1=EXO软解,2=IJK硬解,3=IJK软解),
        // 各入口(设置页/自动切换内核)写入的都是新编码,不再做历史 1=IJK/2=EXO 映射。

        IJKCode codec = ApiConfig.get().getIJKCodec(ijkCode);
        PlayerFactory playerFactory = buildPlayerFactory(playerType, codec);
        RenderViewFactory renderViewFactory = null;
        switch (renderType) {
            case 0:
            default:
                renderViewFactory = TextureRenderViewFactory.create();
                break;
            case 1:
                renderViewFactory = SurfaceRenderViewFactory.create();
                break;
        }
        if(videoView!=null){
            videoView.setPlayerFactory(playerFactory);
            videoView.setRenderViewFactory(renderViewFactory);
            videoView.setScreenScaleType(scale);
        }
    }

    public static void updateCfg(VideoView videoView) {
        int playType = Hawk.get(HawkConfig.PLAY_TYPE, PLAY_TYPE_IJK_HW);
        // PLAY_TYPE 统一为 4 档新编码,直接使用,不做历史 1=IJK/2=EXO 映射。

        IJKCode codec = ApiConfig.get().getIJKCodec("硬解码");
        PlayerFactory playerFactory = buildPlayerFactory(playType, codec);
        int renderType = Hawk.get(HawkConfig.PLAY_RENDER, 0);
        RenderViewFactory renderViewFactory = null;
        switch (renderType) {
            case 0:
            default:
                renderViewFactory = TextureRenderViewFactory.create();
                break;
            case 1:
                renderViewFactory = SurfaceRenderViewFactory.create();
                break;
        }
        videoView.setPlayerFactory(playerFactory);
        videoView.setRenderViewFactory(renderViewFactory);
    }

    /**
     * 强制按指定内核档位重建播放器(0=EXO硬解,1=EXO软解,2=IJK硬解,3=IJK软解)。
     * 供播放失败自动切内核重试使用;入参即 4 档新编码,不做老编码映射。
     */
    public static void updateCfg(VideoView videoView, int playerType) {
        if (playerType < 0 || playerType > 3) playerType = PLAY_TYPE_IJK_HW;
        IJKCode codec = ApiConfig.get().getIJKCodec(playerType == PLAY_TYPE_IJK_SW ? "软解码" : "硬解码");
        PlayerFactory playerFactory = buildPlayerFactory(playerType, codec);
        int renderType = Hawk.get(HawkConfig.PLAY_RENDER, 0);
        RenderViewFactory renderViewFactory = null;
        switch (renderType) {
            case 0:
            default:
                renderViewFactory = TextureRenderViewFactory.create();
                break;
            case 1:
                renderViewFactory = SurfaceRenderViewFactory.create();
                break;
        }
        videoView.setPlayerFactory(playerFactory);
        videoView.setRenderViewFactory(renderViewFactory);
    }

    /**
     * 音频播放专用:固定使用 EXO硬解,不受用户"设置"里全局 PLAY_TYPE(IJK等)影响。
     * 音频场景对硬解性能不敏感,EXO 在纯音频/网络流兼容性上更稳定,
     * 供 OpenList 音频播放、本地音频播放统一调用。
     */
    public static void updateCfgAudioForceExo(VideoView videoView) {
        IJKCode codec = ApiConfig.get().getIJKCodec("硬解码");
        PlayerFactory playerFactory = buildPlayerFactory(PLAY_TYPE_EXO_HW, codec);
        int renderType = Hawk.get(HawkConfig.PLAY_RENDER, 0);
        RenderViewFactory renderViewFactory;
        switch (renderType) {
            case 0:
            default:
                renderViewFactory = TextureRenderViewFactory.create();
                break;
            case 1:
                renderViewFactory = SurfaceRenderViewFactory.create();
                break;
        }
        videoView.setPlayerFactory(playerFactory);
        videoView.setRenderViewFactory(renderViewFactory);
    }

    /**
     * 根据 PLAY_TYPE(4 档)+ IJKCodec 构建 PlayerFactory。
     * 调用方需要自行确保 IJK 类的 codec 参数(只在 IJK 路径下生效)。
     */
    private static PlayerFactory buildPlayerFactory(int playerType, IJKCode codec) {
        switch (playerType) {
            case PLAY_TYPE_EXO_HW:
                return ExoMediaPlayerFactory.create();
            case PLAY_TYPE_EXO_SW:
                return ExoMediaPlayerFactory.createSoftwareDecode();
            case PLAY_TYPE_IJK_HW:
            case PLAY_TYPE_IJK_SW:
            default:
                // IJK 路径:玩家类型本身已经决定软硬解;
                // 仍把 codec 传给 IjkMediaPlayer 兼容老接口(默认会用 mediacodec=1/0)
                return new PlayerFactory<IjkMediaPlayer>() {
                    @Override
                    public IjkMediaPlayer createPlayer(Context context) {
                        return new IjkMediaPlayer(context, codec);
                    }
                };
        }
    }


    public static void init() {
        try {
            tv.danmaku.ijk.media.player.IjkMediaPlayer.loadLibrariesOnce(new IjkLibLoader() {
                @Override
                public void loadLibrary(String s) throws UnsatisfiedLinkError, SecurityException {
                    try {
                        System.loadLibrary(s);
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
            });
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public static String getPlayerName(int playType) {
        HashMap<Integer, String> playersInfo = getPlayersInfo();
        if (playersInfo.containsKey(playType)) {
            return playersInfo.get(playType);
        } else {
            return "未知播放器";
        }
    }

    private static HashMap<Integer, String> mPlayersInfo = null;
    public static HashMap<Integer, String> getPlayersInfo() {
        if (mPlayersInfo == null) {
            HashMap<Integer, String> playersInfo = new HashMap<>();
            // 4 档内置播放器:EXO硬解 / EXO软解 / IJK硬解 / IJK软解(已移除系统播放器)
            playersInfo.put(PLAY_TYPE_EXO_HW, "EXO硬解");
            playersInfo.put(PLAY_TYPE_EXO_SW, "EXO软解");
            playersInfo.put(PLAY_TYPE_IJK_HW, "IJK硬解");
            playersInfo.put(PLAY_TYPE_IJK_SW, "IJK软解");
            // 第三方外部播放器保持不变
            playersInfo.put(10, "MX播放器");
            playersInfo.put(11, "Reex播放器");
            playersInfo.put(12, "Kodi播放器");
            playersInfo.put(13, "附近TVBox");
            playersInfo.put(14, "VLC播放器");
            mPlayersInfo = playersInfo;
        }
        return mPlayersInfo;
    }

    private static HashMap<Integer, Boolean> mPlayersExistInfo = null;
    public static HashMap<Integer, Boolean> getPlayersExistInfo() {
        if (mPlayersExistInfo == null) {
            HashMap<Integer, Boolean> playersExist = new HashMap<>();
            playersExist.put(PLAY_TYPE_EXO_HW, true);
            playersExist.put(PLAY_TYPE_EXO_SW, true);
            playersExist.put(PLAY_TYPE_IJK_HW, true);
            playersExist.put(PLAY_TYPE_IJK_SW, true);
            playersExist.put(10, MXPlayer.getPackageInfo() != null);
            playersExist.put(11, ReexPlayer.getPackageInfo() != null);
            playersExist.put(12, Kodi.getPackageInfo() != null);
            playersExist.put(13, RemoteTVBox.getAvalible() != null);
            playersExist.put(14, VlcPlayer.getPackageInfo() != null);
            mPlayersExistInfo = playersExist;
        }
        return mPlayersExistInfo;
    }

    public static Boolean getPlayerExist(int playType) {
        HashMap<Integer, Boolean> playersExistInfo = getPlayersExistInfo();
        if (playersExistInfo.containsKey(playType)) {
            return playersExistInfo.get(playType);
        } else {
            return false;
        }
    }

    public static ArrayList<Integer> getExistPlayerTypes() {
        HashMap<Integer, Boolean> playersExistInfo = getPlayersExistInfo();
        ArrayList<Integer> existPlayers = new ArrayList<>();
        for(Integer playerType : playersExistInfo.keySet()) {
            if (playersExistInfo.get(playerType)) {
                existPlayers.add(playerType);
            }
        }
        return existPlayers;
    }

    public static Boolean runExternalPlayer(int playerType, Activity activity, String url, String title, String subtitle, HashMap<String, String> headers) {
        return runExternalPlayer(playerType, activity, url, title, subtitle, headers);
    }

    public static Boolean runExternalPlayer(int playerType, Activity activity, String url, String title, String subtitle, HashMap<String, String> headers, long progress) {
        boolean callResult = false;
        switch (playerType) {
            case 10: {
                callResult = MXPlayer.run(activity, url, title, subtitle, headers);
                break;
            }
            case 11: {
                callResult = ReexPlayer.run(activity, url, title, subtitle, headers);
                break;
            }
            case 12: {
                callResult = Kodi.run(activity, url, title, subtitle, headers);
                break;
            }
            case 13: {
                callResult = RemoteTVBox.run(activity, url, title, subtitle, headers);
                break;
            }
            case 14: {
                callResult = VlcPlayer.run(activity, url, title, subtitle, progress);
                break;
            }
        }
        return callResult;
    }

    public static String getRenderName(int renderType) {
        if (renderType == 1) {
            return "SurfaceView";
        } else {
            return "TextureView";
        }
    }

    public static String getScaleName(int screenScaleType) {
        String scaleText = "默认";
        switch (screenScaleType) {
            case VideoView.SCREEN_SCALE_DEFAULT:
                scaleText = "默认";
                break;
            case VideoView.SCREEN_SCALE_16_9:
                scaleText = "16:9";
                break;
            case VideoView.SCREEN_SCALE_4_3:
                scaleText = "4:3";
                break;
            case VideoView.SCREEN_SCALE_MATCH_PARENT:
                scaleText = "填充";
                break;
            case VideoView.SCREEN_SCALE_ORIGINAL:
                scaleText = "原始";
                break;
            case VideoView.SCREEN_SCALE_CENTER_CROP:
                scaleText = "裁剪";
                break;
        }
        return scaleText;
    }

    public static String getDisplaySpeed(long speed,boolean show) {
        if(speed > 1048576)
            return new DecimalFormat("#.00").format(speed / 1048576d) + "Mb/s";
        else if(speed > 1024)
            return (speed / 1024) + "Kb/s";
        else
            return speed > 0?speed + "B/s":(show?"0B/s":"");
    }
    public static String getDisplaySpeedBps(long speed, boolean show) {
        long bitSpeed = speed * 8; // 字节转比特
        if (bitSpeed >= 1_000_000_000) {
            return new DecimalFormat("0.00").format(bitSpeed / 1_000_000_000d) + "Gbps";
        } else if (bitSpeed >= 1_000_000) {
            return new DecimalFormat("0.0").format(bitSpeed / 1_000_000d) + "Mbps";
        } else if (bitSpeed >= 1_000) {
            return new DecimalFormat("0.0").format(bitSpeed / 1_000d) + "Kbps";
        } else {
            return bitSpeed > 0 ? bitSpeed + "bps" : (show ? "0bps" : "");
        }
    }
}
