package com.mobile.novabox.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.mobile.novabox.R;
import com.mobile.novabox.api.DanmakuApi;
import com.mobile.novabox.event.RefreshEvent;
import com.mobile.novabox.util.DanmuHelper;

import org.greenrobot.eventbus.EventBus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 弹幕设置弹窗（参考 NovaTV 的左右分栏样式）
 * 左侧为竖向设置项列表，右侧为对应设置项的竖向列表选项，
 * 字号、屏占比、透明度均以列表选择，不再使用 +- 号调节。
 */
public class DanmuFullSettingDialog extends BaseDialog {

    private static final String[] ITEM_NAMES = {"地址", "开关", "颜色", "字号", "屏占比", "滚动速度", "透明", "行间距", "顶部边距"};
    private static final int IDX_API = 0;
    private static final int IDX_ONOFF = 1;
    private static final int IDX_COLOR = 2;
    private static final int IDX_SIZE = 3;
    private static final int IDX_RATIO = 4;
    private static final int IDX_SPEED = 5;
    private static final int IDX_ALPHA = 6;
    private static final int IDX_LINE_SPACING = 7;
    private static final int IDX_TOP_MARGIN = 8;

    /** 顶部边距档位(px):粗粒度,与字号、行间距的 px 风格一致,默认 40(电视顶部留一点呼吸空间) */
    private static final List<Integer> TOP_MARGINS = Arrays.asList(0, 40, 80, 120, 160, 200);

    private static final List<Boolean> ONOFF_VALUES = Arrays.asList(true, false);
    private static final String[] ONOFF_NAMES = {"开", "关"};
    private static final List<Boolean> COLOR_VALUES = Arrays.asList(false, true);
    private static final String[] COLOR_NAMES = {"默认", "随机"};
    private static final List<Float> SIZES = Arrays.asList(0.6f, 0.8f, 1.0f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 1.8f, 2.0f);
    private static final List<Integer> RATIOS = Arrays.asList(10, 20, 30, 50, 75, 100);
    private static final List<Float> SPEEDS = Arrays.asList(2.4f, 1.8f, 1.5f, 1.0f);
    private static final String[] SPEED_NAMES = {"超慢", "慢", "适中", "快"};
    private static final List<Float> ALPHAS = Arrays.asList(1.0f, 0.9f, 0.8f, 0.7f, 0.6f, 0.5f);
    /** 行间距档位(px):直接作为弹幕库 margin 使用,值越大行距越宽、行数越少 */
    private static final List<Integer> LINE_SPACINGS = Arrays.asList(0, 4, 8, 12, 16, 20, 24, 28, 32);

    private LinearLayout llItemList;
    private TextView tvPanelTitle;
    private View panelApi, panelOnOff, panelColor, panelSize, panelRatio, panelSpeed, panelAlpha, panelLineSpacing, panelTopMargin;
    private EditText inputApi;
    private LinearLayout llOnOffOptions, llColorOptions, llSizeOptions, llRatioOptions, llSpeedOptions, llAlphaOptions, llLineSpacingOptions, llTopMarginOptions;

    private int selectedItem = IDX_API;
    private OnListener listener;

    public interface OnListener {
        void onChange();
    }

    public void setOnListener(OnListener listener) {
        this.listener = listener;
    }

