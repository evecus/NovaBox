package com.mobile.novabox.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.mobile.novabox.R;
import com.mobile.novabox.api.DanmakuApi;
import com.mobile.novabox.bean.DanmuSearchResult;
import com.mobile.novabox.ui.adapter.SearchDanmuAdapter;
import com.mobile.novabox.util.FastClickCheckUtil;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * “在线搜索”弹窗（从“弹幕设置”弹窗里的“在线搜索”入口打开）：
 * 输入名称 → 搜索 → 展示候选结果列表 → 点击某条即加载对应弹幕。
 * 白底黑字，右上角带 ✕ 关闭按钮，手机/平板通过 vs_960、vs_480 等尺寸资源自动适配，
 * 不需要单独的 sw600dp 布局（与本项目其它弹窗一致）。
 */
public class SearchDanmuDialog extends BaseDialog {
    private RecyclerView mGridView;
    private SearchDanmuAdapter searchAdapter;
    private EditText searchInput;
    private ProgressBar loadingBar;
    private DanmuLoader danmuLoader;
    private String episode = "";

    public SearchDanmuDialog(@NonNull @NotNull Context context) {
        super(context);
        if (context instanceof Activity) {
            setOwnerActivity((Activity) context);
        }
        setContentView(R.layout.dialog_search_danmu);
        setCanceledOnTouchOutside(true);
        initView();
    }

    @Override
    public void show() {
        super.show();
        // 与其它设置弹窗保持一致：手机窄屏尽量铺满，平板限制在舒适宽度内
        Context context = getContext();
        while (context instanceof ContextWrapper && !(context instanceof Activity)) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        if (context instanceof Activity) {
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            ((Activity) context).getWindowManager().getDefaultDisplay().getMetrics(dm);
            int maxWidthPx = (int) (dm.density * 640);
            int widthPx = Math.min(dm.widthPixels, maxWidthPx);
            int heightPx = (int) (dm.heightPixels * 0.85f);
            getWindow().setLayout(widthPx, heightPx);
        }
    }

    private void initView() {
        loadingBar = findViewById(R.id.loadingBar);
        mGridView = findViewById(R.id.mGridView);
        searchInput = findViewById(R.id.input);
        TextView searchButton = findViewById(R.id.inputSubmit);
        TextView btnClose = findViewById(R.id.btnSearchDanmuClose);
        if (btnClose != null) btnClose.setOnClickListener(v -> dismiss());

        searchAdapter = new SearchDanmuAdapter();
        mGridView.setHasFixedSize(true);
        mGridView.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.VERTICAL, false));
        mGridView.setAdapter(searchAdapter);
        searchAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                loadDanmu(searchAdapter.getData().get(position));
            }
        });
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                search(searchInput.getText().toString().trim());
            }
        });
        searchAdapter.setNewData(new ArrayList<DanmuSearchResult>());
    }

    public void setEpisode(String episode) {
        this.episode = episode == null ? "" : episode;
    }

    public void setSearchWord(String word) {
        String searchWord = word == null ? "" : word.trim();
        searchInput.setText(searchWord);
        searchInput.setSelection(searchWord.length());
        searchInput.requestFocus();
        search(searchWord);
    }

    public void setDanmuLoader(DanmuLoader danmuLoader) {
        this.danmuLoader = danmuLoader;
    }

    private void search(String word) {
        searchAdapter.setNewData(new ArrayList<DanmuSearchResult>());
        if (TextUtils.isEmpty(word)) {
            Toast.makeText(getContext(), "输入内容不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        showLoading();
        DanmakuApi.searchList(word, episode, new DanmakuApi.SearchListCallback() {
            @Override
            public void onSuccess(List<DanmuSearchResult> results) {
                showResults(results);
            }

            @Override
            public void onError(String message) {
                showResults(new ArrayList<DanmuSearchResult>());
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadDanmu(DanmuSearchResult result) {
        showLoading();
        DanmakuApi.loadSearchResult(result, new DanmakuApi.SearchResultCallback() {
            @Override
            public void onSuccess(String danmu) {
                if (danmuLoader != null) danmuLoader.loadDanmu(danmu);
                dismiss();
            }

            @Override
            public void onError(String message) {
                loadingBar.setVisibility(View.GONE);
                mGridView.setVisibility(View.VISIBLE);
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showLoading() {
        loadingBar.setVisibility(View.VISIBLE);
        mGridView.setVisibility(View.GONE);
    }

    private void showResults(List<DanmuSearchResult> results) {
        if (results == null) results = new ArrayList<>();
        loadingBar.setVisibility(View.GONE);
        mGridView.setVisibility(View.VISIBLE);
        searchAdapter.setNewData(results);
        if (results.isEmpty()) {
            Toast.makeText(getContext(), "未查询到匹配弹幕", Toast.LENGTH_SHORT).show();
            return;
        }
        mGridView.requestFocus();
    }

    public interface DanmuLoader {
        void loadDanmu(String danmu);
    }
}
