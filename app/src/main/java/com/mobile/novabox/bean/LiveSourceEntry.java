package com.mobile.novabox.bean;

/**
 * 直播地址页面条目模型。
 * 同时承载两类来源：
 *  - 用户配置的直播地址（fromVod=false），可编辑、可删除、可选中
 *  - 点播 JSON 内嵌的 lives 直播源（fromVod=true），仅可选中，不可编辑、不可删除
 */
public class LiveSourceEntry {
    /** 是否为点播 JSON 内嵌的 lives 直播源 */
    private boolean fromVod;
    /** 显示名称 */
    private String name;
    /** 直播地址；点播来源为空字符串 */
    private String url;
    /** 点播来源在 lives 数组中的下标；用户配置来源为 -1 */
    private int sourceIndex;

    public LiveSourceEntry() {
    }

    public LiveSourceEntry(boolean fromVod, String name, String url, int sourceIndex) {
        this.fromVod = fromVod;
        this.name = name != null ? name : "";
        this.url = url != null ? url : "";
        this.sourceIndex = sourceIndex;
    }

    public boolean isFromVod() {
        return fromVod;
    }

    public void setFromVod(boolean fromVod) {
        this.fromVod = fromVod;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name : "";
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url != null ? url : "";
    }

    public int getSourceIndex() {
        return sourceIndex;
    }

    public void setSourceIndex(int sourceIndex) {
        this.sourceIndex = sourceIndex;
    }
}
