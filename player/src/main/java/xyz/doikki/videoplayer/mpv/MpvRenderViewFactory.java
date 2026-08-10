package xyz.doikki.videoplayer.mpv;

import android.content.Context;

import xyz.doikki.videoplayer.render.IRenderView;
import xyz.doikki.videoplayer.render.RenderViewFactory;

public class MpvRenderViewFactory extends RenderViewFactory {

    public static MpvRenderViewFactory create() {
        return new MpvRenderViewFactory();
    }

    @Override
    public IRenderView createRenderView(Context context) {
        return new MpvRenderView(context);
    }
}
