package com.mobile.novabox.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mobile.novabox.R;
import com.mobile.novabox.cast.CastDevice;
import com.mobile.novabox.cast.UpnpController;
import com.mobile.novabox.cast.UpnpDiscovery;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 投屏设备搜索弹窗:
 * 1. 打开即后台线程 SSDP 搜索局域网 MediaRenderer
 * 2. 列表显示设备名 + IP,点击即推送当前视频 URL
 * 3. 支持手动刷新
 */
public class CastSearchDialog extends Dialog {

    public interface OnCastListener {
        /** 投屏成功回调 */
        void onCastSuccess(CastDevice device);

        /** 投屏失败回调 */
        void onCastFailed(CastDevice device, String error);
    }

    private final String videoUrl;
    private final String videoTitle;
    private final OnCastListener listener;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    private RecyclerView rvDevices;
    private TextView tvStatus;
    private DeviceAdapter adapter;
    private boolean searching = false;

    public CastSearchDialog(Activity activity, String videoUrl, String videoTitle, OnCastListener listener) {
        super(activity, R.style.CustomDialogStyle);
        this.videoUrl = videoUrl;
        this.videoTitle = videoTitle;
        this.listener = listener;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dlg_cast_search);
        setCanceledOnTouchOutside(true);

        Window window = getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.CENTER);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        tvStatus = findViewById(R.id.tvCastStatus);
        rvDevices = findViewById(R.id.rvCastDevices);
        rvDevices.setLayoutManager(new LinearLayoutManager(activity));
        adapter = new DeviceAdapter();
        rvDevices.setAdapter(adapter);

        findViewById(R.id.tvCastClose).setOnClickListener(v -> dismiss());
        findViewById(R.id.tvCastCancel).setOnClickListener(v -> dismiss());
        findViewById(R.id.tvCastRefresh).setOnClickListener(v -> startSearch());

        startSearch();
    }

    private void startSearch() {
        if (searching) return;
        searching = true;
        tvStatus.setText("正在搜索局域网设备...");
        tvStatus.setTextColor(0x80000000);
        adapter.setDevices(new ArrayList<>());
        pool.execute(() -> {
            UpnpDiscovery discovery = new UpnpDiscovery();
            final List<CastDevice> devices = discovery.discover(getContext());
            rvDevices.post(() -> {
                searching = false;
                if (devices.isEmpty()) {
                    tvStatus.setText("未找到设备,请确认电视/盒子与手机在同一 WiFi");
                    tvStatus.setTextColor(0xFFCC0000);
                } else {
                    tvStatus.setText("找到 " + devices.size() + " 个设备,点击投屏");
                    tvStatus.setTextColor(0xFF008000);
                    adapter.setDevices(devices);
                }
            });
        });
    }

    private void castTo(final CastDevice device) {
        if (searching) return;
        searching = true;
        tvStatus.setText("正在推送到 " + device.friendlyName + " ...");
        pool.execute(() -> {
            boolean ok = false;
            String error = "推送失败";
            try {
                UpnpController controller = new UpnpController();
                ok = controller.play(device, videoUrl, videoTitle);
                if (!ok) error = "设备拒绝了播放请求";
            } catch (Throwable th) {
                error = th.getMessage() == null ? th.getClass().getSimpleName() : th.getMessage();
            }
            final boolean success = ok;
            final String err = error;
            rvDevices.post(() -> {
                searching = false;
                if (success) {
                    Toast.makeText(getContext(), "已投屏到 " + device.friendlyName, Toast.LENGTH_LONG).show();
                    if (listener != null) listener.onCastSuccess(device);
                    dismiss();
                } else {
                    tvStatus.setText("投屏失败: " + err);
                    tvStatus.setTextColor(0xFFCC0000);
                    if (listener != null) listener.onCastFailed(device, err);
                }
            });
        });
    }

    @Override
    public void dismiss() {
        super.dismiss();
        pool.shutdownNow();
    }

    // ─── 设备列表 adapter ───

    class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.VH> {

        private final List<CastDevice> devices = new ArrayList<>();

        void setDevices(List<CastDevice> list) {
            devices.clear();
            if (list != null) devices.addAll(list);
            notifyDataSetChanged();
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setPadding(24, 22, 24, 22);
            tv.setTextSize(15);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setSingleLine(true);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tv.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new VH(tv);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            CastDevice device = devices.get(position);
            String ip = device.ip == null ? "" : "  (" + device.ip + ")";
            holder.tv.setText("📺 " + device.friendlyName + ip);
            holder.tv.setTextColor(0xFF333333);
            holder.tv.setOnClickListener(v -> castTo(device));
        }

        @Override
        public int getItemCount() {
            return devices.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tv;

            VH(TextView v) {
                super(v);
                tv = v;
            }
        }
    }
}
