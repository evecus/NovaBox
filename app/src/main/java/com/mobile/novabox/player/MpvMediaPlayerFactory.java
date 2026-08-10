package com.mobile.novabox.player;

import android.content.Context;

import xyz.doikki.videoplayer.player.PlayerFactory;

public class MpvMediaPlayerFactory extends PlayerFactory<MpvMediaPlayer> {

    public static MpvMediaPlayerFactory create() {
        return new MpvMediaPlayerFactory();
    }

    @Override
    public MpvMediaPlayer createPlayer(Context context) {
        return new MpvMediaPlayer(context);
    }
}
