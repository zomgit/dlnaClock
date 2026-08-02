package com.dlnaclock.screensaver;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.dlnaclock.R;
import com.dlnaclock.screensaver.wallpaper.GestureAwareWallpaper;
import com.dlnaclock.screensaver.wallpaper.ParamDef;
import com.dlnaclock.screensaver.wallpaper.ParamStore;
import com.dlnaclock.screensaver.wallpaper.WallpaperFactory;
import com.dlnaclock.screensaver.wallpaper.WallpaperRenderer;

/**
 * WallpaperControlPanel - 主屏幕壁纸参数实时控制浮层
 * 根据 ParamDef[] 动态生成 SeekBar / 色块按钮 / Switch
 * 拖动 SeekBar 时实时调用 applyParams()，松手后持久化到 ParamStore
 */
public class WallpaperControlPanel {

    private final Context context;
    private final FrameLayout rootView;
    private final LinearLayout paramsContainer;
    private final TextView wallpaperNameLabel;
    private final Button resetButton;
    private final Button closeButton;
    private final LinearLayout rotationRow;       // 手势旋转显示行（仅手势壁纸）
    private final TextView rotationValueLabel;    // 旋转角度值显示
    private final Button rotationResetButton;     // 单独还原旋转按钮
    private Runnable extraResetAction;   // 一键还原时的附加动作（清零叠加式壁纸的手势状态）
    private Runnable rotationResetAction; // 单独还原旋转的回调

    private WallpaperRenderer wallpaperRenderer;
    private ParamDef[] paramDefs;
    private int wallpaperType;
    private Bundle currentParams;

    /** 控件持有者列表 */
    private final java.util.List<ParamViewHolder> holders = new java.util.ArrayList<>();

