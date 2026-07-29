package com.dlnaclock.screensaver.wallpaper;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.os.Bundle;

/**
 * AuroraWallpaper - 极光 V1 壁纸
 * 3-5 层正弦波浪带 + LinearGradient 渐变 + SCREEN 混合
 * 4 层 × 20 控制点
 */
public class AuroraWallpaper implements WallpaperRenderer {

    private int layerCount = 4;
    private static final int CONTROL_POINTS = 20;

    private Paint[] layerPaints;
    private Path[] layerPaths;

    // 每层的参数
    private float[] layerBaseY;
    private float[] layerAmplitude;
    private float[] layerBaseSpeed; // 基础速度，不含 speedMultiplier
    private float[] layerPhase;
    private float[] layerWaveLen;

    // HSV 复用
    private final float[] hsvTmp = new float[3];

    // 预分配渐变颜色数组（每层 3 色）
    private int[][] shiftedColorCache;
    private static final int[][] LAYER_COLORS = {
            {0x8000E676, 0x4000BFA5, 0x00000000},
            {0x7000B0FF, 0x3800E5FF, 0x00000000},
            {0x6076FF03, 0x3064DD17, 0x00000000},
            {0x501DE9B6, 0x2800BFA5, 0x00000000}
    };

    private int width;
    private int height;
    private boolean initialized = false;

    // 可调参数
    private float speedMultiplier = 1.0f;
    private int alpha = 80;
    private float hueShift = 0f;

    private static final ParamDef[] PARAMS = {
            new ParamDef("speed", "速度", 0.5f, 5f, 1.0f, 0.1f),
            new ParamDef("layers", "层数", 2, 8, 4),
            new ParamDef("alpha", "透明度", 30, 120, 80),
            new ParamDef("hueShift", "色调偏移", 0, 180, 0),
    };

    @Override
    public void init(int width, int height) {
        this.width = width;
        this.height = height;

        layerPaints = new Paint[layerCount];
        layerPaths = new Path[layerCount];
        layerBaseY = new float[layerCount];
        layerAmplitude = new float[layerCount];
        layerBaseSpeed = new float[layerCount];
        layerPhase = new float[layerCount];
        layerWaveLen = new float[layerCount];

        // 预分配渐变颜色缓存
        shiftedColorCache = new int[layerCount][];
        for (int i = 0; i < layerCount; i++) {
            shiftedColorCache[i] = new int[LAYER_COLORS[i % LAYER_COLORS.length].length];
        }

        for (int i = 0; i < layerCount; i++) {
            layerPaints[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
            layerPaints[i].setStyle(Paint.Style.FILL);
            layerPaints[i].setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
            layerPaths[i] = new Path();

            layerBaseY[i] = height * (0.25f + i * 0.12f);
            layerAmplitude[i] = height * (0.06f + i * 0.015f);
            layerBaseSpeed[i] = 0.3f + i * 0.15f;
            layerPhase[i] = i * 1.2f;
            layerWaveLen[i] = width / (2.0f + i * 0.5f);
        }

        initialized = true;
    }

    @Override
    public void draw(Canvas canvas, int width, int height, long elapsedMs) {
        if (!initialized) {
            init(width, height);
        }

        // 深色夜空背景
        canvas.drawColor(0xFF050510);

        canvas.save();

        float timeSec = elapsedMs / 1000f;
        float segWidth = (float) width / CONTROL_POINTS;

        for (int layer = 0; layer < layerCount; layer++) {
            Path path = layerPaths[layer];
            path.reset();

            float baseY = layerBaseY[layer];
            float amp = layerAmplitude[layer];
            float speed = layerBaseSpeed[layer] * speedMultiplier;
            float phase = layerPhase[layer];
            float waveLen = layerWaveLen[layer];

            // 起始点
            float startY = baseY + amp * (float) Math.sin(phase + timeSec * speed);
            path.moveTo(0, startY);

            // 使用 cubicTo 构建平滑波浪
            for (int i = 0; i < CONTROL_POINTS; i++) {
                float x0 = i * segWidth;
                float x1 = (i + 1) * segWidth;
                float xMid = (x0 + x1) / 2f;

                float angle0 = (x0 / waveLen) * 2f * (float) Math.PI + phase + timeSec * speed;
                float angle1 = (x1 / waveLen) * 2f * (float) Math.PI + phase + timeSec * speed;
                float angleMid = (xMid / waveLen) * 2f * (float) Math.PI + phase + timeSec * speed;

                float y0 = baseY + amp * (float) Math.sin(angle0);
                float y1 = baseY + amp * (float) Math.sin(angle1);
                float yMid = baseY + amp * (float) Math.sin(angleMid);

                path.cubicTo(x0 + segWidth * 0.33f, y0 + (yMid - y0) * 0.5f,
                        xMid, yMid, x1, y1);
            }

            // 封闭路径到底部
            path.lineTo(width, height);
            path.lineTo(0, height);
            path.close();

            // 设置渐变（应用色相偏移和透明度）
            float gradTop = baseY - amp;
            float gradBottom = baseY + height * 0.35f;
            int ci = layer % LAYER_COLORS.length;
            int[] shifted = shiftedColorCache[layer];
            for (int c = 0; c < LAYER_COLORS[ci].length; c++) {
                shifted[c] = shiftHue(LAYER_COLORS[ci][c], hueShift);
            }
            layerPaints[layer].setAlpha((int)(alpha * 2.55f));
            layerPaints[layer].setShader(new LinearGradient(
                    0, gradTop, 0, gradBottom,
                    shifted,
                    new float[]{0f, 0.4f, 1f},
                    Shader.TileMode.CLAMP));

            canvas.drawPath(path, layerPaints[layer]);
        }
        canvas.restore();
    }

    @Override
    public void release() {
        initialized = false;
    }

    @Override
    public ParamDef[] getParamDefs() { return PARAMS; }

    @Override
    public void applyParams(Bundle params) {
        speedMultiplier = params.getFloat("speed", 1.0f);
        int newLayers = (int) params.getFloat("layers", 4);
        alpha = (int) params.getFloat("alpha", 80);
        hueShift = params.getFloat("hueShift", 0f);
        if (newLayers != layerCount) {
            layerCount = newLayers;
            if (initialized) init(width, height);
        }
    }

    private int shiftHue(int color, float shiftDeg) {
        if (color == 0) return 0;
        Color.colorToHSV(color, hsvTmp);
        hsvTmp[0] = (hsvTmp[0] + shiftDeg) % 360f;
        if (hsvTmp[0] < 0f) hsvTmp[0] += 360f;
        return Color.HSVToColor(hsvTmp);
    }
}
