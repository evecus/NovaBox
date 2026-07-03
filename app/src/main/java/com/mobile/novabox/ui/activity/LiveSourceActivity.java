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

import com.mobile.novabox.R;
import com.mobile.novabox.base.BaseActivity;
import com.mobile.novabox.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.List;

/**
 * 直播地址管理页面
 * 每条 entry 格式: "name\turl"
 * 支持选中一条作为当前直播源（写入 LIVE_API_URL）
 */
public class LiveSourceActivity extends BaseActivity {

    public static final String SEP = "\t";

    private RecyclerView recyclerView;
    private LiveAdapter adapter;
    private List<String> sourceList = new ArrayList<>();
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
        // 根据已保存的 LIVE_API_URL 还原 selectedIndex
        String currentUrl = Hawk.get(HawkConfig.LIVE_API_URL, "");
        selectedIndex = -1;
        if (!currentUrl.isEmpty()) {
            for (int i = 0; i < sourceList.size(); i++) {
                if (currentUrl.equals(getEntryUrl(sourceList.get(i)))) {
                    selectedIndex = i;
                    break;
                }
            }
        }
        adapter = new LiveAdapter(sourceList);
        recyclerView.setAdapter(adapter);
        updateEmpty();
    }

    private void loadList() {
        sourceList.clear();
        ArrayList<String> saved = Hawk.get(HawkConfig.LIVE_SOURCE_LIST, new ArrayList<String>());
        sourceList.addAll(saved);
    }

    private void saveList() {
        Hawk.put(HawkConfig.LIVE_SOURCE_LIST, new ArrayList<>(sourceList));
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
            String entry = name + SEP + url;
            sourceList.add(entry);
            saveList();
            adapter.notifyDataSetChanged();
            updateEmpty();
            Toast.makeText(this, "已添加直播源：" + name, Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    /** 选中某条目作为当前直播源，更新 LIVE_API_URL */
    private void selectSource(int position) {
        if (position < 0 || position >= sourceList.size()) return;
        int oldSelected = selectedIndex;
        if (oldSelected == position) {
            // 再次点击取消选中
            selectedIndex = -1;
            Hawk.put(HawkConfig.LIVE_API_URL, "");
            adapter.notifyItemChanged(position);
            Toast.makeText(this, "已取消直播源选中", Toast.LENGTH_SHORT).show();
            return;
        }
        selectedIndex = position;
        String url = getEntryUrl(sourceList.get(position));
        String name = getEntryName(sourceList.get(position));
        Hawk.put(HawkConfig.LIVE_API_URL, url);
        // 刷新旧选中项和新选中项
        if (oldSelected >= 0 && oldSelected < sourceList.size()) {
            adapter.notifyItemChanged(oldSelected);
        }
        adapter.notifyItemChanged(position);
        Toast.makeText(this, "已选中直播源：" + name, Toast.LENGTH_SHORT).show();
    }

    public static String getEntryName(String entry) {
        if (entry == null) return "";
        String[] parts = entry.split("\t", 2);
        return parts.length > 0 ? parts[0] : "";
    }

    public static String getEntryUrl(String entry) {
        if (entry == null) return "";
        String[] parts = entry.split("\t", 2);
        return parts.length > 1 ? parts[1] : "";
    }

    private void showEditDialog(int position) {
        if (position < 0 || position >= sourceList.size()) return;
        String entry = sourceList.get(position);
        String oldName = getEntryName(entry);
        String oldUrl = getEntryUrl(entry);

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
            String newEntry = name + SEP + url;
            sourceList.set(position, newEntry);
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
        List<String> data;

        LiveAdapter(List<String> data) {
            this.data = data;
        }

        @Override
        public VH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_live_entry, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            String entry = data.get(position);
            holder.tvName.setText(getEntryName(entry));
            holder.tvUrl.setText(getEntryUrl(entry));

            boolean isSelected = (position == selectedIndex);
            // 选中时高亮名称和显示选中图标
            holder.tvName.setTextColor(isSelected ? Color.parseColor("#4FC3F7") : Color.BLACK);
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
                // 若删除的是已选中项，清除 LIVE_API_URL
                if (pos == selectedIndex) {
                    selectedIndex = -1;
                    Hawk.put(HawkConfig.LIVE_API_URL, "");
                } else if (pos < selectedIndex) {
                    selectedIndex--;
                }
                sourceList.remove(pos);
                saveList();
                notifyItemRemoved(pos);
                notifyItemRangeChanged(pos, sourceList.size());
                updateEmpty();
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvUrl;
            ImageView btnDelete, btnEdit;
            ImageView ivSelected;

            VH(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvName);
                tvUrl = itemView.findViewById(R.id.tvUrl);
                btnDelete = itemView.findViewById(R.id.btnDelete);
                btnEdit = itemView.findViewById(R.id.btnEdit);
                ivSelected = itemView.findViewById(R.id.ivSelected);
            }
        }
    }
}
