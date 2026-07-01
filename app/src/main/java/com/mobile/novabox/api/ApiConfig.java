package com.mobile.novabox.api;

import static com.mobile.novabox.util.RegexUtils.getPattern;

import android.app.Activity;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.crawler.JarLoader;
import com.github.catvod.crawler.JsLoader;
import com.github.catvod.crawler.pyLoader;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.python.IPyLoader;
import com.mobile.novabox.base.App;
import com.mobile.novabox.bean.LiveChannelGroup;
import com.mobile.novabox.bean.IJKCode;
import com.mobile.novabox.bean.LiveChannelItem;
import com.mobile.novabox.bean.LiveSettingGroup;
import com.mobile.novabox.bean.LiveSettingItem;
import com.mobile.novabox.bean.ParseBean;
import com.mobile.novabox.bean.ProxyRule;
import com.mobile.novabox.bean.SourceBean;
import com.mobile.novabox.server.ControlManager;
import com.mobile.novabox.util.AES;
import com.mobile.novabox.util.AdBlocker;
import com.mobile.novabox.util.DefaultConfig;
import com.mobile.novabox.util.FileUtils;
import com.mobile.novabox.util.HawkConfig;
import com.mobile.novabox.util.HistoryHelper;
import com.mobile.novabox.util.LOG;
import com.mobile.novabox.util.M3u8;
import com.mobile.novabox.util.MD5;
import com.mobile.novabox.util.OkGoHelper;
import com.mobile.novabox.util.Proxy;
import com.mobile.novabox.util.VideoParseRuler;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author pj567
 * @date :2020/12/18
 * @description:
 */
public class ApiConfig {
    private static ApiConfig instance;
    private final LinkedHashMap<String, SourceBean> sourceBeanList;
    private SourceBean mHomeSource;
    private ParseBean mDefaultParse;
    private final List<LiveChannelGroup> liveChannelGroupList;
    private final List<ParseBean> parseBeanList;
    private List<String> vipParseFlags;
    private Map<String,String> myHosts;
    private List<IJKCode> ijkCodes;
    private String spider = null;
    private String currentPyKey = "";
    private String currentLivePyKey = "";
    private String currentPlaySourceKey = "";
    public String wallpaper = "";
    private String danmaku = "";

    private final SourceBean emptyHome = new SourceBean();

    private final JarLoader jarLoader = new JarLoader();
    private final JsLoader jsLoader = new JsLoader();
    private final IPyLoader pyLoader =  new pyLoader();
    private final Gson gson;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService jarLoadExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService danmuSearchExecutor = Executors.newSingleThreadExecutor();

    private final String userAgent = "okhttp/3.15";

    private final String requestAccept = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9";

    private final String defaultLiveObjString = "{\"lives\":[{\"name\":\"txt_m3u\",\"type\":0,\"url\":\"txt_m3u_url\"}]}";
    private ApiConfig() {
        clearLoader();
        sourceBeanList = new LinkedHashMap<>();
        liveChannelGroupList = new ArrayList<>();
        parseBeanList = new ArrayList<>();
        searchSourceBeanList = new ArrayList<>();
        gson = new Gson();
        Hawk.put(HawkConfig.LIVE_GROUP_LIST,new JsonArray());
        loadDefaultConfig();
    }

    public static ApiConfig get() {
        if (instance == null) {
            synchronized (ApiConfig.class) {
                if (instance == null) {
                    instance = new ApiConfig();
                }
            }
        }
        return instance;
    }

