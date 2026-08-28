package com.mobile.novabox.download;

import android.net.Uri;
import android.provider.MediaStore;

import com.mobile.novabox.base.App;
import com.mobile.novabox.cache.DownloadDao;
import com.mobile.novabox.cache.DownloadEntity;
import com.mobile.novabox.data.AppDataManager;
import com.mobile.novabox.event.DownloadEvent;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 下载管理器(单例)。
 * <p>
 * 核心能力:
 * 1. 支持直接文件(mp4/mkv/ts...)流式下载到 /sdcard/Download/novabox
 * 2. 支持 m3u8(HLS):解析 playlist → 下载全部分片 → 二进制拼接为单一 .ts 文件,
 *    不转码(源本身就是 TS 流,分片直接拼接是合法的),保证离线可播。
 * 3. 加密 m3u8(带 EXT-X-KEY)不支持,直接报错提示。
 * 4. 无断点续传:下载中取消/失败则丢弃临时文件,重下。
 * 5. 并发上限 3,其余排队;状态/进度写入 Room + EventBus 广播。
 */
public class DownloadManager {

    private static final int MAX_CONCURRENT = 3;
    private static final int READ_TIMEOUT_MS = 60 * 1000;
    private static final int CONNECT_TIMEOUT_MS = 15 * 1000;

    private static volatile DownloadManager instance;

