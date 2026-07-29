package com.dlnaclock.screensaver.wallpaper;

import android.graphics.Canvas;
import android.graphics.Camera;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Bundle;

/**
 * HoloSpiralWallpaper - 全息螺旋壁纸
 * 阿基米德螺旋点阵 + 旋转 + RadialGradient 发光
 * 200 个螺旋点，预计算 sin/cos LUT
 */
public class HoloSpiralWallpaper implements WallpaperRenderer {

    private static final int LUT_SIZE = 360;
    private static final int MAX_POINTS = 400;  // 小米3骁龙800安全上限

    // 预计算 sin/cos 查找表
    private float[] sinLut = new float[LUT_SIZE];
    private float[] cosLut = new float[LUT_SIZE];

    // 螺旋点预计算位置（归一化坐标）
    private float[] pointTheta;
    private float[] pointRadius;
    private float[] pointSize;
    private int[] pointColorIndex;

    private Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // 3D Y轴旋转用 Camera + Matrix
    private Camera camera3d;
    private Matrix rotationMatrix;

    private int width;
    private int height;
    private float centerX;
    private float centerY;
    private float maxRadius;
    private boolean initialized = false;

    // 可调参数
    private int pointCount = 73;
    private float speedMultiplier = 1.5f;
    private int armCount = 7;
    private float hueShift = 58f;
    private float glowIntensity = 0.4f;
    private float particleSize = 2.6f;
    private float rotation3d = 248f;
    private int rotationAxis = 1; // 0=无, 1=X轴, 2=Y轴, 3=Z轴
    private float particleGlow = 0.45f; // 粒子发光强度 0~1

    private static final String[] AXIS_OPTIONS = {"无", "X轴", "Y轴", "Z轴"};

    // HSV 复用数组
    private final float[] hsvTmp = new float[3];

    // 色相偏移后的调色板（每帧更新）
    private int[] shiftedPalette = new int[PALETTE.length];

    // 颜色调色板（青-蓝-紫）
    private static final int[] PALETTE = {
            0xFF00E5FF, 0xFF2979FF, 0xFF7C4DFF,
            0xFF00BFA5, 0xFF448AFF, 0xFFB388FF
    };

    private static final ParamDef[] PARAMS = {
            new ParamDef("speed", "旋转速度", 0.1f, 5f, 1.5f, 0.1f),
            new ParamDef("arms", "螺旋臂数", 1, 10, 7),
            new ParamDef("pointCount", "粒子数量", 50, MAX_POINTS, 73),
            new ParamDef("particleSize", "粒子大小", 0.5f, 3f, 2.6f, 0.1f),
            new ParamDef("particleGlow", "粒子发光", 0f, 1f, 0.45f, 0.05f),
            new ParamDef("axis3d", "旋转轴", AXIS_OPTIONS, 1),
            new ParamDef("rotation3d", "3D旋转角", 0, 360, 248),
            new ParamDef("hue", "色调偏移", 0, 360, 58),
            new ParamDef("glow", "中心发光", 0f, 1f, 0.4f, 0.05f),
    };

    @Override
    public void init(int width, int height) {
        this.width = width;
        this.height = height;
        this.centerX = width / 2f;
        this.centerY = height / 2f;
        this.maxRadius = Math.min(width, height) * 0.42f;

        // 预计算 sin/cos LUT
        for (int i = 0; i < LUT_SIZE; i++) {
            double rad = Math.toRadians(i);
            sinLut[i] = (float) Math.sin(rad);
            cosLut[i] = (float) Math.cos(rad);
        }

        // 初始化 3D 旋转资源
        camera3d = new Camera();
        rotationMatrix = new Matrix();

        allocateArrays();

        // 设置发光画笔
        glowPaint.setStyle(Paint.Style.FILL);

        initialized = true;
    }

    private void allocateArrays() {
        // 安全限制：防止粒子数过多导致OOM
        if (pointCount > MAX_POINTS) pointCount = MAX_POINTS;
        pointTheta = new float[pointCount];
        pointRadius = new float[pointCount];
        pointSize = new float[pointCount];
        pointColorIndex = new int[pointCount];

        // 预计算阿基米德螺旋点 r = a + b * theta
        float a = 0.02f;
        float totalTurns = 2f + armCount * 0.75f;
        float maxTheta = totalTurns * 2f * (float) Math.PI;

        for (int i = 0; i < pointCount; i++) {
            float t = (float) i / pointCount;
            pointTheta[i] = t * maxTheta + (i % armCount) * (2f * (float) Math.PI / armCount);
            pointRadius[i] = (a + (1f - a) * t) * maxRadius;
            pointSize[i] = 2f + t * 4f;
            pointColorIndex[i] = i % PALETTE.length;
        }
    }

