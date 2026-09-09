package com.mobile.novabox.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.mobile.novabox.R;
import com.mobile.novabox.util.SearchThreadHelper;

import org.jetbrains.annotations.NotNull;

/**
 * 搜索线程数设置弹窗（手机/平板通用，样式参照 DanmuFullSettingDialog）。
 *
 * 两个数值都可调，但真正决定搜索速度的是"线程池上限"：
 * - 线程池上限：能同时跑多少个源站搜索请求的硬上限，是搜索速度的关键瓶颈。
 * - 每批并发数：每次往线程池投递的源站数量，不建议超过线程池上限
 *   （SearchThreadHelper 写入时会自动收敛，避免出现"批次数 > 线程池上限"这种无效组合，
 *   因为超出部分提交到线程池会被直接拒绝，等下一轮重试，起不到加速效果）。
 *
 * 每次调整都会立即持久化（Hawk），下次搜索直接生效，无需重启 App；
 * 但对"正在进行中"的搜索不生效，因为线程池已经创建，调整只影响下一次发起的搜索。
 */
public class SearchThreadDialog extends BaseDialog {

    private TextView tvMaxValue;
    private TextView tvBatchValue;

    public SearchThreadDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_search_thread);
        setCanceledOnTouchOutside(true);

        TextView btnClose = findViewById(R.id.btnSearchThreadClose);
        if (btnClose != null) btnClose.setOnClickListener(v -> dismiss());

        initMaxThreadStepper();
        initBatchStepper();

        TextView btnReset = findViewById(R.id.btnSearchThreadReset);
        if (btnReset != null) {
            btnReset.setOnClickListener(v -> {
                SearchThreadHelper.resetToDefault();
                refreshValues();
            });
        }
    }

    private void initMaxThreadStepper() {
        View row = findViewById(R.id.stepperMaxThread);
        TextView tvMinus = row.findViewById(R.id.tvStepperMinus);
        TextView tvPlus = row.findViewById(R.id.tvStepperPlus);
        tvMaxValue = row.findViewById(R.id.tvStepperValue);

        tvMinus.setOnClickListener(v -> {
            int current = SearchThreadHelper.getMaxThreadCount();
            SearchThreadHelper.setMaxThreadCount(current - SearchThreadHelper.MAX_THREAD_STEP);
            refreshValues();
        });
        tvPlus.setOnClickListener(v -> {
            int current = SearchThreadHelper.getMaxThreadCount();
            SearchThreadHelper.setMaxThreadCount(current + SearchThreadHelper.MAX_THREAD_STEP);
            refreshValues();
        });
    }

    private void initBatchStepper() {
        View row = findViewById(R.id.stepperBatch);
        TextView tvMinus = row.findViewById(R.id.tvStepperMinus);
        TextView tvPlus = row.findViewById(R.id.tvStepperPlus);
        tvBatchValue = row.findViewById(R.id.tvStepperValue);

        tvMinus.setOnClickListener(v -> {
            int current = SearchThreadHelper.getBatchCount();
            SearchThreadHelper.setBatchCount(current - SearchThreadHelper.BATCH_STEP);
            refreshValues();
        });
        tvPlus.setOnClickListener(v -> {
            int current = SearchThreadHelper.getBatchCount();
            SearchThreadHelper.setBatchCount(current + SearchThreadHelper.BATCH_STEP);
            refreshValues();
        });
    }

    @Override
    public void show() {
        super.show();
        refreshValues();
        // 手机窄屏下尽量铺满窗口，平板限制在舒适宽度内；与 DanmuFullSettingDialog 保持一致的自适应逻辑。
        Context context = getContext();
        while (context instanceof android.content.ContextWrapper && !(context instanceof android.app.Activity)) {
            context = ((android.content.ContextWrapper) context).getBaseContext();
        }
        if (context instanceof android.app.Activity) {
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            ((android.app.Activity) context).getWindowManager().getDefaultDisplay().getMetrics(dm);
            int maxWidthPx = (int) (dm.density * 640);
            int widthPx = Math.min(dm.widthPixels, maxWidthPx);
            int heightPx = (int) (dm.heightPixels * 0.85f);
            if (getWindow() != null) getWindow().setLayout(widthPx, heightPx);
        }
    }

    private void refreshValues() {
        // 线程池上限可能因为用户刚调小、批次数被联动收敛，两个值都要重新读一遍再展示
        int maxThread = SearchThreadHelper.getMaxThreadCount();
        int batch = SearchThreadHelper.getBatchCount();
        if (tvMaxValue != null) tvMaxValue.setText(maxThread + "");
        if (tvBatchValue != null) tvBatchValue.setText(batch + "");
    }
}