    private final ExecutorService pool = Executors.newFixedThreadPool(MAX_CONCURRENT);
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(READ_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .build();
    private final Map<Integer, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();
    private final Map<Integer, String> refererMap = new ConcurrentHashMap<>();
    /** 每个任务:上次进度上报的时间戳和字节数(用于算速度) */
    private final Map<Integer, long[]> speedTrack = new ConcurrentHashMap<>(); // [lastTimeMs, lastBytes]

    public static DownloadManager get() {
        if (instance == null) {
            synchronized (DownloadManager.class) {
                if (instance == null) instance = new DownloadManager();
            }
        }
        return instance;
    }

    private DownloadDao dao() {
        return AppDataManager.get().getDownloadDao();
    }

    // ─── 对外 API ───

    /**
     * 加入下载队列。
     *
     * @param sourceKey   站点 key
     * @param vodName     影片名
     * @param episodeName 集数名
     * @param playFlag    线路名
     * @param playUrl     原始播放地址
     * @param downloadUrl 解析后的直链(已通过 playerContent 拿到)
     * @param referer     可选的 Referer 头
     */
    public int addDownload(String sourceKey, String vodName, String episodeName,
                           String playFlag, String playUrl, String downloadUrl, String referer) {
        DownloadEntity e = new DownloadEntity();
        e.sourceKey = sourceKey;
        e.vodName = vodName;
        e.episodeName = episodeName;
        e.playFlag = playFlag;
        e.playUrl = playUrl;
        e.downloadUrl = downloadUrl;
        e.totalSize = -1;
        e.downloadedSize = 0;
        e.progress = 0;
        e.status = DownloadEntity.STATUS_QUEUED;
        e.createTime = System.currentTimeMillis();
        long id = dao().insert(e);
        e.setId((int) id);
        if (referer != null && !referer.isEmpty()) {
            refererMap.put((int) id, referer);
        }
        dispatchQueue();
        EventBus.getDefault().post(new DownloadEvent(DownloadEvent.TYPE_STATUS, (int) id, e.status, 0));
        return (int) id;
    }

    /** 取消下载(仅对排队/下载中有效) */
    public void cancel(int id) {
        DownloadEntity e = dao().getById(id);
        if (e == null) return;
        if (e.status == DownloadEntity.STATUS_DONE) return;
        cancelFlags.put(id, new AtomicBoolean(true));
        if (e.status == DownloadEntity.STATUS_QUEUED) {
            // 还在排队:直接标记取消
            e.status = DownloadEntity.STATUS_CANCELED;
            dao().updateStatus(id, DownloadEntity.STATUS_CANCELED, "已取消");
            EventBus.getDefault().post(new DownloadEvent(DownloadEvent.TYPE_STATUS, id, e.status, e.progress));
        }
        // 下载中:由 worker 线程轮询 cancelFlag 后自己结束
    }

    /** 删除记录 + 本地文件(默认行为,保留兼容旧调用) */
    public void delete(int id) {
        delete(id, true);
    }

    /**
     * 删除下载记录。
     * @param id 任务 id
     * @param deleteFile 是否同时删除本地文件;false 时仅移除下载记录,保留已下载好的文件
     */
    public void delete(int id, boolean deleteFile) {
        DownloadEntity e = dao().getById(id);
        cancel(id);
        if (deleteFile && e != null && e.localPath != null) {
            File f = new File(e.localPath);
            if (f.exists()) f.delete();
        }
        dao().deleteById(id);
        EventBus.getDefault().post(new DownloadEvent(DownloadEvent.TYPE_STATUS, id, DownloadEntity.STATUS_CANCELED, 0));
    }

    /** 重新排队(失败/取消的任务) */
    public void retry(int id) {
        DownloadEntity e = dao().getById(id);
        if (e == null) return;
        e.status = DownloadEntity.STATUS_QUEUED;
        e.progress = 0;
        e.downloadedSize = 0;
        e.errorMsg = null;
        dao().updateStatus(id, DownloadEntity.STATUS_QUEUED, null);
        dao().updateProgress(id, 0, 0, -1);
        dispatchQueue();
        EventBus.getDefault().post(new DownloadEvent(DownloadEvent.TYPE_STATUS, id, e.status, 0));
    }

    /** 是否正在下载(用于批量入队时判断) */
    public boolean isRunning() {
        List<DownloadEntity> list = dao().getVisible();
        for (DownloadEntity e : list) {
            if (e.status == DownloadEntity.STATUS_QUEUED || e.status == DownloadEntity.STATUS_DOWNLOADING) {
                return true;
            }
        }
        return false;
    }

    /** 活跃任务数 */
    public int activeCount() {
        List<DownloadEntity> list = dao().getVisible();
        int count = 0;
        for (DownloadEntity e : list) {
            if (e.status == DownloadEntity.STATUS_DOWNLOADING) count++;
        }
        return count;
    }

    // ─── 调度 ───

    private void dispatchQueue() {
        List<DownloadEntity> list = dao().getVisible();
        int running = 0;
        List<DownloadEntity> queued = new ArrayList<>();
        for (DownloadEntity e : list) {
            if (e.status == DownloadEntity.STATUS_DOWNLOADING) running++;
            else if (e.status == DownloadEntity.STATUS_QUEUED) queued.add(e);
        }
        while (running < MAX_CONCURRENT && !queued.isEmpty()) {
            DownloadEntity next = queued.remove(0);
            if (cancelFlags.containsKey(next.getId()) && cancelFlags.get(next.getId()).get()) {
                continue;
            }
            startWorker(next);
            running++;
        }
    }

    private void startWorker(final DownloadEntity entity) {
        final int id = entity.getId();
        entity.status = DownloadEntity.STATUS_DOWNLOADING;
        dao().updateStatusOnly(id, DownloadEntity.STATUS_DOWNLOADING);
        EventBus.getDefault().post(new DownloadEvent(DownloadEvent.TYPE_STATUS, id, entity.status, entity.progress));
        pool.execute(() -> runTask(id));
    }

    private void runTask(int id) {
        DownloadEntity e = dao().getById(id);
        if (e == null) return;
        AtomicBoolean cancel = cancelFlags.get(id);
        boolean success = false;
        String error = null;
        try {
            if (isCanceled(id)) {
                dao().updateStatus(id, DownloadEntity.STATUS_CANCELED, "已取消");
                return;
            }
            String url = e.downloadUrl;
            if (url == null || url.isEmpty()) throw new IllegalStateException("解析后直链为空");
            // 关键:部分源的分片/直链 URL 指向 127.0.0.1:9978 本地代理(RemoteServer)。
            // 代理是懒启动的(仅播放链路触发 getAddress 才 startServer),且 proxyLocal 定位源
            // 依赖 currentPlaySourceKey(只在播放页设置)。
            // 下载前必须:1) 确保代理已启动 2) 设置正确的源,否则分片请求连不上/转发错源。
            ensureProxyReady(e.sourceKey);
            String referer = refererMap.get(id);
            if (isM3u8Url(url)) {
                success = downloadM3u8(e, cancel, referer);
            } else {
                success = downloadDirect(e, cancel, referer);
            }
        } catch (Throwable th) {
            // 用户主动取消:标记 CANCELED 而不是 FAILED
            if (th instanceof CanceledException || isCanceled(id)) {
                e.status = DownloadEntity.STATUS_CANCELED;
                dao().updateStatus(id, DownloadEntity.STATUS_CANCELED, "已取消");
                EventBus.getDefault().post(new DownloadEvent(DownloadEvent.TYPE_STATUS, id, DownloadEntity.STATUS_CANCELED, e.progress));
                cancelFlags.remove(id);
                refererMap.remove(id);
                dispatchQueue();
                return;
            }
            error = th.getMessage() == null ? th.getClass().getSimpleName() : th.getMessage();
            success = false;
        } finally {
            cancelFlags.remove(id);
            refererMap.remove(id);
            speedTrack.remove(id);
            if (success) {
                e.status = DownloadEntity.STATUS_DONE;
                e.finishTime = System.currentTimeMillis();
                e.progress = 100;
                dao().markDone(id, e.localPath, e.totalSize, e.finishTime);
                EventBus.getDefault().post(new DownloadEvent(DownloadEvent.TYPE_STATUS, id, DownloadEntity.STATUS_DONE, 100));
            } else if (e.status != DownloadEntity.STATUS_CANCELED) {
                e.status = DownloadEntity.STATUS_FAILED;
                dao().markFailed(id, error == null ? "下载失败" : error);
                EventBus.getDefault().post(new DownloadEvent(DownloadEvent.TYPE_STATUS, id, DownloadEntity.STATUS_FAILED, e.progress));
            }
            dispatchQueue();
        }
    }

    private boolean isCanceled(int id) {
        AtomicBoolean flag = cancelFlags.get(id);
        return flag != null && flag.get();
    }

    /**
     * 确保本地代理(RemoteServer, 127.0.0.1:9978)可用且源正确:
     * 1. 调用 getAddress(true) 触发懒启动(播放链路才会启动,下载路径必须主动拉起来)
     * 2. 设置 currentPlaySourceKey,proxyLocal 定位源时优先用它,
     *    否则回退到首页源,分片转发会失败
     */
    private void ensureProxyReady(String sourceKey) {
        try {
            com.mobile.novabox.server.ControlManager.get().getAddress(true);
            if (sourceKey != null && !sourceKey.isEmpty()) {
                com.mobile.novabox.api.ApiConfig.get().setCurrentPlaySourceKey(sourceKey);
            }
        } catch (Throwable th) {
            // 代理拉不起来不影响后续尝试,分片请求会失败并标 FAILED
        }
    }

    // ─── 直接文件下载 ───

    private boolean downloadDirect(DownloadEntity e, AtomicBoolean cancel, String referer) throws Exception {
        String url = e.downloadUrl;
        Request.Builder rb = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        if (referer != null) rb.header("Referer", referer);
        Response resp = client.newCall(rb.build()).execute();
        if (!resp.isSuccessful()) {
            throw new IllegalStateException("HTTP " + resp.code());
        }
        ResponseBody body = resp.body();

        // 关键:部分源是 .mp4/.mkv 结尾的 URL,但实际返回 m3u8 播放列表文本
        // (服务器 Content-Type 也常是 application/vnd.apple.mpegurl)。
        // 只看 URL 判定会把这 137KB 的 playlist 文本当视频存下来 → 离线播放不了。
        // 这里 peek 响应体前 7 字节,若是 #EXTM3U 则改走 m3u8 下载流程。
        if (body != null && isM3u8Content(body, resp.header("Content-Type"))) {
            resp.close();
            return downloadM3u8(e, cancel, referer);
        }

        long total = body != null ? body.contentLength() : -1;
        e.totalSize = total;
        e.downloadedSize = 0;
        String fileName = DownloadStorage.inferFileName(e.vodName, e.episodeName, url, false);
        File tmp = DownloadStorage.buildTempFile(fileName);
        File target = DownloadStorage.buildTargetFile(fileName);
        e.localPath = target.getAbsolutePath();

        // Android 11+ 公共目录需要 MediaStore 写
        if (DownloadStorage.useMediaStore()) {
            writeViaMediaStore(e, tmp, target, body, total, cancel, fileName);
        } else {
            writeViaFile(e, tmp, target, body, total, cancel);
        }
        return true;
    }

    /**
     * 判断响应体是否实际是 m3u8 播放列表:
     * 1. Content-Type 含 mpegurl(权威)
     * 2. 或 peek 前 7 字节 == "#EXTM3U"(兜底,不消费流)
     */
    private boolean isM3u8Content(ResponseBody body, String contentType) {
        if (contentType != null) {
            String ct = contentType.toLowerCase();
            if (ct.contains("mpegurl") || ct.contains("m3u8") || ct.contains("apple")) {
                return true;
            }
        }
        try {
            okio.BufferedSource source = body.source();
            if (!source.request(7)) return false;
            okio.Buffer peek = new okio.Buffer();
            source.peek().read(peek, 7);
            return "#EXTM3U".equals(peek.readUtf8());
        } catch (Throwable th) {
            return false;
        }
    }

    private void writeViaFile(DownloadEntity e, File tmp, File target, ResponseBody body, long total, AtomicBoolean cancel) throws Exception {
        try (InputStream in = body.byteStream();
             OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            long done = 0;
            while ((n = in.read(buf)) > 0) {
                if (isCanceled(e.getId())) {
                    body.close();
                    throw new CanceledException();
                }
                out.write(buf, 0, n);
                done += n;
                reportProgress(e, total, done);
            }
        }
        moveToTarget(tmp, target);
        e.downloadedSize = target.length();
        e.totalSize = target.length();
    }

    /** Android 11+ 通过 MediaStore.Downloads 写入公共下载目录 */
    private void writeViaMediaStore(DownloadEntity e, File tmp, File target, ResponseBody body, long total, AtomicBoolean cancel, String fileName) throws Exception {
        // 先下载到 app 私有缓存(只写 tmp,不做 move),再插入 MediaStore 复制过去
        try (InputStream in = body.byteStream();
             OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            long done = 0;
            while ((n = in.read(buf)) > 0) {
                if (isCanceled(e.getId())) {
                    body.close();
                    tmp.delete();
                    throw new CanceledException();
                }
                out.write(buf, 0, n);
                done += n;
                reportProgress(e, total, done);
            }
        }
        // 插入 MediaStore
        android.content.ContentValues values = new android.content.ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(fileName));
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, DownloadStorage.getRelativePath());
        Uri uri = App.getInstance().getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("MediaStore 插入失败");
        try (InputStream in = new java.io.FileInputStream(tmp);
             OutputStream out = App.getInstance().getContentResolver().openOutputStream(uri)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0 && out != null) out.write(buf, 0, n);
        }
        tmp.delete();
        String data = DownloadStorage.queryDataColumn(App.getInstance(), uri);
        e.localPath = data != null ? data : uri.toString();
        e.downloadedSize = target.length();
        e.totalSize = target.length();
    }

    // ─── m3u8 下载 ───

    private boolean isM3u8Url(String url) {
        String lower = url.toLowerCase();
        return lower.contains(".m3u8") || lower.contains("playlist") || lower.contains("/m3u8/");
    }

    private boolean downloadM3u8(DownloadEntity e, AtomicBoolean cancel, String referer) throws Exception {
        String url = e.downloadUrl;
        String playlistText = fetchText(url, referer);
        List<String> segments = parseSegments(playlistText, url);
        if (segments.isEmpty()) throw new IllegalStateException("m3u8 解析失败(可能为加密源或空 playlist)");

        String fileName = DownloadStorage.inferFileName(e.vodName, e.episodeName, url, true);
        File tmp = DownloadStorage.buildTempFile(fileName);
        File target = DownloadStorage.buildTargetFile(fileName);
        e.localPath = target.getAbsolutePath();
        e.totalSize = -1;

        long totalSegments = segments.size();
        long doneSegments = 0;
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            for (String seg : segments) {
                if (isCanceled(e.getId())) {
                    out.close();
                    throw new CanceledException();
                }
                downloadSegmentTo(seg, referer, out);
                doneSegments++;
                int pct = (int) (doneSegments * 100 / totalSegments);
                e.progress = Math.min(pct, 99);
                // 必须写 Room:下载管理页从数据库读进度
                dao().updateProgress(e.getId(), e.progress, tmp.length(), -1);
                // 进度事件带速度(m3u8 按分片间隔估算,直接复用 reportProgress 的速度算法)
                long now = System.currentTimeMillis();
                long speed = 0;
                long[] last = speedTrack.get(e.getId());
                if (last != null && now > last[0]) {
                    long dt = now - last[0];
                    if (dt > 0) speed = (tmp.length() - last[1]) * 1000L / dt;
                }
                speedTrack.put(e.getId(), new long[]{now, tmp.length()});
                EventBus.getDefault().post(new DownloadEvent(DownloadEvent.TYPE_PROGRESS, e.getId(), e.status,
                        e.progress, -1, tmp.length(), speed));
                if (e.status == DownloadEntity.STATUS_DONE) break;
            }
        }
        if (isCanceled(e.getId())) {
            tmp.delete();
            throw new CanceledException();
        }
        if (DownloadStorage.useMediaStore()) {
            moveToMediaStore(e, tmp, target, fileName);
        } else {
            moveToTarget(tmp, target);
        }
        e.progress = 100;
        e.downloadedSize = target.length();
        e.totalSize = target.length();
        return true;
    }

    private void downloadSegmentTo(String segUrl, String referer, OutputStream out) throws Exception {
        Request.Builder rb = new Request.Builder().url(segUrl).header("User-Agent", "Mozilla/5.0");
        if (referer != null) rb.header("Referer", referer);
        Response resp = client.newCall(rb.build()).execute();
        if (!resp.isSuccessful()) throw new IllegalStateException("分片 HTTP " + resp.code());
        try (InputStream in = resp.body().byteStream()) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    // ─── m3u8 解析 ───

    /**
     * 解析 m3u8 playlist:
     * - 支持主 playlist(EXT-X-STREAM-INF)选第一条子流
     * - 支持普通 playlist 直接取分片
     * - 检测 EXT-X-KEY(加密)直接报错
     */
    List<String> parseSegments(String playlistText, String baseUrl) throws Exception {
        List<String> segments = new ArrayList<>();
        if (playlistText == null || playlistText.isEmpty()) return segments;
        String[] lines = playlistText.split("\\r?\\n");
        boolean isMaster = false;
        List<String> variantUris = new ArrayList<>();
        String keyUri = null;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("#EXT-X-KEY")) {
                keyUri = trimmed;
            } else if (trimmed.startsWith("#EXT-X-STREAM-INF")) {
                isMaster = true;
            } else if (!trimmed.startsWith("#") && isMaster) {
                variantUris.add(trimmed);
                isMaster = false;
            } else if (!trimmed.startsWith("#")) {
                segments.add(trimmed);
            }
        }
        if (!variantUris.isEmpty()) {
            // 主 playlist:取第一个变体(通常最高码率排最前或靠后,这里取第一个)
            String subPlaylist = resolveUrl(baseUrl, variantUris.get(0));
            return parseSegments(fetchText(subPlaylist, null), subPlaylist);
        }
        if (keyUri != null && !keyUri.contains("METHOD=NONE")) {
            throw new RuntimeException("该源为加密 HLS(EXT-X-KEY),暂不支持下载");
        }
        // 相对路径解析
        List<String> resolved = new ArrayList<>();
        for (String seg : segments) {
            resolved.add(resolveUrl(baseUrl, seg));
        }
        return resolved;
    }

    /** 相对 URL 解析 */
    static String resolveUrl(String base, String ref) {
        if (ref.startsWith("http://") || ref.startsWith("https://")) return ref;
        try {
            java.net.URI baseUri = new java.net.URI(base);
            return baseUri.resolve(ref).toString();
        } catch (Exception e) {
            int idx = base.lastIndexOf('/');
            if (idx > 0) return base.substring(0, idx + 1) + ref;
            return ref;
        }
    }

    private String fetchText(String url, String referer) throws Exception {
        Request.Builder rb = new Request.Builder().url(url).header("User-Agent", "Mozilla/5.0");
        if (referer != null) rb.header("Referer", referer);
        Response resp = client.newCall(rb.build()).execute();
        if (!resp.isSuccessful()) throw new IllegalStateException("HTTP " + resp.code());
        String text = resp.body() != null ? resp.body().string() : "";
        resp.close();
        return text;
    }

    // ─── 工具 ───

    private void reportProgress(DownloadEntity e, long total, long done) {
        e.downloadedSize = done;
        e.totalSize = total;
        int pct;
        if (total > 0) {
            pct = (int) (done * 100 / total);
        } else {
            // 服务器没返回 Content-Length:无法算百分比,进度条交给 UI 用不确定模式
            pct = 0;
        }
        e.progress = Math.min(pct, 99);
        // 速度:按两次上报间隔的字节增量计算
        long now = System.currentTimeMillis();
        long speed = 0;
        long[] last = speedTrack.get(e.getId());
        if (last != null && now > last[0]) {
            long dt = now - last[0];
            if (dt > 0) speed = (done - last[1]) * 1000L / dt;
        }
        speedTrack.put(e.getId(), new long[]{now, done});
        // 必须写 Room:下载管理页从数据库读进度,只发事件不落库 UI 永远是 0
        dao().updateProgress(e.getId(), e.progress, e.downloadedSize, e.totalSize);
        EventBus.getDefault().post(new DownloadEvent(DownloadEvent.TYPE_PROGRESS, e.getId(), e.status,
                e.progress, e.totalSize, e.downloadedSize, speed));
    }

    private void moveToTarget(File tmp, File target) {
        if (target.exists()) target.delete();
        if (!tmp.renameTo(target)) {
            // rename 失败(跨目录)则复制
            try {
                java.nio.file.Files.copy(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                tmp.delete();
            } catch (Exception ex) {
                throw new RuntimeException("保存文件失败");
            }
        }
    }

    private void moveToMediaStore(DownloadEntity e, File tmp, File target, String fileName) throws Exception {
        android.content.ContentValues values = new android.content.ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(fileName));
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, DownloadStorage.getRelativePath());
        Uri uri = App.getInstance().getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("MediaStore 插入失败");
        try (InputStream in = new java.io.FileInputStream(tmp);
             OutputStream out = App.getInstance().getContentResolver().openOutputStream(uri)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0 && out != null) out.write(buf, 0, n);
        }
        tmp.delete();
        String data = DownloadStorage.queryDataColumn(App.getInstance(), uri);
        e.localPath = data != null ? data : uri.toString();
    }

    private String mimeFor(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".mkv")) return "video/x-matroska";
        if (lower.endsWith(".ts")) return "video/mp2t";
        if (lower.endsWith(".flv")) return "video/x-flv";
        if (lower.endsWith(".avi")) return "video/x-msvideo";
        if (lower.endsWith(".webm")) return "video/webm";
        return "video/mp4";
    }

    static class CanceledException extends RuntimeException {
    }
}