    public static String FindResult(String json, String configKey) {
        String content = json;
        try {
            if (AES.isJson(content)) return content;
            Pattern pattern = getPattern("[A-Za-z0-9]{8}\\*\\*");
            Matcher matcher = pattern.matcher(content);
            if(matcher.find()){
                content=content.substring(content.indexOf(matcher.group()) + 10);
                content = new String(Base64.decode(content, Base64.DEFAULT));
            }
            content = content.trim();
            if (content.startsWith("2423")) {
                content = content.replaceAll("\\s+", "");
                String data = content.substring(content.indexOf("2324") + 4, content.length() - 26);
                content = new String(AES.toBytes(content)).toLowerCase();
                String key = AES.rightPadding(content.substring(content.indexOf("$#") + 2, content.indexOf("#$")), "0", 16);
                String iv = AES.rightPadding(content.substring(content.length() - 13), "0", 16);
                json = AES.CBC(data, key, iv);
            }else if (configKey !=null && !AES.isJson(content)) {
                json = AES.ECB(content, configKey);
            }
            else{
                json = content;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return json;
    }

    private static byte[] getImgJar(String body){
        Pattern pattern = getPattern("[A-Za-z0-9]{8}\\*\\*");
        Matcher matcher = pattern.matcher(body);
        if(matcher.find()){
            body = body.substring(body.indexOf(matcher.group()) + 10);
            return Base64.decode(body, Base64.DEFAULT);
        }
        return "".getBytes();
    }

    private String TempKey = null;
    private String configUrl(String apiUrl){
        String configUrl = "", pk = ";pk;";
        apiUrl=apiUrl.replace("file://", "clan://localhost/");
        if (apiUrl.contains(pk)) {
            String[] a = apiUrl.split(pk);
            TempKey = a[1];
            if (apiUrl.startsWith("clan")){
                configUrl = clanToAddress(a[0]);
            }else if (apiUrl.startsWith("http")){
                configUrl = a[0];
            }else {
                configUrl = "http://" + a[0];
            }
        } else if (apiUrl.startsWith("clan")) {
            configUrl = clanToAddress(apiUrl);
        } else if (!apiUrl.startsWith("http")) {
            configUrl = "http://" + apiUrl;
        } else {
            configUrl = apiUrl;
        }
        return configUrl;
    }
    public void loadConfig(boolean useCache, LoadConfigCallback callback, Activity activity) {
        String apiUrl = Hawk.get(HawkConfig.API_URL, "");
        //独立加载直播配置
        String liveApiUrl = Hawk.get(HawkConfig.LIVE_API_URL, "");
        String liveApiConfigUrl=configUrl(liveApiUrl);
        if(!liveApiUrl.isEmpty() && !liveApiUrl.equals(apiUrl)){
            if(liveApiUrl.contains(".txt") || liveApiUrl.contains(".m3u") || liveApiUrl.contains("=txt") || liveApiUrl.contains("=m3u")){
                initLiveSettings();
                parseLiveJson(liveApiUrl, defaultLiveObjString.replace("txt_m3u_url", liveApiConfigUrl));
            }else {
                File live_cache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/" + MD5.encode(liveApiUrl));
                LOG.i("echo-加载独立直播");
                if (useCache && live_cache.exists()) {
                    try {
                        // 缓存内容嗅探
                        BufferedReader bReader2 = new BufferedReader(new InputStreamReader(new FileInputStream(live_cache), "UTF-8"));
                        StringBuilder sbCache2 = new StringBuilder();
                        String sLine2;
                        while ((sLine2 = bReader2.readLine()) != null) sbCache2.append(sLine2).append("\n");
                        bReader2.close();
                        String cachedContent2 = sbCache2.toString();
                        if (isLiveContent(cachedContent2)) {
                            initLiveSettings();
                            parseLiveJson(liveApiUrl, defaultLiveObjString.replace("txt_m3u_url", liveApiConfigUrl));
                        } else {
                            parseLiveJson(liveApiUrl, cachedContent2);
                        }
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }else {
                    OkGo.<String>get(liveApiConfigUrl)
                            .headers("User-Agent", userAgent)
                            .headers("Accept", requestAccept)
                            .execute(new AbsCallback<String>() {
                                @Override
                                public void onSuccess(Response<String> response) {
                                    try {
                                        String content = response.body();
                                        // 内容嗅探：如果是直播源文本，走 txt/m3u 路径
                                        if (isLiveContent(content)) {
                                            initLiveSettings();
                                            parseLiveJson(liveApiUrl, defaultLiveObjString.replace("txt_m3u_url", liveApiConfigUrl));
                                        } else {
                                            parseLiveJson(liveApiUrl, content);
                                        }
                                        FileUtils.saveCache(live_cache, content);
                                    } catch (Throwable th) {
                                        th.printStackTrace();
                                        callback.notice("解析直播配置失败");
                                    }
                                }

                                @Override
                                public void onError(Response<String> response) {
                                    super.onError(response);
                                    if (live_cache.exists()) {
                                        try {
                                            parseLiveJson(liveApiUrl, live_cache);
                                            callback.success();
                                            return;
                                        } catch (Throwable th) {
                                            th.printStackTrace();
                                        }
                                    }
                                    callback.notice("直播配置拉取失败");
                                }

                                public String convertResponse(okhttp3.Response response) throws Throwable {
                                    String result = "";
                                    if (response.body() == null) {
                                        result = "";
                                    }else {
                                        result = FindResult(response.body().string(), TempKey);
                                        if (liveApiUrl.startsWith("clan")) {
                                            result = clanContentFix(clanToAddress(liveApiUrl), result);
                                        }
                                        //假相對路徑
                                        result = fixContentPath(liveApiUrl,result);
                                    }
                                    return result;
                                }
                            });
                }
            }
        }

        if (apiUrl.isEmpty()) {
            callback.error("-1");
            return;
        }
        File cache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/" + MD5.encode(apiUrl));
        if (useCache && cache.exists()) {
            try {
                String json = readConfigFile(cache);
                if (switchApiCollectionIfNeeded(apiUrl, json)) {
                    loadConfig(false, callback, activity);
                    return;
                }
                clearApiLinesIfUnmatched(apiUrl);
                parseJson(apiUrl, json);
                callback.success();
                return;
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        String configUrl=configUrl(apiUrl);
        // 使用内部存储，将当前配置地址写入到应用的私有目录中
//        File configUrlFile = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/config_url");
//        FileUtils.saveCache(configUrlFile,configUrl);

        OkGo.<String>get(configUrl)
                .headers("User-Agent", userAgent)
                .headers("Accept", requestAccept)
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            String json = response.body();
//                            LOG.i("echo-ConfigJson"+json);
                            if (switchApiCollectionIfNeeded(apiUrl, json)) {
                                FileUtils.saveCache(cache,json);
                                loadConfig(false, callback, activity);
                                return;
                            }
                            clearApiLinesIfUnmatched(apiUrl);
                            parseJson(apiUrl, json);
                            FileUtils.saveCache(cache,json);
                            callback.success();
                        } catch (Throwable th) {
                            th.printStackTrace();
                            callback.error("解析配置失败");
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        if (cache.exists()) {
                            try {
                                String json = readConfigFile(cache);
                                if (switchApiCollectionIfNeeded(apiUrl, json)) {
                                    loadConfig(false, callback, activity);
                                    return;
                                }
                                clearApiLinesIfUnmatched(apiUrl);
                                parseJson(apiUrl, json);
                                callback.success();
                                return;
                            } catch (Throwable th) {
                                th.printStackTrace();
                            }
                        }
                        callback.error("拉取配置失败\n" + (response.getException() != null ? response.getException().getMessage() : ""));
                    }

                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        String result = "";
                        if (response.body() == null) {
                            result = "";
                        } else {
                            result = FindResult(response.body().string(), TempKey);
                        }

                        if (apiUrl.startsWith("clan")) {
                            result = clanContentFix(clanToAddress(apiUrl), result);
                        }
                        //假相對路徑
                        result = fixContentPath(apiUrl,result);
                        return result;
                    }
                });
    }

    public void loadLiveConfig(boolean useCache, LoadConfigCallback callback) {
        String apiUrl = Hawk.get(HawkConfig.LIVE_API_URL, "");
        if (apiUrl.isEmpty()) {
            callback.error("-1");
            return;
        }
        String liveApiConfigUrl = configUrl(apiUrl);
        if (apiUrl.contains(".txt") || apiUrl.contains(".m3u") || apiUrl.contains("=txt") || apiUrl.contains("=m3u")) {
            initLiveSettings();
            parseLiveJson(apiUrl, defaultLiveObjString.replace("txt_m3u_url", liveApiConfigUrl));
            if (!hasLiveConfigResult()) {
                callback.error("直播配置解析失败");
                return;
            }
            callback.success();
            return;
        }
        File live_cache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/" + MD5.encode(apiUrl));
        LOG.i("echo-load live config");
        if (useCache && live_cache.exists()) {
            try {
                // 读缓存内容，先嗅探是否为直播文本
                BufferedReader bReader = new BufferedReader(new InputStreamReader(new FileInputStream(live_cache), "UTF-8"));
                StringBuilder sbCache = new StringBuilder();
                String sLine;
                while ((sLine = bReader.readLine()) != null) sbCache.append(sLine).append("\n");
                bReader.close();
                String cachedContent = sbCache.toString();
                if (isLiveContent(cachedContent)) {
                    initLiveSettings();
                    parseLiveJson(apiUrl, defaultLiveObjString.replace("txt_m3u_url", liveApiConfigUrl));
                } else {
                    parseLiveJson(apiUrl, cachedContent);
                }
                if (hasLiveConfigResult()) {
                    callback.success();
                    return;
                }
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        OkGo.<String>get(liveApiConfigUrl)
                .headers("User-Agent", userAgent)
                .headers("Accept", requestAccept)
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            String content = response.body();
                            // 内容嗅探：如果返回的是直播源文本（M3U/TXT），而非 TVBox JSON 配置
                            // 则走与 .m3u/.txt URL 相同的解析路径
                            if (isLiveContent(content)) {
                                initLiveSettings();
                                parseLiveJson(apiUrl, defaultLiveObjString.replace("txt_m3u_url", liveApiConfigUrl));
                            } else {
                                parseLiveJson(apiUrl, content);
                            }
                            if (!hasLiveConfigResult()) {
                                callback.error("直播配置解析失败");
                                return;
                            }
                            FileUtils.saveCache(live_cache, content);
                            callback.success();
                        } catch (Throwable th) {
                            th.printStackTrace();
                            callback.error("直播配置解析失败");
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        if (live_cache.exists()) {
                            try {
                                parseLiveJson(apiUrl, live_cache);
                                if (hasLiveConfigResult()) {
                                    callback.success();
                                    return;
                                }
                            } catch (Throwable th) {
                                th.printStackTrace();
                            }
                        }
                        callback.error("直播配置拉取失败");
                    }

                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        String result = "";
                        if (response.body() == null) {
                            result = "";
                        } else {
                            result = FindResult(response.body().string(), TempKey);
                            if (apiUrl.startsWith("clan")) {
                                result = clanContentFix(clanToAddress(apiUrl), result);
                            }
                            result = fixContentPath(apiUrl, result);
                        }
                        return result;
                    }
        });
    }

    private boolean hasLiveConfigResult() {
        return liveChannelGroupList != null && !liveChannelGroupList.isEmpty();
    }

    /**
     * 判断内容是否为直播源文本（M3U / TXT 格式），而非 TVBox JSON 配置。
     * 用于 URL 没有 .txt/.m3u 后缀时的内容嗅探。
     */
    private boolean isLiveContent(String content) {
        if (content == null) return false;
        String trimmed = content.trim();
        // M3U 格式
        if (trimmed.startsWith("#EXTM3U") || trimmed.startsWith("#EXTINF")) return true;
        // TXT 频道表格式：含 #genre# 分组标记，或第一行形如 "频道名,http..."
        if (trimmed.contains("#genre#")) return true;
        // 兜底：第一行是 "任意名称,http(s)://" 格式
        String firstLine = trimmed.split("[\r\n]")[0].trim();
        int comma = firstLine.indexOf(',');
        if (comma > 0 && comma < firstLine.length() - 1) {
            String afterComma = firstLine.substring(comma + 1).trim();
            if (afterComma.startsWith("http://") || afterComma.startsWith("https://")
                    || afterComma.startsWith("rtsp://") || afterComma.startsWith("rtmp://")
                    || afterComma.startsWith("rtp://")) {
                return true;
            }
        }
        return false;
    }

    public static String getLiveGroupIndexKey() {
        String liveApiUrl = Hawk.get(HawkConfig.LIVE_API_URL, "");
        if (liveApiUrl == null || liveApiUrl.length() == 0) {
            return HawkConfig.LIVE_GROUP_INDEX;
        }
        return HawkConfig.LIVE_GROUP_INDEX + "_" + liveApiUrl;
    }

    public static int getLiveGroupIndex() {
        return Hawk.get(getLiveGroupIndexKey(), 0);
    }

    public static void setLiveGroupIndex(int index) {
        Hawk.put(getLiveGroupIndexKey(), index);
    }

    private static final int LOAD_JAR_MAX_RETRY = 1;

    public void loadJar(boolean useCache, String spider, LoadConfigCallback callback) {
        loadJar(useCache, spider, callback, 0);
    }

    private interface JarLoadCallback {
        void complete(boolean success);
    }

    private void loadJarAsync(File file, JarLoadCallback callback) {
        jarLoadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                boolean success = false;
                try {
                    success = file != null && file.exists() && jarLoader.load(file.getAbsolutePath());
                } catch (Throwable th) {
                    LOG.e("echo---jar Loader threw exception: " + th.getMessage());
                }
                final boolean result = success;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.complete(result);
                    }
                });
            }
        });
    }

    private void loadJar(boolean useCache, String spider, LoadConfigCallback callback, int retryCount) {
        String[] urls = spider.split(";md5;");
        String jarUrl = urls[0];
        String md5 = urls.length > 1 ? urls[1].trim() : "";
        File cache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/csp/"+MD5.string2MD5(jarUrl)+".jar");

        if (!md5.isEmpty() || useCache) {
            if (cache.exists() && (useCache || MD5.getFileMd5(cache).equalsIgnoreCase(md5))) {
                if (cache.exists()) {
                    loadJarAsync(cache, new JarLoadCallback() {
                        @Override
                        public void complete(boolean success) {
                            if (success) {
                                callback.success();
                            } else {
                                callback.error("md5缓存失效");
                            }
                        }
                    });
                    return;
                }
                if (jarLoader.load(cache.getAbsolutePath())) {
                    callback.success();
                } else {
                    callback.error("md5缓存失效");
                }
                return;
            }
        }else {
            if (Boolean.parseBoolean(jarCache) && cache.exists() && !FileUtils.isWeekAgo(cache)) {
                LOG.i("echo-load jar jarCache:"+jarUrl);
                if (cache.exists()) {
                    loadJarAsync(cache, new JarLoadCallback() {
                        @Override
                        public void complete(boolean success) {
                            if (success) {
                                callback.success();
                            } else {
                                loadJar(false, spider, callback, retryCount);
                            }
                        }
                    });
                    return;
                }
                if (jarLoader.load(cache.getAbsolutePath())) {
                    callback.success();
                    return;
                }
            }
        }

        boolean isJarInImg = jarUrl.startsWith("img+");
        jarUrl = jarUrl.replace("img+", "");
        LOG.i("echo-load jar start:"+jarUrl);
        final String requestUrl = jarUrl;
        OkGo.<File>get(jarUrl)
                .headers("User-Agent", userAgent)
                .headers("Accept", requestAccept)
                .execute(new AbsCallback<File>() {

                    private boolean retryLoad(String reason) {
                        if (retryCount >= LOAD_JAR_MAX_RETRY) return false;
                        if (cache.exists() && !cache.delete()) {
                            LOG.i("echo---delete bad jar cache failed:" + cache.getAbsolutePath());
                        }
                        LOG.i("echo---retry load jar reason:" + reason + " url:" + requestUrl + " retry:" + (retryCount + 1));
                        loadJar(false, spider, callback, retryCount + 1);
                        return true;
                    }

                    @Override
                    public File convertResponse(okhttp3.Response response){
                        File cacheDir = cache.getParentFile();
                        assert cacheDir != null;
                        if (!cacheDir.exists()) cacheDir.mkdirs();
                        if (cache.exists()) cache.delete();
                        // 3. 使用 try-with-resources 确保流关闭
                        assert response.body() != null;
                        try (FileOutputStream fos = new FileOutputStream(cache)) {
                            if (isJarInImg) {
                                String respData = response.body().string();
                                LOG.i("echo---jar Response: " + respData);
                                byte[] imgJar = getImgJar(respData);
                                if (imgJar == null || imgJar.length == 0) {
                                    LOG.e("echo---Generated JAR data is empty");
                                    if (retryLoad("empty_img_jar")) return null;
                                    callback.error("JAR is empty");
                                    return null;
                                }
                                fos.write(imgJar);
                            } else {
                                // 使用流式传输避免内存溢出
                                InputStream inputStream = response.body().byteStream();
                                byte[] buffer = new byte[4096];
                                int bytesRead;
                                while ((bytesRead = inputStream.read(buffer)) != -1) {
                                    fos.write(buffer, 0, bytesRead);
                                }
                            }
                            fos.flush();
                        } catch (IOException e) {
                            return null;
                        }
                        return cache;
                    }

                    @Override
                    public void onSuccess(Response<File> response) {
                        File file = response.body();
                        if (file != null && file.exists()) {
                            loadJarAsync(file, new JarLoadCallback() {
                                @Override
                                public void complete(boolean success) {
                                    if (success) {
                                        LOG.i("echo---load-jar-success");
                                        callback.success();
                                    } else {
                                        LOG.e("echo---jar Loader returned false");
                                        if (retryLoad("loader_false")) return;
                                        callback.error("JAR加载失败");
                                    }
                                }
                            });
                            return;
                        }
                        if (file != null && file.exists()) {
                            try {
                                if (jarLoader.load(file.getAbsolutePath())) {
                                    LOG.i("echo---load-jar-success");
                                    callback.success();
                                } else {
                                    LOG.e("echo---jar Loader returned false");
                                    if (retryLoad("loader_false")) return;
                                    callback.error("JAR加载失败");
                                }
                            } catch (Exception e) {
                                LOG.e("echo---jar Loader threw exception: " + e.getMessage());
                                if (retryLoad("loader_exception")) return;
                                callback.error("JAR加载异常: ");
                            }
                        } else {
                            LOG.e("echo---jar File not found");
                            if (retryLoad("file_missing")) return;
                            callback.error("JAR file not found");
                        }
                    }

                    @Override
                    public void onError(Response<File> response) {
                        Throwable ex = response.getException();
                        if (ex != null) {
                            LOG.i("echo---jar Request failed: " + ex.getMessage());
                        }
                        if (cache.exists()) {
                            loadJarAsync(cache, new JarLoadCallback() {
                                @Override
                                public void complete(boolean success) {
                                    if (success) {
                                        callback.success();
                                    } else {
                                        if (retryLoad("request_error")) return;
                                        callback.error("网络错误");
                                    }
                                }
                            });
                            return;
                        }
                        if (cache.exists() && jarLoader.load(cache.getAbsolutePath())) {
                            callback.success();
                            return;
                        }
                        if (retryLoad("request_error")) return;
                        if(cache.exists())jarLoader.load(cache.getAbsolutePath());
                        callback.error("网络错误");
                    }
                });
    }

    private void parseJson(String apiUrl, File f) throws Throwable {
        parseJson(apiUrl, readConfigFile(f));
    }

    private String readConfigFile(File f) throws Throwable {
        BufferedReader bReader = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String s = "";
        while ((s = bReader.readLine()) != null) {
            sb.append(s + "\n");
        }
        bReader.close();
        return sb.toString();
    }

    private boolean switchApiCollectionIfNeeded(String apiUrl, String jsonStr) {
        ArrayList<String> apiLines = parseApiCollection(jsonStr);
        if (apiLines.isEmpty()) {
            return false;
        }
        String firstApi = HistoryHelper.getApiLineUrl(apiLines.get(0));
        if (TextUtils.isEmpty(firstApi) || firstApi.equals(apiUrl)) {
            return false;
        }
        Hawk.put(HawkConfig.API_LINE_LIST, apiLines);
        Hawk.put(HawkConfig.API_LINE_SOURCE, apiUrl);
        Hawk.put(HawkConfig.API_URL, firstApi);
        HistoryHelper.setApiHistory(apiUrl);
        return true;
    }

    private ArrayList<String> parseApiCollection(String jsonStr) {
        ArrayList<String> apiLines = new ArrayList<>();
        try {
            String json = trimJsonObject(jsonStr);
            if (TextUtils.isEmpty(json)) {
                return apiLines;
            }
            JsonObject infoJson = gson.fromJson(json, JsonObject.class);
            if (infoJson == null || infoJson.has("sites") || !infoJson.has("urls") || !infoJson.get("urls").isJsonArray()) {
                return apiLines;
            }
            JsonArray urls = infoJson.get("urls").getAsJsonArray();
            for (JsonElement element : urls) {
                String name = "";
                String url = "";
                if (element.isJsonObject()) {
                    JsonObject item = element.getAsJsonObject();
                    name = DefaultConfig.safeJsonString(item, "name", "");
                    url = DefaultConfig.safeJsonString(item, "url", "");
                    if (TextUtils.isEmpty(url)) {
                        url = DefaultConfig.safeJsonString(item, "api", "");
                    }
                } else if (element.isJsonPrimitive()) {
                    url = element.getAsString();
                }
                if (!TextUtils.isEmpty(url)) {
                    apiLines.add(HistoryHelper.buildApiLine(name, url));
                }
            }
        } catch (Throwable ignored) {
        }
        return apiLines;
    }

    private String trimJsonObject(String content) {
        if (content == null) {
            return "";
        }
        String trimContent = content.trim();
        int start = trimContent.indexOf("{");
        int end = trimContent.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return trimContent.substring(start, end + 1);
        }
        return trimContent;
    }

    private void clearApiLinesIfUnmatched(String apiUrl) {
        ArrayList<String> apiLines = Hawk.get(HawkConfig.API_LINE_LIST, new ArrayList<String>());
        if (apiLines.isEmpty()) {
            return;
        }
        for (String apiLine : apiLines) {
            if (apiUrl.equals(HistoryHelper.getApiLineUrl(apiLine))) {
                return;
            }
        }
        HistoryHelper.clearApiLineList();
    }

    private static  String jarCache ="true";
    private void parseJson(String apiUrl, String jsonStr) {
//        pyLoader.setConfig(jsonStr);
        JsonObject infoJson = gson.fromJson(jsonStr, JsonObject.class);
        // spider
        spider = DefaultConfig.safeJsonString(infoJson, "spider", "");
        jarCache = DefaultConfig.safeJsonString(infoJson, "jarCache", "true");
        danmaku = DefaultConfig.safeJsonString(infoJson, "danmaku", "");
        // wallpaper
        wallpaper = DefaultConfig.safeJsonString(infoJson, "wallpaper", "");
        // 远端站点源
        SourceBean firstSite = null;
        for (JsonElement opt : infoJson.get("sites").getAsJsonArray()) {
            JsonObject obj = (JsonObject) opt;
            SourceBean sb = new SourceBean();
            String siteKey = obj.get("key").getAsString().trim();
            sb.setKey(siteKey);
            sb.setName(obj.has("name")?obj.get("name").getAsString().trim():siteKey);
            sb.setType(obj.get("type").getAsInt());
            sb.setApi(obj.get("api").getAsString().trim());
            sb.setSearchable(DefaultConfig.safeJsonInt(obj, "searchable", 1));
            sb.setQuickSearch(DefaultConfig.safeJsonInt(obj, "quickSearch", 1));
            if(siteKey.startsWith("py_")){
                sb.setFilterable(1);
            }else {
                sb.setFilterable(DefaultConfig.safeJsonInt(obj, "filterable", 1));
            }
            sb.setPlayerUrl(DefaultConfig.safeJsonString(obj, "playUrl", ""));
            sb.setExt(DefaultConfig.safeJsonString(obj, "ext", ""));
            sb.setJar(DefaultConfig.safeJsonString(obj, "jar", ""));
            sb.setPlayerType(DefaultConfig.safeJsonInt(obj, "playerType", -1));
            sb.setCategories(DefaultConfig.safeJsonStringList(obj, "categories"));
            sb.setTimeout(DefaultConfig.safeJsonInt(obj, "timeout", 0));
            sb.setClickSelector(DefaultConfig.safeJsonString(obj, "click", ""));
            sb.setStyle(DefaultConfig.safeJsonString(obj, "style", ""));
            if (firstSite == null) firstSite = sb;
            sourceBeanList.put(siteKey, sb);
        }
        if (sourceBeanList != null && sourceBeanList.size() > 0) {
            String home = Hawk.get(HawkConfig.HOME_API, "");
            SourceBean sh = getSource(home);
            if (sh == null) {
                assert firstSite != null;
                setSourceBean(firstSite);
            }
            else
                setSourceBean(sh);
        }
        // 需要使用vip解析的flag
        vipParseFlags = DefaultConfig.safeJsonStringList(infoJson, "flags");
        // 解析地址
        parseBeanList.clear();
        if(infoJson.has("parses")){
            JsonArray parses = infoJson.get("parses").getAsJsonArray();
            for (JsonElement opt : parses) {
                JsonObject obj = (JsonObject) opt;
                ParseBean pb = new ParseBean();
                pb.setName(obj.get("name").getAsString().trim());
                pb.setUrl(obj.get("url").getAsString().trim());
                String ext = obj.has("ext") ? obj.get("ext").getAsJsonObject().toString() : "";
                pb.setExt(ext);
                pb.setType(DefaultConfig.safeJsonInt(obj, "type", 0));
                parseBeanList.add(pb);
            }
            if(!parseBeanList.isEmpty())addSuperParse();
        }
        // 获取默认解析
        if (parseBeanList != null && parseBeanList.size() > 0) {
            String defaultParse = Hawk.get(HawkConfig.DEFAULT_PARSE, "");
            if (!TextUtils.isEmpty(defaultParse))
                for (ParseBean pb : parseBeanList) {
                    if (pb.getName().equals(defaultParse))
                        setDefaultParse(pb);
                }
            if (mDefaultParse == null)
                setDefaultParse(parseBeanList.get(0));
        }

        // 直播源
        String live_api_url=Hawk.get(HawkConfig.LIVE_API_URL,"");
        if(live_api_url.isEmpty() || apiUrl.equals(live_api_url)){
            LOG.i("echo-load-config_live");
            initLiveSettings();
            if(infoJson.has("lives")){
                JsonArray lives_groups=infoJson.get("lives").getAsJsonArray();
                int live_group_index=getLiveGroupIndex();
                if(live_group_index>lives_groups.size()-1)live_group_index=0;
                Hawk.put(HawkConfig.LIVE_GROUP_LIST,lives_groups);
                // 多源切换列表：只来自直播地址（LIVE_SOURCE_LIST）
                refreshLiveApiHistoryItems();

                JsonObject livesOBJ = lives_groups.get(live_group_index).getAsJsonObject();
                loadLiveApi(livesOBJ);
            }
        }

        myHosts = new HashMap<>();
        if (infoJson.has("hosts")) {
            JsonArray hostsArray = infoJson.getAsJsonArray("hosts");
            for (int i = 0; i < hostsArray.size(); i++) {
                String entry = hostsArray.get(i).getAsString();
                String[] parts = entry.split("=", 2); // 只分割一次，防止 value 里有 =
                if (parts.length == 2) {
                    myHosts.put(parts[0], parts[1]);
                }
            }
        }

        loadProxyRules(infoJson);

        //video parse rule for host
        if (infoJson.has("rules")) {
            VideoParseRuler.clearRule();
            for(JsonElement oneHostRule : infoJson.getAsJsonArray("rules")) {
                JsonObject obj = (JsonObject) oneHostRule;
                //嗅探过滤规则
                if (obj.has("host")) {
                    String host = obj.get("host").getAsString();
                    if (obj.has("rule")) {
                        JsonArray ruleJsonArr = obj.getAsJsonArray("rule");
                        ArrayList<String> rule = new ArrayList<>();
                        for (JsonElement one : ruleJsonArr) {
                            String oneRule = one.getAsString();
                            rule.add(oneRule);
                        }
                        if (rule.size() > 0) {
                            VideoParseRuler.addHostRule(host, rule);
                        }
                    }
                    if (obj.has("filter")) {
                        JsonArray filterJsonArr = obj.getAsJsonArray("filter");
                        ArrayList<String> filter = new ArrayList<>();
                        for (JsonElement one : filterJsonArr) {
                            String oneFilter = one.getAsString();
                            filter.add(oneFilter);
                        }
                        if (filter.size() > 0) {
                            VideoParseRuler.addHostFilter(host, filter);
                        }
                    }
                }
                //广告过滤规则
                if (obj.has("hosts") && obj.has("regex")) {
                    ArrayList<String> rule = new ArrayList<>();
                    ArrayList<String> ads = new ArrayList<>();
                    JsonArray regexArray = obj.getAsJsonArray("regex");
                    for (JsonElement one : regexArray) {
                        String regex = one.getAsString();
                        if (M3u8.isAd(regex)) ads.add(regex);
                        else rule.add(regex);
                    }
                    JsonArray array = obj.getAsJsonArray("hosts");
                    for (JsonElement one : array) {
                        String host = one.getAsString();
                        VideoParseRuler.addHostRule(host, rule);
                        VideoParseRuler.addHostRegex(host, ads);
                    }
                }
                //嗅探脚本规则 如 click
                if (obj.has("hosts") && obj.has("script")) {
                    ArrayList<String> scripts = new ArrayList<>();
                    JsonArray scriptArray = obj.getAsJsonArray("script");
                    for (JsonElement one : scriptArray) {
                        String script = one.getAsString();
                        scripts.add(script);
                    }
                    JsonArray array = obj.getAsJsonArray("hosts");
                    for (JsonElement one : array) {
                        String host = one.getAsString();
                        VideoParseRuler.addHostScript(host, scripts);
                    }
                }
            }
        }

        if (infoJson.has("doh")) {
            String doh_json = infoJson.getAsJsonArray("doh").toString();
            if(!Hawk.get(HawkConfig.DOH_JSON, "").equals(doh_json)){
                Hawk.put(HawkConfig.DOH_URL, 0);
                Hawk.put(HawkConfig.DOH_JSON,doh_json);
            }
        }else {
            Hawk.put(HawkConfig.DOH_JSON,"");
        }
        OkGoHelper.setDnsList();
        LOG.i("echo-api-config-----------load");
        //追加的广告拦截
        if(infoJson.has("ads")){
            for (JsonElement host : infoJson.getAsJsonArray("ads")) {
                if(!AdBlocker.hasHost(host.getAsString())){
                    AdBlocker.addAdHost(host.getAsString());
                }
            }
        }
    }

    private void loadDefaultConfig() {
        String defaultIJKADS="{\"ijk\":[{\"options\":[{\"name\":\"opensles\",\"category\":4,\"value\":\"0\"},{\"name\":\"framedrop\",\"category\":4,\"value\":\"1\"},{\"name\":\"soundtouch\",\"category\":4,\"value\":\"1\"},{\"name\":\"start-on-prepared\",\"category\":4,\"value\":\"1\"},{\"name\":\"http-detect-rangeupport\",\"category\":1,\"value\":\"0\"},{\"name\":\"fflags\",\"category\":1,\"value\":\"fastseek\"},{\"name\":\"skip_loop_filter\",\"category\":2,\"value\":\"48\"},{\"name\":\"reconnect\",\"category\":4,\"value\":\"1\"},{\"name\":\"enable-accurate-seek\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec-all-videos\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec-auto-rotate\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec-handle-resolution-change\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec-hevc\",\"category\":4,\"value\":\"0\"},{\"name\":\"max-buffer-size\",\"category\":4,\"value\":\"15728640\"}],\"group\":\"软解码\"},{\"options\":[{\"name\":\"opensles\",\"category\":4,\"value\":\"0\"},{\"name\":\"framedrop\",\"category\":4,\"value\":\"1\"},{\"name\":\"soundtouch\",\"category\":4,\"value\":\"1\"},{\"name\":\"start-on-prepared\",\"category\":4,\"value\":\"1\"},{\"name\":\"http-detect-rangeupport\",\"category\":1,\"value\":\"0\"},{\"name\":\"fflags\",\"category\":1,\"value\":\"fastseek\"},{\"name\":\"skip_loop_filter\",\"category\":2,\"value\":\"48\"},{\"name\":\"reconnect\",\"category\":4,\"value\":\"1\"},{\"name\":\"enable-accurate-seek\",\"category\":4,\"value\":\"0\"},{\"name\":\"mediacodec\",\"category\":4,\"value\":\"1\"},{\"name\":\"mediacodec-all-videos\",\"category\":4,\"value\":\"1\"},{\"name\":\"mediacodec-auto-rotate\",\"category\":4,\"value\":\"1\"},{\"name\":\"mediacodec-handle-resolution-change\",\"category\":4,\"value\":\"1\"},{\"name\":\"mediacodec-hevc\",\"category\":4,\"value\":\"1\"},{\"name\":\"max-buffer-size\",\"category\":4,\"value\":\"15728640\"}],\"group\":\"硬解码\"}],\"ads\":[\"mimg.0c1q0l.cn\",\"www.googletagmanager.com\",\"www.google-analytics.com\",\"mc.usihnbcq.cn\",\"mg.g1mm3d.cn\",\"mscs.svaeuzh.cn\",\"cnzz.hhttm.top\",\"tp.vinuxhome.com\",\"cnzz.mmstat.com\",\"www.baihuillq.com\",\"s23.cnzz.com\",\"z3.cnzz.com\",\"c.cnzz.com\",\"stj.v1vo.top\",\"z12.cnzz.com\",\"img.mosflower.cn\",\"tips.gamevvip.com\",\"ehwe.yhdtns.com\",\"xdn.cqqc3.com\",\"www.jixunkyy.cn\",\"sp.chemacid.cn\",\"hm.baidu.com\",\"s9.cnzz.com\",\"z6.cnzz.com\",\"um.cavuc.com\",\"mav.mavuz.com\",\"wofwk.aoidf3.com\",\"z5.cnzz.com\",\"xc.hubeijieshikj.cn\",\"tj.tianwenhu.com\",\"xg.gars57.cn\",\"k.jinxiuzhilv.com\",\"cdn.bootcss.com\",\"ppl.xunzhuo123.com\",\"xomk.jiangjunmh.top\",\"img.xunzhuo123.com\",\"z1.cnzz.com\",\"s13.cnzz.com\",\"xg.huataisangao.cn\",\"z7.cnzz.com\",\"xg.huataisangao.cn\",\"z2.cnzz.com\",\"s96.cnzz.com\",\"q11.cnzz.com\",\"thy.dacedsfa.cn\",\"xg.whsbpw.cn\",\"s19.cnzz.com\",\"z8.cnzz.com\",\"s4.cnzz.com\",\"f5w.as12df.top\",\"ae01.alicdn.com\",\"www.92424.cn\",\"k.wudejia.com\",\"vivovip.mmszxc.top\",\"qiu.xixiqiu.com\",\"cdnjs.hnfenxun.com\",\"cms.qdwght.com\"]}";
        JsonObject defaultJson=gson.fromJson(defaultIJKADS, JsonObject.class);
        // 广告地址
        if(AdBlocker.isEmpty()){
            //默认广告拦截
            for (JsonElement host : defaultJson.getAsJsonArray("ads")) {
                AdBlocker.addAdHost(host.getAsString());
            }
        }
        // IJK解码配置
        if(ijkCodes==null){
            ijkCodes = new ArrayList<>();
            boolean foundOldSelect = false;
            String ijkCodec = Hawk.get(HawkConfig.IJK_CODEC, "硬解码");
            JsonArray ijkJsonArray = defaultJson.get("ijk").getAsJsonArray();
            for (JsonElement opt : ijkJsonArray) {
                JsonObject obj = (JsonObject) opt;
                String name = obj.get("group").getAsString();
                LinkedHashMap<String, String> baseOpt = new LinkedHashMap<>();
                for (JsonElement cfg : obj.get("options").getAsJsonArray()) {
                    JsonObject cObj = (JsonObject) cfg;
                    String key = cObj.get("category").getAsString() + "|" + cObj.get("name").getAsString();
                    String val = cObj.get("value").getAsString();
                    baseOpt.put(key, val);
                }
                IJKCode codec = new IJKCode();
                codec.setName(name);
                codec.setOption(baseOpt);
                if (name.equals(ijkCodec) || TextUtils.isEmpty(ijkCodec)) {
                    codec.selected(true);
                    ijkCodec = name;
                    foundOldSelect = true;
                } else {
                    codec.selected(false);
                }
                ijkCodes.add(codec);
            }
            if (!foundOldSelect && ijkCodes.size() > 0) {
                ijkCodes.get(0).selected(true);
            }
        }
        LOG.i("echo-default-config-----------load");
    }
    private void parseLiveJson(String apiUrl, File f) throws Throwable {
        BufferedReader bReader = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String s = "";
        while ((s = bReader.readLine()) != null) {
            sb.append(s + "\n");
        }
        bReader.close();
        parseLiveJson(apiUrl, sb.toString());
    }

    private String liveSpider="";
    private void parseLiveJson(String apiUrl, String jsonStr) {
        liveChannelGroupList.clear();
        JsonObject infoJson = gson.fromJson(jsonStr, JsonObject.class);
        // spider
        liveSpider = DefaultConfig.safeJsonString(infoJson, "spider", "");
        // 直播源
        initLiveSettings();
        if(infoJson.has("lives")){
            JsonArray lives_groups=infoJson.get("lives").getAsJsonArray();

            int live_group_index=getLiveGroupIndex();
            if(live_group_index>lives_groups.size()-1)live_group_index=0;
            Hawk.put(HawkConfig.LIVE_GROUP_LIST,lives_groups);
            //加载多源配置：只来自直播地址（LIVE_SOURCE_LIST）
            refreshLiveApiHistoryItems();

            JsonObject livesOBJ = lives_groups.get(live_group_index).getAsJsonObject();
            loadLiveApi(livesOBJ);
        }

        myHosts = new HashMap<>();
        if (infoJson.has("hosts")) {
            JsonArray hostsArray = infoJson.getAsJsonArray("hosts");
            for (int i = 0; i < hostsArray.size(); i++) {
                String entry = hostsArray.get(i).getAsString();
                String[] parts = entry.split("=", 2); // 只分割一次，防止 value 里有 =
                if (parts.length == 2) {
                    myHosts.put(parts[0], parts[1]);
                }
            }
        }
        LOG.i("echo-api-live-config-----------load");
    }

    private final List<LiveSettingGroup> liveSettingGroupList = new ArrayList<>();
    private void initLiveSettings() {
        ArrayList<String> groupNames = new ArrayList<>(Arrays.asList("线路选择", "画面比例", "播放解码", "超时换源", "偏好设置", "多源切换"));
        ArrayList<ArrayList<String>> itemsArrayList = new ArrayList<>();
        ArrayList<String> sourceItems = new ArrayList<>();
        ArrayList<String> scaleItems = new ArrayList<>(Arrays.asList("默认", "16:9", "4:3", "填充", "原始", "裁剪"));
        ArrayList<String> playerDecoderItems = new ArrayList<>(Arrays.asList("系统", "ijk硬解", "ijk软解", "exo"));
        ArrayList<String> timeoutItems = new ArrayList<>(Arrays.asList("5s", "10s", "15s", "20s", "25s", "30s"));
        ArrayList<String> personalSettingItems = new ArrayList<>(Arrays.asList("显示时间", "显示网速", "显分辨率", "换台反转", "跨选分类"));
        ArrayList<String> yumItems = new ArrayList<>();

        itemsArrayList.add(sourceItems);
        itemsArrayList.add(scaleItems);
        itemsArrayList.add(playerDecoderItems);
        itemsArrayList.add(timeoutItems);
        itemsArrayList.add(personalSettingItems);
        itemsArrayList.add(yumItems);

        liveSettingGroupList.clear();
        for (int i = 0; i < groupNames.size(); i++) {
            LiveSettingGroup liveSettingGroup = new LiveSettingGroup();
            ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
            liveSettingGroup.setGroupIndex(i);
            liveSettingGroup.setGroupName(groupNames.get(i));
            for (int j = 0; j < itemsArrayList.get(i).size(); j++) {
                LiveSettingItem liveSettingItem = new LiveSettingItem();
                liveSettingItem.setItemIndex(j);
                liveSettingItem.setItemName(itemsArrayList.get(i).get(j));
                liveSettingItemList.add(liveSettingItem);
            }
            liveSettingGroup.setLiveSettingItems(liveSettingItemList);
            liveSettingGroupList.add(liveSettingGroup);
        }
        refreshLiveApiHistoryItems();
    }

    public List<LiveSettingGroup> getLiveSettingGroupList() {
        return liveSettingGroupList;
    }

    public void refreshLiveApiHistoryItems() {
        if (liveSettingGroupList.size() < 6) return;
        ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
        int itemIdx = 0;

        // 只从直播地址列表（LIVE_SOURCE_LIST）加载
        ArrayList<String> liveSourceList = Hawk.get(HawkConfig.LIVE_SOURCE_LIST, new ArrayList<String>());
        for (String entry : liveSourceList) {
            String name;
            String url;
            if (entry.contains("\t")) {
                String[] parts = entry.split("\t", 2);
                name = parts[0];
                url = parts[1];
            } else {
                name = entry;
                url = entry;
            }
            LiveSettingItem item = new LiveSettingItem();
            item.setItemIndex(itemIdx++);
            item.setItemName(name);
            item.setItemUrl(url);
            item.setItemGroup(0); // group=0 表示来自直播地址
            liveSettingItemList.add(item);
        }

        liveSettingGroupList.get(5).setLiveSettingItems(liveSettingItemList);
    }

    /** 从配置entry字符串解析出名称（复用ConfigManagerActivity逻辑） */
    private String getEntryNameFromEntry(String entry) {
        if (entry == null || entry.isEmpty()) return "";
        try {
            org.json.JSONObject obj = new org.json.JSONObject(entry);
            if (obj.has("name")) return obj.getString("name");
        } catch (Throwable ignored) {}
        // 如果是url格式，取最后一段
        String url = entry;
        try {
            url = new org.json.JSONObject(entry).optString("url", entry);
        } catch (Throwable ignored) {}
        if (url.contains("/")) {
            String last = url.substring(url.lastIndexOf('/') + 1);
            if (!last.isEmpty()) return last;
        }
        return url;
    }

    /** 从配置entry字符串解析出线路列表（格式 name\turl 或 json {name,url,routes:[]} ） */
    private List<String[]> getRoutesFromEntry(String entry) {
        List<String[]> result = new ArrayList<>();
        if (entry == null || entry.isEmpty()) return result;
        try {
            org.json.JSONObject obj = new org.json.JSONObject(entry);
            String name = obj.optString("name", "");
            if (obj.has("routes")) {
                org.json.JSONArray routes = obj.getJSONArray("routes");
                for (int i = 0; i < routes.length(); i++) {
                    org.json.JSONObject r = routes.getJSONObject(i);
                    String rname = r.optString("name", "线路" + (i + 1));
                    String rurl = r.optString("url", "");
                    if (!rurl.isEmpty()) result.add(new String[]{rname, rurl});
                }
                if (!result.isEmpty()) return result;
            }
            String url = obj.optString("url", "");
            if (!url.isEmpty()) {
                result.add(new String[]{name.isEmpty() ? url : name, url});
                return result;
            }
        } catch (Throwable ignored) {}
        // plain url or name\turl
        if (entry.contains("\t")) {
            String[] parts = entry.split("\t", 2);
            result.add(new String[]{parts[0], parts[1]});
        } else {
            result.add(new String[]{getEntryNameFromEntry(entry), entry});
        }
        return result;
    }

    public void loadLives(JsonArray livesArray) {
        liveChannelGroupList.clear();
        int groupIndex = 0;
        int channelIndex = 0;
        int channelNum = 0;
        for (JsonElement groupElement : livesArray) {
            LiveChannelGroup liveChannelGroup = new LiveChannelGroup();
            liveChannelGroup.setLiveChannels(new ArrayList<LiveChannelItem>());
            liveChannelGroup.setGroupIndex(groupIndex++);
            String groupName = ((JsonObject) groupElement).get("group").getAsString().trim();
            String[] splitGroupName = groupName.split("_", 2);
            liveChannelGroup.setGroupName(splitGroupName[0]);
            if (splitGroupName.length > 1)
                liveChannelGroup.setGroupPassword(splitGroupName[1]);
            else
                liveChannelGroup.setGroupPassword("");
            channelIndex = 0;
            for (JsonElement channelElement : ((JsonObject) groupElement).get("channels").getAsJsonArray()) {
                JsonObject obj = (JsonObject) channelElement;
                LiveChannelItem liveChannelItem = new LiveChannelItem();
                liveChannelItem.setChannelName(obj.get("name").getAsString().trim());
                liveChannelItem.setChannelLogo(DefaultConfig.safeJsonString(obj, "logo", ""));
                liveChannelItem.setChannelEpg(DefaultConfig.safeJsonString(obj, "epg", ""));
                liveChannelItem.setChannelUa(DefaultConfig.safeJsonString(obj, "ua", ""));
                liveChannelItem.setChannelClick(DefaultConfig.safeJsonString(obj, "click", ""));
                liveChannelItem.setChannelFormat(DefaultConfig.safeJsonString(obj, "format", ""));
                liveChannelItem.setChannelOrigin(DefaultConfig.safeJsonString(obj, "origin", ""));
                liveChannelItem.setChannelReferer(DefaultConfig.safeJsonString(obj, "referer", ""));
                liveChannelItem.setChannelTvgId(DefaultConfig.safeJsonString(obj, "tvg-id", ""));
                liveChannelItem.setChannelTvgName(DefaultConfig.safeJsonString(obj, "tvg-name", ""));
                if (obj.has("parse")) {
                    try {
                        liveChannelItem.setChannelParse(obj.get("parse").getAsInt());
                    } catch (Throwable ignored) {
                    }
                }
                if (obj.has("catchup")) {
                    JsonObject catchupObj = new JsonObject();
                    if (obj.get("catchup").isJsonObject()) {
                        catchupObj = obj.getAsJsonObject("catchup");
                    } else {
                        catchupObj.addProperty("type", obj.get("catchup").getAsString());
                        if (obj.has("catchup-source")) catchupObj.addProperty("source", obj.get("catchup-source").getAsString());
                        if (obj.has("catchup-replace")) catchupObj.addProperty("replace", obj.get("catchup-replace").getAsString());
                    }
                    liveChannelItem.setChannelCatchup(catchupObj);
                }
                if (obj.has("header") && obj.get("header").isJsonObject()) {
                    JsonObject headerObj = obj.getAsJsonObject("header");
                    HashMap<String, String> channelHeader = new HashMap<>();
                    for (Map.Entry<String, JsonElement> entry : headerObj.entrySet()) {
                        channelHeader.put(entry.getKey(), entry.getValue().getAsString());
                    }
                    liveChannelItem.setChannelHeader(channelHeader);
                }
                ArrayList<String> urls = DefaultConfig.safeJsonStringList(obj, "urls");
                ArrayList<String> sourceNames = new ArrayList<>();
                ArrayList<String> sourceUrls = new ArrayList<>();
                int sourceIndex = 1;
                for (String url : urls) {
                    String[] splitText = url.split("\\$", 2);
                    sourceUrls.add(splitText[0]);
                    if (splitText.length > 1)
                        sourceNames.add(splitText[1]);
                    else
                        sourceNames.add("源" + Integer.toString(sourceIndex));
                    sourceIndex++;
                }
                liveChannelItem.setChannelSourceNames(sourceNames);
                liveChannelItem.setChannelUrls(sourceUrls);
                if (mergeLiveChannel(liveChannelGroup.getLiveChannels(), liveChannelItem)) {
                    liveChannelItem.setChannelIndex(channelIndex++);
                    liveChannelItem.setChannelNum(++channelNum);
                }
            }
            liveChannelGroupList.add(liveChannelGroup);
        }
    }

    private boolean mergeLiveChannel(ArrayList<LiveChannelItem> channelItems, LiveChannelItem newItem) {
        LiveChannelItem oldItem = findLiveChannel(channelItems, newItem.getChannelName());
        if (oldItem == null) {
            channelItems.add(newItem);
            return true;
        }
        mergeLiveChannelUrls(oldItem, newItem);
        return false;
    }

    private LiveChannelItem findLiveChannel(ArrayList<LiveChannelItem> channelItems, String channelName) {
        for (LiveChannelItem item : channelItems) {
            if (channelName != null && channelName.equals(item.getChannelName())) return item;
        }
        return null;
    }

    private void mergeLiveChannelUrls(LiveChannelItem oldItem, LiveChannelItem newItem) {
        ArrayList<String> oldUrls = oldItem.getChannelUrls();
        ArrayList<String> oldSourceNames = oldItem.getChannelSourceNames();
        if (oldUrls == null) {
            oldUrls = new ArrayList<>();
            oldItem.setChannelUrls(oldUrls);
        }
        if (oldSourceNames == null) {
            oldSourceNames = new ArrayList<>();
            oldItem.setChannelSourceNames(oldSourceNames);
        }
        while (oldSourceNames.size() < oldUrls.size()) {
            oldSourceNames.add("源" + Integer.toString(oldSourceNames.size() + 1));
        }
        ArrayList<String> newUrls = newItem.getChannelUrls();
        ArrayList<String> newSourceNames = newItem.getChannelSourceNames();
        if (newUrls == null) return;
        for (int i = 0; i < newUrls.size(); i++) {
            String url = newUrls.get(i);
            if (oldUrls.contains(url)) continue;
            oldUrls.add(url);
            if (newSourceNames != null && i < newSourceNames.size()) {
                oldSourceNames.add(newSourceNames.get(i));
            } else {
                oldSourceNames.add("源" + Integer.toString(oldSourceNames.size() + 1));
            }
        }
        oldItem.setChannelUrls(oldUrls);
        oldItem.setChannelSourceNames(oldSourceNames);
    }

    public void loadLiveApi(JsonObject livesOBJ) {
        try {
            LOG.i("echo-loadLiveApi");
            liveChannelGroupList.clear();
            currentLiveSpider = "";
            currentLivePyKey = "";
            String lives = livesOBJ.toString();
            int index = lives.indexOf("proxy://");
            String url;
            if (index != -1) {
                int endIndex = lives.lastIndexOf("\"");
                url = lives.substring(index, endIndex);
                url = DefaultConfig.checkReplaceProxy(url);
                String extUrl = Uri.parse(url).getQueryParameter("ext");
                if (extUrl != null && !extUrl.isEmpty()) {
                    String extUrlFix;
                    if(extUrl.startsWith("http") || extUrl.startsWith("clan://")){
                        extUrlFix = extUrl;
                    }else {
                        extUrlFix = new String(Base64.decode(extUrl, Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8");
                    }
                    extUrlFix = Base64.encodeToString(extUrlFix.getBytes("UTF-8"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
                    url = url.replace(extUrl, extUrlFix);
                }
            } else {
                String api = livesOBJ.has("api") ? livesOBJ.get("api").getAsString().trim() : "";
                String type = livesOBJ.has("type") ? livesOBJ.get("type").getAsString() : (isLiveSpiderApi(api) ? "3" : "0");
                if(type.equals("0") || type.equals("3")){
                    url = livesOBJ.has("url")?livesOBJ.get("url").getAsString():"";
                    if(url.isEmpty())url=api;
                    LOG.i("echo-liveurl"+url);
                    if(!url.startsWith("http://127.0.0.1")){
                        if(url.startsWith("http")){
                            url = Base64.encodeToString(url.getBytes("UTF-8"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
                        }
                        url ="http://127.0.0.1:9978/proxy?do=live&type=txt&ext="+url;
                    }
                    if(type.equals("3")){
                        String jarUrl = livesOBJ.has("jar")?livesOBJ.get("jar").getAsString().trim():"";
                        LOG.i("echo-liveApi1"+api);
                        if(api.contains(".py")){
                            LOG.i("echo-pyLoader.getSpider");
                            String ext="";
                            if(livesOBJ.has("ext") && (livesOBJ.get("ext").isJsonObject() || livesOBJ.get("ext").isJsonArray())){
                                ext=livesOBJ.get("ext").toString();
                            }else {
                                ext=DefaultConfig.safeJsonString(livesOBJ, "ext", "");
                            }

                            currentLivePyKey = MD5.string2MD5(api);
                            currentLiveSpider = api;
                            pyLoader.getSpider(currentLivePyKey,api,ext);
                        } else if (api.contains(".js")) {
                            LOG.i("echo-jsLoader.getSpider");
                            String ext="";
                            if(livesOBJ.has("ext") && (livesOBJ.get("ext").isJsonObject() || livesOBJ.get("ext").isJsonArray())){
                                ext=livesOBJ.get("ext").toString();
                            }else {
                                ext=DefaultConfig.safeJsonString(livesOBJ, "ext", "");
                            }
                            currentLiveSpider = api;
                            jsLoader.getSpider(MD5.string2MD5(api), api, ext, jarUrl);
                        }
                        if(!jarUrl.isEmpty() && !isLiveSpiderApi(api)){
                            jarLoader.loadLiveJar(jarUrl);
                            if (TextUtils.isEmpty(currentLiveSpider)) {
                                currentLiveSpider = jarUrl;
                            }
                        }else if(!liveSpider.isEmpty() && !isLiveSpiderApi(api)){
                            jarLoader.loadLiveJar(liveSpider);
                            if (TextUtils.isEmpty(currentLiveSpider)) {
                                currentLiveSpider = liveSpider;
                            }
                        }
                    }
                }else {
                    liveChannelGroupList.clear();
                    return;
                }
            }
            //设置epg
            if(livesOBJ.has("epg")){
                String epg =livesOBJ.get("epg").getAsString();
                Hawk.put(HawkConfig.EPG_URL,epg);
            }else {
                Hawk.put(HawkConfig.EPG_URL,"");
            }
            //直播播放器类型
            if(livesOBJ.has("playerType")){
                String livePlayType =livesOBJ.get("playerType").getAsString();
                Hawk.put(HawkConfig.LIVE_PLAY_TYPE,livePlayType);
            }else {
                Hawk.put(HawkConfig.LIVE_PLAY_TYPE,Hawk.get(HawkConfig.PLAY_TYPE, 0));
            }
            //设置UA
            if(livesOBJ.has("timeout")){
                int timeout = Math.max(5, Math.min(30, livesOBJ.get("timeout").getAsInt()));
                Hawk.put(HawkConfig.LIVE_CONNECT_TIMEOUT, (timeout + 4) / 5 - 1);
            }
            if(livesOBJ.has("header")) {
                JsonObject headerObj = livesOBJ.getAsJsonObject("header");
                HashMap<String, String> liveHeader = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : headerObj.entrySet()) {
                    liveHeader.put(entry.getKey(), entry.getValue().getAsString());
                }
                Hawk.put(HawkConfig.LIVE_WEB_HEADER, liveHeader);
            } else if(livesOBJ.has("ua")) {
                String ua = livesOBJ.get("ua").getAsString();
                HashMap<String,String> liveHeader = new HashMap<>();
                liveHeader.put("User-Agent", ua);
                Hawk.put(HawkConfig.LIVE_WEB_HEADER, liveHeader);
            }else {
                Hawk.put(HawkConfig.LIVE_WEB_HEADER,null);
            }
            LiveChannelGroup liveChannelGroup = new LiveChannelGroup();
            liveChannelGroup.setGroupName(url);
            liveChannelGroupList.clear();
            liveChannelGroupList.add(liveChannelGroup);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private String currentLiveSpider;
    public void setLiveJar(String liveJar)
    {
        if(liveJar.contains(".py")){
            currentLivePyKey = MD5.string2MD5(liveJar);
            pyLoader.getSpider(currentLivePyKey, liveJar, "");
            pyLoader.setRecentPyKey(currentLivePyKey);
        }else if(liveJar.contains(".js")){
            jsLoader.getSpider(MD5.string2MD5(liveJar), liveJar, "", "");
        }else {
            String jarUrl=!liveJar.isEmpty()?liveJar:liveSpider;
            jarLoader.setRecentJarKey(MD5.string2MD5(jarUrl));
        }
        currentLiveSpider=liveJar;
    }

    public String getSpider() {
        return spider;
    }

    public String getDanmaku() {
        return danmaku == null ? "" : danmaku;
    }

    public Spider getCSP(SourceBean sourceBean) {
        if (sourceBean.getApi().endsWith(".js") || sourceBean.getApi().contains(".js?")){
            currentPyKey = "";
            return jsLoader.getSpider(sourceBean.getKey(), sourceBean.getApi(), sourceBean.getExt(), sourceBean.getJar());
        }
        else if (sourceBean.getApi().contains(".py")) {
            currentPyKey = sourceBean.getKey();
            pyLoader.setRecentPyKey(currentPyKey);
            return pyLoader.getSpider(sourceBean.getKey(), sourceBean.getApi(), sourceBean.getExt());
        }
        else {
            currentPyKey = "";
            return jarLoader.getSpider(sourceBean.getKey(), sourceBean.getApi(), sourceBean.getExt(), sourceBean.getJar());
        }
    }

    public Spider getPyCSP(String url) {
        currentLivePyKey = MD5.string2MD5(url);
        currentLiveSpider = url;
        return pyLoader.getSpider(currentLivePyKey, url, "");
    }

    public Spider getJsCSP(String url) {
        currentLiveSpider = url;
        return jsLoader.getSpider(MD5.string2MD5(url), url, "", "");
    }

    public Spider getLiveCSP(String url) {
        return url.contains(".js") ? getJsCSP(url) : getPyCSP(url);
    }

    public void searchDanmuUi(String name, String episode, boolean longClick) {
        danmuSearchExecutor.execute(() -> {
            try {
                jarLoader.searchDanmuUi(name, episode, longClick);
            } catch (Throwable th) {
                LOG.e("ApiConfig searchDanmuUi error: " + th.getMessage());
                th.printStackTrace();
            }
        });
    }

    public boolean hasDanmuSearchUi() {
        return jarLoader.hasDanmuSearchUi();
    }

    public int getLiveConnectTimeoutSeconds() {
        return (Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 1) + 1) * 5;
    }

    private boolean isLiveSpiderApi(String api) {
        return api.contains(".py") || api.contains(".js");
    }

    public Object[] proxyLocal(Map<String, String> param) {
        SourceBean source = getCurrentProxySource(param);
        String api = source.getApi();

        String siteKey = param.get("siteKey");
        String action = param.get("do");

        boolean isJs = "js".equals(action);
        boolean isPy = "py".equals(action);
        boolean isLive = Hawk.get(HawkConfig.PLAYER_IS_LIVE, false);
        boolean isApiJs = api.contains(".js");
        boolean isApiPy = api.contains(".py");

        boolean canUseType3 = !TextUtils.isEmpty(siteKey)
                && source.getType() == 3
                && !isJs
                && !isPy
                && !isLive
                && !isApiJs
                && !isApiPy;

        if (canUseType3) {
            try {
                Spider spider = getCSP(source);

                Object[] result = spider.proxy(param);
                if (result != null) return result;

                result = jarLoader.proxyInvoke(param);
                if (result != null) return result;

                result = proxyDirect(param);
                if (result != null) return result;

                return null;
            } catch (Throwable th) {
                LOG.e("echo-proxy siteKey error: " + th.getMessage());
                return null;
            }
        }

        if (isJs) {
            return jsLoader.proxyInvoke(param);
        }

        if (isLive) {
            String liveApi = currentLiveSpider != null ? currentLiveSpider : "";

            if (liveApi.contains(".py")) {
                return pyLoader.proxyInvoke(param, currentLivePyKey);
            }
            if (liveApi.contains(".js")) {
                return jsLoader.proxyInvoke(param);
            }
            return jarLoader.proxyInvoke(param);
        }

        if (isPy) {
            return pyLoader.proxyInvoke(param, getCurrentPyKey());
        }

        if (isApiPy) {
            return pyLoader.proxyInvoke(param, getCurrentPyKey());
        }

        return jarLoader.proxyInvoke(param);
    }

    private Object[] proxyDirect(Map<String, String> param) {
        try {
            String url = param.get("url");
            if (TextUtils.isEmpty(url)) return null;
            url = URLDecoder.decode(url, "UTF-8");
            if (!url.startsWith("http://") && !url.startsWith("https://")) return null;
            if (!DefaultConfig.isVideoFormat(url)) return null;
            if (url.contains(".m3u8")) {
                param.put("url", url);
                param.put("go", "live");
                param.put("type", "m3u8");
                return Proxy.itv(param);
            }
            return null;
        } catch (Throwable th) {
            LOG.e("echo-proxy direct fallback error: " + th.getMessage());
            return null;
        }
    }

    private SourceBean getCurrentProxySource(Map<String, String> param) {
        String siteKey = param.get("siteKey");
        if (TextUtils.isEmpty(siteKey)) {
            siteKey = currentPlaySourceKey;
            if (!TextUtils.isEmpty(siteKey)) param.put("siteKey", siteKey);
        }
        SourceBean sourceBean = TextUtils.isEmpty(siteKey) ? null : getSource(siteKey);
        return sourceBean == null ? ApiConfig.get().getHomeSourceBean() : sourceBean;
    }

    public void setCurrentPlaySourceKey(String sourceKey) {
        currentPlaySourceKey = sourceKey == null ? "" : sourceKey;
    }

    private String getCurrentPyKey() {
        SourceBean sourceBean = getCurrentProxySource(new HashMap<String, String>());
        if (sourceBean.getApi().contains(".py")) {
            if (!sourceBean.getKey().equals(currentPyKey)) {
                currentPyKey = sourceBean.getKey();
                pyLoader.getSpider(currentPyKey, sourceBean.getApi(), sourceBean.getExt());
                pyLoader.setRecentPyKey(currentPyKey);
            }
            return currentPyKey;
        }
        return currentPyKey;
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) {
        return jarLoader.jsonExt(key, jxs, url);
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) {
        return jarLoader.jsonExtMix(flag, key, name, jxs, url);
    }

    public interface LoadConfigCallback {
        void success();

        void error(String msg);
        void notice(String msg);
    }

    public interface FastParseCallback {
        void success(boolean parse, String url, Map<String, String> header);

        void fail(int code, String msg);
    }

    public SourceBean getSource(String key) {
        if (!sourceBeanList.containsKey(key))
            return null;
        return sourceBeanList.get(key);
    }

    public void setSourceBean(SourceBean sourceBean) {
        this.mHomeSource = sourceBean;
        Hawk.put(HawkConfig.HOME_API, sourceBean.getKey());
    }

    public void setDefaultParse(ParseBean parseBean) {
        if (this.mDefaultParse != null)
            this.mDefaultParse.setDefault(false);
        this.mDefaultParse = parseBean;
        Hawk.put(HawkConfig.DEFAULT_PARSE, parseBean.getName());
        parseBean.setDefault(true);
    }

    public ParseBean getDefaultParse() {
        return mDefaultParse;
    }

    public List<SourceBean> getSourceBeanList() {
        return new ArrayList<>(sourceBeanList.values());
    }
    public List<SourceBean> getSwitchSourceBeanList() {
        List<SourceBean> filteredList = new ArrayList<>();
        for (SourceBean bean : sourceBeanList.values()) {
            filteredList.add(bean);
        }
        return filteredList;
    }

    private List<SourceBean> searchSourceBeanList;
    public List<SourceBean> getSearchSourceBeanList() {
        if(searchSourceBeanList.isEmpty()){
            LOG.i("echo-第一次getSearchSourceBeanList");
            searchSourceBeanList = new ArrayList<>();
            for (SourceBean bean : sourceBeanList.values()) {
                if (bean.isSearchable()) {
                    searchSourceBeanList.add(bean);
                }
            }
        }
        return searchSourceBeanList;
    }

    public List<ParseBean> getParseBeanList() {
        return parseBeanList;
    }

    public List<String> getVipParseFlags() {
        return vipParseFlags;
    }

    public SourceBean getHomeSourceBean() {
        return mHomeSource == null ? emptyHome : mHomeSource;
    }

    public List<LiveChannelGroup> getChannelGroupList() {
        return liveChannelGroupList;
    }

    public List<IJKCode> getIjkCodes() {
        return ijkCodes;
    }

    public IJKCode getCurrentIJKCode() {
        String codeName = Hawk.get(HawkConfig.IJK_CODEC, "硬解码");
        return getIJKCodec(codeName);
    }

    public IJKCode getIJKCodec(String name) {
        for (IJKCode code : ijkCodes) {
            if (code.getName().equals(name))
                return code;
        }
        return ijkCodes.get(0);
    }

    String clanToAddress(String lanLink) {
        if (lanLink.startsWith("clan://localhost/")) {
            return lanLink.replace("clan://localhost/", ControlManager.get().getAddress(true) + "file/");
        } else {
            String link = lanLink.substring(7);
            int end = link.indexOf('/');
            return "http://" + link.substring(0, end) + "/file/" + link.substring(end + 1);
        }
    }

    String clanContentFix(String lanLink, String content) {
        String fix = lanLink.substring(0, lanLink.indexOf("/file/") + 6);
        return content.replace("clan://localhost/", fix).replace("file://", fix);
    }

    String fixContentPath(String url, String content) {
        if (content.contains("\"./") || content.contains("\"../")) {
            url=url.replace("file://","clan://localhost/");
            if(!url.startsWith("http") && !url.startsWith("clan://")){
                url = "http://" + url;
            }
            if(url.startsWith("clan://"))url=clanToAddress(url);
            String base = url.substring(0,url.lastIndexOf("/") + 1);
            String parent = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
            int parentEnd = parent.lastIndexOf("/");
            if (parentEnd >= 0) parent = parent.substring(0, parentEnd + 1);
            content = content.replace("../", parent);
            content = content.replace("./", base);
        }
        return content;
    }

    public Map<String,String> getMyHost() {
        return myHosts;
    }

    private void loadProxyRules(JsonObject infoJson) {
        if (!infoJson.has("proxy")) {
            OkGoHelper.setProxyList(null);
            return;
        }
        try {
            OkGoHelper.setProxyList(ProxyRule.arrayFrom(infoJson.get("proxy")));
        } catch (Throwable th) {
            th.printStackTrace();
            OkGoHelper.setProxyList(null);
        }
    }

    public void clearJarLoader()
    {
        jarLoader.clear();
    }

    private void addSuperParse()
    {
        ParseBean superPb = new ParseBean();
        superPb.setName("超级解析");
        superPb.setUrl("SuperParse");
        superPb.setExt("");
        superPb.setType(4);
        parseBeanList.add(0, superPb);
    }

    public void clearLoader(){
        jarLoader.clear();
        pyLoader.clear();
        jsLoader.clear();
    }

    public void clearSpiderCache() {
        currentPyKey = "";
        currentLivePyKey = "";
        currentLiveSpider = "";
        clearLoader();
    }
}
