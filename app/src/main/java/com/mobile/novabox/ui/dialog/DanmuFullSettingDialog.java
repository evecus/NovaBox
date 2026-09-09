package com.mobile.novabox.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.mobile.novabox.R;
import com.mobile.novabox.event.RefreshEvent;
import com.mobile.novabox.util.DanmuHelper;

import org.greenrobot.eventbus.EventBus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 弹幕设置弹窗（对齐 NovaTV 的单页纵向列表样式）。
 * 单页展示全部设置项：开关/颜色/速度为选项 chips，字号/行数/透明度/行间距/顶边距为
 * "－ 数值 ＋" 步进器，取值区间与步长与 NovaTV 完全一致。
 * 与 NovaTV 唯一的交互差异：手机是触屏操作，弹窗保留右上角 ✕ 关闭按钮
 * （NovaTV 是遥控器返回键关闭，不需要这个按钮）；背景、文字颜色也保持 NovaBox 原有样式不变。
 * “行数”替代了原来的“屏占比”：弹幕轨道固定铺满播放器（MATCH_PARENT），显示区域完全由
 * 行数直接决定，不再通过屏占比换算，逻辑详见 DanmuLoadController。
 */
public class DanmuFullSettingDialog extends BaseDialog {

    private static final List<Float> SPEEDS = Arrays.asList(2.4f, 1.8f, 1.5f, 1.0f);
    private static final String[] SPEED_NAMES = {"超慢", "慢", "适中", "快"};
    private static final String[] SWITCH_NAMES = {"开启", "关闭"};

    private static final List<Boolean> COLOR_VALUES = Arrays.asList(false, true);
    private static final String[] COLOR_NAMES = {"默认", "随机"};

    // 字号：0.4x ~ 2.4x，步长 0.1x
    private static final float SIZE_MIN = 0.4f;
    private static final float SIZE_MAX = 2.4f;
    private static final float SIZE_STEP = 0.1f;

    // 行数：1 ~ 30，步长 1
    private static final int LINE_MIN = 1;
    private static final int LINE_MAX = 30;
    private static final int LINE_STEP = 1;

    // 透明度：0.1 ~ 1.0，步长 0.1
    private static final float ALPHA_MIN = 0.1f;
    private static final float ALPHA_MAX = 1.0f;
    private static final float ALPHA_STEP = 0.1f;

    // 行间距(px)：0 ~ 32，步长 4
    private static final int LINE_SPACING_MIN = 0;
    private static final int LINE_SPACING_MAX = 32;
    private static final int LINE_SPACING_STEP = 4;

    // 顶边距(px)：0 ~ 300，步长 20
    private static final int TOP_MARGIN_MIN = 0;
    private static final int TOP_MARGIN_MAX = 300;
    private static final int TOP_MARGIN_STEP = 20;

    private LinearLayout llSwitchOptions, llColorOptions, llSpeedOptions;
    private View rowSearchOnline;
    private TextView btnSearchOnline;
    private OnListener listener;
    private String vodName;
    private String vodEpisode;
    private boolean hasVodContext = false;
    private OnSearchOnlineListener searchOnlineListener;

    public interface OnListener {
        void onChange();
    }

    /**
     * 在线搜索结果选中后的回调：把加载到的弹幕内容交给外部（播放页）去 checkDanmu()。
     */
    public interface OnSearchOnlineListener {
        void loadDanmu(String danmu);
    }

    public void setOnListener(OnListener listener) {
        this.listener = listener;
    }

    /**
     * 仅播放页调用：传入当前剧名/集数，弹窗才会显示"在线搜索"入口。
     * 设置页（没有正在播放的视频）不调用此方法，"在线搜索"行保持隐藏。
     */
    public void setVodContext(String vodName, String vodEpisode, OnSearchOnlineListener searchOnlineListener) {
        this.vodName = vodName == null ? "" : vodName;
        this.vodEpisode = vodEpisode == null ? "" : vodEpisode;
        this.hasVodContext = true;
        this.searchOnlineListener = searchOnlineListener;
        updateSearchOnlineRow();
    }

