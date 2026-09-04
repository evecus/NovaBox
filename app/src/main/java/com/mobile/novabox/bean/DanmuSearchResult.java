package com.mobile.novabox.bean;

import android.text.TextUtils;

/**
 * 弹幕在线搜索的单条结果。
 * builtIn=true 表示 url 是内置弹幕库的评论接口地址，加载时需要再请求一次并转换为 xml；
 * builtIn=false 表示 url 已经是可直接使用的弹幕文件地址。
 */
public class DanmuSearchResult {
    private final String name;
    private final String url;
    private final boolean builtIn;

    public DanmuSearchResult(String name, String url, boolean builtIn) {
        this.name = name;
        this.url = url;
        this.builtIn = builtIn;
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? getUrl() : name;
    }

    public String getUrl() {
        return url == null ? "" : url;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }
}
