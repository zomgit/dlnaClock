package com.dlnaclock.screensaver.wallpaper;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.Bundle;

/**
 * MagicSmokeWallpaper - 魔烟壁纸
 *
 * 多层半透明烟雾叠加 + 缓慢形变 + alpha 混合
 * 5 层 Path，正弦驱动控制点偏移
 *
 * 实现：5 条水平烟雾带像极光一样横跨屏幕，每条带由正弦波
 * 复合驱动上下飘动。通过三层不同粗细/透明度的描边叠加
 * 模拟柔边烟雾效果，层间以 SCREEN 模式混合。
 */
public class MagicSmokeWallpaper implements WallpaperRenderer {

    // ── 结构常量 ──
    private static final int LAYERS = 5;
    private static final int PATH_SAMPLES = 60; // 路径采样点数

    // ── 烟雾基础色（紫/蓝/青，半透明可见） ──
    private static final int[] BASE_COLORS = {
            0xFF7020D0, // 深紫
            0xFF2050DD, // 蓝紫
            0xFF1090AA, // 青蓝
            0xFF8820AA, // 品紫
            0xFF205080, // 暗蓝
    };

    // ═══════════════════════════════════════
    //  预分配数据（零 GC）
    // ═══════════════════════════════════════

    private Path[]  layerPaths;
    private Paint   smokePaint;

    // 每层烟雾带参数
    private final float[] baseY      = new float[LAYERS]; // 垂直中心
    private final float[] amplitude  = new float[LAYERS]; // 波动幅度
    private final float[] thickness  = new float[LAYERS]; // 带厚度
    private final float[] freqX      = new float[LAYERS]; // 水平频率
    private final float[] speed      = new float[LAYERS]; // 动画速度
    private final float[] phase      = new float[LAYERS]; // 初始相位
    private final int[]   bandColors = new int[LAYERS];   // 颜色

    // ── 画布 ──
    private int width;
    private int height;
    private boolean initialized;

    // ── 可调参数 ──
    private float scale    = 1.0f;
    private float speedMul = 1.0f;
    private float density  = 1.0f;
    private float userAlpha = 70f;
    private float hueShift = 0f;

    private final float[] hsvTmp = new float[3];

    private static final ParamDef[] PARAMS = {
            new ParamDef("scale",   "整体大小", 0.3f, 2.5f, 1.0f, 0.1f),
            new ParamDef("speed",   "流动速度", 0.1f, 5f,   1.0f, 0.1f),
            new ParamDef("density", "烟雾浓度", 0.3f, 2f,   1.0f, 0.1f),
            new ParamDef("alpha",   "透明度",   20,   100,  70),
            new ParamDef("hue",     "色相偏移", 0,    360,  0),
    };

    // ═══════════════════════════════════════
    //  WallpaperRenderer 接口
    // ═══════════════════════════════════════

