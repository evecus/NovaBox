package com.mobile.novabox.cast;

import com.mobile.novabox.api.ApiConfig;
import com.mobile.novabox.server.ControlManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 投屏相关工具:
 * - resolve:URL 决策(直链优先,防盗链才转局域网 IP)
 * - resolvePlayUrl:复用播放链路,把原始 playUrl 用 playerContent 解析成直链
 *   (下载和详情页投屏都需要)
 */
public class CastUrlResolver {

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);

    /**
     * @param rawUrl     当前播放的 URL(playerContent 解析结果)
     * @param headers    当前播放的 headers(非空且有内容 → 防盗链源)
     * @return 推送给电视的 URL
     */
    public static String resolve(String rawUrl, Map<String, String> headers) {
        CastResolveResult r = resolveWithProxyFlag(rawUrl, headers);
        return r.url;
    }

    /**
     * 与 {@link #resolve} 相同的地址转换逻辑,额外返回"本次投屏是否依赖本机代理"。
     * 依赖代理意味着:App 进程/RemoteServer 一旦停止,电视端会播放中断,
     * 调用方应据此决定是否需要启动 {@link CastProxyService} 保活。
     */
    public static CastResolveResult resolveWithProxyFlag(String rawUrl, Map<String, String> headers) {
        if (rawUrl == null || rawUrl.isEmpty()) return new CastResolveResult(rawUrl, false);

        // 代理地址(127.0.0.1) → 替换为局域网 IP
        if (rawUrl.contains("127.0.0.1:9978")) {
            String lanIp = lanIp();
            if (lanIp != null && !lanIp.isEmpty()) {
                return new CastResolveResult(rawUrl.replace("127.0.0.1:9978", lanIp + ":9978"), true);
            }
            return new CastResolveResult(rawUrl, true);
        }
        // 直链:直接返回(电视自己播),不依赖本机代理
        return new CastResolveResult(rawUrl, false);
    }

    /**
     * 复用播放链路:sp.playerContent(flag, url, vipFlags) 把原始 URL 解析成直链。
     * 失败返回 null,headers 可能需要一并使用(防盗链)。
     */
    public static ResolveResult resolvePlayUrl(String sourceKey, String playFlag, String url) {
        if (sourceKey == null || sourceKey.isEmpty() || url == null || url.isEmpty()) {
            return null;
        }
        try {
            com.mobile.novabox.bean.SourceBean sourceBean = ApiConfig.get().getSource(sourceKey);
            if (sourceBean == null) return null;
            final com.github.catvod.crawler.Spider sp = ApiConfig.get().getCSP(sourceBean);
            if (sp == null) return null;
            final String u = url;
            Future<String> future = POOL.submit((Callable<String>) () -> {
                java.util.List<String> vipFlags = ApiConfig.get().getVipParseFlags();
                return sp.playerContent(playFlag, u, vipFlags);
            });
            String json = future.get(20, TimeUnit.SECONDS);
            if (json == null || json.isEmpty()) return null;
            org.json.JSONObject jo = new org.json.JSONObject(json);
            String resolvedUrl = jo.optString("url", "");
            if (resolvedUrl.isEmpty()) return null;
            // 提取 headers(防盗链);playerContent 的 json 可能含 "header" 字段
            Map<String, String> headers = null;
            org.json.JSONObject headerObj = jo.optJSONObject("header");
            if (headerObj != null) {
                headers = new HashMap<>();
                java.util.Iterator<String> it = headerObj.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    headers.put(k, headerObj.optString(k));
                }
            }
            return new ResolveResult(resolvedUrl, headers);
        } catch (Throwable th) {
            return null;
        }
    }

    private static String lanIp() {
        try {
            String addr = ControlManager.get().getAddress(false);
            if (addr == null || addr.isEmpty()) return null;
            String s = addr.replace("http://", "").replace("https://", "");
            int idx = s.indexOf(':');
            return idx > 0 ? s.substring(0, idx) : s;
        } catch (Throwable th) {
            return null;
        }
    }

    public static class ResolveResult {
        public final String url;
        public final Map<String, String> headers;

        public ResolveResult(String url, Map<String, String> headers) {
            this.url = url;
            this.headers = headers;
        }
    }

    /** resolve 地址转换结果 + 是否依赖本机代理(9978)。 */
    public static class CastResolveResult {
        public final String url;
        public final boolean usesLocalProxy;

        public CastResolveResult(String url, boolean usesLocalProxy) {
            this.url = url;
            this.usesLocalProxy = usesLocalProxy;
        }
    }
}
