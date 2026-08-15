package com.mobile.novabox.ui.activity;

import android.app.Dialog;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mobile.novabox.R;
import com.mobile.novabox.api.ApiConfig;
import com.mobile.novabox.base.BaseActivity;
import com.mobile.novabox.bean.LiveSourceEntry;
import com.mobile.novabox.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.List;

/**
 * 直播地址管理页面
 * 用户配置的每条 entry 格式: "name\turl"
 * 除用户配置外，还会展示点播 JSON 内嵌的 lives 直播源（带"点播"标记，仅可选中，不可编辑、删除）
 * 支持选中一条作为当前直播源：用户配置写入 LIVE_API_URL；点播来源写入 live_group_index 并清空 LIVE_API_URL
 */
public class LiveSourceActivity extends BaseActivity {

    public static final String SEP = "\t";

    private RecyclerView recyclerView;
    private LiveAdapter adapter;
    private List<LiveSourceEntry> sourceList = new ArrayList<>();
    private TextView tvEmpty;
    /** 当前选中的条目索引，-1 表示无 */
    private int selectedIndex = -1;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_live_source;
    }

    @Override
    protected void init() {
        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> onBackPressed());

        ImageView btnAdd = findViewById(R.id.btnAdd);
        btnAdd.setOnClickListener(v -> showAddDialog());

        tvEmpty = findViewById(R.id.tvEmpty);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadList();
        restoreSelectedIndex();
        adapter = new LiveAdapter(sourceList);
        recyclerView.setAdapter(adapter);
        updateEmpty();
    }

    private void loadList() {
        sourceList.clear();
        // 第一部分：用户配置的直播地址
        ArrayList<String> saved = Hawk.get(HawkConfig.LIVE_SOURCE_LIST, new ArrayList<String>());
        for (String entry : saved) {
            String[] parts = splitEntry(entry);
            sourceList.add(new LiveSourceEntry(false, parts[0], parts[1], -1));
        }
        // 第二部分：点播 JSON 内嵌 lives 直播源（仅可选中）
        JsonArray livesGroups = Hawk.get(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
        for (int i = 0; i < livesGroups.size(); i++) {
            JsonObject livesObj = livesGroups.get(i).getAsJsonObject();
            String name = livesObj.has("name") ? livesObj.get("name").getAsString() : ("线路" + (i + 1));
            sourceList.add(new LiveSourceEntry(true, name, "", i));
        }
    }

    /** 仅保存用户配置部分，点播来源不持久化 */
    private void saveList() {
        ArrayList<String> userList = new ArrayList<>();
        for (LiveSourceEntry e : sourceList) {
            if (e.isFromVod()) continue;
            if (e.getName().isEmpty() && !e.getUrl().isEmpty()) {
                userList.add(e.getUrl()); // 兼容历史遗留的纯 URL 条目
            } else {
                userList.add(e.getName() + SEP + e.getUrl());
            }
        }
        Hawk.put(HawkConfig.LIVE_SOURCE_LIST, userList);
    }

    private void restoreSelectedIndex() {
        selectedIndex = -1;
        String currentUrl = Hawk.get(HawkConfig.LIVE_API_URL, "");
        if (!TextUtils.isEmpty(currentUrl)) {
            // 当前在使用用户配置的直播地址
            for (int i = 0; i < sourceList.size(); i++) {
                LiveSourceEntry e = sourceList.get(i);
                if (!e.isFromVod() && currentUrl.equals(e.getUrl())) {
                    selectedIndex = i;
                    return;
                }
            }
            return;
        }
        // LIVE_API_URL 为空：当前在使用点播 lives 源（live_group_index 命中）
        JsonArray livesGroups = Hawk.get(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
        int liveGroupIndex = ApiConfig.getLiveGroupIndex();
        if (livesGroups != null && livesGroups.size() > 0
                && liveGroupIndex >= 0 && liveGroupIndex < livesGroups.size()) {
            for (int i = 0; i < sourceList.size(); i++) {
                LiveSourceEntry e = sourceList.get(i);
                if (e.isFromVod() && e.getSourceIndex() == liveGroupIndex) {
                    selectedIndex = i;
                    return;
                }
            }
        }
    }

    private void updateEmpty() {
        tvEmpty.setVisibility(sourceList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showAddDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_live);
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
            sourceList.add(new LiveSourceEntry(false, name, url, -1));
            saveList();
            adapter.notifyDataSetChanged();
            updateEmpty();
            Toast.makeText(this, "已添加直播源：" + name, Toast.LENGTH_SHORT).show();
            autoApplyFirstSourceIfNone();
        });

        dialog.show();
    }

    /**
     * 若当前尚未选中任何直播源，则自动选中列表中最早添加的那条用户直播源（下标0）。
     * 若当前正在使用点播 lives 源，则不做改动。
     */
    private void autoApplyFirstSourceIfNone() {
        String current = Hawk.get(HawkConfig.LIVE_API_URL, "");
        if (!TextUtils.isEmpty(current)) {
            return; // 已有选中的直播地址，不做改动
        }
        // LIVE_API_URL 为空：若当前正在使用点播 lives 源则不动
        JsonArray livesGroups = Hawk.get(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
        int liveGroupIndex = ApiConfig.getLiveGroupIndex();
        if (livesGroups != null && livesGroups.size() > 0
                && liveGroupIndex >= 0 && liveGroupIndex < livesGroups.size()) {
            return;
        }
        if (sourceList.isEmpty()) {
            return;
        }
        LiveSourceEntry first = sourceList.get(0);
        if (first.isFromVod() || TextUtils.isEmpty(first.getUrl())) {
            return;
        }
        selectedIndex = 0;
        Hawk.put(HawkConfig.LIVE_API_URL, first.getUrl());
        adapter.notifyItemChanged(0);
    }

    /** 选中某条目作为当前直播源 */
    private void selectSource(int position) {
        if (position < 0 || position >= sourceList.size()) return;
        LiveSourceEntry entry = sourceList.get(position);
        int oldSelected = selectedIndex;
        if (entry.isFromVod()) {
            // 点播 lives 来源：仅可选中，不可取消
            if (oldSelected == position) {
                Toast.makeText(this, "当前正在使用点播线路：" + entry.getName(), Toast.LENGTH_SHORT).show();
                return;
            }
            JsonArray livesGroups = Hawk.get(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
            int vodIndex = entry.getSourceIndex();
            if (livesGroups == null || vodIndex < 0 || vodIndex >= livesGroups.size()) return;
            selectedIndex = position;
            Hawk.put(HawkConfig.LIVE_API_URL, ""); // 使用点播 lives，清空独立直播地址
            ApiConfig.setLiveGroupIndex(vodIndex);
            if (oldSelected >= 0 && oldSelected < sourceList.size()) adapter.notifyItemChanged(oldSelected);
            adapter.notifyItemChanged(position);
            Toast.makeText(this, "已选中点播线路：" + entry.getName(), Toast.LENGTH_SHORT).show();
            return;
        }
        // 用户配置来源
        if (oldSelected == position) {
            // 再次点击取消选中
            selectedIndex = -1;
            Hawk.put(HawkConfig.LIVE_API_URL, "");
            adapter.notifyItemChanged(position);
            Toast.makeText(this, "已取消直播源选中", Toast.LENGTH_SHORT).show();
            return;
        }
        selectedIndex = position;
        Hawk.put(HawkConfig.LIVE_API_URL, entry.getUrl());
        Hawk.put(ApiConfig.getLiveGroupIndexKey(), 0); // 重置该直播地址对应的线路选择下标
        if (oldSelected >= 0 && oldSelected < sourceList.size()) adapter.notifyItemChanged(oldSelected);
        adapter.notifyItemChanged(position);
        Toast.makeText(this, "已选中直播源：" + entry.getName(), Toast.LENGTH_SHORT).show();
    }

    /** 解析 entry 字符串为 {name, url}；纯 URL 条目返回 {"", url} */
    private static String[] splitEntry(String entry) {
        if (entry == null) return new String[]{"", ""};
        if (entry.contains("\t")) {
            String[] parts = entry.split("\t", 2);
            return new String[]{parts[0], parts.length > 1 ? parts[1] : ""};
        }
        return new String[]{"", entry};
    }

    private void showEditDialog(int position) {
        if (position < 0 || position >= sourceList.size()) return;
        LiveSourceEntry entry = sourceList.get(position);
        if (entry.isFromVod()) return; // 点播来源不可编辑
        String oldName = entry.getName();
        String oldUrl = entry.getUrl();

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_live);
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
            sourceList.set(position, new LiveSourceEntry(false, name, url, -1));
            saveList();
            // 若修改的是当前选中项，同步更新 LIVE_API_URL
            if (position == selectedIndex) {
                Hawk.put(HawkConfig.LIVE_API_URL, url);
            }
            adapter.notifyItemChanged(position);
            Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    // ───── Adapter ─────

    class LiveAdapter extends RecyclerView.Adapter<LiveAdapter.VH> {
        List<LiveSourceEntry> data;

        LiveAdapter(List<LiveSourceEntry> data) {
            this.data = data;
        }

        @Override
        public VH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_live_entry, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            LiveSourceEntry entry = data.get(position);
            boolean isVod = entry.isFromVod();
            boolean isSelected = (position == selectedIndex);

            // 名称显示：点播来源用紫色区分，选中时统一高亮
            String displayName = entry.getName();
            if (TextUtils.isEmpty(displayName)) displayName = entry.getUrl();
            holder.tvName.setText(displayName);
            if (isSelected) {
                holder.tvName.setTextColor(Color.parseColor("#4FC3F7"));
            } else if (isVod) {
                holder.tvName.setTextColor(Color.parseColor("#7B1FA2"));
            } else {
                holder.tvName.setTextColor(Color.BLACK);
            }

            if (isVod) {
                // 点播来源：显示标记，不可编辑、不可删除
                holder.tvUrl.setText("点播JSON内嵌线路");
                holder.tvTag.setVisibility(View.VISIBLE);
                holder.btnEdit.setVisibility(View.GONE);
                holder.btnDelete.setVisibility(View.GONE);
            } else {
                holder.tvUrl.setText(entry.getUrl());
                holder.tvTag.setVisibility(View.GONE);
                holder.btnEdit.setVisibility(View.VISIBLE);
                holder.btnDelete.setVisibility(View.VISIBLE);
            }

            if (holder.ivSelected != null) {
                holder.ivSelected.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            }

            // 点击整个条目=选中该源
            holder.itemView.setOnClickListener(v -> {
                selectSource(holder.getAdapterPosition());
            });

            holder.btnEdit.setOnClickListener(v -> {
                showEditDialog(holder.getAdapterPosition());
            });

            holder.btnDelete.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos < 0 || pos >= data.size()) return;
                if (data.get(pos).isFromVod()) return; // 点播来源不可删除
                // 若删除的是已选中项，清除 LIVE_API_URL
                if (pos == selectedIndex) {
                    selectedIndex = -1;
                    Hawk.put(HawkConfig.LIVE_API_URL, "");
                } else if (pos < selectedIndex) {
                    selectedIndex--;
                }
                data.remove(pos);
                saveList();
                notifyItemRemoved(pos);
                notifyItemRangeChanged(pos, data.size() - pos);
                updateEmpty();
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvUrl, tvTag;
            ImageView btnDelete, btnEdit;
            ImageView ivSelected;

            VH(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvName);
                tvUrl = itemView.findViewById(R.id.tvUrl);
                tvTag = itemView.findViewById(R.id.tvTag);
                btnDelete = itemView.findViewById(R.id.btnDelete);
                btnEdit = itemView.findViewById(R.id.btnEdit);
                ivSelected = itemView.findViewById(R.id.ivSelected);
            }
        }
    }
}
