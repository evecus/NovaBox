package xyz.doikki.videoplayer.mpv;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.util.Map;

import is.xyz.mpv.MPV;
import is.xyz.mpv.MPVNode;
import xyz.doikki.videoplayer.player.AbstractPlayer;

/**
 * 基于 libmpv 的播放内核，对接 dkplayer 的 AbstractPlayer 接口。
 * 渲染由 {@link MpvRenderView} 负责（SurfaceView + attachSurface）。
 */
public class MpvPlayer extends AbstractPlayer {

    protected Context mAppContext;
    protected MPV mpv;
    protected String mPath;
    protected Map<String, String> mHeaders;
    protected boolean mLooping;
    protected boolean mSurfaceAttached;
    protected float mSpeed = 1f;
    protected int mVolume = 100;

    /** mpv 事件回调运行在其事件线程，dkplayer 的监听器需要在主线程执行 */
    protected final Handler mMainHandler = new Handler(Looper.getMainLooper());

    protected final MPV.EventObserver mObserver = new MPV.EventObserver() {
        @Override
        public void eventProperty(String property) {
        }

        @Override
        public void eventProperty(String property, long value) {
        }

        @Override
        public void eventProperty(String property, boolean value) {
            if ("paused-for-cache".equals(property)) {
                final boolean buffering = value;
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mPlayerEventListener != null) {
                            mPlayerEventListener.onInfo(buffering ? AbstractPlayer.MEDIA_INFO_BUFFERING_START
                                    : AbstractPlayer.MEDIA_INFO_BUFFERING_END, 0);
                        }
                    }
                });
            }
        }

        @Override
        public void eventProperty(String property, String value) {
        }

        @Override
        public void eventProperty(String property, double value) {
        }

        @Override
        public void eventProperty(String property, MPVNode value) {
        }

        @Override
        public void event(int eventId, MPVNode data) {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    handleEvent(eventId);
                }
            });
        }
    };

    public MpvPlayer(Context context) {
        mAppContext = context.getApplicationContext();
    }

    @Override
    public void initPlayer() {
        try {
            Context context = mAppContext;
            mpv = new MPV();
            mpv.create(context);
            mpv.init();
            mpv.setOptionString("config", "no");
            mpv.setOptionString("gpu-shader-cache-dir", context.getCacheDir().getAbsolutePath() + "/mpv");
            mpv.setOptionString("vo", "gpu");
            mpv.setOptionString("hwdec", "auto-safe");
            mpv.setOptionString("demuxer-max-bytes", "50MiB");
            mpv.setOptionString("demuxer-max-back-bytes", "5MiB");
            mpv.setOptionString("video-sync", "display-resample");
            mpv.setOptionString("audio-channels", "2");
            mpv.setOptionString("sub-auto", "fuzzy");
            mpv.addObserver(mObserver);
            mpv.observeProperty("paused-for-cache", MPV.mpvFormat.MPV_FORMAT_FLAG);
            mpv.observeProperty("eof-reached", MPV.mpvFormat.MPV_FORMAT_FLAG);
            mSurfaceAttached = false;
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    protected void handleEvent(int eventId) {
        if (mpv == null) return;
        if (mPlayerEventListener == null) return;
        if (eventId == MPV.mpvEvent.MPV_EVENT_FILE_LOADED) {
            Integer width = mpv.getPropertyInt("width");
            Integer height = mpv.getPropertyInt("height");
            if (width != null && height != null && width > 0 && height > 0) {
                mPlayerEventListener.onVideoSizeChanged(width, height);
            }
            mPlayerEventListener.onPrepared();
            // 模拟 ijk 的 start-on-prepared：prepared 后自动开始播放
            start();
        } else if (eventId == MPV.mpvEvent.MPV_EVENT_END_FILE) {
            Boolean eof = mpv.getPropertyBoolean("eof-reached");
            if (eof != null && eof) {
                mPlayerEventListener.onCompletion();
            }
        } else if (eventId == MPV.mpvEvent.MPV_EVENT_VIDEO_RECONFIG) {
            Integer width = mpv.getPropertyInt("width");
            Integer height = mpv.getPropertyInt("height");
            if (width != null && height != null && width > 0 && height > 0) {
                mPlayerEventListener.onVideoSizeChanged(width, height);
            }
        }
    }

    @Override
    public void setDataSource(String path, Map<String, String> headers) {
        this.mPath = path;
        this.mHeaders = headers;
    }

    @Override
    public void setDataSource(AssetFileDescriptor fd) {
        if (fd != null) {
            this.mPath = fd.getFileDescriptor().toString();
        }
    }

    @Override
    public void start() {
        if (mpv == null) return;
        try {
            mpv.setPropertyBoolean("pause", false);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    @Override
    public void pause() {
        if (mpv == null) return;
        try {
            mpv.setPropertyBoolean("pause", true);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    @Override
    public void stop() {
        if (mpv == null) return;
        try {
            mpv.command("stop");
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    @Override
    public void prepareAsync() {
        if (mpv == null) return;
        try {
            applyHeaders();
            String loadPath = mPath == null ? "" : mPath;
            mpv.setPropertyBoolean("pause", true);
            mpv.command("loadfile", loadPath);
        } catch (Throwable th) {
            th.printStackTrace();
            if (mPlayerEventListener != null) mPlayerEventListener.onError();
        }
    }

    protected void applyHeaders() {
        if (mpv == null || mHeaders == null || mHeaders.isEmpty()) return;
        try {
            String ua = null;
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : mHeaders.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key == null || value == null || key.isEmpty() || value.isEmpty()) continue;
                if ("User-Agent".equalsIgnoreCase(key)) {
                    ua = value.trim();
                    continue;
                }
                if (sb.length() > 0) sb.append(",");
                sb.append(key.trim()).append(": ").append(value.trim());
            }
            if (ua != null) {
                mpv.setOptionString("http-user-agent", ua);
            }
            if (sb.length() > 0) {
                mpv.setOptionString("http-header-fields", sb.toString());
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    @Override
    public void reset() {
        if (mpv == null) return;
        try {
            mpv.command("stop");
            mpv.setPropertyBoolean("pause", true);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    @Override
    public boolean isPlaying() {
        if (mpv == null) return false;
        try {
            Boolean pause = mpv.getPropertyBoolean("pause");
            return pause == null || !pause;
        } catch (Throwable th) {
            return false;
        }
    }

    @Override
    public void seekTo(long time) {
        if (mpv == null) return;
        try {
            mpv.setPropertyDouble("time-pos", time / 1000.0d);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    @Override
    public void release() {
        if (mpv != null) {
            try {
                if (mSurfaceAttached) {
                    mpv.detachSurface();
                    mSurfaceAttached = false;
                }
                mpv.removeObserver(mObserver);
                mpv.destroy();
            } catch (Throwable th) {
                th.printStackTrace();
            }
            mpv = null;
        }
    }

    @Override
    public long getCurrentPosition() {
        if (mpv == null) return 0;
        try {
            Double pos = mpv.getPropertyDouble("time-pos");
            return pos == null ? 0 : (long) (pos * 1000);
        } catch (Throwable th) {
            return 0;
        }
    }

    @Override
    public long getDuration() {
        if (mpv == null) return 0;
        try {
            Double duration = mpv.getPropertyDouble("duration");
            return duration == null ? 0 : (long) (duration * 1000);
        } catch (Throwable th) {
            return 0;
        }
    }

    @Override
    public int getBufferedPercentage() {
        return 0;
    }

    @Override
    public void setSurface(Surface surface) {
        if (surface != null && !mSurfaceAttached && mpv != null) {
            attachSurface(surface);
        }
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        if (holder == null) return;
        if (holder.getSurface() != null && holder.getSurface().isValid() && !mSurfaceAttached && mpv != null) {
            attachSurface(holder.getSurface());
        }
    }

    public void attachSurface(Surface surface) {
        if (mpv == null || surface == null || mSurfaceAttached) return;
        try {
            mpv.setOptionString("force-window", "yes");
            mpv.setPropertyString("vo", "gpu");
            mpv.attachSurface(surface);
            mSurfaceAttached = true;
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public void detachSurface() {
        if (mpv == null) return;
        try {
            if (mSurfaceAttached) {
                mpv.setPropertyString("vo", "null");
                mpv.setPropertyString("force-window", "no");
                mpv.detachSurface();
                mSurfaceAttached = false;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public void onSurfaceSizeChanged(int width, int height) {
        if (mpv == null) return;
        try {
            mpv.setPropertyString("android-surface-size", width + "x" + height);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    @Override
    public void setVolume(float v1, float v2) {
        if (mpv == null) return;
        try {
            float max = Math.max(v1, v2);
            mVolume = Math.max(0, Math.min(100, (int) (max * 100)));
            mpv.setPropertyInt("volume", mVolume);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    @Override
    public void setLooping(boolean isLooping) {
        mLooping = isLooping;
        if (mpv == null) return;
        try {
            mpv.setPropertyString("loop-file", isLooping ? "inf" : "no");
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    @Override
    public void setOptions() {
        if (mpv == null) return;
        try {
            mpv.setPropertyString("loop-file", mLooping ? "inf" : "no");
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    @Override
    public void setSpeed(float speed) {
        mSpeed = speed;
        if (mpv == null) return;
        try {
            mpv.setPropertyDouble("speed", speed);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    @Override
    public float getSpeed() {
        if (mpv == null) return mSpeed;
        try {
            Double speed = mpv.getPropertyDouble("speed");
            return speed == null ? mSpeed : speed.floatValue();
        } catch (Throwable th) {
            return mSpeed;
        }
    }

    @Override
    public long getTcpSpeed() {
        if (mpv == null) return 0;
        try {
            Integer speed = mpv.getPropertyInt("cache-speed");
            return speed == null ? 0 : speed.longValue();
        } catch (Throwable th) {
            return 0;
        }
    }

    public MPV getMpv() {
        return mpv;
    }
}
