package com.mobile.novabox.bean;

import androidx.annotation.NonNull;

import com.mobile.novabox.util.HawkConfig;
import com.mobile.novabox.util.LOG;
import com.mobile.novabox.util.PlayerHelper;
import com.orhanobut.hawk.Hawk;

import org.json.JSONException;
import org.json.JSONObject;

import xyz.doikki.videoplayer.player.VideoView;

public class LivePlayerManager {
    JSONObject defaultPlayerConfig = new JSONObject();
    JSONObject currentPlayerConfig;
    private String currentApi="";

    public void init(VideoView videoView) {
        try {
            currentApi=Hawk.get(HawkConfig.LIVE_API_URL,"");
            // 4 档 PLAY_TYPE:0=EXO硬解,1=EXO软解,2=IJK硬解,3=IJK软解;默认 IJK硬解
            // 兼容历史 PLAY_TYPE(老编码:1=IJK,2=EXO)。老 0(系统播放器)保留为 0=EXO硬解,
            // 满足"所有播放不使用系统播放器";新 0=EXO硬解 不受影响。
            int playType = Hawk.get(HawkConfig.LIVE_PLAY_TYPE, Hawk.get(HawkConfig.PLAY_TYPE, 2));
            if (playType == 1) playType = 2;        // 老 IJK -> IJK硬解
            else if (playType == 2) playType = 0;    // 老 EXO -> EXO硬解
            if (playType < 0 || playType > 3) playType = 2;
            defaultPlayerConfig.put("pl", playType);
            defaultPlayerConfig.put("ijk", Hawk.get(HawkConfig.IJK_CODEC, "硬解码"));
            defaultPlayerConfig.put("pr", Hawk.get(HawkConfig.PLAY_RENDER, 0));
            defaultPlayerConfig.put("sc", Hawk.get(HawkConfig.LIVE_PLAY_SCALE, 0));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        getDefaultLiveChannelPlayer(videoView);
    }

    public void getDefaultLiveChannelPlayer(VideoView videoView) {
        PlayerHelper.updateCfg(videoView, defaultPlayerConfig);
        try {
            currentPlayerConfig = new JSONObject(defaultPlayerConfig.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void getLiveChannelPlayer(VideoView videoView, String channelName) {
        channelName=currentCfgKey(channelName);
        JSONObject playerConfig = Hawk.get(channelName, null);
        if (playerConfig == null) {
            try {
                defaultPlayerConfig.put("sc", Hawk.get(HawkConfig.LIVE_PLAY_SCALE, 0));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (!currentPlayerConfig.toString().equals(defaultPlayerConfig.toString()))
                getDefaultLiveChannelPlayer(videoView);
            else
                videoView.setScreenScaleType(Hawk.get(HawkConfig.LIVE_PLAY_SCALE, 0));
            return;
        }
        try {
            playerConfig.put("sc", Hawk.get(HawkConfig.LIVE_PLAY_SCALE, 0));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (playerConfig.toString().equals(currentPlayerConfig.toString()))
            return;

        try {
            if (playerConfig.getInt("pl") == currentPlayerConfig.getInt("pl")
                    && playerConfig.getInt("pr") == currentPlayerConfig.getInt("pr")
                    && playerConfig.getString("ijk").equals(currentPlayerConfig.getString("ijk"))) {
                videoView.setScreenScaleType(playerConfig.getInt("sc"));
            } else {
                PlayerHelper.updateCfg(videoView, playerConfig);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        currentPlayerConfig = playerConfig;
    }

    public int getLivePlayerType() {
        int playerTypeIndex = 0;
        try {
            int playerType = currentPlayerConfig.getInt("pl");
            // 4 档直接对应 position(EXO硬解=0, EXO软解=1, IJK硬解=2, IJK软解=3)
            // 兼容历史 PLAY_TYPE:0=系统 -> IJK硬解(2),1=IJK -> IJK硬解(2),2=EXO -> EXO硬解(0)
            if (playerType == 0 || playerType > 3) {
                // 历史 0=系统播放器,新 0=EXO硬解;历史配置已归一化,这里按 EXO硬解(0)显示
                playerTypeIndex = 0;
            } else if (playerType == 1) {
                playerTypeIndex = 1; // EXO软解
            } else if (playerType == 2) {
                playerTypeIndex = 2; // IJK硬解
            } else {
                playerTypeIndex = 3; // IJK软解
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return playerTypeIndex;
    }

    public int getLivePlayerScale() {
        try {
            return currentPlayerConfig.getInt("sc");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void changeLivePlayerType(VideoView videoView, int playerType, String channelName) {
        channelName=currentCfgKey(channelName);
        JSONObject playerConfig = currentPlayerConfig;
        try {
            // 4 档位置直接映射到 PLAY_TYPE(0=EXO硬解,1=EXO软解,2=IJK硬解,3=IJK软解)
            switch (playerType) {
                case 0:
                    playerConfig.put("pl", 0); // EXO硬解
                    playerConfig.put("ijk", "硬解码");
                    break;
                case 1:
                    playerConfig.put("pl", 1); // EXO软解
                    playerConfig.put("ijk", "硬解码");
                    break;
                case 2:
                    playerConfig.put("pl", 2); // IJK硬解
                    playerConfig.put("ijk", "硬解码");
                    break;
                case 3:
                    playerConfig.put("pl", 3); // IJK软解
                    playerConfig.put("ijk", "软解码");
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        PlayerHelper.updateCfg(videoView, playerConfig);

        if (playerConfig.toString().equals(defaultPlayerConfig.toString()))
            Hawk.delete(channelName);
        else
            Hawk.put(channelName, playerConfig);

        currentPlayerConfig = playerConfig;
    }

    public boolean switchLivePlayer(VideoView videoView, String channelName) {
        channelName = currentCfgKey(channelName);
        JSONObject playerConfig = currentPlayerConfig;
        if (playerConfig == null) {
            LOG.i("echo-liveSwitchPlayer: skip empty player config");
            return false;
        }
        try {
            int playerType = playerConfig.getInt("pl");
            // 4 档内同内核切换:EXO硬解(0)<->EXO软解(1),IJK硬解(2)<->IJK软解(3)
            int switchPlayerType;
            switch (playerType) {
                case 0: switchPlayerType = 1; break; // EXO硬解 -> EXO软解
                case 1: switchPlayerType = 0; break; // EXO软解 -> EXO硬解
                case 2: switchPlayerType = 3; break; // IJK硬解 -> IJK软解
                case 3: switchPlayerType = 2; break; // IJK软解 -> IJK硬解
                default: switchPlayerType = 0; break;
            }
            if (switchPlayerType == playerType) {
                LOG.i("echo-liveSwitchPlayer: skip unsupported playerType=" + playerType);
                return false;
            }
            LOG.i("echo-liveSwitchPlayer: " + playerType + " -> " + switchPlayerType);
            playerConfig.put("pl", switchPlayerType);
        } catch (JSONException e) {
            LOG.i("echo-liveSwitchPlayer error: " + e.getMessage());
            return false;
        }
        PlayerHelper.updateCfg(videoView, playerConfig);

        if (playerConfig.toString().equals(defaultPlayerConfig.toString()))
            Hawk.delete(channelName);
        else
            Hawk.put(channelName, playerConfig);

        currentPlayerConfig = playerConfig;
        return true;
    }

    public void changeLivePlayerScale(@NonNull VideoView videoView, int playerScale, String channelName){
        videoView.setScreenScaleType(playerScale);
        Hawk.put(HawkConfig.LIVE_PLAY_SCALE, playerScale);

        JSONObject playerConfig = currentPlayerConfig;
        try {
            playerConfig.put("sc", playerScale);
            defaultPlayerConfig.put("sc", playerScale);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        currentPlayerConfig = playerConfig;
    }

    private String currentCfgKey(String channelName)
    {
        return currentApi+"_"+channelName;
    }
}
