package com.mobile.novabox.cast;

import android.content.Context;
import android.net.wifi.WifiManager;

import java.io.ByteArrayInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * UPnP SSDP 局域网设备发现:
 * 1. 组播 239.255.255.250:1900 发 M-SEARCH(找 MediaRenderer)
 * 2. 收集响应里的 LOCATION(设备描述 URL)
 * 3. 逐个 GET 描述 XML,解析 friendlyName / AVTransport 控制 URL
 * 4. 过滤出支持投屏的 MediaRenderer,去重
 */
public class UpnpDiscovery {

    private static final String MULTICAST_ADDR = "239.255.255.250";
    private static final int MULTICAST_PORT = 1900;
    private static final int SEARCH_TIMEOUT_MS = 6000;
    private static final int SEARCH_TRIES = 2;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    /** 同步搜索局域网投屏设备(工作线程调用) */
    public List<CastDevice> discover(Context context) {
        List<CastDevice> result = new ArrayList<>();
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null) return result;
        // 组播锁:防止系统休眠丢弃 SSDP 组播包
        WifiManager.MulticastLock lock = wm.createMulticastLock("novabox_cast");
        lock.setReferenceCounted(true);
        lock.acquire();
        try {
            Map<String, CastDevice> byDescription = new HashMap<>();
            DatagramSocket socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            try {
                // 优先绑定 1900(部分设备只往 1900 回),冲突则降级临时端口
                socket.bind(new InetSocketAddress(MULTICAST_PORT));
            } catch (Throwable bindErr) {
                socket.bind(new InetSocketAddress(0));
            }
            socket.setSoTimeout(1500);
            InetAddress group = InetAddress.getByName(MULTICAST_ADDR);

            long deadline = System.currentTimeMillis() + SEARCH_TIMEOUT_MS;
            int round = 0;
            // 收包循环:M-SEARCH 发 SEARCH_TRIES 轮,期间持续收
            while (System.currentTimeMillis() < deadline) {
                if (round < SEARCH_TRIES) {
                    sendSearch(socket, group);
                    round++;
                }
                try {
                    byte[] buf = new byte[8192];
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    socket.receive(pkt);
                    String msg = new String(pkt.getData(), 0, pkt.getLength(), "ISO-8859-1");
                    parseSearchResponse(msg, pkt.getAddress(), byDescription);
                } catch (SocketTimeoutException ignored) {
                    // 正常超时,继续循环直到 deadline
                } catch (Throwable ignored) {
                }
            }
            socket.close();

            // 逐个拉设备描述 XML
            for (CastDevice dev : byDescription.values()) {
                if (dev.isMediaRenderer()) {
                    CastDevice filled = fetchDescription(dev);
                    if (filled != null) result.add(filled);
                }
            }
        } catch (Throwable th) {
            th.printStackTrace();
        } finally {
            try {
                lock.release();
            } catch (Throwable ignored) {
            }
        }
        return result;
    }

    private void sendSearch(DatagramSocket socket, InetAddress group) throws Exception {
        String msg = "M-SEARCH * HTTP/1.1\r\n"
                + "HOST: 239.255.255.250:1900\r\n"
                + "MAN: \"ssdp:discover\"\r\n"
                + "MX: 3\r\n"
                + "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n"
                + "\r\n";
        byte[] data = msg.getBytes("UTF-8");
        DatagramPacket pkt = new DatagramPacket(data, data.length, group, MULTICAST_PORT);
        socket.send(pkt);
    }

    /** 解析 SSDP 响应:LOCATION 头部是设备描述 URL */
    private void parseSearchResponse(String msg, InetAddress from, Map<String, CastDevice> byDescription) {
        if (msg == null || !msg.contains("200 OK")) return;
        String location = null;
        String server = null;
        String st = null;
        for (String line : msg.split("\r\n")) {
            String lower = line.toLowerCase();
            if (lower.startsWith("location:")) {
                location = line.substring(line.indexOf(':') + 1).trim();
            } else if (lower.startsWith("server:")) {
                server = line.substring(line.indexOf(':') + 1).trim();
            } else if (lower.startsWith("st:")) {
                st = line.substring(line.indexOf(':') + 1).trim();
            }
        }
        if (location == null || location.isEmpty()) return;
        CastDevice dev = new CastDevice();
        dev.descriptionUrl = location;
        dev.baseUrl = extractBaseUrl(location);
        dev.ip = from != null ? from.getHostAddress() : null;
        dev.deviceType = st;
        byDescription.put(location, dev);
    }

    private String extractBaseUrl(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            int port = uri.getPort() > 0 ? uri.getPort() : ("https".equals(uri.getScheme()) ? 443 : 80);
            return uri.getScheme() + "://" + uri.getHost() + ":" + port;
        } catch (Exception e) {
            return url;
        }
    }

    /** 拉设备描述 XML,提取 friendlyName + AVTransport 控制 URL */
    private CastDevice fetchDescription(CastDevice dev) {
        try {
            Request request = new Request.Builder()
                    .url(dev.descriptionUrl)
                    .header("User-Agent", "NovaBox/1.0 UPnP/1.0")
                    .build();
            Response resp = client.newCall(request).execute();
            if (!resp.isSuccessful()) return null;
            String xml = resp.body() != null ? resp.body().string() : "";
            resp.close();
            if (xml.isEmpty()) return null;

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));

            // friendlyName
            NodeList nameNodes = doc.getElementsByTagName("friendlyName");
            if (nameNodes.getLength() > 0) {
                dev.friendlyName = nameNodes.item(0).getTextContent().trim();
            }
            if (dev.friendlyName == null || dev.friendlyName.isEmpty()) {
                dev.friendlyName = dev.ip == null ? "未知设备" : dev.ip;
            }

            // 找 serviceType 含 AVTransport 的 service → 拿 controlURL
            NodeList serviceNodes = doc.getElementsByTagName("service");
            for (int i = 0; i < serviceNodes.getLength(); i++) {
                Node service = serviceNodes.item(i);
                String type = getChildText(service, "serviceType");
                if (type != null && type.contains("AVTransport")) {
                    String controlUrl = getChildText(service, "controlURL");
                    if (controlUrl != null && !controlUrl.isEmpty()) {
                        dev.controlUrl = resolveUrl(dev.baseUrl, controlUrl);
                    }
                }
            }
            return dev.controlUrl == null || dev.controlUrl.isEmpty() ? null : dev;
        } catch (Throwable th) {
            return null;
        }
    }

    private String getChildText(Node parent, String tag) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element && tag.equals(node.getNodeName())) {
                return node.getTextContent().trim();
            }
        }
        return null;
    }

    private String resolveUrl(String base, String relative) {
        if (relative.startsWith("http://") || relative.startsWith("https://")) return relative;
        try {
            java.net.URI baseUri = new java.net.URI(base);
            return baseUri.resolve(relative).toString();
        } catch (Exception e) {
            if (base != null && relative.startsWith("/")) return base + relative;
            return relative;
        }
    }
}
