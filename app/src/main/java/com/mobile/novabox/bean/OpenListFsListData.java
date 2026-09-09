package com.mobile.novabox.bean;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class OpenListFsListData {
    @SerializedName("content")
    public List<OpenListFile> content;
    @SerializedName("total")
    public int total = 0;
    @SerializedName("provider")
    public String provider = "";
}