    public DanmuFullSettingDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_danmu_full_setting);
        setCanceledOnTouchOutside(true);

        llItemList = findViewById(R.id.llDanmuItemList);
        tvPanelTitle = findViewById(R.id.tvDanmuPanelTitle);
        panelApi = findViewById(R.id.panelApi);
        panelOnOff = findViewById(R.id.panelOnOff);
        panelColor = findViewById(R.id.panelColor);
        panelSize = findViewById(R.id.panelSize);
        panelRatio = findViewById(R.id.panelRatio);
        panelSpeed = findViewById(R.id.panelSpeed);
        panelAlpha = findViewById(R.id.panelAlpha);
        panelLineSpacing = findViewById(R.id.panelLineSpacing);
        panelTopMargin = findViewById(R.id.panelTopMargin);
        inputApi = findViewById(R.id.inputApi);
        llOnOffOptions = findViewById(R.id.llOnOffOptions);
        llColorOptions = findViewById(R.id.llColorOptions);
        llSizeOptions = findViewById(R.id.llSizeOptions);
        llRatioOptions = findViewById(R.id.llRatioOptions);
        llSpeedOptions = findViewById(R.id.llSpeedOptions);
        llAlphaOptions = findViewById(R.id.llAlphaOptions);
        llLineSpacingOptions = findViewById(R.id.llLineSpacingOptions);
        llTopMarginOptions = findViewById(R.id.llTopMarginOptions);

        // 右上角 ✕ 关闭按钮
        TextView btnClose = findViewById(R.id.btnDanmuClose);
        if (btnClose != null) btnClose.setOnClickListener(v -> dismiss());

        // 底部"关闭"按钮
        TextView btnCloseBottom = findViewById(R.id.btnDanmuCloseBottom);
        if (btnCloseBottom != null) btnCloseBottom.setOnClickListener(v -> dismiss());

        initItemList();
        initApiPanel();
        initOnOffPanel();
        initColorPanel();
        initSizePanel();
        initRatioPanel();
        initSpeedPanel();
        initAlphaPanel();
        initLineSpacingPanel();
        initTopMarginPanel();

        selectItem(IDX_API);
    }

    @Override
    public void show() {
        super.show();
        // 左右分栏弹窗需要更宽的显示区域：手机窄屏下尽量铺满窗口，平板限制在舒适宽度内；
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

    private void initItemList() {
        llItemList.removeAllViews();
        for (int i = 0; i < ITEM_NAMES.length; i++) {
            final int pos = i;
            View item = LayoutInflater.from(getContext()).inflate(R.layout.item_route_name_tv, llItemList, false);
            TextView tv = item.findViewById(R.id.tvName);
            tv.setText(ITEM_NAMES[i]);
            item.setTag(tv);
            tv.setOnClickListener(v -> selectItem(pos));
            llItemList.addView(item);
        }
    }

    private void selectItem(int pos) {
        selectedItem = pos;
        for (int i = 0; i < llItemList.getChildCount(); i++) {
            TextView tv = (TextView) llItemList.getChildAt(i).getTag();
            boolean sel = i == pos;
            tv.setTextColor(getContext().getResources().getColor(R.color.dialog_text_primary));
            tv.setTypeface(null, sel ? Typeface.BOLD : Typeface.NORMAL);
            tv.setText((sel ? "● " : "") + ITEM_NAMES[i]);
        }
        panelApi.setVisibility(pos == IDX_API ? View.VISIBLE : View.GONE);
        panelOnOff.setVisibility(pos == IDX_ONOFF ? View.VISIBLE : View.GONE);
        panelColor.setVisibility(pos == IDX_COLOR ? View.VISIBLE : View.GONE);
        panelSize.setVisibility(pos == IDX_SIZE ? View.VISIBLE : View.GONE);
        panelRatio.setVisibility(pos == IDX_RATIO ? View.VISIBLE : View.GONE);
        panelSpeed.setVisibility(pos == IDX_SPEED ? View.VISIBLE : View.GONE);
        panelAlpha.setVisibility(pos == IDX_ALPHA ? View.VISIBLE : View.GONE);
        panelLineSpacing.setVisibility(pos == IDX_LINE_SPACING ? View.VISIBLE : View.GONE);
        panelTopMargin.setVisibility(pos == IDX_TOP_MARGIN ? View.VISIBLE : View.GONE);
        tvPanelTitle.setText("弹幕" + ITEM_NAMES[pos]);
    }

    private void initApiPanel() {
        inputApi.setText(DanmakuApi.getDisplayApiUrl());
        String defaultApi = DanmakuApi.getDisplayApiUrl();
        inputApi.setHint(defaultApi.isEmpty() ? "请输入弹幕搜索地址" : defaultApi);
        findViewById(R.id.apiDefault).setOnClickListener(v -> {
            DanmakuApi.setUseDefault(true);
            inputApi.setText("");
            notifyChanged();
        });
        findViewById(R.id.apiSubmit).setOnClickListener(v -> saveApi(inputApi.getText().toString().trim()));
        inputApi.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                saveApi(inputApi.getText().toString().trim());
                return true;
            }
            return false;
        });
    }

    private void saveApi(String api) {
        DanmakuApi.setCustomApi(api);
        notifyChanged();
    }

    private void initOnOffPanel() {
        boolean current = DanmuHelper.isOpen();
        List<View> chips = new ArrayList<>();
        for (String name : ONOFF_NAMES) {
            chips.add(createChip(name));
        }
        highlightChipsBoolean(chips, ONOFF_VALUES, current);
        for (int i = 0; i < ONOFF_VALUES.size(); i++) {
            final boolean value = ONOFF_VALUES.get(i);
            chips.get(i).setOnClickListener(v -> {
                DanmuHelper.setOpen(value);
                highlightChipsBoolean(chips, ONOFF_VALUES, value);
                notifyChanged(true);
            });
        }
        for (View chip : chips) {
            llOnOffOptions.addView(chip);
        }
    }

    private void initColorPanel() {
        boolean current = DanmuHelper.useRandomColor();
        List<View> chips = new ArrayList<>();
        for (String name : COLOR_NAMES) {
            chips.add(createChip(name));
        }
        highlightChipsBoolean(chips, COLOR_VALUES, current);
        for (int i = 0; i < COLOR_VALUES.size(); i++) {
            final boolean value = COLOR_VALUES.get(i);
            chips.get(i).setOnClickListener(v -> {
                DanmuHelper.setRandomColor(value);
                highlightChipsBoolean(chips, COLOR_VALUES, value);
                notifyChanged(true);
            });
        }
        for (View chip : chips) {
            llColorOptions.addView(chip);
        }
    }

    private void initSizePanel() {
        float current = DanmuHelper.getSizeScale();
        List<View> chips = new ArrayList<>();
        for (Float size : SIZES) {
            chips.add(createChip(String.format("%.1fx", size)));
        }
        for (View chip : chips) {
            llSizeOptions.addView(chip);
        }
        highlightChips(chips, SIZES, current);
        for (int i = 0; i < SIZES.size(); i++) {
            final float value = SIZES.get(i);
            chips.get(i).setOnClickListener(v -> {
                DanmuHelper.setSizeScale(value);
                highlightChips(chips, SIZES, value);
                notifyChanged();
            });
        }
    }

    private void initRatioPanel() {
        int current = DanmuHelper.getScreenRatio();
        List<View> chips = new ArrayList<>();
        for (Integer ratio : RATIOS) {
            chips.add(createChip(ratio + "%"));
        }
        for (View chip : chips) {
            llRatioOptions.addView(chip);
        }
        highlightChipsInt(chips, RATIOS, current);
        for (int i = 0; i < RATIOS.size(); i++) {
            final int value = RATIOS.get(i);
            chips.get(i).setOnClickListener(v -> {
                DanmuHelper.setScreenRatio(value);
                highlightChipsInt(chips, RATIOS, value);
                // 屏占比改变了弹幕轨道画布的高度，弹幕库需要重新 prepare 才能生效
                notifyChanged(true);
            });
        }
    }

    private void initSpeedPanel() {
        float current = DanmuHelper.getSpeed();
        List<View> chips = new ArrayList<>();
        for (String name : SPEED_NAMES) {
            chips.add(createChip(name));
        }
        for (View chip : chips) {
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

    private void initAlphaPanel() {
        float current = DanmuHelper.getAlpha();
        List<View> chips = new ArrayList<>();
        for (Float alpha : ALPHAS) {
            chips.add(createChip(Math.round(alpha * 100) + "%"));
        }
        for (View chip : chips) {
            llAlphaOptions.addView(chip);
        }
        highlightChips(chips, ALPHAS, current);
        for (int i = 0; i < ALPHAS.size(); i++) {
            final float value = ALPHAS.get(i);
            chips.get(i).setOnClickListener(v -> {
                DanmuHelper.setAlpha(value);
                highlightChips(chips, ALPHAS, value);
                notifyChanged();
            });
        }
    }

    private void initLineSpacingPanel() {
        // 直接显示 px 数字档位
        int current = DanmuHelper.getLineSpacingPx();
        List<View> chips = new ArrayList<>();
        for (Integer spacing : LINE_SPACINGS) {
            TextView chip = createChip(spacing + "px");
            chips.add(chip);
            llLineSpacingOptions.addView(chip);
        }
        highlightChipsInt(chips, LINE_SPACINGS, current);
        for (int i = 0; i < LINE_SPACINGS.size(); i++) {
            final int value = LINE_SPACINGS.get(i);
            chips.get(i).setOnClickListener(v -> {
                DanmuHelper.setLineSpacingPx(value);
                highlightChipsInt(chips, LINE_SPACINGS, value);
                // 行间距变了会影响实际行距和最大行数,需要 reload 让弹幕库重新准备
                notifyChanged(true);
            });
        }
    }

    private void initTopMarginPanel() {
        // 顶部边距(像素):与行间距同样直接显示 px 数字档位
        int current = DanmuHelper.getTopMarginPx();
        List<View> chips = new ArrayList<>();
        for (Integer px : TOP_MARGINS) {
            TextView chip = createChip(px + "px");
            chips.add(chip);
            llTopMarginOptions.addView(chip);
        }
        highlightChipsInt(chips, TOP_MARGINS, current);
        for (int i = 0; i < TOP_MARGINS.size(); i++) {
            final int value = TOP_MARGINS.get(i);
            chips.get(i).setOnClickListener(v -> {
                DanmuHelper.setTopMarginPx(value);
                highlightChipsInt(chips, TOP_MARGINS, value);
                // 顶部边距变更 → FrameLayout.LayoutParams.topMargin 重设 + 重算屏占比,
                // 不需要重新 prepare 弹幕(轨道范围由 view 实际位置自动决定)。
                notifyChanged(false);
            });
        }
    }

    private TextView createChip(String text) {
        TextView tv = new TextView(getContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = getContext().getResources().getDimensionPixelSize(R.dimen.vs_8);
        tv.setLayoutParams(lp);
        tv.setBackgroundResource(R.drawable.button_danmu_setting);
        tv.setFocusable(true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, getContext().getResources().getDimensionPixelSize(R.dimen.vs_12),
                0, getContext().getResources().getDimensionPixelSize(R.dimen.vs_12));
        tv.setText(text);
        tv.setTextColor(getContext().getResources().getColor(R.color.dialog_text_primary));
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                getContext().getResources().getDimension(R.dimen.ts_22));
        return tv;
    }

    private void highlightChips(List<View> chips, List<Float> values, float selected) {
        for (int i = 0; i < chips.size(); i++) {
            boolean sel = Math.abs(values.get(i) - selected) < 0.001f;
            markChip((TextView) chips.get(i), sel);
        }
    }

    private void highlightChipsInt(List<View> chips, List<Integer> values, int selected) {
        for (int i = 0; i < chips.size(); i++) {
            boolean sel = values.get(i) == selected;
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

    private void notifyChanged() {
        notifyChanged(false);
    }

    /**
     * @param reload 是否需要重新 prepare 弹幕（画布尺寸变化，如屏占比调整时必须为 true，
     *               否则弹幕库会继续用旧尺寸算出的轨迹绘制）
     */
    private void notifyChanged(boolean reload) {
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SET_DANMU_SETTINGS, reload));
        if (listener != null) listener.onChange();
    }
}