    @Override
    public void draw(Canvas canvas, int width, int height, long elapsedMs) {
        if (!initialized) {
            init(width, height);
        }

        // 深色背景
        canvas.drawColor(0xFF0A0A14);

        float timeSec = elapsedMs / 1000f;
        // 旋转角度（缓慢旋转）
        float rotationDeg = (timeSec * 15f * speedMultiplier) % 360f;
        int rotIdx = ((int) rotationDeg) % LUT_SIZE;

        canvas.save();
        canvas.translate(centerX, centerY);

        // 应用 3D 旋转透视效果
        if (rotation3d > 0.5f && rotationAxis > 0) {
            camera3d.save();
            switch (rotationAxis) {
                case 1: camera3d.rotateX(rotation3d); break;
                case 2: camera3d.rotateY(rotation3d); break;
                case 3: camera3d.rotateZ(rotation3d); break;
            }
            camera3d.getMatrix(rotationMatrix);
            camera3d.restore();
            canvas.concat(rotationMatrix);
        }

        canvas.rotate(rotationDeg);

        // 应用色相偏移到调色板
        for (int p = 0; p < PALETTE.length; p++) {
            shiftedPalette[p] = shiftHue(PALETTE[p], hueShift - 200f);
        }

        // 中心发光
        float glowRadius = maxRadius * 0.3f;
        int glowAlpha = (int)(0x40 * glowIntensity);
        glowPaint.setShader(new RadialGradient(0, 0, glowRadius,
                (glowAlpha << 24) | 0x00E5FF, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawCircle(0, 0, glowRadius, glowPaint);

        // 绘制螺旋点
        for (int i = 0; i < pointCount; i++) {
            float theta = pointTheta[i];
            float r = pointRadius[i];

            // 使用 LUT 计算位置
            int idx = ((int) (theta * 180f / Math.PI)) % LUT_SIZE;
            if (idx < 0) idx += LUT_SIZE;
            float x = r * cosLut[idx];
            float y = r * sinLut[idx];

            // 脉动 alpha
            float pulse = 0.5f + 0.5f * sinLut[((int) (timeSec * 60f * speedMultiplier + i * 3f)) % LUT_SIZE];
            int alpha = (int) (100 + 155 * pulse);

            int color = shiftedPalette[pointColorIndex[i]];
            pointPaint.setColor(color);
            pointPaint.setAlpha(alpha);

            float size = pointSize[i] * (0.8f + 0.4f * pulse) * particleSize;

            // 粒子发光/柔光：先绘制更大的半透明光晕
            if (particleGlow > 0.01f) {
                float glowSize = size * (1f + particleGlow * 2.5f);
                int ptGlowAlpha = (int) (alpha * particleGlow * 0.4f);
                pointPaint.setAlpha(ptGlowAlpha);
                canvas.drawCircle(x, y, glowSize, pointPaint);
                pointPaint.setAlpha(alpha);
            }

            canvas.drawCircle(x, y, size, pointPaint);
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
        speedMultiplier = params.getFloat("speed", 1.5f);
        int newArms = (int) params.getFloat("arms", 7);
        int newCount = (int) params.getFloat("pointCount", 73);
        particleSize = params.getFloat("particleSize", 2.6f);
        particleGlow = params.getFloat("particleGlow", 0.45f);
        rotation3d = params.getFloat("rotation3d", 248f);
        rotationAxis = (int) params.getFloat("axis3d", 1);
        hueShift = params.getFloat("hue", 58f);
        glowIntensity = params.getFloat("glow", 0.4f);
        if (newArms != armCount || newCount != pointCount) {
            armCount = newArms;
            pointCount = newCount;
            if (initialized) allocateArrays();
        }
    }

    /** 对 ARGB 颜色应用色相偏移 */
    private int shiftHue(int color, float shiftDeg) {
        Color.colorToHSV(color, hsvTmp);
        hsvTmp[0] = (hsvTmp[0] + shiftDeg) % 360f;
        if (hsvTmp[0] < 0f) hsvTmp[0] += 360f;
        return Color.HSVToColor(hsvTmp);
    }
}
