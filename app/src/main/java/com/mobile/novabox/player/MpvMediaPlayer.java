package com.mobile.novabox.player;

import android.content.Context;
import android.text.TextUtils;

import com.mobile.novabox.util.AudioTrackMemory;
import com.mobile.novabox.util.LOG;

import java.util.Map;

import is.xyz.mpv.MPV;
import is.xyz.mpv.MPVNode;
import xyz.doikki.videoplayer.mpv.MpvPlayer;

/**
 * app 层 mpv 播放器封装：音轨/字幕切换、字幕文本监听、音轨记忆。
 */
public class MpvMediaPlayer extends MpvPlayer {

    private static AudioTrackMemory memory;

    private MPV.EventObserver mSubtitleObserver;
    private OnTimedTextListener mTimedTextListener;

    public MpvMediaPlayer(Context context) {
        super(context);
        memory = AudioTrackMemory.getInstance(context);
        mSubtitleObserver = new MPV.EventObserver() {
            @Override
            public void eventProperty(String property) {
            }

            @Override
            public void eventProperty(String property, long value) {
            }

            @Override
            public void eventProperty(String property, boolean value) {
            }

            @Override
            public void eventProperty(String property, String value) {
                if ("sub-text".equals(property) && mTimedTextListener != null && !TextUtils.isEmpty(value)) {
                    final String text = value;
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mTimedTextListener != null) {
                                mTimedTextListener.onTimedText(text);
                            }
                        }
                    });
                }
            }

            @Override
            public void eventProperty(String property, double value) {
            }

            @Override
            public void eventProperty(String property, MPVNode value) {
            }

            @Override
            public void event(int eventId, MPVNode data) {
            }
        };
    }

    @Override
    public void initPlayer() {
        super.initPlayer();
        if (mpv != null) {
            try {
                mpv.addObserver(mSubtitleObserver);
                mpv.observeProperty("sub-text", MPV.mpvFormat.MPV_FORMAT_STRING);
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }

    @Override
    public void release() {
        if (mpv != null && mSubtitleObserver != null) {
            try {
                mpv.removeObserver(mSubtitleObserver);
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        super.release();
    }

    public TrackInfo getTrackInfo() {
        TrackInfo data = new TrackInfo();
        if (mpv == null) return data;
        try {
            MPVNode trackList = mpv.getPropertyNode("track-list");
            if (trackList == null || trackList.asArray() == null) return data;
            Integer aid = mpv.getPropertyInt("aid");
            Integer sid = mpv.getPropertyInt("sid");
            int index = 0;
            for (MPVNode node : trackList.asArray()) {
                Map<String, MPVNode> map = node.asMap();
                if (map == null) continue;
                String type = valueOf(map, "type");
                Long id = longOf(map, "id");
                TrackInfoBean bean = new TrackInfoBean();
                bean.trackId = id == null ? index : id.intValue();
                bean.index = index;
                bean.groupIndex = index;
                if ("audio".equals(type)) {
                    String lang = valueOf(map, "lang");
                    String title = valueOf(map, "title");
                    bean.language = getFriendlyLanguage(lang, title);
                    bean.name = buildDisplayName("音轨", data.getAudio().size() + 1, bean.language, title);
                    bean.selected = aid != null && aid == bean.trackId;
                    data.addAudio(bean);
                } else if ("sub".equals(type)) {
                    String lang = valueOf(map, "lang");
                    String title = valueOf(map, "title");
                    bean.language = getFriendlyLanguage(lang, title);
                    bean.name = buildDisplayName("字幕", data.getSubtitle().size() + 1, bean.language, title);
                    bean.selected = sid != null && sid == bean.trackId;
                    data.addSubtitle(bean);
                }
                index++;
            }
        } catch (Throwable th) {
            LOG.i("echo-mpv getTrackInfo error: " + th.getMessage());
        }
        return data;
    }

    private String valueOf(Map<String, MPVNode> map, String key) {
        MPVNode node = map.get(key);
        if (node == null) return "";
        String value = node.asString();
        return value == null ? "" : value;
    }

    private Long longOf(Map<String, MPVNode> map, String key) {
        MPVNode node = map.get(key);
        if (node == null) return null;
        return node.asInt();
    }

    /**
     * 切换音轨
     */
    public void setTrack(int trackIndex, String playKey) {
        if (mpv == null) return;
        try {
            Integer aid = mpv.getPropertyInt("aid");
            if (aid != null && aid == trackIndex) return;
            if (!playKey.isEmpty()) {
                memory.save(playKey, trackIndex);
            }
            mpv.setPropertyInt("aid", trackIndex);
        } catch (Throwable th) {
            LOG.i("echo-mpv setTrack audio error: " + th.getMessage());
        }
    }

    /**
     * 切换字幕
     */
    public void setTrack(int trackIndex) {
        if (mpv == null) return;
        try {
            mpv.setPropertyInt("sid", trackIndex);
        } catch (Throwable th) {
            LOG.i("echo-mpv setTrack subtitle error: " + th.getMessage());
        }
    }

    public void loadDefaultTrack(TrackInfo trackInfo, String playKey) {
        if (trackInfo != null && trackInfo.getAudio().size() > 1) {
            Integer trackIndex = memory.ijkLoad(playKey);
            if (trackIndex == null || trackIndex == -1) {
                int firstIndex = trackInfo.getAudio().get(0).trackId;
                setTrack(firstIndex);
                return;
            }
            setTrack(trackIndex, "");
        }
    }

    public void setOnTimedTextListener(OnTimedTextListener listener) {
        this.mTimedTextListener = listener;
    }

    public interface OnTimedTextListener {
        void onTimedText(String text);
    }

    private String getFriendlyLanguage(String language, String rawInfo) {
        String text = ((language == null ? "" : language) + " " + (rawInfo == null ? "" : rawInfo)).toLowerCase();
        if (text.contains("yue") || text.contains("cantonese") || text.contains("粤") || text.contains("广东")) {
            return "粤语";
        }
        if (text.contains("zh") || text.contains("chi") || text.contains("zho") || text.contains("chs")
                || text.contains("cht") || text.contains("cmn") || text.contains("中")
                || text.contains("国语") || text.contains("普通话")) {
            return "国语";
        }
        if (text.contains("en") || text.contains("eng") || text.contains("english") || text.contains("英")) {
            return "英语";
        }
        if (text.contains("ja") || text.contains("jpn") || text.contains("japanese") || text.contains("日")) {
            return "日语";
        }
        if (text.contains("ko") || text.contains("kor") || text.contains("korean") || text.contains("韩")) {
            return "韩语";
        }
        if (text.contains("tha") || text.contains("thai") || text.contains("th")) {
            return "泰语";
        }
        return "";
    }

    private String buildDisplayName(String prefix, int number, String language, String detail) {
        StringBuilder builder = new StringBuilder(prefix).append(" ").append(number);
        if (language != null && !language.isEmpty()) {
            builder.append(" - ").append(language);
        }
        if (detail != null && !detail.isEmpty()) {
            builder.append(" ").append(detail);
        }
        return builder.toString();
    }
}
