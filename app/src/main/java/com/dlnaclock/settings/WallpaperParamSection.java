package com.dlnaclock.settings;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.dlnaclock.R;
import com.dlnaclock.screensaver.wallpaper.ParamDef;
import com.dlnaclock.screensaver.wallpaper.ParamStore;
import com.dlnaclock.screensaver.wallpaper.WallpaperRenderer;

/**
 * WallpaperParamSection - 设置界面中的壁纸参数区域
 * 根据 ParamDef[] 动态生成 SeekBar / 色块按钮 / Switch
 * 与主屏 WallpaperControlPanel 共享同一个 ParamStore，实现双端同步
 */
public class WallpaperParamSection {

    private final Context context;
    private final LinearLayout container;
    private WallpaperRenderer wallpaperRenderer;
    private ParamDef[] paramDefs;
    private int wallpaperType;
    private Bundle currentParams;

    private final java.util.List<ParamViewHolder> holders = new java.util.ArrayList<>();

    public WallpaperParamSection(Context context, LinearLayout container) {
        this.context = context;
        this.container = container;
    }

    /**
     * 绑定壁纸渲染器并刷新控件
     */
    public void bind(WallpaperRenderer renderer, int wallpaperType) {
        this.wallpaperRenderer = renderer;
        this.wallpaperType = wallpaperType;
        this.paramDefs = renderer != null ? renderer.getParamDefs() : new ParamDef[0];
        this.currentParams = ParamStore.loadParams(wallpaperType, paramDefs);
        rebuildControls();
    }

    /**
     * 通过 ParamDef[] 直接绑定（不依赖 WallpaperRenderer 实例）
     * 用于设置界面无需运行壁纸时的参数控件生成
     */
    public void bindFromDefs(ParamDef[] defs, int wallpaperType) {
        this.wallpaperRenderer = null;
        this.wallpaperType = wallpaperType;
        this.paramDefs = defs != null ? defs : new ParamDef[0];
        this.currentParams = ParamStore.loadParams(wallpaperType, paramDefs);
        rebuildControls();
    }

    /**
     * 刷新面板（从 ParamStore 重新加载并更新控件值）
     */
    public void refresh() {
        if (wallpaperRenderer != null && paramDefs != null && paramDefs.length > 0) {
            currentParams = ParamStore.loadParams(wallpaperType, paramDefs);
            updateControlValues();
        }
    }

    /**
     * 根据 paramDefs 动态重建所有控件
     */
    private void rebuildControls() {
        container.removeAllViews();
        holders.clear();

        if (paramDefs == null || paramDefs.length == 0) {
            container.setVisibility(View.GONE);
            return;
        }
        container.setVisibility(View.VISIBLE);

        // 标题
        TextView title = new TextView(context);
        title.setText("壁纸参数");
        title.setTextColor(0xB3FFFFFF);
        title.setTextSize(14);
        title.setPadding(0, dpToPx(8), 0, dpToPx(4));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        title.setLayoutParams(titleLp);
        container.addView(title);

        for (ParamDef def : paramDefs) {
            ParamViewHolder holder = createControl(def);
            if (holder != null) {
                holders.add(holder);
            }
        }
    }

    /**
     * 更新已有控件的值
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
                    if (holder.valueLabel != null) {
                        holder.valueLabel.setText(formatValue(holder.def, val));
                    }
                }
                break;
        }
    }

    /**
     * 为单个 ParamDef 创建控件行
     */
    private ParamViewHolder createControl(final ParamDef def) {
        final float currentVal = currentParams.getFloat(def.key, def.defaultValue);

        // 行容器
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dpToPx(4), 0, dpToPx(4));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        row.setLayoutParams(rowLp);

        // 标签行：名称 + 当前值
        LinearLayout labelRow = new LinearLayout(context);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = new TextView(context);
        label.setText(def.label);
        label.setTextColor(0xB3FFFFFF);
        label.setTextSize(13);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        label.setLayoutParams(labelLp);

        TextView valueLabel = new TextView(context);
        valueLabel.setText(formatValue(def, currentVal));
        valueLabel.setTextColor(0xFF80CBC4);
        valueLabel.setTextSize(12);
        valueLabel.setGravity(Gravity.END);
        valueLabel.setMinWidth(dpToPx(50));
        LinearLayout.LayoutParams valLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        valLp.setMargins(dpToPx(8), 0, 0, 0);
        valueLabel.setLayoutParams(valLp);

