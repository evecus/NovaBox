package com.mobile.novabox.dlna;

import com.mobile.novabox.util.LOG;

import org.fourthline.cling.android.AndroidUpnpService;
import org.fourthline.cling.controlpoint.ControlPoint;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.RemoteService;
import org.fourthline.cling.model.types.UDADeviceType;
import org.fourthline.cling.model.types.UDAServiceType;
import org.fourthline.cling.support.avtransport.callback.GetTransportInfo;
import org.fourthline.cling.support.model.TransportInfo;
import org.fourthline.cling.support.model.TransportState;

/**
 * DLNA 状态查询的独立工具方法。
 *
 * 为什么不直接用 {@link DLNACastManager}:
 * 那是个全局单例,它的 upnpService 绑定/解绑跟着"设备选择弹窗"的生命周期走
 * (弹窗关闭时会 release() 解绑)。而投屏保活服务 CastProxyService 需要在弹窗
 * 关闭之后仍然能持续查询电视状态,所以它会自己单独 bindService(DLNACastService)
 * 拿一份不受弹窗影响的 AndroidUpnpService 实例,再通过这里的静态方法发起查询,
 * 两条绑定互不干扰。
 */
public final class DLNACastManagerBridge {

    private static final UDADeviceType RENDERER_TYPE = new UDADeviceType("MediaRenderer", 1);
    private static final UDAServiceType AVT_TYPE = new UDAServiceType("AVTransport", 1);

    private DLNACastManagerBridge() {
    }

    /**
     * 查询设备当前播放状态。
     *
     * @param upnpService 调用方自行持有、已绑定好的 UPnP 服务连接
     * @param device      目标设备(必须是 {@link CastDevice#TYPE_DLNA} 类型)
     * @param callback    state 常见取值:PLAYING / PAUSED_PLAYBACK / STOPPED / NO_MEDIA_PRESENT。
     *                    查询失败(设备离线、服务未就绪、找不到 AVTransport 等)时传 null。
     */
    public static void queryTransportState(AndroidUpnpService upnpService, CastDevice device, TransportStateCallback callback) {
        if (upnpService == null || device == null) {
            if (callback != null) callback.onResult(null);
            return;
        }
        ControlPoint control = upnpService.getControlPoint();
        RemoteService service = findAVTransport(upnpService, device);
        if (control == null || service == null) {
            if (callback != null) callback.onResult(null);
            return;
        }
        control.execute(new GetTransportInfo(service) {
            @Override
            public void received(ActionInvocation invocation, TransportInfo transportInfo) {
                TransportState state = transportInfo == null ? null : transportInfo.getCurrentTransportState();
                if (callback != null) callback.onResult(state == null ? null : state.getValue());
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                LOG.i("dlna-poll GetTransportInfo failure: " + (operation == null ? defaultMsg : operation.getStatusCode() + " " + defaultMsg));
                if (callback != null) callback.onResult(null);
            }
        });
    }

    private static RemoteService findAVTransport(AndroidUpnpService upnpService, CastDevice device) {
        for (org.fourthline.cling.model.meta.Device item : upnpService.getRegistry().getDevices(RENDERER_TYPE)) {
            if (!(item instanceof RemoteDevice)) continue;
            RemoteDevice remote = (RemoteDevice) item;
            if (remote.getIdentity().getUdn().getIdentifierString().equals(device.getId())) {
                return remote.findService(AVT_TYPE);
            }
        }
        return null;
    }

    public interface TransportStateCallback {
        /** state 为 null 表示查询失败(设备离线/无响应/未找到服务等) */
        void onResult(String state);
    }
}
