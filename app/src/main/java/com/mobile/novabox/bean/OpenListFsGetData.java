package com.mobile.novabox.bean;

import com.google.gson.annotations.SerializedName;

public class OpenListFsGetData {
    @SerializedName("name")
    public String name = "";
    @SerializedName("size")
    public long size = 0;
    @SerializedName("is_dir")
    public boolean isDir = false;
    @SerializedName("sign")
    public String sign = "";
    @SerializedName("type")
    public int type = 0;
    @SerializedName("raw_url")
    public String rawUrl = "";
    @SerializedName("provider")
    public String provider = "";
}
