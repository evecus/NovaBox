package com.mobile.novabox.util;

import com.google.gson.Gson;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.mobile.novabox.bean.OpenListFsGetData;
import com.mobile.novabox.bean.OpenListFsListData;
import com.mobile.novabox.bean.OpenListLoginData;
import com.mobile.novabox.bean.OpenListResp;
import com.orhanobut.hawk.Hawk;

import org.json.JSONObject;

/**
 * OpenList (AList 协议) 网盘接口封装。
 * 实现登录鉴权、目录浏览、获取可播放直链 3 个能力。
 */
public class OpenListApi {
    private static final Gson gson = new Gson();

    public interface Callback<T> {
        void onSuccess(T data);
        void onError(String msg);
    }

    public static String getServerUrl() {
        return Hawk.get(HawkConfig.OPENLIST_SERVER_URL, "");
    }

    public static String getToken() {
        return Hawk.get(HawkConfig.OPENLIST_TOKEN, "");
    }

    public static boolean isLogin() {
        return !getServerUrl().isEmpty() && !getToken().isEmpty();
    }

    public static void logout() {
        Hawk.put(HawkConfig.OPENLIST_TOKEN, "");
        Hawk.put(HawkConfig.OPENLIST_USERNAME, "");
    }

    public static String normalizeUrl(String url) {
        if (url == null) return "";
        String u = url.trim();
        if (u.isEmpty()) return "";
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "http://" + u;
        }
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    public static void login(String serverUrl, String username, String password, Callback<String> cb) {
        String base = normalizeUrl(serverUrl);
        if (base.isEmpty()) {
            cb.onError("请输入服务器地址");
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("username", username);
            body.put("password", password);
            body.put("otp_code", "");
        } catch (Throwable ignore) {}

        OkGo.<String>post(base + "/api/auth/login")
                .tag("openlist_login")
                .upJson(body.toString())
                .execute(new AbsCallback<String>() {
                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        if (response.body() != null) return response.body().string();
                        return "";
                    }

                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            OpenListResp<OpenListLoginData> resp = gson.fromJson(response.body(),
                                    com.google.gson.reflect.TypeToken.getParameterized(OpenListResp.class, OpenListLoginData.class).getType());
                            if (resp != null && resp.isSuccess() && resp.data != null && !resp.data.token.isEmpty()) {
                                Hawk.put(HawkConfig.OPENLIST_SERVER_URL, base);
                                Hawk.put(HawkConfig.OPENLIST_TOKEN, resp.data.token);
                                Hawk.put(HawkConfig.OPENLIST_USERNAME, username);
                                cb.onSuccess(resp.data.token);
                            } else {
                                cb.onError(resp != null && resp.message != null && !resp.message.isEmpty() ? resp.message : "登录失败");
                            }
                        } catch (Throwable th) {
                            cb.onError("登录响应解析失败");
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        cb.onError("网络请求失败，请检查服务器地址");
                    }
                });
    }

    public static void listFiles(String path, Callback<OpenListFsListData> cb) {
        String base = getServerUrl();
        String token = getToken();
        if (base.isEmpty()) {
            cb.onError("未配置 OpenList 服务器");
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("path", path == null || path.isEmpty() ? "/" : path);
            body.put("password", "");
            body.put("page", 1);
            body.put("per_page", 0);
            body.put("refresh", false);
        } catch (Throwable ignore) {}

        OkGo.<String>post(base + "/api/fs/list")
                .tag("openlist_list")
                .headers("Authorization", token)
                .upJson(body.toString())
                .execute(new AbsCallback<String>() {
                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        if (response.body() != null) return response.body().string();
                        return "";
                    }

                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            OpenListResp<OpenListFsListData> resp = gson.fromJson(response.body(),
                                    com.google.gson.reflect.TypeToken.getParameterized(OpenListResp.class, OpenListFsListData.class).getType());
                            if (resp != null && resp.isSuccess() && resp.data != null) {
                                cb.onSuccess(resp.data);
                            } else {
                                cb.onError(resp != null && resp.message != null && !resp.message.isEmpty() ? resp.message : "获取目录失败");
                            }
                        } catch (Throwable th) {
                            cb.onError("目录响应解析失败");
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        cb.onError("网络请求失败");
                    }
                });
    }

    public static void getFile(String path, Callback<OpenListFsGetData> cb) {
        String base = getServerUrl();
        String token = getToken();
        if (base.isEmpty()) {
            cb.onError("未配置 OpenList 服务器");
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("path", path);
            body.put("password", "");
        } catch (Throwable ignore) {}

        OkGo.<String>post(base + "/api/fs/get")
                .tag("openlist_get")
                .headers("Authorization", token)
                .upJson(body.toString())
                .execute(new AbsCallback<String>() {
                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        if (response.body() != null) return response.body().string();
                        return "";
                    }

                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            OpenListResp<OpenListFsGetData> resp = gson.fromJson(response.body(),
                                    com.google.gson.reflect.TypeToken.getParameterized(OpenListResp.class, OpenListFsGetData.class).getType());
                            if (resp != null && resp.isSuccess() && resp.data != null) {
                                cb.onSuccess(resp.data);
                            } else {
                                cb.onError(resp != null && resp.message != null && !resp.message.isEmpty() ? resp.message : "获取文件信息失败");
                            }
                        } catch (Throwable th) {
                            cb.onError("文件响应解析失败");
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        cb.onError("网络请求失败");
                    }
                });
    }
}
