package com.dlnaclock.screensaver.wallpaper;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Bundle;

/**
 * ForestWallpaper - 森林壁纸
 * 3 层树木剪影视差滚动 + 渐变天空
 * 每层用 Path 生成锯齿状树冠轮廓
 */
public class ForestWallpaper implements WallpaperRenderer {

    private int layerCount = 3;

    private Paint skyPaint = new Paint();
    private Paint[] layerPaints;
    private Path[] layerPaths;
    private Paint fogPaint;
    private Paint celestialPaint;  // 太阳/月亮画笔
    private Paint celestialGlowPaint;  // 光晕画笔

    // 每层参数
    private float[] layerSpeed;
    private float[] layerBaseY;
    private float[] layerTreeHeight;
    private int[] layerColors = new int[3];

    private int width;
    private int height;
    private boolean initialized = false;

    // 可调参数
    private float speedMultiplier = 1.0f;
    private float treeDensity = 1.8f;
    private float fogAmount = 0.55f;
    private float hue = 162f;
    private float scale = 1.3f;
    private float brightness = 0.75f;  // 天空亮度
    private int celestialType = 2;    // 0=无, 1=太阳, 2=月亮, 3=太阳+月亮

    private static final String[] CELESTIAL_OPTIONS = {"无", "太阳", "月亮", "太阳+月亮"};

    private final float[] hsvTmp = new float[3];

    private static final ParamDef[] PARAMS = {
            new ParamDef("scale", "整体大小", 0.5f, 2f, 1.3f, 0.1f),
            new ParamDef("speed", "速度", 0.5f, 3f, 1.0f, 0.1f),
            new ParamDef("celestial", "天体", CELESTIAL_OPTIONS, 2),
            new ParamDef("treeDensity", "树木密度", 0.5f, 2f, 1.8f, 0.1f),
            new ParamDef("brightness", "天空亮度", 0.2f, 1.5f, 0.75f, 0.05f),
            new ParamDef("fogAmount", "雾气浓度", 0f, 1f, 0.55f, 0.05f),
            new ParamDef("hue", "色调", 0, 360, 162),
    };

