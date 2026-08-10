package xyz.doikki.videoplayer.mpv;

import android.content.Context;

import xyz.doikki.videoplayer.player.PlayerFactory;

public class MpvPlayerFactory extends PlayerFactory<MpvPlayer> {

    public static MpvPlayerFactory create() {
        return new MpvPlayerFactory();
    }

    @Override
    public MpvPlayer createPlayer(Context context) {
        return new MpvPlayer(context);
    }
}
