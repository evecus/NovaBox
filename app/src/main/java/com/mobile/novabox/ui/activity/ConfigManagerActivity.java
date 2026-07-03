package com.mobile.novabox.ui.activity;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mobile.novabox.R;
import com.mobile.novabox.base.BaseActivity;
import com.mobile.novabox.util.HawkConfig;
import com.mobile.novabox.util.HistoryHelper;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.StringCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 点播配置地址管理页面
 * 支持添加多个仓库或线路，显示在列表中，可刷新或删除
 */
public class ConfigManagerActivity extends BaseActivity {

    // Each entry: "name\turl\tROUTE_JSON" where ROUTE_JSON is a JSON array of {name,url} for warehouses
    // or empty string if it's a direct route
    public static final String SEP = "\t";

    private RecyclerView recyclerView;
    private ConfigAdapter adapter;
    private List<String> configList = new ArrayList<>();
    private TextView tvEmpty;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_config_manager;
    }

    @Override
    protected void init() {
        // Toolbar back button
        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> onBackPressed());

        // Add button
        ImageView btnAdd = findViewById(R.id.btnAdd);
        btnAdd.setOnClickListener(v -> showAddDialog());

        tvEmpty = findViewById(R.id.tvEmpty);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadList();
        adapter = new ConfigAdapter(configList);
        recyclerView.setAdapter(adapter);
        updateEmpty();
    }

    private void loadList() {
        configList.clear();
        ArrayList<String> saved = Hawk.get(HawkConfig.VOD_CONFIG_LIST, new ArrayList<String>());
        configList.addAll(saved);
    }

    private void saveList() {
        Hawk.put(HawkConfig.VOD_CONFIG_LIST, new ArrayList<>(configList));
    }

    private void updateEmpty() {
        tvEmpty.setVisibility(configList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showAddDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_config);
        dialog.setCanceledOnTouchOutside(true);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }

        EditText etName = dialog.findViewById(R.id.etName);
        EditText etUrl = dialog.findViewById(R.id.etUrl);
        TextView btnConfirm = dialog.findViewById(R.id.btnConfirm);

        btnConfirm.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String url = etUrl.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                Toast.makeText(this, "请输入名称", Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(url)) {
                Toast.makeText(this, "请输入地址", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            fetchAndAddConfig(name, url);
        });

        dialog.show();
    }

    private void fetchAndAddConfig(String name, String url) {
        Toast.makeText(this, "正在解析地址...", Toast.LENGTH_SHORT).show();
        OkGo.<String>get(url)
                .execute(new StringCallback() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        String body = response.body();
                        runOnUiThread(() -> {
                            String entry = parseEntry(name, url, body);
                            configList.add(entry);
                            saveList();
                            adapter.notifyDataSetChanged();
                            updateEmpty();
                        });
                    }

                    @Override
                    public void onError(Response<String> response) {
                        runOnUiThread(() -> {
                            // Save with empty routes – treat as direct route
                            String entry = buildEntry(name, url, null);
                            configList.add(entry);
                            saveList();
                            adapter.notifyDataSetChanged();
                            updateEmpty();
                            Toast.makeText(ConfigManagerActivity.this, "地址解析失败，已作为线路保存", Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    /**
     * Parse whether it's a warehouse (has urls array) or a direct route
     */
    private String parseEntry(String name, String url, String body) {
        try {
            String trimmed = body.trim();
            if (trimmed.startsWith("{")) {
                JSONObject obj = new JSONObject(trimmed);
                if (obj.has("urls") && !obj.has("sites")) {
                    // Warehouse: has urls array
                    JSONArray urls = obj.getJSONArray("urls");
                    JSONArray routes = new JSONArray();
                    for (int i = 0; i < urls.length(); i++) {
                        Object item = urls.get(i);
                        JSONObject route = new JSONObject();
                        if (item instanceof JSONObject) {
                            JSONObject jo = (JSONObject) item;
                            route.put("name", jo.optString("name", "线路" + (i + 1)));
                            String u = jo.optString("url", jo.optString("api", ""));
                            route.put("url", u);
                        } else {
                            route.put("name", "线路" + (i + 1));
                            route.put("url", item.toString());
                        }
                        routes.put(route);
                    }
                    return buildEntry(name, url, routes.toString());
                }
            }
        } catch (Throwable ignored) {
        }
        // Direct route
        return buildEntry(name, url, null);
    }

    private String buildEntry(String name, String url, String routesJson) {
        return name + SEP + url + SEP + (routesJson == null ? "" : routesJson);
    }

    public static String getEntryName(String entry) {
        if (entry == null) return "";
        String[] parts = entry.split("\t", 3);
        return parts.length > 0 ? parts[0] : "";
    }

    public static String getEntryUrl(String entry) {
        if (entry == null) return "";
        String[] parts = entry.split("\t", 3);
        return parts.length > 1 ? parts[1] : "";
    }

    public static String getEntryRoutes(String entry) {
        if (entry == null) return "";
        String[] parts = entry.split("\t", 3);
        return parts.length > 2 ? parts[2] : "";
    }

    public static boolean isWarehouse(String entry) {
        return !TextUtils.isEmpty(getEntryRoutes(entry));
    }

    /**
     * Get route list for an entry. For direct routes, returns a single-item list with the entry itself.
     */
    public static List<String[]> getRoutes(String entry) {
        List<String[]> result = new ArrayList<>();
        String routesJson = getEntryRoutes(entry);
        if (!TextUtils.isEmpty(routesJson)) {
            try {
                JSONArray arr = new JSONArray(routesJson);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    result.add(new String[]{o.optString("name", "线路" + (i + 1)), o.optString("url", "")});
                }
            } catch (Throwable ignored) {
            }
        }
        if (result.isEmpty()) {
            // Direct route: single route with the entry's name and url
            result.add(new String[]{getEntryName(entry), getEntryUrl(entry)});
        }
        return result;
    }

    // ───── Adapter ─────

    class ConfigAdapter extends RecyclerView.Adapter<ConfigAdapter.VH> {
        List<String> data;

        ConfigAdapter(List<String> data) {
            this.data = data;
        }

        @Override
        public VH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_config_entry, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            String entry = data.get(position);
            holder.tvName.setText(getEntryName(entry));
            String type = isWarehouse(entry) ? "[仓库]" : "[线路]";
            holder.tvType.setText(type);
            holder.tvUrl.setText(getEntryUrl(entry));

            holder.btnEdit.setOnClickListener(v -> showEditDialog(position));
            holder.btnRefresh.setOnClickListener(v -> refreshEntry(position));
            holder.btnDelete.setOnClickListener(v -> deleteEntry(position));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvType, tvUrl;
            ImageView btnRefresh, btnDelete, btnEdit;

            VH(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvName);
                tvType = itemView.findViewById(R.id.tvType);
                tvUrl = itemView.findViewById(R.id.tvUrl);
                btnRefresh = itemView.findViewById(R.id.btnRefresh);
                btnDelete = itemView.findViewById(R.id.btnDelete);
                btnEdit = itemView.findViewById(R.id.btnEdit);
            }
        }
    }

    private void refreshEntry(int position) {
        String entry = configList.get(position);
        String name = getEntryName(entry);
        String url = getEntryUrl(entry);
        Toast.makeText(this, "正在刷新 " + name + "...", Toast.LENGTH_SHORT).show();
        OkGo.<String>get(url)
                .execute(new StringCallback() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        String body = response.body();
                        runOnUiThread(() -> {
                            String newEntry = parseEntry(name, url, body);
                            configList.set(position, newEntry);
                            saveList();
                            adapter.notifyItemChanged(position);
                            Toast.makeText(ConfigManagerActivity.this, "刷新成功", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(Response<String> response) {
                        runOnUiThread(() ->
                                Toast.makeText(ConfigManagerActivity.this, "刷新失败", Toast.LENGTH_SHORT).show()
                        );
                    }
                });
    }

    private void deleteEntry(int position) {
        configList.remove(position);
        saveList();
        adapter.notifyItemRemoved(position);
        updateEmpty();
    }

    private void showEditDialog(int position) {
        String entry = configList.get(position);
        String oldName = getEntryName(entry);
        String oldUrl = getEntryUrl(entry);

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_config);
        dialog.setCanceledOnTouchOutside(true);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }

        EditText etName = dialog.findViewById(R.id.etName);
        EditText etUrl = dialog.findViewById(R.id.etUrl);
        TextView btnConfirm = dialog.findViewById(R.id.btnConfirm);

        // 回填原有数据
        etName.setText(oldName);
        etUrl.setText(oldUrl);

        btnConfirm.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String url = etUrl.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                Toast.makeText(this, "请输入名称", Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(url)) {
                Toast.makeText(this, "请输入地址", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            if (url.equals(oldUrl)) {
                // URL未变，直接更新名称保留原路由信息
                String routes = getEntryRoutes(entry);
                configList.set(position, buildEntry(name, url, routes.isEmpty() ? null : routes));
                saveList();
                adapter.notifyItemChanged(position);
            } else {
                // URL变了，重新拉取
                Toast.makeText(this, "正在解析新地址...", Toast.LENGTH_SHORT).show();
                OkGo.<String>get(url).execute(new com.lzy.okgo.callback.StringCallback() {
                    @Override
                    public void onSuccess(com.lzy.okgo.model.Response<String> response) {
                        String body = response.body();
                        runOnUiThread(() -> {
                            String newEntry = parseEntry(name, url, body);
                            configList.set(position, newEntry);
                            saveList();
                            adapter.notifyItemChanged(position);
                            Toast.makeText(ConfigManagerActivity.this, "保存成功", Toast.LENGTH_SHORT).show();
                        });
                    }
                    @Override
                    public void onError(com.lzy.okgo.model.Response<String> response) {
                        runOnUiThread(() -> {
                            String newEntry = buildEntry(name, url, null);
                            configList.set(position, newEntry);
                            saveList();
                            adapter.notifyItemChanged(position);
                            Toast.makeText(ConfigManagerActivity.this, "地址解析失败，已保存为线路", Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            }
        });

        dialog.show();
    }
}
