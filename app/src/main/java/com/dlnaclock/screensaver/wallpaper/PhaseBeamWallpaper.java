package com.dlnaclock.screensaver.wallpaper;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;

/**
 * PhaseBeamWallpaper - 相位光束壁纸
 * 水平光束粒子 + LinearGradient 拖尾 + ADD 叠加
 * 15 条光束，HSL 色相缓慢变化
 */
public class PhaseBeamWallpaper implements WallpaperRenderer {

    private int beamCount = 15;

    private Paint beamPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RectF beamRect = new RectF();

    // 每条光束参数
    private float[] beamY;
    private float[] beamBaseSpeed; // 基础速度，不含 speedMultiplier
    private float[] beamThickness;
    private float[] beamPhase;
    private float[] beamHueOffset;
    private float[] beamLength;

    private int width;
    private int height;
    private boolean initialized = false;

    // 可调参数
    private float speedMultiplier = 1.0f;
    private float brightness = 0.8f;
    private float hue = 200f;
    private float scale = 1.0f;

    private static final ParamDef[] PARAMS = {
            new ParamDef("scale", "整体大小", 0.5f, 2f, 1.0f, 0.1f),
            new ParamDef("speed", "速度", 0.5f, 5f, 1.0f, 0.1f),
            new ParamDef("beams", "光束数量", 3, 20, 15),
            new ParamDef("brightness", "亮度", 0.3f, 1.0f, 0.8f, 0.05f),
            new ParamDef("hue", "色调", 0, 360, 200),
    };

    // 预分配 HSV 数组
    private float[] hsv = new float[3];

    @Override
    public void init(int width, int height) {
        this.width = width;
        this.height = height;

        beamPaint.setStyle(Paint.Style.FILL);
        beamPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));

        allocateBeams();

        initialized = true;
    }

    private void allocateBeams() {
        beamY = new float[beamCount];
        beamBaseSpeed = new float[beamCount];
        beamThickness = new float[beamCount];
        beamPhase = new float[beamCount];
        beamHueOffset = new float[beamCount];
        beamLength = new float[beamCount];

        for (int i = 0; i < beamCount; i++) {
            float t = (float) i / beamCount;
            beamY[i] = height * (0.1f + t * 0.8f);
            beamBaseSpeed[i] = 40f + (float) Math.random() * 80f;
            beamThickness[i] = 2f + (float) Math.random() * 4f;
            beamPhase[i] = (float) Math.random() * width;
            beamHueOffset[i] = t * 120f;
            beamLength[i] = width * (0.3f + (float) Math.random() * 0.4f);
        }

        initialized = true;
    }

    @Override
    public void draw(Canvas canvas, int width, int height, long elapsedMs) {
        if (!initialized) {
            init(width, height);
        }

        // 纯黑背景
        canvas.drawColor(0xFF000008);

        canvas.save();
        canvas.scale(scale, scale, width / 2f, height / 2f);

        float timeSec = elapsedMs / 1000f;
        // 全局色相缓慢旋转（基于用户设置的 hue）
        float baseHue = (hue + timeSec * 10f) % 360f;

        for (int i = 0; i < beamCount; i++) {
            // 光束水平位置（循环移动）
            float x = (beamPhase[i] + timeSec * beamBaseSpeed[i] * speedMultiplier) % (width + beamLength[i]) - beamLength[i];

            // 垂直微摆
            float yOffset = beamY[i] + (float) Math.sin(timeSec * 0.5f + i) * 8f;

            float beamLen = beamLength[i];
            float thick = beamThickness[i];

            // 色相（应用 brightness）
            float bhue = (baseHue + beamHueOffset[i]) % 360f;
            hsv[0] = bhue;
            hsv[1] = 0.9f;
            hsv[2] = brightness;
            int color = android.graphics.Color.HSVToColor(hsv);

            // 光束渐变：两端 alpha=0，中间最亮
            int colorMid = color & 0x00FFFFFF | 0xC0000000;
            int colorEnd = color & 0x00FFFFFF;

            beamPaint.setShader(new LinearGradient(
                    x, 0, x + beamLen, 0,
                    new int[]{colorEnd, colorMid, colorEnd},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP));

            beamRect.set(x, yOffset - thick / 2f, x + beamLen, yOffset + thick / 2f);
            canvas.drawRoundRect(beamRect, thick / 2f, thick / 2f, beamPaint);
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
        int newBeams = (int) params.getFloat("beams", 15);
        brightness = params.getFloat("brightness", 0.8f);
        hue = params.getFloat("hue", 200f);
        if (newBeams != beamCount) {
            beamCount = newBeams;
            if (initialized) allocateBeams();
        }
    }
}