    @Override
    public void init(int width, int height) {
        if (width <= 0 || height <= 0) return;
        this.width = width;
        this.height = height;

        // 共用一支 SCREEN 混合画笔（描边模式，改粗细/透明度复用）
        smokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        smokePaint.setStyle(Paint.Style.STROKE);
        smokePaint.setStrokeCap(Paint.Cap.ROUND);
        smokePaint.setStrokeJoin(Paint.Join.ROUND);
        smokePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));

        layerPaths = new Path[LAYERS];
        for (int l = 0; l < LAYERS; l++) {
            layerPaths[l] = new Path();
            // 均匀分布在屏幕纵向上
            baseY[l]      = height * (0.10f + l * 0.20f);
            // 振幅越上层越大
            amplitude[l]  = height * (0.06f + l * 0.025f);
            // 厚度：中间层最厚
            thickness[l]  = height * (0.04f + Math.abs(l - 2) * 0.005f);
            // 水平频率各不相同
            freqX[l]      = 0.003f + l * 0.0008f;
            // 速度各不相同
            speed[l]      = 0.25f + l * 0.12f;
            // 随机初相
            phase[l]      = (float) (Math.random() * Math.PI * 2.0);
            // 颜色
            bandColors[l] = BASE_COLORS[l % BASE_COLORS.length];
        }

        initialized = true;
    }

    @Override
    public void draw(Canvas canvas, int width, int height, long elapsedMs) {
        if (!initialized) {
            init(width, height);
        }

        // 深邃暗色背景
        canvas.drawColor(0xFF040408);

        float timeSec = elapsedMs / 1000f;
        float margin  = width * 0.08f; // 路径超出屏幕两侧避免断头

        for (int layer = 0; layer < LAYERS; layer++) {
            Path p = layerPaths[layer];
            p.reset();

            float spd = speed[layer] * speedMul;
            float ph  = phase[layer];
            float amp = amplitude[layer] * scale;
            float thk = thickness[layer] * scale * density;
            float frq = freqX[layer];
            int   baseColor = bandColors[layer];

            // ── 构建中心线 Path（复合正弦波，有机飘动感） ──
            float step = (width + margin * 2f) / PATH_SAMPLES;
            for (int i = 0; i <= PATH_SAMPLES; i++) {
                float x = -margin + i * step;
                // 主波 + 次波叠加，创造不规则有机形态
                float y = baseY[layer]
                        + amp * TrigLut.sin(x * frq + ph + timeSec * spd)
                        + amp * 0.35f * TrigLut.sin(x * frq * 2.3f + ph * 1.7f + timeSec * spd * 0.65f)
                        + amp * 0.15f * TrigLut.sin(x * frq * 0.5f + ph * 0.3f + timeSec * spd * 1.4f);

                if (i == 0) p.moveTo(x, y);
                else p.lineTo(x, y);
            }

            // ── 三层叠加描边模拟柔边烟雾 ──
            int color = shiftHue(baseColor, hueShift) & 0x00FFFFFF;
            float baseAlpha = userAlpha * 2.55f * density;

            // 外层辉光：最粗、最透
            smokePaint.setStrokeWidth(thk * 2.8f);
            smokePaint.setAlpha(clamp((int) (baseAlpha * 0.10f), 3, 35));
            smokePaint.setColor(color | (((int)(baseAlpha * 0.10f)) << 24));
            canvas.drawPath(p, smokePaint);

            // 中层过渡：中等粗细、半透
            smokePaint.setStrokeWidth(thk * 1.3f);
            int midA = clamp((int) (baseAlpha * 0.35f), 5, 110);
            smokePaint.setAlpha(midA);
            smokePaint.setColor(color | ((midA << 24) & 0xFF000000));
            canvas.drawPath(p, smokePaint);

            // 内层核心：细、浓
            smokePaint.setStrokeWidth(thk * 0.45f);
            int coreA = clamp((int) (baseAlpha * 0.75f), 10, 210);
            smokePaint.setAlpha(coreA);
            smokePaint.setColor(color | ((coreA << 24) & 0xFF000000));
            canvas.drawPath(p, smokePaint);
        }
    }

    @Override
    public void release() {
        initialized = false;
        smokePaint = null;
        layerPaths = null;
    }

    @Override
    public ParamDef[] getParamDefs() {
        return PARAMS;
    }

    @Override
    public void applyParams(Bundle params) {
        scale     = params.getFloat("scale",   1.0f);
        speedMul  = params.getFloat("speed",   1.0f);
        density   = params.getFloat("density", 1.0f);
        userAlpha = params.getFloat("alpha",   70f);
        hueShift  = params.getFloat("hue",     0f);
        if (scale    < 0.1f) scale    = 1.0f;
        if (speedMul < 0.05f) speedMul = 1.0f;
        if (density  < 0.1f) density  = 1.0f;
    }

    // ═══════════════════════════════════════
    //  工具
    // ═══════════════════════════════════════

    private int shiftHue(int color, float degrees) {
        if (degrees == 0f) return color;
        Color.colorToHSV(color, hsvTmp);
        hsvTmp[0] = (hsvTmp[0] + degrees) % 360f;
        if (hsvTmp[0] < 0f) hsvTmp[0] += 360f;
        return Color.HSVToColor(Color.alpha(color), hsvTmp);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
