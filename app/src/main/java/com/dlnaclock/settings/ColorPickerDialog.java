package com.dlnaclock.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * ColorPickerDialog - HSV 色环颜色选择对话框
 * 包含：色环（Hue+Saturation）+ 亮度滑块 + 预览色块 + #RRGGBB hex 输入
 * 回调返回选中的 ARGB 颜色值
 */
public class ColorPickerDialog {

    /** OnColorSelectedListener - 颜色选择回调 */
    public interface OnColorSelectedListener {
        void onColorSelected(int color);
    }

    /** show - 显示颜色选择对话框 */
    public static void show(Context context, String title, int initialColor, final OnColorSelectedListener listener) {
        final ColorPickerView picker = new ColorPickerView(context, initialColor);

        // 包裹 ScrollView 以支持横屏小屏滚动
        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(picker, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(scrollView)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (listener != null) {
                            listener.onColorSelected(picker.getColor());
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * ColorPickerView - 自定义色环+亮度+预览+hex 的组合 View
     */
    static class ColorPickerView extends LinearLayout {

        private float hue = 0;
        private float saturation = 1;
        private float brightness = 1;

        private HueSatWheel hueSatWheel;
        private BrightnessSlider brightnessSlider;
        private View previewSwatch;
        private EditText hexInput;
        private boolean updatingFromHex = false;

        ColorPickerView(Context context, int initialColor) {
            super(context);
            setOrientation(VERTICAL);
            int pad = dp(16);
            setPadding(pad, pad, pad, pad);

            // 解析初始颜色
            float[] hsv = new float[3];
            Color.colorToHSV(initialColor, hsv);
            hue = hsv[0];
            saturation = hsv[1];
            brightness = hsv[2];

            // 根据屏幕方向决定色环高度
            boolean isLandscape = context.getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE;
            int wheelHeight = isLandscape ? dp(150) : dp(200);

            // 色环
            hueSatWheel = new HueSatWheel(context);
            addView(hueSatWheel, new LayoutParams(LayoutParams.MATCH_PARENT, wheelHeight));

            // 亮度标签
            TextView brightLabel = new TextView(context);
            brightLabel.setText("亮度");
            brightLabel.setTextColor(0xFFCCCCCC);
            brightLabel.setTextSize(14);
            LayoutParams labelParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = dp(12);
            addView(brightLabel, labelParams);

            // 亮度滑块
            brightnessSlider = new BrightnessSlider(context);
            LayoutParams sliderParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(40));
            sliderParams.topMargin = dp(4);
            addView(brightnessSlider, sliderParams);

            // 预览行：色块 + hex 输入
            LinearLayout previewRow = new LinearLayout(context);
            previewRow.setOrientation(HORIZONTAL);
            previewRow.setGravity(Gravity.CENTER_VERTICAL);
            LayoutParams rowParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = dp(12);
            addView(previewRow, rowParams);

            previewSwatch = new View(context);
            previewSwatch.setBackgroundColor(initialColor);
            LayoutParams swatchParams = new LayoutParams(dp(48), dp(48));
            swatchParams.rightMargin = dp(12);
            previewRow.addView(previewSwatch, swatchParams);

            hexInput = new EditText(context);
            hexInput.setHint("#RRGGBB");
            hexInput.setTextColor(0xFFFFFFFF);
            hexInput.setHintTextColor(0xFF666666);
            hexInput.setTextSize(16);
            hexInput.setSingleLine(true);
            hexInput.setText(String.format("#%06X", initialColor & 0xFFFFFF));
            LayoutParams hexParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
            previewRow.addView(hexInput, hexParams);

            // 色环触摸事件
            hueSatWheel.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    float cx = hueSatWheel.getWidth() / 2f;
                    float cy = hueSatWheel.getHeight() / 2f;
                    float dx = event.getX() - cx;
                    float dy = event.getY() - cy;
                    float radius = Math.min(cx, cy) * 0.9f;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);

                    if (dist <= radius * 1.1f) {
                        // 计算角度 -> hue (0-360)
                        // 绘制使用 drawArc(oval, i-90, ...) 即 hue=0 在顶部
                        // atan2 返回 0° 在右侧，需要 +90° 对齐
                        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
                        hue = (angle + 90 + 360) % 360;
                        // 计算距离 -> saturation (0-1)
                        saturation = Math.min(dist / radius, 1f);
                        updateFromHSV();
                    }
                    return true;
                }
            });