        labelRow.addView(label);
        labelRow.addView(valueLabel);
        row.addView(labelRow);

        ParamViewHolder holder = new ParamViewHolder();
        holder.def = def;
        holder.valueLabel = valueLabel;
        holder.row = row;

        switch (def.type) {
            case FLOAT:
            case INT:
            case COLOR:
                holder.seekBar = createSeekBar(def, currentVal, holder);
                row.addView(holder.seekBar);
                break;
            case BOOL:
                holder.switchControl = createSwitch(def, currentVal, holder);
                labelRow.addView(holder.switchControl);
                break;
            case SELECT:
                // 单选芯片组（与主屏幕 WallpaperControlPanel 样式一致）
                if (def.options != null && def.options.length > 0) {
                    int curSel = Math.max(0, Math.min((int) currentVal, def.options.length - 1));
                    LinearLayout chipsRow = new LinearLayout(context);
                    chipsRow.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams chipsLp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    chipsLp.setMargins(0, dpToPx(4), 0, 0);
                    chipsRow.setLayoutParams(chipsLp);
                    holder.selectChips = new TextView[def.options.length];
                    for (int i = 0; i < def.options.length; i++) {
                        final int optIdx = i;
                        TextView chip = new TextView(context);
                        chip.setText(def.options[i]);
                        chip.setTextSize(12);
                        chip.setGravity(Gravity.CENTER);
                        chip.setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6));
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
                                float chipVal = optIdx;
                                currentParams.putFloat(def.key, chipVal);
                                // 刷新所有芯片的选中状态
                                for (int j = 0; j < holder.selectChips.length; j++) {
                                    boolean s = (j == optIdx);
                                    holder.selectChips[j].setBackgroundColor(s ? 0x4080CBC4 : 0x18FFFFFF);
                                    holder.selectChips[j].setTextColor(s ? 0xFFFFFFFF : 0x99FFFFFF);
                                }
                                if (holder.valueLabel != null) {
                                    holder.valueLabel.setText(def.options[optIdx]);
                                }
                                ParamStore.saveParam(wallpaperType, def.key, chipVal);
                                if (wallpaperRenderer != null) {
                                    wallpaperRenderer.applyParams(currentParams);
                                }
                            }
                        });
                        holder.selectChips[i] = chip;
                        chipsRow.addView(chip);
                    }
                    row.addView(chipsRow);
                }
                break;
        }

        container.addView(row);
        return holder;
    }

    private SeekBar createSeekBar(final ParamDef def, float currentVal, final ParamViewHolder holder) {
        SeekBar seekBar = new SeekBar(context);
        seekBar.setMax(100);
        seekBar.setProgress(valueToProgress(def, currentVal));

        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(36));
        sbLp.setMargins(0, dpToPx(2), 0, dpToPx(4));
        seekBar.setLayoutParams(sbLp);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                float val = progressToValue(def, progress);
                if (holder.valueLabel != null) {
                    holder.valueLabel.setText(formatValue(def, val));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 松手后持久化到 ParamStore
                float val = progressToValue(def, seekBar.getProgress());
                currentParams.putFloat(def.key, val);
                ParamStore.saveParam(wallpaperType, def.key, val);
                // 如果壁纸渲染器可用，立即应用
                if (wallpaperRenderer != null) {
                    wallpaperRenderer.applyParams(currentParams);
                }
            }
        });

        return seekBar;
    }

    private Switch createSwitch(final ParamDef def, float currentVal, final ParamViewHolder holder) {
        Switch sw = new Switch(context);
        sw.setChecked(currentVal >= 0.5f);
        sw.setTextColor(0xB3FFFFFF);

        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                float val = isChecked ? 1f : 0f;
                currentParams.putFloat(def.key, val);
                ParamStore.saveParam(wallpaperType, def.key, val);
                if (wallpaperRenderer != null) {
                    wallpaperRenderer.applyParams(currentParams);
                }
            }
        });

        return sw;
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
                return def.options != null && (int) value >= 0 && (int) value < def.options.length
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
    }
}
