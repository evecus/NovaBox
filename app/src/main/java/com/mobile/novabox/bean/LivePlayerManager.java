package com.mobile.novabox.bean;

import androidx.annotation.NonNull;

import com.mobile.novabox.util.HawkConfig;
import com.mobile.novabox.util.LOG;
import com.mobile.novabox.util.PlayerHelper;
import com.mobile.novabox.util.PlayerSwitchUtil;
import com.orhanobut.hawk.Hawk;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;

import xyz.doikki.videoplayer.player.VideoView;

public class LivePlayerManager {
    JSONObject defaultPlayerConfig = new JSONObject();
    JSONObject currentPlayerConfig;
    private String currentApi="";

    public void init(VideoView videoView) {
        try {
            currentApi=Hawk.get(HawkConfig.LIVE_API_URL,"");
            // 4 档 LIVE_PLAY_TYPE:0=EXO硬解,1=EXO软解,2=IJK硬解,3=IJK软解;默认 IJK硬解(2)
            // 直播播放器与视频播放器(点播 PLAY_TYPE)相互独立配置,不再互相回退
            // 用 safeGetInt 兼容历史脏数据(曾被误存成 String)，避免直接 Hawk.get(key,int) 抛
            // ClassCastException 导致直播页进不去、被迫回到首页
            int playType = com.mobile.novabox.api.ApiConfig.safeGetInt(HawkConfig.LIVE_PLAY_TYPE, 2);
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

    /**
     * 自动切换播放内核(直播播放失败时由上层调用)。
     * 按固定顺序 0→1→2→3 尝试除当前外的其余内核,已尝试过的记录在 triedPlayerTypes 中。
     *
     * @param triedPlayerTypes 已尝试过的内核档位集合(内部会累加当前内核)
     * @return true 表示已切换到下一个内核,调用方应使用当前源 URL 重新播放;
     *         false 表示其余三个内核都已试过(或配置不支持),调用方应降级处理(如换源/换频道)
     */
    public boolean switchLivePlayer(VideoView videoView, String channelName, Set<Integer> triedPlayerTypes) {
        channelName = currentCfgKey(channelName);
        JSONObject playerConfig = currentPlayerConfig;
        if (playerConfig == null) {
            LOG.i("echo-liveSwitchPlayer: skip empty player config");
            return false;
        }
        try {
            int playerType = playerConfig.getInt("pl");
            int switchPlayerType = PlayerSwitchUtil.nextPlayerType(playerType, triedPlayerTypes);
            if (switchPlayerType < 0) {
                LOG.i("echo-liveSwitchPlayer: all player types tried, skip");
                return false;
            }
            LOG.i("echo-liveSwitchPlayer: " + playerType + " -> " + switchPlayerType);
            playerConfig.put("pl", switchPlayerType);
            playerConfig.put("ijk", PlayerSwitchUtil.ijkCodeFor(switchPlayerType));
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
