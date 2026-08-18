package com.mobile.novabox.cast;

/**
 * DLNA/UPnP 投屏设备。
 */
public class CastDevice {
    public String friendlyName;      // 设备名(电视/盒子的 friendlyName)
    public String deviceType;        // 设备类型(如 urn:schemas-upnp-org:device:MediaRenderer:1)
    public String descriptionUrl;    // 设备描述 XML URL(唯一标识,用于去重)
    public String baseUrl;           // 设备描述 URL 的根(拼接控制 URL 用)
    public String controlUrl;        // AVTransport 控制 URL(SOAP 推送用)
    public String ip;                // 设备 IP

    /** 是否是媒体渲染器(可接收投屏) */
    public boolean isMediaRenderer() {
        return deviceType != null && deviceType.contains("MediaRenderer");
    }

    @Override
    public String toString() {
        return friendlyName == null ? ip : friendlyName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CastDevice that = (CastDevice) o;
        return descriptionUrl != null ? descriptionUrl.equals(that.descriptionUrl) : that.descriptionUrl == null;
    }

    @Override
    public int hashCode() {
        return descriptionUrl != null ? descriptionUrl.hashCode() : 0;
    }
}
