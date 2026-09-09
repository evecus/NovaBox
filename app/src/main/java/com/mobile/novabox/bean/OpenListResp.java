package com.mobile.novabox.bean;

import com.google.gson.annotations.SerializedName;

public class OpenListResp<T> {
    @SerializedName("code")
    public int code;
    @SerializedName("message")
    public String message = "";
    @SerializedName("data")
    public T data;

    public boolean isSuccess() {
        return code == 200;
    }
}
