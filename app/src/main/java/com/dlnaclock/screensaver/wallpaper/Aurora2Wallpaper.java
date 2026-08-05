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
 * Aurora2Wallpaper - 极光 V2 壁纸
 * 更绚烂的极光色彩变体，增加粉/橙色调
 * 5 层 × 25 控制点
 */
public class Aurora2Wallpaper implements WallpaperRenderer {

    private int layerCount = 3;
    private static final int CONTROL_POINTS = 25;

    private Paint[] layerPaints;
    private Path[] layerPaths;

    private float[] layerBaseY;
    private float[] layerAmplitude;
    private float[] layerBaseSpeed; // 基础速度，不含 speedMultiplier
    private float[] layerPhase;
    private float[] layerWaveLen;
    private float[] layerFreq2;

    // 粉/橙/紫绚烂色调
    private static final int[][] LAYER_COLORS = {
            {0x90FF4081, 0x48F50057, 0x00000000},
            {0x80FF6E40, 0x40FF3D00, 0x00000000},
            {0x70E040FB, 0x38AA00FF, 0x00000000},
            {0x607C4DFF, 0x30651FFF, 0x00000000},
            {0x50FF80AB, 0x28FF4081, 0x00000000}
    };

    private int width;
    private int height;
    private boolean initialized = false;

    // 可调参数
    private float speedMultiplier = 1.0f;
    private int alpha = 64;
    private float hueShift = 11f;
    private float scale = 1.0f;

    private final float[] hsvTmp = new float[3];

    // 预分配渐变颜色缓存
    private int[][] shiftedColorCache;

    private static final ParamDef[] PARAMS = {
            new ParamDef("scale", "整体大小", 0.5f, 2f, 1.0f, 0.1f),
            new ParamDef("speed", "速度", 0.5f, 5f, 1.0f, 0.1f),
            new ParamDef("layers", "层数", 2, 8, 3),
            new ParamDef("alpha", "透明度", 30, 120, 64),
            new ParamDef("hueShift", "色调偏移", 0, 180, 11),
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
        layerFreq2 = new float[layerCount];

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

            layerBaseY[i] = height * (0.2f + i * 0.11f);
            layerAmplitude[i] = height * (0.05f + i * 0.018f);
            layerBaseSpeed[i] = 0.25f + i * 0.12f;
            layerPhase[i] = i * 0.9f;
            layerWaveLen[i] = width / (1.8f + i * 0.4f);
            layerFreq2[i] = 2.5f + i * 0.7f;
        }

        initialized = true;
    }

    @Override
    public void draw(Canvas canvas, int width, int height, long elapsedMs) {
        if (!initialized) {
            init(width, height);
        }

        // 深紫黑背景
        canvas.drawColor(0xFF080412);

        canvas.save();
        canvas.scale(scale, scale, width / 2f, height / 2f);

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
            float freq2 = layerFreq2[layer];

            // 起始点（叠加二次谐波使形状更复杂）
            float startY = baseY + amp * TrigLut.sin(phase + timeSec * speed)
                    + amp * 0.3f * TrigLut.sin(freq2 * phase + timeSec * speed * 1.7f);
            path.moveTo(0, startY);

            for (int i = 0; i < CONTROL_POINTS; i++) {
                float x0 = i * segWidth;
                float x1 = (i + 1) * segWidth;
                float xMid = (x0 + x1) / 2f;

                float angle0 = (x0 / waveLen) * 2f * (float) Math.PI + phase + timeSec * speed;
                float angle1 = (x1 / waveLen) * 2f * (float) Math.PI + phase + timeSec * speed;
                float angleMid = (xMid / waveLen) * 2f * (float) Math.PI + phase + timeSec * speed;

                float y0 = baseY + amp * TrigLut.sin(angle0)
                        + amp * 0.3f * TrigLut.sin(freq2 * angle0 + timeSec * speed * 1.7f);
                float y1 = baseY + amp * TrigLut.sin(angle1)
                        + amp * 0.3f * TrigLut.sin(freq2 * angle1 + timeSec * speed * 1.7f);
                float yMid = baseY + amp * TrigLut.sin(angleMid)
                        + amp * 0.3f * TrigLut.sin(freq2 * angleMid + timeSec * speed * 1.7f);

                path.cubicTo(x0 + segWidth * 0.33f, y0 + (yMid - y0) * 0.5f,
                        xMid, yMid, x1, y1);
            }

            path.lineTo(width, height);
            path.lineTo(0, height);
            path.close();

            float gradTop = baseY - amp * 1.5f;
            float gradBottom = baseY + height * 0.3f;
            int ci = layer % LAYER_COLORS.length;
            int[] shifted = shiftedColorCache[layer];
            for (int c = 0; c < LAYER_COLORS[ci].length; c++) {
                shifted[c] = shiftHue(LAYER_COLORS[ci][c], hueShift);
            }
            layerPaints[layer].setAlpha((int)(alpha * 2.55f));
            layerPaints[layer].setShader(new LinearGradient(
                    0, gradTop, 0, gradBottom,
                    shifted,
                    new float[]{0f, 0.35f, 1f},
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
        scale = params.getFloat("scale", 1.0f);
        speedMultiplier = params.getFloat("speed", 1.0f);
        int newLayers = (int) params.getFloat("layers", 3);
        alpha = (int) params.getFloat("alpha", 64);
        hueShift = params.getFloat("hueShift", 11f);
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