            // 亮度滑块触摸事件
            brightnessSlider.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    float x = event.getX();
                    float w = brightnessSlider.getWidth();
                    brightness = Math.max(0, Math.min(1, x / w));
                    updateFromHSV();
                    return true;
                }
            });

            // hex 输入监听
            hexInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    if (updatingFromHex) return;
                    String text = s.toString().replace("#", "").trim();
                    if (text.length() == 6) {
                        try {
                            int rgb = Integer.parseInt(text, 16);
                            float[] hsv = new float[3];
                            Color.colorToHSV(0xFF000000 | rgb, hsv);
                            hue = hsv[0];
                            saturation = hsv[1];
                            brightness = hsv[2];
                            updateFromHSV();
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }
                }
            });

            updateFromHSV();
        }

        /** updateFromHSV - 从当前 HSV 值刷新所有子视图 */
        private void updateFromHSV() {
            int color = getColor();
            previewSwatch.setBackgroundColor(color);
            hueSatWheel.invalidate();
            brightnessSlider.invalidate();

            // 更新 hex 输入（避免循环）
            updatingFromHex = true;
            hexInput.setText(String.format("#%06X", color & 0xFFFFFF));
            hexInput.setSelection(hexInput.getText().length());
            updatingFromHex = false;
        }

        /** getColor - 获取当前选中的 ARGB 颜色 */
        int getColor() {
            float[] hsv = {hue, saturation, brightness};
            return Color.HSVToColor(255, hsv);
        }

        private int dp(int value) {
            return (int) (value * getContext().getResources().getDisplayMetrics().density + 0.5f);
        }

        /**
         * HueSatWheel - 色环（Hue 角度 + Saturation 半径）绘制 View
         */
        class HueSatWheel extends View {
            private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            HueSatWheel(Context context) {
                super(context);
                indicatorPaint.setStyle(Paint.Style.STROKE);
                indicatorPaint.setStrokeWidth(3);
                indicatorPaint.setColor(0xFFFFFFFF);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float cx = getWidth() / 2f;
                float cy = getHeight() / 2f;
                float radius = Math.min(cx, cy) * 0.9f;

                // 绘制色环（36度扇形 × 10）
                RectF oval = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
                Paint sectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                for (int i = 0; i < 360; i += 2) {
                    float[] hsv = {i, 1f, 1f};
                    sectorPaint.setColor(Color.HSVToColor(hsv));
                    canvas.drawArc(oval, i - 90, 3, true, sectorPaint);
                }

                // 绘制饱和度渐变覆盖层（从中心白色到边缘透明）
                Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                centerPaint.setShader(new android.graphics.RadialGradient(
                        cx, cy, radius,
                        0xFFFFFFFF, 0x00FFFFFF,
                        Shader.TileMode.CLAMP));
                canvas.drawCircle(cx, cy, radius, centerPaint);

                // 绘制当前选择位置指示器
                double angle = Math.toRadians(hue - 90);
                float ix = cx + (float) (radius * saturation * Math.cos(angle));
                float iy = cy + (float) (radius * saturation * Math.sin(angle));
                indicatorPaint.setColor(brightness > 0.5f ? 0xFF000000 : 0xFFFFFFFF);
                canvas.drawCircle(ix, iy, 8, indicatorPaint);
                indicatorPaint.setColor(brightness > 0.5f ? 0xFFFFFFFF : 0xFF000000);
                canvas.drawCircle(ix, iy, 5, indicatorPaint);
            }
        }

        /**
         * BrightnessSlider - 亮度滑块
         */
        class BrightnessSlider extends View {
            private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            BrightnessSlider(Context context) {
                super(context);
                indicatorPaint.setStyle(Paint.Style.STROKE);
                indicatorPaint.setStrokeWidth(2);
                indicatorPaint.setColor(0xFFFFFFFF);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                int w = getWidth();
                int h = getHeight();

                // 绘制亮度渐变条
                float[] hsvStart = {hue, saturation, 0f};
                float[] hsvEnd = {hue, saturation, 1f};
                int colorStart = Color.HSVToColor(hsvStart);
                int colorEnd = Color.HSVToColor(hsvEnd);

                paint.setShader(new LinearGradient(0, 0, w, 0, colorStart, colorEnd, Shader.TileMode.CLAMP));
                RectF rect = new RectF(0, h * 0.25f, w, h * 0.75f);
                canvas.drawRoundRect(rect, 4, 4, paint);

                // 绘制当前位置指示器
                float ix = brightness * w;
                indicatorPaint.setColor(0xFFFFFFFF);
                canvas.drawCircle(ix, h / 2f, h * 0.35f, indicatorPaint);
                paint.setShader(null);
                paint.setColor(getColor());
                canvas.drawCircle(ix, h / 2f, h * 0.25f, paint);
            }
        }
    }
}
