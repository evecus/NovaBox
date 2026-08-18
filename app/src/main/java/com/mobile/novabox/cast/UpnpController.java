package com.mobile.novabox.cast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * UPnP AVTransport 控制(SOAP):
 * - SetAVTransportURI:把视频 URL 推给设备
 * - Play:开始播放
 * - Stop:停止(可选)
 */
public class UpnpController {

    private static final String SERVICE_TYPE = "urn:schemas-upnp-org:service:AVTransport:1";

    /** 推送 URL 并开始播放 */
    public boolean play(CastDevice device, String videoUrl, String title) throws Exception {
        boolean setOk = setAVTransportURI(device, videoUrl, title);
        if (!setOk) return false;
        return play(device);
    }

    /** SetAVTransportURI:推送播放地址 */
    public boolean setAVTransportURI(CastDevice device, String videoUrl, String title) throws Exception {
        String metadata = buildMetadata(videoUrl, title);
        String body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n"
                + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                + "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\r\n"
                + "<s:Body>\r\n"
                + "<u:SetAVTransportURI xmlns:u=\"" + SERVICE_TYPE + "\">\r\n"
                + "<InstanceID>0</InstanceID>\r\n"
                + "<CurrentURI>" + escapeXml(videoUrl) + "</CurrentURI>\r\n"
                + "<CurrentURIMetaData>" + escapeXml(metadata) + "</CurrentURIMetaData>\r\n"
                + "</u:SetAVTransportURI>\r\n"
                + "</s:Body>\r\n"
                + "</s:Envelope>";
        return soapPost(device.controlUrl, "SetAVTransportURI", body);
    }

    /** Play:开始播放 */
    public boolean play(CastDevice device) throws Exception {
        String body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n"
                + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                + "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\r\n"
                + "<s:Body>\r\n"
                + "<u:Play xmlns:u=\"" + SERVICE_TYPE + "\">\r\n"
                + "<InstanceID>0</InstanceID>\r\n"
                + "<Speed>1</Speed>\r\n"
                + "</u:Play>\r\n"
                + "</s:Body>\r\n"
                + "</s:Envelope>";
        return soapPost(device.controlUrl, "Play", body);
    }

    /** Stop:停止播放 */
    public boolean stop(CastDevice device) throws Exception {
        String body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n"
                + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                + "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\r\n"
                + "<s:Body>\r\n"
                + "<u:Stop xmlns:u=\"" + SERVICE_TYPE + "\">\r\n"
                + "<InstanceID>0</InstanceID>\r\n"
                + "</u:Stop>\r\n"
                + "</s:Body>\r\n"
                + "</s:Envelope>";
        return soapPost(device.controlUrl, "Stop", body);
    }

    private boolean soapPost(String controlUrl, String action, String body) throws Exception {
        URL url = new URL(controlUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(10000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
        conn.setRequestProperty("SOAPAction", "\"" + SERVICE_TYPE + "#" + action + "\"");
        conn.setRequestProperty("User-Agent", "NovaBox/1.0 UPnP/1.0");
        byte[] data = body.getBytes("UTF-8");
        conn.setFixedLengthStreamingMode(data.length);
        try (java.io.OutputStream out = conn.getOutputStream()) {
            out.write(data);
        }
        int code = conn.getResponseCode();
        // 200 成功;500 也可能成功(某些设备返回 500 但实际已执行)
        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (is != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) baos.write(buf, 0, n);
            is.close();
            String respBody = new String(baos.toByteArray(), "UTF-8");
            // 500 带 UPnPError 才算真正失败
            if (code == 500 && respBody.contains("UPnPError")) return false;
        }
        conn.disconnect();
        return code < 400;
    }

    private String buildMetadata(String videoUrl, String title) {
        // DIDL-Lite 元数据:部分设备要求非空才能推送
        String safeTitle = escapeXml(title == null ? "NovaBox" : title);
        return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" "
                + "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" "
                + "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\" "
                + "xmlns:sec=\"http://www.sec.co.kr/\">"
                + "<item id=\"0\" parentID=\"-1\" restricted=\"0\">"
                + "<dc:title>" + safeTitle + "</dc:title>"
                + "<upnp:class>object.item.videoItem</upnp:class>"
                + "<upnp:mimeType>video/mp4</upnp:mimeType>"
                + "<res protocolInfo=\"http-get:*:video/mp4:DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000\">"
                + escapeXml(videoUrl) + "</res>"
                + "</item>"
                + "</DIDL-Lite>";
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