    public DanmuFullSettingDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_danmu_full_setting);
        setCanceledOnTouchOutside(true);

        llSwitchOptions = findViewById(R.id.llSwitchOptions);
        llColorOptions = findViewById(R.id.llColorOptions);
        llSpeedOptions = findViewById(R.id.llSpeedOptions);
        rowSearchOnline = findViewById(R.id.rowSearchOnline);
        btnSearchOnline = findViewById(R.id.btnSearchOnline);
        if (btnSearchOnline != null) {
            btnSearchOnline.setOnClickListener(v -> openSearchOnlineDialog());
        }

        // 右上角 ✕ 关闭按钮（触屏场景保留，NovaTV 靠遥控器返回键不需要）
        TextView btnClose = findViewById(R.id.btnDanmuClose);
        if (btnClose != null) btnClose.setOnClickListener(v -> dismiss());

        initSwitchRow();
        initColorRow();
        initSpeedRow();
        initSizeStepper();
        initLineStepper();
        initAlphaStepper();
        initLineSpacingStepper();
        initTopMarginStepper();
    }

    private void updateSearchOnlineRow() {
        if (rowSearchOnline == null) return;
        rowSearchOnline.setVisibility(hasVodContext ? View.VISIBLE : View.GONE);
    }

    private void openSearchOnlineDialog() {
        SearchDanmuDialog searchDanmuDialog = new SearchDanmuDialog(getContext());
        searchDanmuDialog.setDanmuLoader(danmu -> {
            if (searchOnlineListener != null) searchOnlineListener.loadDanmu(danmu);
        });
        searchDanmuDialog.setEpisode(vodEpisode);
        searchDanmuDialog.setSearchWord(vodName);
        searchDanmuDialog.show();
    }

    @Override
    public void show() {
        super.show();
        // 手机窄屏下尽量铺满窗口，平板限制在舒适宽度内；
        // 高度限制为屏幕高度的 85%：手机横屏可用高度很低，若按 WRAP_CONTENT 撑高会把
        // 下方设置项挤出屏幕且无法滚动，改为固定最大高度 + 外层 NestedScrollView 滚动。
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

    private void initSwitchRow() {
        boolean current = DanmuHelper.isOpen();
        List<View> chips = new ArrayList<>();
        for (String name : SWITCH_NAMES) {
            TextView chip = createChip(name);
            chips.add(chip);
            llSwitchOptions.addView(chip);
        }
        highlightSwitchChips(chips, current);
        for (int i = 0; i < SWITCH_NAMES.length; i++) {
            final boolean value = i == 0; // "开启" 在 index 0
            chips.get(i).setOnClickListener(v -> {
                DanmuHelper.setOpen(value);
                highlightSwitchChips(chips, value);
                // 开启弹幕时需要 reload=true 触发 prepare，否则弹幕不会立即显示（与播放器内切换弹幕开关行为一致）
                notifyChanged(value);
            });
        }
    }

    private void highlightSwitchChips(List<View> chips, boolean open) {
        markChip((TextView) chips.get(0), open);
        markChip((TextView) chips.get(1), !open);
    }

    private void initColorRow() {
        boolean current = DanmuHelper.useRandomColor();
        List<View> chips = new ArrayList<>();
        for (String name : COLOR_NAMES) {
            TextView chip = createChip(name);
            chips.add(chip);
            llColorOptions.addView(chip);
        }
        highlightChipsBoolean(chips, COLOR_VALUES, current);
        for (int i = 0; i < COLOR_VALUES.size(); i++) {
            final boolean value = COLOR_VALUES.get(i);
            chips.get(i).setOnClickListener(v -> {
                DanmuHelper.setRandomColor(value);
                highlightChipsBoolean(chips, COLOR_VALUES, value);
                notifyChanged();
            });
        }
    }

    private void initSpeedRow() {
        float current = DanmuHelper.getSpeed();
        List<View> chips = new ArrayList<>();
        for (int i = 0; i < SPEEDS.size(); i++) {
            TextView chip = createChip(SPEED_NAMES[i]);
            chips.add(chip);
            llSpeedOptions.addView(chip);
        }
        highlightChips(chips, SPEEDS, current);
        for (int i = 0; i < SPEEDS.size(); i++) {
            final float value = SPEEDS.get(i);
            chips.get(i).setOnClickListener(v -> {
                DanmuHelper.setSpeed(value);
                highlightChips(chips, SPEEDS, value);
                notifyChanged();
            });
        }
    }

    private void initSizeStepper() {
        bindFloatStepper(R.id.stepperSize, DanmuHelper.getSizeScale(), SIZE_MIN, SIZE_MAX, SIZE_STEP,
                value -> String.format(Locale.getDefault(), "%.1fx", value),
                value -> {
                    DanmuHelper.setSizeScale(value);
                    notifyChanged();
                });
    }

    private void initLineStepper() {
        bindIntStepper(R.id.stepperLine, DanmuHelper.getMaxLine(), LINE_MIN, LINE_MAX, LINE_STEP,
                value -> value + "行",
                value -> {
                    DanmuHelper.setMaxLine(value);
                    // 行数改变了弹幕轨道数量，需要 reload 让弹幕库重新准备
                    notifyChanged(true);
                });
    }

    private void initAlphaStepper() {
        bindFloatStepper(R.id.stepperAlpha, DanmuHelper.getAlpha(), ALPHA_MIN, ALPHA_MAX, ALPHA_STEP,
                value -> String.format(Locale.getDefault(), "%.0f%%", value * 100),
                value -> {
                    DanmuHelper.setAlpha(value);
                    notifyChanged();
                });
    }

    private void initLineSpacingStepper() {
        bindIntStepper(R.id.stepperLineSpacing, DanmuHelper.getLineSpacingPx(), LINE_SPACING_MIN, LINE_SPACING_MAX, LINE_SPACING_STEP,
                value -> value + "px",
                value -> {
                    DanmuHelper.setLineSpacingPx(value);
                    // 行间距变了会影响实际行距，需要 reload 让弹幕库重新准备
                    notifyChanged(true);
                });
    }

    private void initTopMarginStepper() {
        bindIntStepper(R.id.stepperTopMargin, DanmuHelper.getTopMarginPx(), TOP_MARGIN_MIN, TOP_MARGIN_MAX, TOP_MARGIN_STEP,
                value -> value + "px",
                value -> {
                    DanmuHelper.setTopMarginPx(value);
                    // 顶部边距变更 → FrameLayout.LayoutParams.topMargin 重设，
                    // 不需要重新 prepare 弹幕(轨道范围由 view 实际位置自动决定)。
                    notifyChanged(false);
                });
    }

    // ---------------- 通用步进器（浮点） ----------------

    private interface FloatFormatter {
        String format(float value);
    }

    private interface FloatSetter {
        void set(float value);
    }

    private void bindFloatStepper(int includeId, float initial, float min, float max, float step,
                                   FloatFormatter formatter, FloatSetter setter) {
        View row = findViewById(includeId);
        TextView tvMinus = row.findViewById(R.id.tvStepperMinus);
        TextView tvValue = row.findViewById(R.id.tvStepperValue);
        TextView tvPlus = row.findViewById(R.id.tvStepperPlus);

        final float[] current = {clamp(initial, min, max)};
        tvValue.setText(formatter.format(current[0]));

        tvMinus.setOnClickListener(v -> {
            current[0] = clamp(round1(current[0] - step), min, max);
            tvValue.setText(formatter.format(current[0]));
            setter.set(current[0]);
        });
        tvPlus.setOnClickListener(v -> {
            current[0] = clamp(round1(current[0] + step), min, max);
            tvValue.setText(formatter.format(current[0]));
            setter.set(current[0]);
        });
    }

    // ---------------- 通用步进器（整数） ----------------

    private interface IntFormatter {
        String format(int value);
    }

    private interface IntSetter {
        void set(int value);
    }

    private void bindIntStepper(int includeId, int initial, int min, int max, int step,
                                 IntFormatter formatter, IntSetter setter) {
        View row = findViewById(includeId);
        TextView tvMinus = row.findViewById(R.id.tvStepperMinus);
        TextView tvValue = row.findViewById(R.id.tvStepperValue);
        TextView tvPlus = row.findViewById(R.id.tvStepperPlus);

        final int[] current = {clamp(initial, min, max)};
        tvValue.setText(formatter.format(current[0]));

        tvMinus.setOnClickListener(v -> {
            current[0] = clamp(current[0] - step, min, max);
            tvValue.setText(formatter.format(current[0]));
            setter.set(current[0]);
        });
        tvPlus.setOnClickListener(v -> {
            current[0] = clamp(current[0] + step, min, max);
            tvValue.setText(formatter.format(current[0]));
            setter.set(current[0]);
        });
    }

    private TextView createChip(String text) {
        TextView tv = new TextView(getContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = getContext().getResources().getDimensionPixelSize(R.dimen.vs_8);
        tv.setLayoutParams(lp);
        tv.setBackgroundResource(R.drawable.button_danmu_setting);
        tv.setFocusable(true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, getContext().getResources().getDimensionPixelSize(R.dimen.vs_12),
                0, getContext().getResources().getDimensionPixelSize(R.dimen.vs_12));
        tv.setText(text);
        tv.setTextColor(getContext().getResources().getColor(R.color.dialog_text_primary));
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                getContext().getResources().getDimension(R.dimen.ts_20));
        return tv;
    }

    private void highlightChips(List<View> chips, List<Float> values, float selected) {
        for (int i = 0; i < chips.size(); i++) {
            boolean sel = Math.abs(values.get(i) - selected) < 0.001f;
            markChip((TextView) chips.get(i), sel);
        }
    }

    private void highlightChipsBoolean(List<View> chips, List<Boolean> values, boolean selected) {
        for (int i = 0; i < chips.size(); i++) {
            boolean sel = values.get(i) == selected;
            markChip((TextView) chips.get(i), sel);
        }
    }

    private void markChip(TextView tv, boolean selected) {
        tv.setSelected(selected);
        tv.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        tv.setTextColor(selected
                ? getContext().getResources().getColor(R.color.dialog_control_stroke_focused)
                : getContext().getResources().getColor(R.color.dialog_text_primary));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }

    /** 避免连续 +/-0.1 步长的浮点误差累积（如 1.2000001） */
    private static float round1(float value) {
        return Math.round(value * 10f) / 10f;
    }

    private void notifyChanged() {
        notifyChanged(false);
    }

    /**
     * @param reload 是否需要重新 prepare 弹幕（画布/轨道数变化，如行数调整时必须为 true，
     *               否则弹幕库会继续用旧的轨道数绘制，造成重叠或视觉上无变化）
     */
    private void notifyChanged(boolean reload) {
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, reload));
        if (listener != null) listener.onChange();
    }
}
