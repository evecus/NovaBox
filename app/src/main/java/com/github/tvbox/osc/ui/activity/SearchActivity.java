package com.mobile.novabox.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.catvod.crawler.JsLoader;
import com.mobile.novabox.R;
import com.mobile.novabox.api.ApiConfig;
import com.mobile.novabox.base.BaseActivity;
import com.mobile.novabox.bean.AbsXml;
import com.mobile.novabox.bean.Movie;
import com.mobile.novabox.bean.SourceBean;
import com.mobile.novabox.event.RefreshEvent;
import com.mobile.novabox.event.ServerEvent;
import com.mobile.novabox.ui.adapter.PinyinAdapter;
import com.mobile.novabox.ui.adapter.SearchAdapter;
import com.mobile.novabox.ui.dialog.RemoteDialog;
import com.mobile.novabox.ui.dialog.SearchCheckboxDialog;
import com.mobile.novabox.util.FastClickCheckUtil;
import com.mobile.novabox.util.HawkConfig;
import com.mobile.novabox.util.HistoryHelper;
import com.mobile.novabox.util.SearchHelper;
import com.mobile.novabox.viewmodel.SourceViewModel;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class SearchActivity extends BaseActivity {
    private static final String HOT_SEARCH_URL = "https://movie.douban.com/j/search_subjects?type=tv&tag=%E7%83%AD%E9%97%A8&sort=recommend&page_limit=20&page_start=0";
    private static final int SEARCH_THREAD_COUNT = 6;
    private static final int SEARCH_MAX_THREAD_COUNT = Build.VERSION.SDK_INT >= 30 ? 18 : 12;
    private static final int SEARCH_NEXT_BATCH_SECONDS = 3;
    private static final int SEARCH_SITE_TIMEOUT_SECONDS = 10;
    private static final String[] DEFAULT_HOT_WORDS = {
            "\u5bb6\u4e1a",
            "\u4e3b\u89d2",
            "\u4f4e\u667a\u5546\u72af\u7f6a",
            "\u82cf\u8d85",
            "\u4e66\u5377\u4e00\u68a6",
            "\u7f8e\u4eba\u4f59",
            "\u85cf\u6d77\u4f20",
            "\u957f\u5b89\u7684\u8354\u679d",
            "\u5e86\u4f59\u5e74",
            "\u51e1\u4eba\u4fee\u4ed9\u4f20"
    };
    private LinearLayout llLayout;
    private LinearLayout llHistoryWord;
    private LinearLayout llHotGrid;          // 已废弃，保留防编译错（新布局无此 id）
    private View llPadSearchHome;             // Pad 聚合模式首页容器（sw600dp LinearLayout）
    private RecyclerView mGridView;
    private RecyclerView mGridViewWord;
    private RecyclerView mGridViewHotPad;    // 已废弃，保留防编译错（新布局无此 id）
    private GridLayout historyWordGrid;
    SourceViewModel sourceViewModel;
    private RemoteDialog remoteDialog;
    private EditText etSearch;
    private View tvSearch;
    private TextView tvClear;
    private ImageView tvHistoryClear;
    private SearchAdapter searchAdapter;
    private PinyinAdapter wordAdapter;
    private PinyinAdapter hotWordAdapter;
    private String searchTitle = "";
    private TextView tvSearchCheckboxBtn;

    private static HashMap<String, String> mCheckSources = null;
    private SearchCheckboxDialog mSearchCheckboxDialog = null;

    private TextView wordsSwitch;
    private boolean aggregateSearchMode;
    private boolean aggregateSearchModeInited = false;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_search;
    }


    private static Boolean hasKeyBoard;
    private static Boolean isSearchBack;
    @Override
    protected void init() {
        initView();
        initViewModel();
        initData();
        hasKeyBoard = true;
        isSearchBack = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (searchPaused) {
            resumePausedSearches();
        }
        requestSearchFocusWhenReady();
        applySearchWordMode();
        if (aggregateSearchMode) {
            refreshSearchHistoryWords();
            if (hots != null && !hots.isEmpty()) {
                hotWordAdapter.setNewData(hots);
            }
        }
    }

    private void requestSearchFocusWhenReady() {
        final View focusView = hasKeyBoard || isSearchBack ? tvSearch : etSearch;
        if (focusView == null) return;
        focusView.post(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) return;
                focusView.requestFocus();
                focusView.requestFocusFromTouch();
            }
        });
    }

    private void initView() {
        EventBus.getDefault().register(this);
        llLayout = findViewById(R.id.llLayout);
        llHistoryWord = findViewById(R.id.llHistoryWord);
        etSearch = findViewById(R.id.etSearch);
        tvSearch = findViewById(R.id.tvSearch);
        tvSearchCheckboxBtn = findViewById(R.id.tvSearchCheckboxBtn);
        tvClear = findViewById(R.id.tvClear);
        mGridView = findViewById(R.id.mGridView);
        mGridViewWord = findViewById(R.id.mGridViewWord);
        historyWordGrid = findViewById(R.id.historyWordGrid);
        tvHistoryClear = findViewById(R.id.tvHistoryClear);
        // Pad 聚合模式首页容器（仅 sw600dp 布局存在该 id，手机布局返回 null）
        llHotGrid = null;
        mGridViewHotPad = null;
        llPadSearchHome = findViewById(R.id.llPadSearchHome);
        // 返回键
        View ivBack = findViewById(R.id.ivBack);
        if (ivBack != null) {
            ivBack.setOnClickListener(v -> onBackPressed());
        }
        mGridViewWord.setHasFixedSize(true);
        wordAdapter = new PinyinAdapter();
        hotWordAdapter = new PinyinAdapter();
        // Pad 模式下，mGridViewWord 用于热门搜索多列宫格（GridLayoutManager）
        // 手机模式在 applySearchWordMode 中统一用 LinearLayoutManager
        wordsSwitch = findViewById(R.id.wordSwitch);
        applySearchWordMode();
        wordAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                startSearch(wordAdapter.getItem(position));
            }
        });
        hotWordAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                startSearch(hotWordAdapter.getItem(position));
            }
        });
        mGridView.setHasFixedSize(true);
        // lite 模式手机用单列，Pad 始终多列；非 lite 模式按 PadUiHelper 列数
        if (Hawk.get(HawkConfig.SEARCH_VIEW, 0) == 0) {
            if (com.mobile.novabox.util.PadUiHelper.isPad(this)) {
                mGridView.setLayoutManager(new GridLayoutManager(this.mContext, com.mobile.novabox.util.PadUiHelper.getSearchResultSpanCount(this)));
            } else {
                mGridView.setLayoutManager(new LinearLayoutManager(this.mContext, 1, false));
            }
        } else {
            mGridView.setLayoutManager(new GridLayoutManager(this.mContext, com.mobile.novabox.util.PadUiHelper.getSearchResultSpanCount(this)));
        }
        searchAdapter = new SearchAdapter();
        mGridView.setAdapter(searchAdapter);
        searchAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                Movie.Video video = searchAdapter.getData().get(position);
                if (video != null) {
                    pauseSearchTasks();
                    hasKeyBoard = false;
                    isSearchBack = true;
                    Bundle bundle = new Bundle();
                    bundle.putString("id", video.id);
                    bundle.putString("sourceKey", video.sourceKey);
                    bundle.putString("title", video.name);
                    bundle.putString("picture", video.pic);
                    jumpActivity(DetailActivity.class, bundle);
                }
            }
        });
        wordsSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (aggregateSearchMode) {
                    return;
                }
                FastClickCheckUtil.check(v);
                String wd = wordsSwitch.getText().toString().trim();
                if(wd.contains("热词")){
                    ArrayList<String> hisWord= Hawk.get(HawkConfig.SEARCH_HISTORY, new ArrayList<String>());
                    if (hisWord.isEmpty()){
                        Toast.makeText(mContext, "暂无历史搜索", Toast.LENGTH_SHORT).show();
                    }else {
                        wordsSwitch.setText("历史 搜索");
                        wordAdapter.setNewData(hisWord);
                    }
                }
                if(wd.equals("历史 搜索")){
                    wordsSwitch.setText("热词 搜索");
                    if(hots!=null && !hots.isEmpty()){
                        wordAdapter.setNewData(hots);
                    }
                }
            }
        });
        tvHistoryClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                HistoryHelper.clearSearchHistory();
                refreshSearchHistoryWords();
                Toast.makeText(mContext, "已清空搜索历史", Toast.LENGTH_SHORT).show();
            }
        });
        tvSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                hasKeyBoard = true;
                String wd = etSearch.getText().toString().trim();
                if (!TextUtils.isEmpty(wd)) {
                    if(Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)){
                        Bundle bundle = new Bundle();
                        bundle.putString("title", wd);
                        jumpActivity(FastSearchActivity.class, bundle);
                    }else {
                        search(wd);
                    }
                } else {
                    Toast.makeText(mContext, "输入内容不能为空", Toast.LENGTH_SHORT).show();
                }
            }
        });
        tvClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                initData();
                etSearch.setText("");
            }
        });

        //软键盘

        etSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    String wd = etSearch.getText().toString().trim();
                    if (!TextUtils.isEmpty(wd)) {
                        if (Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)) {
                            Bundle bundle = new Bundle();
                            bundle.putString("title", wd);
                            jumpActivity(FastSearchActivity.class, bundle);
                        } else {
                            hiddenImm();
                            search(wd);
                        }
                    } else {
                        Toast.makeText(mContext, "输入内容不能为空", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                return false;
            }
        });

        // 监听遥控器
        etSearch.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                    String wd = etSearch.getText().toString().trim();
                    if (!TextUtils.isEmpty(wd)) {
                        if (Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)) {
                            Bundle bundle = new Bundle();
                            bundle.putString("title", wd);
                            jumpActivity(FastSearchActivity.class, bundle);
                        } else {
                            hiddenImm();
                            search(wd);
                        }
                    } else {
                        Toast.makeText(mContext, "输入内容不能为空", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                return false;
            }
        });
        setLoadSir(llLayout);
        tvSearchCheckboxBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                List<SourceBean> searchAbleSource = ApiConfig.get().getSearchSourceBeanList();
                if (mSearchCheckboxDialog == null) {
                    mSearchCheckboxDialog = new SearchCheckboxDialog(SearchActivity.this, searchAbleSource, mCheckSources);
                }else {
                    if(searchAbleSource.size()!=mSearchCheckboxDialog.mSourceList.size()){
                        mSearchCheckboxDialog.setMSourceList(searchAbleSource);
                    }
                }
                mSearchCheckboxDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        dialog.dismiss();
                    }
                });
                mSearchCheckboxDialog.show();
            }
        });
    }

    private void startSearch(String wd) {
        if (TextUtils.isEmpty(wd)) {
            return;
        }
        if (Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)) {
            Bundle bundle = new Bundle();
            bundle.putString("title", wd);
            jumpActivity(FastSearchActivity.class, bundle);
        } else {
            search(wd);
        }
    }

    private boolean isAggregateSearchMode() {
        return Hawk.get(HawkConfig.FAST_SEARCH_MODE, true);
    }

    private void setAggregateHotTitle() {
        wordsSwitch.setText("热门搜索");
        wordsSwitch.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.ts_22));
        wordsSwitch.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            wordsSwitch.setLetterSpacing(0f);
        }
    }

    private void setNormalWordTitle() {
        wordsSwitch.setText("热词 | 历史");
        wordsSwitch.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.ts_20));
        wordsSwitch.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            wordsSwitch.setLetterSpacing(0f);
        }
    }

    private void applySearchWordMode() {
        boolean aggregateMode = isAggregateSearchMode();
        if (aggregateSearchModeInited && aggregateSearchMode == aggregateMode) {
            return;
        }
        aggregateSearchModeInited = true;
        aggregateSearchMode = aggregateMode;
        if (aggregateSearchMode) {
            // Pad：显示左右分栏首页（llPadSearchHome），隐藏结果区
            if (llPadSearchHome != null) {
                llPadSearchHome.setVisibility(View.VISIBLE);
                llLayout.setVisibility(View.GONE);
                mGridView.setVisibility(View.GONE);
            } else {
                // 手机保持原逻辑
                llHistoryWord.setVisibility(View.VISIBLE);
                llLayout.setVisibility(View.GONE);
                mGridView.setVisibility(View.GONE);
            }
            setAggregateHotTitle();
            wordsSwitch.setFocusable(false);
            wordsSwitch.setBackground(null);
            // Pad 左栏热门搜索：3列网格；手机改为2列网格
            if (llPadSearchHome != null) {
                mGridViewWord.setLayoutManager(new GridLayoutManager(this.mContext, 3));
            } else {
                mGridViewWord.setLayoutManager(new GridLayoutManager(this.mContext, 2));
            }
            mGridViewWord.setAdapter(hotWordAdapter);
            refreshSearchHistoryWords();
        } else {
            // 手机 / Pad 搜索结果页
            if (llPadSearchHome != null) {
                llPadSearchHome.setVisibility(View.GONE);
            } else {
                llHistoryWord.setVisibility(View.GONE);
            }
            llLayout.setVisibility(View.VISIBLE);
            if (mGridView.getVisibility() == View.GONE) {
                mGridView.setVisibility(View.INVISIBLE);
            }
            setNormalWordTitle();
            wordsSwitch.setFocusable(true);
            wordsSwitch.setBackgroundResource(R.drawable.shape_user_focus);
            mGridViewWord.setLayoutManager(new LinearLayoutManager(this.mContext, 1, false));
            mGridViewWord.setAdapter(wordAdapter);
        }
    }

    private void setHotWordsData(ArrayList<String> data) {
        if (aggregateSearchMode) {
            hotWordAdapter.setNewData(data);
        } else {
            wordAdapter.setNewData(data);
        }
    }

    private void refreshSearchHistoryWords() {
        ArrayList<String> history = Hawk.get(HawkConfig.SEARCH_HISTORY, new ArrayList<String>());
        historyWordGrid.removeAllViews();
        // Pad 右栏历史搜索固定3列；手机维持3列
        int histCols = 3;
        historyWordGrid.setColumnCount(histCols);
        int itemHeight = getResources().getDimensionPixelSize(R.dimen.vs_50);
        int itemMargin = getResources().getDimensionPixelSize(R.dimen.vs_5);
        int paddingH = getResources().getDimensionPixelSize(R.dimen.vs_10);
        int maxWidth = getResources().getDimensionPixelSize(R.dimen.vs_220);
        float textSize = getResources().getDimension(R.dimen.ts_22);
        int textColor = getResources().getColor(R.color.color_000000);
        for (int i = 0; i < history.size(); i++) {
            final String word = history.get(i);
            TextView item = new TextView(this);
            item.setText(word);
            item.setSingleLine(true);
            item.setEllipsize(TextUtils.TruncateAt.END);
            item.setGravity(Gravity.CENTER);
            item.setIncludeFontPadding(false);
            item.setFocusable(true);
            item.setTextColor(textColor);
            item.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
            item.setMaxWidth(maxWidth);
            item.setMinWidth(getResources().getDimensionPixelSize(R.dimen.vs_80));
            item.setPadding(paddingH, 0, paddingH, 0);
            item.setBackgroundResource(R.drawable.shape_search_word_bg);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startSearch(word);
                }
            });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(i / histCols),
                    GridLayout.spec(i % histCols)
            );
            params.width = GridLayout.LayoutParams.WRAP_CONTENT;
            params.height = itemHeight;
            params.setMargins(itemMargin, itemMargin, itemMargin, itemMargin);
            historyWordGrid.addView(item, params);
        }
        // Pad 右栏：有历史才显示整列；手机同样控制 llHistoryWord
        boolean hasHistory = !history.isEmpty();
        llHistoryWord.setVisibility(hasHistory ? View.VISIBLE : View.GONE);
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
    }

    /**
     * 拼音联想
     */
    private void loadRec(String key) {
        OkGo.get("https://tv.aiseet.atianqi.com/i-tvbin/qtv_video/search/get_search_smart_box")
                .params("format", "json")
                .params("page_num", 0)
                .params("page_size", 20)
                .params("key", key)
                .execute(new AbsCallback() {
                    @Override
                    public void onSuccess(Response response) {
                        try {
                            ArrayList hots = new ArrayList<>();
                            String result = (String) response.body();
                            Gson gson = new Gson();
                            JsonElement json = gson.fromJson(result, JsonElement.class);
                            JsonArray groupDataArr = json.getAsJsonObject()
                                    .get("data").getAsJsonObject()
                                    .get("search_data").getAsJsonObject()
                                    .get("vecGroupData").getAsJsonArray()
                                    .get(0).getAsJsonObject()
                                    .get("group_data").getAsJsonArray();
                            for (JsonElement groupDataElement : groupDataArr) {
                                JsonObject groupData = groupDataElement.getAsJsonObject();
                                String keywordTxt = groupData.getAsJsonObject("dtReportInfo")
                                        .getAsJsonObject("reportData")
                                        .get("keyword_txt").getAsString();
                                hots.add(keywordTxt.trim());
                            }
                            wordsSwitch.setText("猜你 想搜");
                            setHotWordsData(hots);
                            mGridViewWord.smoothScrollToPosition(0);
                        } catch (Throwable th) {
                            th.printStackTrace();
                        }
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body().string();
                    }
                });
    }

    private static ArrayList<String> hots;
    private static boolean hotWordsRequested;

    private void useDefaultHotWords() {
        ArrayList<String> data = new ArrayList<>();
        for (String word : DEFAULT_HOT_WORDS) {
            data.add(word);
        }
        cacheHotWords(data);
    }

    private void cacheHotWords(ArrayList<String> data) {
        hots = data;
        setHotWordsData(hots);
    }

    private String cleanHotWord(String title) {
        if (TextUtils.isEmpty(title)) return "";
        return title.trim().replaceAll("<|>|《|》|-", "").split(" ")[0];
    }

    private void addHotWord(ArrayList<String> data, String title) {
        String word = cleanHotWord(title);
        if (!TextUtils.isEmpty(word) && !data.contains(word)) {
            data.add(word);
        }
    }

    private void initData() {
        initCheckedSourcesForSearch();
        applySearchWordMode();
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("title")) {
            String title = intent.getStringExtra("title");
            showLoading();
            if(Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)){
                Bundle bundle = new Bundle();
                bundle.putString("title", title);
                jumpActivity(FastSearchActivity.class, bundle);
            }else {
                search(title);
            }
        }
        if (aggregateSearchMode) {
            setAggregateHotTitle();
            refreshSearchHistoryWords();
        } else {
            setNormalWordTitle();
        }
        if(hots!=null && !hots.isEmpty()){
            setHotWordsData(hots);
            return;
        }
        if (hotWordsRequested) {
            return;
        }
        hotWordsRequested = true;
        // 加载热词
        OkGo.<String>get(HOT_SEARCH_URL)
