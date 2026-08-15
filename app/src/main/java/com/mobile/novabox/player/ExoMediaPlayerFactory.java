package com.mobile.novabox.player;

import android.content.Context;

import xyz.doikki.videoplayer.player.PlayerFactory;

public class ExoMediaPlayerFactory extends PlayerFactory<ExoPlayer> {

    private final boolean softwareDecode;

    private ExoMediaPlayerFactory(boolean softwareDecode) {
        this.softwareDecode = softwareDecode;
    }

    /** 硬解(默认,MediaCodec) */
    public static ExoMediaPlayerFactory create() {
        return new ExoMediaPlayerFactory(false);
    }

    /** 软解(EXTENSION_RENDERER_MODE_PREFER,需 exoplayer-ffmpeg-extension 依赖) */
    public static ExoMediaPlayerFactory createSoftwareDecode() {
        return new ExoMediaPlayerFactory(true);
    }

    @Override
    public ExoPlayer createPlayer(Context context) {
        return new ExoPlayer(context, softwareDecode);
    }
}