    public WallpaperControlPanel(Context context, FrameLayout parent) {
        this.context = context;
        // 将布局 inflate 到 parent 中
        LayoutInflater.from(context).inflate(R.layout.wallpaper_control_panel, parent, true);
        this.rootView = parent;
        this.paramsContainer = (LinearLayout) parent.findViewById(R.id.layout_params_container);
        this.wallpaperNameLabel = (TextView) parent.findViewById(R.id.tv_wallpaper_name);
        this.resetButton = (Button) parent.findViewById(R.id.btn_reset_params);
        this.closeButton = (Button) parent.findViewById(R.id.btn_close_panel);
        this.rotationRow = (LinearLayout) parent.findViewById(R.id.layout_rotation_row);
        this.rotationValueLabel = (TextView) parent.findViewById(R.id.tv_rotation_value);
        this.rotationResetButton = (Button) parent.findViewById(R.id.btn_reset_rotation);

        // 面板居中显示，根据当前屏幕方向计算尺寸
        recalculateLayout();

        if (resetButton != null) {
            resetButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    resetToDefaults();
                }
            });
        }

        // 关闭按钮：隐藏整个面板（面板仅由底部“参数”按钮控制显示）
        if (closeButton != null) {
            closeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    rootView.setVisibility(View.GONE);
                }
            });
        }

        // 点击面板外空白处关闭面板（根容器全屏，面板本体为其子 View）
        rootView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rootView.setVisibility(View.GONE);
            }
        });
        // 面板本体消费点击，防止点击面板内部空白区域时冒泡触发外层关闭
        View inflatedView = rootView.getChildAt(0);
        if (inflatedView != null) {
            inflatedView.setClickable(true);
        }

        // 单独还原旋转：清零手势旋转角（保留平移/缩放）
        if (rotationResetButton != null) {
            rotationResetButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (rotationResetAction != null) {
                        rotationResetAction.run();
                    }
                    updateRotationDisplay(0f, 0f);
                }
            });
        }
    }

    /**
     * 重新计算面板尺寸（屏幕旋转后调用）
     * 居中弹窗形态：横屏 68%×68%，竖屏 85%宽×68%高，屏幕旋转时同步调整
     */
    public void recalculateLayout() {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int orientation = context.getResources().getConfiguration().orientation;
        // 横屏 68% 宽，竖屏 85% 宽；高度恒为屏幕 68%
        float widthRatio = (orientation == Configuration.ORIENTATION_LANDSCAPE) ? 0.68f : 0.85f;
        int panelWidth = (int) (dm.widthPixels * widthRatio);
        int panelHeight = (int) (dm.heightPixels * 0.68f);
        View inflatedView = rootView.getChildAt(0);
        if (inflatedView != null) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(panelWidth, panelHeight);
            lp.gravity = Gravity.CENTER;
            inflatedView.setLayoutParams(lp);
        }
    }

    /**
     * 绑定壁纸渲染器并刷新控件
     */
    public void bind(WallpaperRenderer renderer, int wallpaperType) {
        this.wallpaperRenderer = renderer;
        this.wallpaperType = wallpaperType;
        this.paramDefs = renderer != null ? renderer.getParamDefs() : new ParamDef[0];
        try {
            this.currentParams = ParamStore.loadParams(wallpaperType, paramDefs);
        } catch (Exception e) {
            // 参数加载失败时使用默认值，避免控件全部丢失
            this.currentParams = new Bundle();
            if (paramDefs != null) {
                for (ParamDef def : paramDefs) {
                    currentParams.putFloat(def.key, def.defaultValue);
                }
            }
        }
        // 更新壁纸名称显示
        if (wallpaperNameLabel != null) {
            java.util.List<String> names = WallpaperFactory.getWallpaperNames();
            if (wallpaperType >= 0 && wallpaperType < names.size()) {
                wallpaperNameLabel.setText(names.get(wallpaperType));
            }
        }
        // 手势壁纸显示“手势旋转”行，其余壁纸隐藏
        if (rotationRow != null) {
            rotationRow.setVisibility(renderer instanceof GestureAwareWallpaper
                    ? View.VISIBLE : View.GONE);
        }
        rebuildControls();
    }

    /**
     * 更新手势旋转角显示（面板实时联动，角度取整）
     */
    public void updateRotationDisplay(float rotX, float rotY) {
        if (rotationValueLabel != null) {
            rotationValueLabel.setText("X " + Math.round(rotX) + "°  Y " + Math.round(rotY) + "°");
        }
    }

    /**
     * 设置单独还原旋转的回调（由 Activity 注入，清零当前壁纸手势旋转角）
     */
    public void setRotationResetAction(Runnable action) {
        this.rotationResetAction = action;
    }

    /**
     * 刷新面板（从 ParamStore 重新加载参数值并更新控件）
     */
    public void refresh() {
        if (wallpaperRenderer != null && paramDefs.length > 0) {
            try {
                currentParams = ParamStore.loadParams(wallpaperType, paramDefs);
            } catch (Exception e) {
                currentParams = new Bundle();
                for (ParamDef def : paramDefs) {
                    currentParams.putFloat(def.key, def.defaultValue);
                }
            }
            updateControlValues();
        }
    }

    /**
     * 设置一键还原的附加动作（清零叠加式壁纸的手势状态）
     */
    public void setExtraResetAction(Runnable action) {
        this.extraResetAction = action;
    }

    /**
     * 根据 paramDefs 动态重建所有控件
     */
    private void rebuildControls() {
        // 清除旧控件
        paramsContainer.removeAllViews();
        holders.clear();

        if (paramDefs == null || paramDefs.length == 0) {
            return;
        }

        for (ParamDef def : paramDefs) {
            ParamViewHolder holder = createControl(def);
            if (holder != null) {
                holders.add(holder);
            }
        }
    }

    /**
     * 更新已有控件的值（不重建 UI）
     */
    private void updateControlValues() {
        for (ParamViewHolder holder : holders) {
            float val = currentParams.getFloat(holder.def.key, holder.def.defaultValue);
            updateHolderValue(holder, val);
        }
    }

    private void updateHolderValue(ParamViewHolder holder, float val) {
        switch (holder.def.type) {
            case FLOAT:
            case INT:
            case COLOR:
                if (holder.seekBar != null) {
                    int progress = valueToProgress(holder.def, val);
                    holder.seekBar.setProgress(progress);
                }
                if (holder.valueLabel != null) {
                    holder.valueLabel.setText(formatValue(holder.def, val));
                }
                break;
            case BOOL:
                if (holder.switchControl != null) {
                    holder.switchControl.setChecked(val >= 0.5f);
                }
                break;
            case SELECT:
                if (holder.selectChips != null && holder.def.options != null) {
                    int idx = Math.round(val);
                    if (idx >= 0 && idx < holder.def.options.length) {
                        for (int i = 0; i < holder.selectChips.length; i++) {
                            boolean sel = (i == idx);
                            holder.selectChips[i].setBackgroundColor(sel ? 0x4080CBC4 : 0x18FFFFFF);
                            holder.selectChips[i].setTextColor(sel ? 0xFFFFFFFF : 0x99FFFFFF);
                        }
                    }
                }
                break;
        }
    }

    /**
     * 为单个 ParamDef 创建控件行（标签和控制项同一行）
     */
    private ParamViewHolder createControl(final ParamDef def) {
        final float currentVal = currentParams.getFloat(def.key, def.defaultValue);

        // 单行容器：水平布局
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dpToPx(3), 0, dpToPx(3));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        row.setLayoutParams(rowLp);

        // 标签
        TextView label = new TextView(context);
        label.setText(def.label);
        label.setTextColor(0xCCFFFFFF);
        label.setTextSize(12);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                dpToPx(72), LinearLayout.LayoutParams.WRAP_CONTENT);
        label.setLayoutParams(labelLp);

        ParamViewHolder holder = new ParamViewHolder();
        holder.def = def;
        holder.row = row;

        // 值标签（SeekBar 右侧 / Switch 右侧）
        TextView valueLabel = new TextView(context);
        valueLabel.setText(formatValue(def, currentVal));
        valueLabel.setTextColor(0xFF80CBC4);
        valueLabel.setTextSize(11);
        valueLabel.setGravity(Gravity.END);
        valueLabel.setMinWidth(dpToPx(40));
        holder.valueLabel = valueLabel;

        switch (def.type) {
            case FLOAT:
            case INT:
            case COLOR:
                // [标签] [SeekBar...] [值]
                holder.seekBar = createSeekBar(def, currentVal, holder);
                LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
                        0, dpToPx(32), 1.0f);
                sbLp.setMargins(dpToPx(4), 0, dpToPx(4), 0);
                holder.seekBar.setLayoutParams(sbLp);

                LinearLayout.LayoutParams valLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                valueLabel.setLayoutParams(valLp);

                row.addView(label);
                row.addView(holder.seekBar);
                row.addView(valueLabel);
                break;
            case BOOL:
                // [标签] [Switch]
                holder.switchControl = createSwitch(def, currentVal, holder);
                LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                swLp.setMargins(dpToPx(8), 0, 0, 0);
                holder.switchControl.setLayoutParams(swLp);

                row.addView(label);
                row.addView(holder.switchControl);
                break;
            case SELECT:
                // [标签] [○选项1 ○选项2 ○选项3] 单选芯片组
                if (def.options == null || def.options.length == 0) break;
                int curSel = Math.round(currentVal);
                LinearLayout chipsRow = new LinearLayout(context);
                chipsRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams chipsLp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                chipsRow.setLayoutParams(chipsLp);
                holder.selectChipsContainer = chipsRow;
                holder.selectChips = new TextView[def.options.length];
                for (int i = 0; i < def.options.length; i++) {
                    final int optIdx = i;
                    TextView chip = new TextView(context);
                    chip.setText(def.options[i]);
                    chip.setTextSize(10);
                    chip.setGravity(Gravity.CENTER);
                    chip.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
                    boolean sel = (i == curSel);
                    chip.setBackgroundColor(sel ? 0x4080CBC4 : 0x18FFFFFF);
                    chip.setTextColor(sel ? 0xFFFFFFFF : 0x99FFFFFF);
                    LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                    chipLp.setMargins(dpToPx(2), 0, dpToPx(2), 0);
                    chip.setLayoutParams(chipLp);
                    chip.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            float val = optIdx;
                            currentParams.putFloat(def.key, val);
                            // 刷新所有芯片的选中状态
                            for (int j = 0; j < holder.selectChips.length; j++) {
                                boolean s = (j == optIdx);
                                holder.selectChips[j].setBackgroundColor(s ? 0x4080CBC4 : 0x18FFFFFF);
                                holder.selectChips[j].setTextColor(s ? 0xFFFFFFFF : 0x99FFFFFF);
                            }
                            if (wallpaperRenderer != null) {
                                wallpaperRenderer.applyParams(currentParams);
                            }
                            ParamStore.saveParam(wallpaperType, def.key, val);
                        }
                    });
                    holder.selectChips[i] = chip;
                    chipsRow.addView(chip);
                }
                row.addView(label);
                row.addView(chipsRow);
                break;
        }

        paramsContainer.addView(row);
        return holder;
    }

    private SeekBar createSeekBar(final ParamDef def, float currentVal, final ParamViewHolder holder) {
        SeekBar seekBar = new SeekBar(context);
        seekBar.setMax(100);
        seekBar.setProgress(valueToProgress(def, currentVal));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                float val = progressToValue(def, progress);
                // 实时更新值标签
                if (holder.valueLabel != null) {
                    holder.valueLabel.setText(formatValue(def, val));
                }
                // 实时应用到壁纸
                currentParams.putFloat(def.key, val);
                if (wallpaperRenderer != null) {
                    wallpaperRenderer.applyParams(currentParams);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 松手后持久化
                float val = progressToValue(def, seekBar.getProgress());
                currentParams.putFloat(def.key, val);
                ParamStore.saveParam(wallpaperType, def.key, val);
            }
        });

        return seekBar;
    }

    private Switch createSwitch(final ParamDef def, float currentVal, final ParamViewHolder holder) {
        Switch sw = new Switch(context);
        sw.setChecked(currentVal >= 0.5f);
        sw.setTextColor(0xCCFFFFFF);

        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                float val = isChecked ? 1f : 0f;
                currentParams.putFloat(def.key, val);
                if (wallpaperRenderer != null) {
                    wallpaperRenderer.applyParams(currentParams);
                }
                ParamStore.saveParam(wallpaperType, def.key, val);
            }
        });

        return sw;
    }

    /**
     * 重置所有参数为默认值
     */
    private void resetToDefaults() {
        if (wallpaperRenderer == null || paramDefs == null) return;
        ParamStore.resetParams(wallpaperType, paramDefs);
        currentParams = ParamStore.loadParams(wallpaperType, paramDefs);
        if (wallpaperRenderer != null) {
            wallpaperRenderer.applyParams(currentParams);
        }
        // 叠加式壁纸的手势状态一并清零（直控壁纸的参数已重置）
        if (extraResetAction != null) {
            extraResetAction.run();
        }
        updateControlValues();
        Toast.makeText(context, "已重置为默认值", Toast.LENGTH_SHORT).show();
    }

    // === 工具方法 ===

    private int valueToProgress(ParamDef def, float value) {
        float range = def.max - def.min;
        if (range <= 0) return 0;
        return Math.round((value - def.min) / range * 100f);
    }

    private float progressToValue(ParamDef def, int progress) {
        float range = def.max - def.min;
        float raw = def.min + range * progress / 100f;
        if (def.type == ParamDef.Type.INT) {
            return Math.round(raw);
        }
        // 按步进四舍五入
        if (def.step > 0) {
            return Math.round(raw / def.step) * def.step;
        }
        return raw;
    }

    private String formatValue(ParamDef def, float value) {
        switch (def.type) {
            case INT:
                return String.valueOf((int) value);
            case BOOL:
                return value >= 0.5f ? "开" : "关";
            case COLOR:
                return "#" + Integer.toHexString(((int) value) & 0xFFFFFF).toUpperCase();
            case SELECT:
                return def.options != null && value >= 0 && value < def.options.length
                        ? def.options[(int) value] : "";
            default:
                return String.format("%.1f", value);
        }
    }

    private int dpToPx(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    // === 控件持有者 ===

    static class ParamViewHolder {
        ParamDef def;
        LinearLayout row;
        SeekBar seekBar;
        TextView valueLabel;
        Switch switchControl;
        TextView[] selectChips;
        LinearLayout selectChipsContainer;
    }
}