    @Override
    public void init(int width, int height) {
        this.width = width;
        this.height = height;

        // 天空渐变画笔
        skyPaint.setStyle(Paint.Style.FILL);

        layerPaints = new Paint[layerCount];
        layerPaths = new Path[layerCount];
        layerSpeed = new float[layerCount];
        layerBaseY = new float[layerCount];
        layerTreeHeight = new float[layerCount];
        layerColors = new int[layerCount];

        fogPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fogPaint.setStyle(Paint.Style.FILL);

        celestialPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        celestialPaint.setStyle(Paint.Style.FILL);
        celestialGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        celestialGlowPaint.setStyle(Paint.Style.FILL);

        // 层参数：远→近，颜色从浅到深（应用色调偏移）
        int[] baseColors = {0xFF1A2A1A, 0xFF0F1F0F, 0xFF081008};
        for (int i = 0; i < layerCount; i++) {
            int ci = i * 3 / layerCount;
            int base = ci < baseColors.length ? baseColors[ci] : baseColors[baseColors.length - 1];
            layerColors[i] = shiftHue(base, hue - 120f);
        }

        float[] baseSpeeds = {3f, 6f, 12f};
        float[] baseY = {0.55f, 0.65f, 0.78f};
        float[] baseH = {0.18f, 0.22f, 0.28f};

        for (int i = 0; i < layerCount; i++) {
            int si = i * 3 / layerCount;
            layerSpeed[i] = baseSpeeds[Math.min(si, 2)] * speedMultiplier * treeDensity;
            layerBaseY[i] = height * baseY[Math.min(si, 2)];
            layerTreeHeight[i] = height * baseH[Math.min(si, 2)];

            layerPaints[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
            layerPaints[i].setStyle(Paint.Style.FILL);
            layerPaints[i].setColor(layerColors[i]);
            layerPaths[i] = new Path();
        }

        initialized = true;
    }

    @Override
    public void draw(Canvas canvas, int width, int height, long elapsedMs) {
        if (!initialized) {
            init(width, height);
        }

        float timeSec = elapsedMs / 1000f;

        canvas.save();
        canvas.scale(scale, scale, width / 2f, height / 2f);

        // 渐变天空（深蓝色夜空到暗橙色地平线）
        int skyAlpha = (int)(brightness * 255);
        skyPaint.setShader(new LinearGradient(0, 0, 0, height,
                new int[]{(skyAlpha << 24) | 0x0A0A20, (skyAlpha << 24) | 0x101830, (skyAlpha << 24) | 0x1A1020},
                new float[]{0f, 0.6f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, skyPaint);

        // 绘制太阳/月亮
        if (celestialType > 0) {
            drawCelestial(canvas, width, height, timeSec);
        }

        // 绘制 3 层树木剪影
        for (int layer = 0; layer < layerCount; layer++) {
            Path path = layerPaths[layer];
            path.reset();

            float baseY = layerBaseY[layer];
            float treeH = layerTreeHeight[layer];
            float offset = (timeSec * layerSpeed[layer]) % (width * 0.5f);

            // 生成锯齿状树冠轮廓
            float treeWidth = width * (0.04f + layer * 0.015f);
            int treeCount = (int) (width / treeWidth) + 4;

            path.moveTo(-treeWidth * 2, height);
            path.lineTo(-treeWidth * 2, baseY);

            for (int t = -2; t < treeCount; t++) {
                float x = t * treeWidth - offset;
                // 三角形树冠
                float tipY = baseY - treeH * (0.6f + 0.4f * TrigLut.sin(t * 1.7f + layer));
                float halfW = treeWidth * 0.5f;

                path.lineTo(x, baseY);
                path.lineTo(x + halfW * 0.3f, tipY + treeH * 0.3f);
                path.lineTo(x + halfW * 0.15f, tipY + treeH * 0.35f);
                path.lineTo(x + halfW * 0.5f, tipY);
                path.lineTo(x + halfW * 0.85f, tipY + treeH * 0.35f);
                path.lineTo(x + halfW * 0.7f, tipY + treeH * 0.3f);
                path.lineTo(x + treeWidth, baseY);
            }

            path.lineTo(width + treeWidth * 2, baseY);
            path.lineTo(width + treeWidth * 2, height);
            path.close();

            canvas.drawPath(path, layerPaints[layer]);
        }

        // 雾气叠加（底部半透明白色）
        if (fogAmount > 0.01f) {
            float fogHeight = height * fogAmount * 0.6f;
            fogPaint.setShader(new LinearGradient(0, height - fogHeight, 0, height,
                    new int[]{0x00000000, (int)(fogAmount * 180) << 24 | 0xAACCFF},
                    new float[]{0f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0, height - fogHeight, width, height, fogPaint);
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
        scale = params.getFloat("scale", 1.3f);
        speedMultiplier = params.getFloat("speed", 1.0f);
        celestialType = (int) params.getFloat("celestial", 2);
        treeDensity = params.getFloat("treeDensity", 1.8f);
        brightness = params.getFloat("brightness", 0.75f);
        fogAmount = params.getFloat("fogAmount", 0.55f);
        hue = params.getFloat("hue", 162f);
        if (initialized) init(width, height);
    }

    /** 绘制太阳/月亮 */
    private void drawCelestial(Canvas canvas, int w, int h, float timeSec) {
        // 天体从左侧缓慢移动到右侧，高度在天空上1/4区域
        float cycle = 120f; // 120秒一个周期
        float t = (timeSec % cycle) / cycle; // 0→1
        float cx = w * (0.1f + t * 0.8f); // 从左到右
        float cy = h * (0.15f + 0.08f * TrigLut.sin(t * (float) Math.PI)); // 弧线轨迹
        float baseRadius = Math.min(w, h) * 0.06f;

        if (celestialType == 1 || celestialType == 3) {
            // 太阳：暖橙色 + 光晕
            float sunRadius = baseRadius * 1.2f;
            float glowRadius = sunRadius * 3f;
            celestialGlowPaint.setShader(new RadialGradient(cx, cy, glowRadius,
                    new int[]{0x60FFD080, 0x20FFA040, 0x00000000},
                    new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, glowRadius, celestialGlowPaint);
            celestialPaint.setColor(0xFFFFF0D0);
            canvas.drawCircle(cx, cy, sunRadius, celestialPaint);
        }

        if (celestialType == 2 || celestialType == 3) {
            // 月亮：银白色（如果和太阳一起，偏移位置）
            float moonCX = cx + baseRadius * 3f * (celestialType == 3 ? 1f : 0f);
            float moonCY = cy - baseRadius * 0.5f;
            float moonRadius = baseRadius * 0.8f;
            float glowR = moonRadius * 2.5f;
            celestialGlowPaint.setShader(new RadialGradient(moonCX, moonCY, glowR,
                    new int[]{0x40C0D0FF, 0x1080A0C0, 0x00000000},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(moonCX, moonCY, glowR, celestialGlowPaint);
            celestialPaint.setColor(0xFFE8ECFF);
            canvas.drawCircle(moonCX, moonCY, moonRadius, celestialPaint);
            // 月牙阴影（模拟相位）
            celestialPaint.setColor(0xFF0A0A20);
            canvas.drawCircle(moonCX + moonRadius * 0.5f, moonCY - moonRadius * 0.2f, moonRadius * 0.7f, celestialPaint);
        }
    }

    private int shiftHue(int color, float shiftDeg) {
        Color.colorToHSV(color, hsvTmp);
        hsvTmp[0] = (hsvTmp[0] + shiftDeg) % 360f;
        if (hsvTmp[0] < 0f) hsvTmp[0] += 360f;
        return Color.HSVToColor(hsvTmp);
    }
}
