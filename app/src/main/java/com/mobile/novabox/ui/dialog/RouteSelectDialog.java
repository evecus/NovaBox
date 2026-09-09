package com.mobile.novabox.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mobile.novabox.R;
import com.mobile.novabox.ui.activity.ConfigManagerActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * 线路选择弹窗(原 ModelSettingFragment.showRouteSelectDialog 抽出来共用)。
 * 调用方只需传入 Activity 和 OnRouteSelectedListener:
 *   - listener.onSelected(url)   用户点了"确认"
 *   - listener.onCancel()        用户点了"取消"或返回键
 *   - 弹窗生命周期内不需要关心数据加载细节
 */
public class RouteSelectDialog {

    /** 选完回调(用户点确认)。注意 url 可能为空字符串(没选就按确认时不应触发,但兜底仍传 "" 表示无效)。 */
    public interface OnRouteSelectedListener {
        void onSelected(String url);
        void onCancel();
    }

    public static void show(Activity activity, OnRouteSelectedListener listener) {
        if (activity == null || activity.isFinishing()) return;
        ArrayList<String> vodConfigs = com.orhanobut.hawk.Hawk.get(
                com.mobile.novabox.util.HawkConfig.VOD_CONFIG_LIST, new ArrayList<String>());
        if (vodConfigs.isEmpty()) {
            android.widget.Toast.makeText(activity, "请先在\"配置地址\"中添加配置", android.widget.Toast.LENGTH_SHORT).show();
            if (listener != null) listener.onCancel();
            return;
        }

        final Dialog dialog = new Dialog(activity, R.style.CustomDialogStyle);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_route_select);
        dialog.setCanceledOnTouchOutside(true);

        Window window = dialog.getWindow();
        if (window != null) {
            // 高度 WRAP_CONTENT:让 FrameLayout 高度跟随弹窗卡片 LinearLayout,
            // 这样 ✕(top|end)紧贴弹窗卡片右上角,不再被弹窗留白甩到屏幕顶角
            // (否则会和 SettingActivity 的 ActionBar 关闭按钮视觉重叠,用户看不见)
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        RecyclerView rvConfigs = dialog.findViewById(R.id.rvConfigs);
        RecyclerView rvRoutes = dialog.findViewById(R.id.rvRoutes);
        rvConfigs.setLayoutManager(new LinearLayoutManager(activity));
        rvRoutes.setLayoutManager(new LinearLayoutManager(activity));

        final int[] selectedConfig = {0};
        final String[] selectedRouteUrl = {""};

        RouteAdapter routeAdapter = new RouteAdapter(new ArrayList<>(), selectedRouteUrl);
        rvRoutes.setAdapter(routeAdapter);
        // 点选即生效:点击某条线路 → 通知监听者;**不关闭弹窗**,让用户继续浏览/调整,
// 由右上角 ✕ 或点击外部(CancelListener)关闭 → 触发 onCancel(可视为"放弃本次选择")
        routeAdapter.setOnPicked(() -> {
            String url = selectedRouteUrl[0];
            if (url.isEmpty()) return;
            if (listener != null) listener.onSelected(url);
        });

        Runnable refreshRoutes = () -> {
            if (selectedConfig[0] < vodConfigs.size()) {
                List<String[]> routes = ConfigManagerActivity.getRoutes(vodConfigs.get(selectedConfig[0]));
                routeAdapter.updateData(routes);
                // 默认选中:当前 API_URL 匹配的 url
                String current = com.orhanobut.hawk.Hawk.get(
                        com.mobile.novabox.util.HawkConfig.API_URL, "");
                for (int i = 0; i < routes.size(); i++) {
                    if (current.equals(routes.get(i)[1])) {
                        selectedRouteUrl[0] = routes.get(i)[1];
                        routeAdapter.notifyDataSetChanged();
                        break;
                    }
                }
            }
        };

        ConfigLeftAdapter configLeftAdapter = new ConfigLeftAdapter(vodConfigs, selectedConfig, () -> refreshRoutes.run());
        rvConfigs.setAdapter(configLeftAdapter);
        refreshRoutes.run();

        dialog.findViewById(R.id.btnRouteClose).setOnClickListener(v -> {
            dialog.dismiss();
            if (listener != null) listener.onCancel();
        });

        dialog.setOnCancelListener(d -> {
            if (listener != null) listener.onCancel();
        });

        dialog.show();
    }

    // ───── 左栏:配置名列表 ─────

    public static class ConfigLeftAdapter extends RecyclerView.Adapter<ConfigLeftAdapter.VH> {
        private final List<String> data;
        private final int[] selectedConfig;
        private final Runnable onSelected;

        public ConfigLeftAdapter(List<String> data, int[] selectedConfig, Runnable onSelected) {
            this.data = data;
            this.selectedConfig = selectedConfig;
            this.onSelected = onSelected;
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setPadding(20, 28, 20, 28);
            tv.setTextSize(13);
            tv.setTextColor(0xFF000000);
            tv.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT));
            return new VH(tv);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            String name = ConfigManagerActivity.getEntryName(data.get(position));
            holder.tv.setText(name);
            boolean sel = selectedConfig[0] == position;
            holder.tv.setBackgroundColor(sel ? 0x1A1890FF : 0x00000000);
            holder.tv.setTextColor(sel ? 0xFF1890FF : 0xFF333333);
            holder.tv.setOnClickListener(v -> {
                selectedConfig[0] = position;
                notifyDataSetChanged();
                onSelected.run();
            });
        }

        @Override
        public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tv;
            VH(TextView v) { super(v); tv = v; }
        }
    }

    // ───── 右栏:线路列表 ─────

    public static class RouteAdapter extends RecyclerView.Adapter<RouteAdapter.VH> {
        private List<String[]> data;
        private final String[] selectedUrl;

        /** 点击 item 后的回调(由外部 RouteSelectDialog 注入,通常用于 dismiss + notify listener) */
        private Runnable onPicked;

        public RouteAdapter(List<String[]> data, String[] selectedUrl) {
            this.data = data;
            this.selectedUrl = selectedUrl;
        }

        public void updateData(List<String[]> newData) {
            this.data = newData;
            notifyDataSetChanged();
        }

        public void setOnPicked(Runnable onPicked) {
            this.onPicked = onPicked;
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setPadding(24, 28, 24, 28);
            tv.setTextSize(13);
            tv.setTextColor(0xFF333333);
            tv.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT));
            return new VH(tv);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            String[] route = data.get(position);
            holder.tv.setText(route[0]);
            boolean sel = route[1].equals(selectedUrl[0]);
            holder.tv.setBackgroundColor(sel ? 0x22F5C518 : 0x00000000);
            holder.tv.setTextColor(sel ? 0xFFB8860B : 0xFF333333);
            holder.tv.setOnClickListener(v -> {
                selectedUrl[0] = route[1];
                notifyDataSetChanged();
                // 点选即生效:由外部 RouteSelectDialog 注入的 dismiss + 通知监听者逻辑
                if (onPicked != null) onPicked.run();
            });
        }

        @Override
        public int getItemCount() { return data == null ? 0 : data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tv;
            VH(TextView v) { super(v); tv = v; }
        }
    }
}