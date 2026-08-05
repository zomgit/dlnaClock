package com.dlnaclock.screensaver.wallpaper;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;

/**
 * CubeWallpaper - 立方体壁纸
 * 3D 线框几何体旋转（透视投影）
 * 8 顶点 × 12 边，手动矩阵运算
 */
public class CubeWallpaper implements WallpaperRenderer, GestureAwareWallpaper {

    // 立方体 8 个顶点（归一化 -1~1）
    private static final float[][] VERTICES = {
            {-1, -1, -1}, {1, -1, -1}, {1, 1, -1}, {-1, 1, -1},
            {-1, -1,  1}, {1, -1,  1}, {1, 1,  1}, {-1, 1,  1}
    };

    // 12 条边（顶点索引对）
    private static final int[][] EDGES = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0}, // 后面
            {4, 5}, {5, 6}, {6, 7}, {7, 4}, // 前面
            {0, 4}, {1, 5}, {2, 6}, {3, 7}  // 连接
    };

    private Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint vertexPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // 投影后的 2D 坐标（预分配）
    private float[] projX = new float[8];
    private float[] projY = new float[8];

    private float centerX;
    private float centerY;
    private float projScale;
    private int width;
    private int height;
    private boolean initialized = false;

    // 可调参数
    private float speedMultiplier = 1.0f;
    private float cubeSize = 2.8f;
    private int lineWidth = 19;
    private float hueParam = 72f;

    // 手势叠加
    private float gestureRotX, gestureRotY;
    private float gestureOffsetX, gestureOffsetY;
    private float gestureScale = 1f;

    private static final ParamDef[] PARAMS = {
            new ParamDef("cubeSize", "立方体大小", 0.5f, 6f, 2.8f, 0.1f),
            new ParamDef("speed", "速度", 0.5f, 5f, 1.0f, 0.1f),
            new ParamDef("lineWidth", "线宽", 1, 100, 19),
            new ParamDef("hue", "色调", 0, 360, 72),
    };

    @Override
    public void init(int width, int height) {
        this.width = width;
        this.height = height;
        this.centerX = width / 2f;
        this.centerY = height / 2f;
        this.projScale = Math.min(width, height) * 0.2f * cubeSize;

        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(lineWidth);
        edgePaint.setStrokeCap(Paint.Cap.ROUND);

        vertexPaint.setStyle(Paint.Style.FILL);
        glowPaint.setStyle(Paint.Style.FILL);

        initialized = true;
    }

    @Override
    public void draw(Canvas canvas, int width, int height, long elapsedMs) {
        if (!initialized) {
            init(width, height);
        }

        // 深色背景
        canvas.drawColor(0xFF060610);

        float timeSec = elapsedMs / 1000f;

        // 旋转角度
        float angleX = timeSec * 0.4f * speedMultiplier;
        float angleY = timeSec * 0.6f * speedMultiplier;
        float angleZ = timeSec * 0.2f * speedMultiplier;

        // 手势叠加：手势旋转在自动旋转之上再叠加（跟手方向）
        float gesRadX = (float) Math.toRadians(gestureRotX);
        float gesRadY = (float) Math.toRadians(gestureRotY);

        float cosX = TrigLut.cos(angleX + gesRadX), sinX = TrigLut.sin(angleX + gesRadX);
        float cosY = TrigLut.cos(angleY + gesRadY), sinY = TrigLut.sin(angleY + gesRadY);
        float cosZ = TrigLut.cos(angleZ), sinZ = TrigLut.sin(angleZ);

        // 变换和投影每个顶点
        float perspective = 4f; // 透视距离
        for (int i = 0; i < 8; i++) {
            float x = VERTICES[i][0];
            float y = VERTICES[i][1];
            float z = VERTICES[i][2];

            // 绕 X 轴旋转
            float y1 = y * cosX - z * sinX;
            float z1 = y * sinX + z * cosX;

            // 绕 Y 轴旋转
            float x2 = x * cosY + z1 * sinY;
            float z2 = -x * sinY + z1 * cosY;

            // 绕 Z 轴旋转
            float x3 = x2 * cosZ - y1 * sinZ;
            float y3 = x2 * sinZ + y1 * cosZ;

            // 透视投影
            float factor = perspective / (perspective + z2);
            float baseScale = projScale * gestureScale;
            projX[i] = centerX + gestureOffsetX + x3 * baseScale * factor;
            projY[i] = centerY + gestureOffsetY + y3 * baseScale * factor;
        }

        // 绘制边（根据深度调整透明度）
        for (int i = 0; i < EDGES.length; i++) {
            int a = EDGES[i][0];
            int b = EDGES[i][1];

            // 颜色渐变（青-蓝）+ 色调偏移
            float hue = (hueParam + timeSec * 20f + i * 15f) % 360f;
            int color = hsvToColor(hue, 0.7f, 1f);
            edgePaint.setColor(color);
            edgePaint.setAlpha(180);

            canvas.drawLine(projX[a], projY[a], projX[b], projY[b], edgePaint);
        }

        // 绘制顶点
        for (int i = 0; i < 8; i++) {
            vertexPaint.setColor(0xFF00E5FF);
            vertexPaint.setAlpha(220);
            canvas.drawCircle(projX[i], projY[i], 4f, vertexPaint);

            // 顶点发光
            glowPaint.setColor(0x3000E5FF);
            canvas.drawCircle(projX[i], projY[i], 10f, glowPaint);
        }
    }

    /** 简易 HSV → RGB（避免每帧分配 float[]） */
    private int hsvToColor(float h, float s, float v) {
        float c = v * s;
        float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
        float m = v - c;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        int ri = (int) ((r + m) * 255);
        int gi = (int) ((g + m) * 255);
        int bi = (int) ((b + m) * 255);
        return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
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
        cubeSize = params.getFloat("cubeSize", 2.8f);
        lineWidth = (int) params.getFloat("lineWidth", 19);
        hueParam = params.getFloat("hue", 72f);
        if (edgePaint != null) edgePaint.setStrokeWidth(lineWidth);
        if (initialized) projScale = Math.min(width, height) * 0.2f * cubeSize;
    }

    @Override
    public void applyGesture(float rotX, float rotY, float offsetX, float offsetY, float scale) {
        // 手势旋转叠加到自动旋转之上（手势存储为角度，draw 中转角弧度）
        gestureRotX = rotX;
        gestureRotY = rotY;
        gestureOffsetX = offsetX;
        gestureOffsetY = offsetY;
        gestureScale = scale;
    }
}