//        OkGo.<String>get("https://api.web.360kan.com/v1/rank")
//                .params("cat", "1")
                .headers("User-Agent", "Mozilla/5.0")
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            ArrayList<String> data = new ArrayList<String>();
                            JsonArray itemList = JsonParser.parseString(response.body()).getAsJsonObject().get("subjects").getAsJsonArray();
//                            JsonArray itemList = JsonParser.parseString(response.body()).getAsJsonObject().get("data").getAsJsonArray();
                            for (JsonElement ele : itemList) {
                                JsonObject obj = (JsonObject) ele;
                                if (obj.has("title")) {
                                    addHotWord(data, obj.get("title").getAsString());
                                }
                            }
                            if (data.isEmpty()) {
                                useDefaultHotWords();
                                return;
                            }
                            cacheHotWords(data);
                        } catch (Throwable th) {
                            th.printStackTrace();
                            useDefaultHotWords();
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        useDefaultHotWords();
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body().string();
                    }
                });

    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void server(ServerEvent event) {
        if (event.type == ServerEvent.SERVER_SEARCH) {
            String title = (String) event.obj;
            showLoading();
            if(Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)){
                Bundle bundle = new Bundle();
                bundle.putString("title", title);
                jumpActivity(FastSearchActivity.class, bundle);
            }else{
                search(title);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_SEARCH_RESULT) {
            try {
                searchData(event.obj == null ? null : (AbsXml) event.obj);
            } catch (Exception e) {
                searchData(null);
            }
        }
    }

    private void initCheckedSourcesForSearch() {
        mCheckSources = SearchHelper.getSourcesForSearch();
    }

    public static void setCheckedSourcesForSearch(HashMap<String,String> checkedSources) {
        mCheckSources = checkedSources;
    }

    private void search(String title) {
        cancel();
        if (remoteDialog != null) {
            remoteDialog.dismiss();
            remoteDialog = null;
        }
        showLoading();
        etSearch.setText(title);

        //写入历史记录
        HistoryHelper.setSearchHistory(title);


        this.searchTitle = title;
        mGridView.setVisibility(View.INVISIBLE);
        searchAdapter.setNewData(new ArrayList<>());
        searchResult();
    }

    private ExecutorService searchExecutorService = null;
    private ScheduledExecutorService searchTimeoutExecutor = null;
    private AtomicInteger allRunCount = new AtomicInteger(0);
    private final Set<String> pendingSearchKeys = Collections.synchronizedSet(new HashSet<String>());
    private final List<SearchTask> waitingSearchTasks = Collections.synchronizedList(new ArrayList<SearchTask>());
    private final Set<String> startedSearchKeys = Collections.synchronizedSet(new HashSet<String>());
    private final Set<String> releasedSearchKeys = Collections.synchronizedSet(new HashSet<String>());
    private final AtomicInteger searchTokenSeq = new AtomicInteger(0);
    private String currentSearchToken = "";
    private boolean searchPaused = false;

    private void searchResult() {
        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
                JsLoader.stopAll();
            }
            if (searchTimeoutExecutor != null) {
                searchTimeoutExecutor.shutdownNow();
                searchTimeoutExecutor = null;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        } finally {
            searchAdapter.setNewData(new ArrayList<>());
            allRunCount.set(0);
            pendingSearchKeys.clear();
            waitingSearchTasks.clear();
            startedSearchKeys.clear();
            releasedSearchKeys.clear();
            currentSearchToken = String.valueOf(searchTokenSeq.incrementAndGet());
            searchPaused = false;
        }
        List<SourceBean> searchRequestList = new ArrayList<>();
        searchRequestList.addAll(ApiConfig.get().getSourceBeanList());
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        searchRequestList.remove(home);
        searchRequestList.add(0, home);

        ArrayList<SearchTask> searchTasks = new ArrayList<>();
        for (SourceBean bean : searchRequestList) {
            if (!bean.isSearchable()) {
                continue;
            }
            if (mCheckSources != null && !mCheckSources.containsKey(bean.getKey())) {
                continue;
            }
            searchTasks.add(new SearchTask(bean.getKey(), searchTitle, currentSearchToken, isBlockingSearchSource(bean)));
        }
        if (searchTasks.size() <= 0) {
            Toast.makeText(mContext, "没有指定搜索源", Toast.LENGTH_SHORT).show();
            showEmpty();
            return;
        }
        for (SearchTask task : searchTasks) {
            pendingSearchKeys.add(task.sourceKey);
        }
        allRunCount.set(searchTasks.size());
        searchExecutorService = createSearchExecutor();
        searchTimeoutExecutor = Executors.newSingleThreadScheduledExecutor();
        startFastSearchTasks(searchTasks);
        waitingSearchTasks.addAll(searchTasks);
        startNextSearchBatch(currentSearchToken);
    }

    private boolean matchSearchResult(String name, String searchTitle) {
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(searchTitle)) return false;
        searchTitle = searchTitle.trim();
        String[] arr = searchTitle.split("\\s+");
        int matchNum = 0;
        for(String one : arr) {
            if (name.contains(one)) matchNum++;
        }
        return matchNum == arr.length ? true : false;
    }

    private void searchData(AbsXml absXml) {
        if (!isCurrentSearchResult(absXml)) {
            return;
        }
        String sourceKey = absXml == null ? "" : absXml.sourceKey;
        if (!markSearchFinished(sourceKey, absXml.searchToken)) {
            return;
        }
        releaseSearchSlotAndStartNext(sourceKey, absXml.searchToken);
        if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
            List<Movie.Video> data = new ArrayList<>();
            for (Movie.Video video : absXml.movie.videoList) {
                if (matchSearchResult(video.name, searchTitle)) data.add(video);
            }
            if (searchAdapter.getData().size() > 0) {
                searchAdapter.addData(data);
            } else {
                showSuccess();
                mGridView.setVisibility(View.VISIBLE);
                searchAdapter.setNewData(data);
            }
        }

        finishSearchIfDone();
    }

    private void scheduleSearchAdvance(final String sourceKey, final String searchToken) {
        if (searchTimeoutExecutor == null) return;
        searchTimeoutExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                if (!isCurrentSearchToken(searchToken)) return;
                if (isSearchPending(sourceKey, searchToken) && releaseSearchSlot(sourceKey, searchToken)) {
                    startNextSearchTask(searchToken);
                }
            }
        }, SEARCH_NEXT_BATCH_SECONDS, TimeUnit.SECONDS);
    }

    private void scheduleSearchTimeout(final String sourceKey, final String searchToken) {
        if (searchTimeoutExecutor == null) return;
        searchTimeoutExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                if (!isCurrentSearchToken(searchToken)) return;
                if (markSearchFinished(sourceKey, searchToken)) {
                    releaseSearchSlotAndStartNext(sourceKey, searchToken);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            finishSearchIfDone();
                        }
                    });
                }
            }
        }, SEARCH_SITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private boolean submitSearchTask(SearchTask task) {
        if (!isSearchPending(task.sourceKey, task.searchToken)) return false;
        if (searchExecutorService == null || searchExecutorService.isShutdown()) return false;
        try {
            searchExecutorService.execute(task);
        } catch (RejectedExecutionException e) {
            return false;
        }
        scheduleSearchAdvance(task.sourceKey, task.searchToken);
        scheduleSearchTimeout(task.sourceKey, task.searchToken);
        return true;
    }

    private ExecutorService createSearchExecutor() {
        return new ThreadPoolExecutor(0, SEARCH_MAX_THREAD_COUNT, 30L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
    }

    private void startNextSearchBatch(String searchToken) {
        for (int i = 0; i < SEARCH_THREAD_COUNT; i++) {
            if (!startNextSearchTask(searchToken)) {
                return;
            }
        }
    }

    private boolean startNextSearchTask(String searchToken) {
        if (!isCurrentSearchToken(searchToken)) return false;
        SearchTask task = takeNextSearchTask(searchToken);
        if (task == null) {
            return false;
        }
        if (!submitSearchTask(task)) {
            startedSearchKeys.remove(task.sourceKey);
            synchronized (waitingSearchTasks) {
                waitingSearchTasks.add(0, task);
            }
            return false;
        }
        return true;
    }

    private SearchTask takeNextSearchTask(String searchToken) {
        synchronized (waitingSearchTasks) {
            while (!waitingSearchTasks.isEmpty()) {
                SearchTask task = waitingSearchTasks.remove(0);
                if (!isSearchPending(task.sourceKey, searchToken) || !startedSearchKeys.add(task.sourceKey)) {
                    continue;
                }
                return task;
            }
        }
        return null;
    }

    private void resumePausedSearches() {
        if (!searchPaused) {
            return;
        }
        searchPaused = false;
        List<String> sourceKeys = getPendingSearchKeys();
        if (sourceKeys.isEmpty()) {
            finishSearchIfDone();
            return;
        }
        currentSearchToken = String.valueOf(searchTokenSeq.incrementAndGet());
        waitingSearchTasks.clear();
        startedSearchKeys.clear();
        releasedSearchKeys.clear();
        for (String sourceKey : sourceKeys) {
            SourceBean bean = ApiConfig.get().getSource(sourceKey);
            waitingSearchTasks.add(new SearchTask(sourceKey, searchTitle, currentSearchToken, isBlockingSearchSource(bean)));
        }
        if (searchExecutorService == null || searchExecutorService.isShutdown()) {
            searchExecutorService = createSearchExecutor();
        }
        if (searchTimeoutExecutor == null || searchTimeoutExecutor.isShutdown()) {
            searchTimeoutExecutor = Executors.newSingleThreadScheduledExecutor();
        }
        startNextSearchBatch(currentSearchToken);
    }

    private void pauseSearchTasks() {
        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
                JsLoader.stopAll();
            }
            if (searchTimeoutExecutor != null) {
                searchTimeoutExecutor.shutdownNow();
                searchTimeoutExecutor = null;
            }
            searchPaused = allRunCount.get() > 0;
            if (searchPaused) {
                cancel();
                currentSearchToken = "";
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private boolean isCurrentSearchResult(AbsXml absXml) {
        return absXml != null && isCurrentSearchToken(absXml.searchToken);
    }

    private boolean isCurrentSearchToken(String searchToken) {
        return !TextUtils.isEmpty(searchToken) && searchToken.equals(currentSearchToken);
    }

    private boolean markSearchFinished(String sourceKey, String searchToken) {
        if (!isCurrentSearchToken(searchToken)) return false;
        synchronized (pendingSearchKeys) {
            if (TextUtils.isEmpty(sourceKey)) {
                return false;
            }
            if (!pendingSearchKeys.remove(sourceKey)) {
                return false;
            }
            allRunCount.set(pendingSearchKeys.size());
            return true;
        }
    }

    private boolean releaseSearchSlot(String sourceKey, String searchToken) {
        if (!isCurrentSearchToken(searchToken) || TextUtils.isEmpty(sourceKey)) return false;
        return releasedSearchKeys.add(sourceKey);
    }

    private void releaseSearchSlotAndStartNext(String sourceKey, String searchToken) {
        if (releaseSearchSlot(sourceKey, searchToken)) {
            startNextSearchTask(searchToken);
        }
    }

    private boolean isSearchPending(String sourceKey, String searchToken) {
        if (!isCurrentSearchToken(searchToken) || TextUtils.isEmpty(sourceKey)) return false;
        synchronized (pendingSearchKeys) {
            return pendingSearchKeys.contains(sourceKey);
        }
    }

    private boolean isBlockingSearchSource(SourceBean bean) {
        return bean == null || bean.getType() == 3;
    }

    private void startFastSearchTasks(List<SearchTask> tasks) {
        for (SearchTask task : tasks) {
            if (task.blocking) {
                continue;
            }
            if (startedSearchKeys.add(task.sourceKey)) {
                submitDirectSearchTask(task);
            }
        }
    }

    private void submitDirectSearchTask(SearchTask task) {
        if (!isSearchPending(task.sourceKey, task.searchToken)) return;
        scheduleSearchTimeout(task.sourceKey, task.searchToken);
        try {
            sourceViewModel.getSearch(task.sourceKey, task.title, task.searchToken);
        } catch (Throwable th) {
            th.printStackTrace();
            if (markSearchFinished(task.sourceKey, task.searchToken)) {
                finishSearchIfDone();
            }
        }
    }

    private List<String> getPendingSearchKeys() {
        synchronized (pendingSearchKeys) {
            return new ArrayList<>(pendingSearchKeys);
        }
    }

    private void finishSearchIfDone() {
        if (allRunCount.get() > 0) return;
        searchPaused = false;
        if (searchAdapter.getData().size() <= 0) {
            showEmpty();
        }
        cancel();
        if (searchTimeoutExecutor != null) {
            searchTimeoutExecutor.shutdownNow();
            searchTimeoutExecutor = null;
        }
    }

    private class SearchTask implements Runnable {
        private final String sourceKey;
        private final String title;
        private final String searchToken;
        private final boolean blocking;

        private SearchTask(String sourceKey, String title, String searchToken, boolean blocking) {
            this.sourceKey = sourceKey;
            this.title = title;
            this.searchToken = searchToken;
            this.blocking = blocking;
        }

        @Override
        public void run() {
            if (!isSearchPending(sourceKey, searchToken)) return;
            try {
                sourceViewModel.getSearch(sourceKey, title, searchToken);
            } catch (Throwable th) {
                th.printStackTrace();
                if (markSearchFinished(sourceKey, searchToken)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            finishSearchIfDone();
                        }
                    });
                }
            }
        }
    }


    private void cancel() {
        OkGo.getInstance().cancelTag("search");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancel();
        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
                JsLoader.stopAll();
            }
            if (searchTimeoutExecutor != null) {
                searchTimeoutExecutor.shutdownNow();
                searchTimeoutExecutor = null;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        EventBus.getDefault().unregister(this);
    }

    private void hiddenImm()
    {
        InputMethodManager imm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
        }
    }
}
