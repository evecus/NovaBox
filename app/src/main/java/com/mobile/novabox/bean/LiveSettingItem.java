package com.mobile.novabox.bean;

/**
 * @author pj567
 * @date :2021/1/12
 * @description:
 */
public class LiveSettingItem {
    private int itemIndex;
    private String itemName;
    private boolean itemSelected = false;
    private String itemUrl = ""; // 用于配置切换：存储实际URL
    private int itemGroup = -1; // 0=直播地址来源, 1=线路选择来源, -1=其他

    public int getItemIndex() {
        return itemIndex;
    }

    public void setItemIndex(int itemIndex) {
        this.itemIndex = itemIndex;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public boolean isItemSelected() {
        return itemSelected;
    }

    public void setItemSelected(boolean itemSelected) {
        this.itemSelected = itemSelected;
    }

    public String getItemUrl() {
        return itemUrl;
    }

    public void setItemUrl(String itemUrl) {
        this.itemUrl = itemUrl != null ? itemUrl : "";
    }

    public int getItemGroup() {
        return itemGroup;
    }

    public void setItemGroup(int itemGroup) {
        this.itemGroup = itemGroup;
    }
}