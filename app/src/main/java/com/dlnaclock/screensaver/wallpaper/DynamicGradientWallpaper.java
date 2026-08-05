package com.dlnaclock.screensaver.wallpaper;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Bundle;

/**
 * DynamicGradientWallpaper - 动态渐变（Mesh Gradient）壁纸
 *
 * Samsung One UI / iOS 风格的全屏动态网格渐变背景。
 *
 * 核心算法：
 * 1. 4~8 个超大半透明径向渐变色团覆盖全屏
 * 2. 色团之间通过 SCREEN 混合自然叠加，边缘极其柔和，无可见边界
 * 3. 每个色团的位置、半径、透明度通过不同频率的正弦函数缓慢变化
 * 4. 动画周期 30~90 秒，无明显开始/结束，呈现呼吸般的流动感
 * 5. 色彩使用低饱和、柔和的高级色板，不出现彩虹/高饱和/强对比
 */
public class DynamicGradientWallpaper implements WallpaperRenderer {

    // ── 默认参数 ──
    private static final int DEFAULT_BLOB_COUNT = 3;
    private static final int MAX_BLOB_COUNT = 5;
    private static final float DEFAULT_ALPHA = 100f;

    // ── 高饱和鲜艳色板 ──
    // 每组 3 色: [中心色, 边缘色, 边缘全透]
    // 默认风格：#9338D8 紫 / #4AA7B4 青蓝 为主调
    private static final int[][] COLOR_SETS = {
            // 紫罗兰 - vivid purple (#9338D8)
            {0x339338D8, 0x187020B0, 0x00000000},
            // 青蓝 - teal cyan (#4AA7B4)
            {0x334AA7B4, 0x18308898, 0x00000000},
            // 电光蓝 - electric blue
            {0x332860FF, 0x181848D0, 0x00000000},
            // 品红 - hot magenta
            {0x33E83090, 0x18C02070, 0x00000000},
            // 翡翠绿 - emerald
            {0x3318C860, 0x1810A048, 0x00000000},
            // 琥珀橙 - deep amber
            {0x33FF9020, 0x18D07010, 0x00000000},
            // 靛紫 - indigo violet
            {0x337030FF, 0x185820D0, 0x00000000},
            // 珊瑚红 - coral red
            {0x33FF4048, 0x18D03038, 0x00000000},
    };

    // ── 预分配渲染资源 ──
    private Paint blobPaint;
    private Paint bgPaint;

    /** 渐变颜色复用数组 */
    private final int[] gradColors = new int[3];

    /** 渐变位置 */
    private final float[] gradStops = new float[]{0f, 0.5f, 1f};

    // ── 色团数据（动态分配） ──
    private int blobCount = DEFAULT_BLOB_COUNT;

    /** 色团基础位置 X（0~1 归一化） */
    private float[] baseX;

    /** 色团基础位置 Y（0~1 归一化） */
    private float[] baseY;

    /** 色团基础半径（像素） */
    private float[] baseRadius;

    /** 色团基础透明度（0~255） */
    private float[] baseAlpha;

    /** 颜色集索引 */
    private int[] colorIdx;

    // ── 动画参数（每色团独立频率/相位，确保无重复感） ──
    private float[] freqX;
    private float[] freqY;
    private float[] freqR;
    private float[] freqA;
    private float[] phaseX;
    private float[] phaseY;
    private float[] phaseR;
    private float[] phaseA;

    /** X 位移幅度（占画面宽度的比例） */
    private static final float AMP_X = 0.45f;
    /** Y 位移幅度（占画面高度的比例） */
    private static final float AMP_Y = 0.45f;
    /** 半径摆动幅度（相对基础半径） */
    private static final float AMP_R = 0.30f;
    /** 透明度摆动幅度 */
    private static final float AMP_A = 0.20f;

    // ── 画布尺寸 ──
    private int width;
    private int height;
    private float cx;
    private float cy;
    private float maxR;
    private boolean initialized;

    // ── 可调参数 ──
    private float scale = 0.5f;
    private float speed = 2.5f;
    private float userAlpha = DEFAULT_ALPHA;
    private float hueShift = 101f;

    /** HSV 复用数组，避免每帧分配 */
    private final float[] hsvTemp = new float[3];

    private static final ParamDef[] PARAMS = {
            new ParamDef("scale", "整体大小", 0.2f, 2f, 0.5f, 0.1f),
            new ParamDef("speed", "流动速度", 0.1f, 10f, 2.5f, 0.1f),
            new ParamDef("blobs", "色团数量", 1, 5, 3),
            new ParamDef("alpha", "透明度", 20, 100, 100),
            new ParamDef("hue", "色相偏移", 0, 360, 101),
    };

    // ═══════════════════════════════════════════════════════════
    //  WallpaperRenderer 接口实现
    // ═══════════════════════════════════════════════════════════

    @Override
    public void init(int width, int height) {
        this.width = width;
        this.height = height;
        this.cx = width / 2f;
        this.cy = height / 2f;
        this.maxR = Math.max(width, height) * 0.55f;

        blobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blobPaint.setStyle(Paint.Style.FILL);
        blobPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));

        bgPaint = new Paint();
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(0xFF050814); // 深邃暗色基底

        allocateBlobs();

        initialized = true;
    }

    @Override
    public void draw(Canvas canvas, int width, int height, long elapsedMs) {
        if (!initialized) {
            init(width, height);
        }

        // 暗色基底背景
        canvas.drawRect(0, 0, width, height, bgPaint);

        float t = elapsedMs / 1000f * speed;

        canvas.save();
        canvas.scale(scale, scale, cx, cy);

        // 逐色团绘制
        for (int i = 0; i < blobCount; i++) {
            drawBlob(canvas, i, t);
        }

        canvas.restore();
    }

    @Override
    public void release() {
        initialized = false;
        blobPaint = null;
        bgPaint = null;
        baseX = null;
        baseY = null;
        baseRadius = null;
        baseAlpha = null;
        colorIdx = null;
        freqX = null;
        freqY = null;
        freqR = null;
        freqA = null;
        phaseX = null;
        phaseY = null;
        phaseR = null;
        phaseA = null;
    }

    @Override
    public ParamDef[] getParamDefs() {
        return PARAMS;
    }

    @Override
    public void applyParams(Bundle params) {
        scale = params.getFloat("scale", 0.5f);
        speed = params.getFloat("speed", 2.5f);
        hueShift = params.getFloat("hue", 101f);
        float newAlpha = params.getFloat("alpha", DEFAULT_ALPHA);
        int newCount = (int) params.getFloat("blobs", DEFAULT_BLOB_COUNT);

        // 透明度变化时更新所有色团的 baseAlpha
        if (newAlpha != userAlpha) {
            userAlpha = newAlpha;
            if (baseAlpha != null) {
                for (int i = 0; i < blobCount; i++) {
                    baseAlpha[i] = userAlpha * (0.7f + 0.3f * ((float) i / blobCount));
                }
            }
        }

        if (newCount != blobCount) {
            blobCount = clamp(newCount, 1, MAX_BLOB_COUNT);
            if (initialized) allocateBlobs();
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  内部方法
    // ═══════════════════════════════════════════════════════════

    /** 分配色团数据，初始化基础位置和动画参数 */
    private void allocateBlobs() {
        baseX = new float[blobCount];
        baseY = new float[blobCount];
        baseRadius = new float[blobCount];
        baseAlpha = new float[blobCount];
        colorIdx = new int[blobCount];

        freqX = new float[blobCount];
        freqY = new float[blobCount];
        freqR = new float[blobCount];
        freqA = new float[blobCount];
        phaseX = new float[blobCount];
        phaseY = new float[blobCount];
        phaseR = new float[blobCount];
        phaseA = new float[blobCount];

        // 可用的画布矩形范围
        float cw = width > 0 ? width : 1080f;
        float ch = height > 0 ? height : 1920f;

        for (int i = 0; i < blobCount; i++) {
            // 色团均匀分散在画面各处，避免全部集中在中心
            // 使用黄金角度螺旋分布确保视觉均衡
            float goldenAngle = (float) (i * Math.PI * 0.618033988749895);
            float dist = blobCount > 1 ? 0.60f + 0.90f * ((float) i / (blobCount - 1)) : 0.8f;

            baseX[i] = 0.5f + dist * (float) Math.cos(goldenAngle);
            baseY[i] = 0.5f + dist * (float) Math.sin(goldenAngle);

            // 色团半径覆盖大半画面（4倍大小）
            baseRadius[i] = maxR * 4f * (0.6f + 0.3f * (float) i / blobCount);

            // 基础透明度变化，避免所有色团相同
            baseAlpha[i] = userAlpha * (0.7f + 0.3f * ((float) i / blobCount));

            // 颜色循环使用
            colorIdx[i] = i % COLOR_SETS.length;

            // ── 动画频率：慢速流动，周期 15~45 秒 ──
            // 频率 = 2π / 周期(秒)
            float cycleX = 15f + (float) Math.random() * 30f;  // 15~45s
            float cycleY = 18f + (float) Math.random() * 27f;  // 18~45s
            float cycleR = 12f + (float) Math.random() * 23f;  // 12~35s
            float cycleA = 15f + (float) Math.random() * 25f;  // 15~40s

            freqX[i] = (float) (2.0 * Math.PI / cycleX);
            freqY[i] = (float) (2.0 * Math.PI / cycleY);
            freqR[i] = (float) (2.0 * Math.PI / cycleR);
            freqA[i] = (float) (2.0 * Math.PI / cycleA);

            // 随机初始相位，确保各色团不会同步
            phaseX[i] = (float) (Math.random() * Math.PI * 2.0);
            phaseY[i] = (float) (Math.random() * Math.PI * 2.0);
            phaseR[i] = (float) (Math.random() * Math.PI * 2.0);
            phaseA[i] = (float) (Math.random() * Math.PI * 2.0);
        }
    }

    /**
     * 绘制单个色团
     * 位置、半径、透明度通过正弦函数缓慢变化
     * 对角线参考线效果：左上→右下隐形线影响颜色和形状
     */
    private void drawBlob(Canvas canvas, int i, float t) {
        // ── 位置缓慢漂移 ──
        float dx = AMP_X * width * TrigLut.sin(t * freqX[i] + phaseX[i]);
        float dy = AMP_Y * height * TrigLut.sin(t * freqY[i] + phaseY[i]);

        float bx = baseX[i] * width + dx;
        float by = baseY[i] * height + dy;

        // ── 半径呼吸 ──
        float radiusOsc = 1f + AMP_R * TrigLut.sin(t * freqR[i] + phaseR[i]);
        float r = baseRadius[i] * radiusOsc;

        // ── 透明度呼吸 ──
        float alphaOsc = 1f + AMP_A * TrigLut.sin(t * freqA[i] + phaseA[i]);
        int alpha = (int) (baseAlpha[i] * alphaOsc * 2.55f); // userAlpha(0-100) → alpha(0-255)
        alpha = clamp(alpha, 15, 200);

        // ── 对角线参考线效果（左上→右下隐形线） ──
        // 计算色团中心到对角线的归一化距离
        float diagLen = (float) Math.sqrt(width * width + height * height);
        float distToDiag = Math.abs(by * width - bx * height) / diagLen;
        float maxDist = width * height / diagLen;
        float normalizedDist = Math.min(distToDiag / maxDist, 1f);
        float nearLine = 1f - normalizedDist; // 1=在线上, 0=远离线

        // 中心颜色加深：越靠近线，中心颜色越深
        float centerDeepen = 1f + 1.5f * nearLine;
        int centerAlpha = clamp((int) (alpha * centerDeepen), 10, 255);

        // 边缘颜色变淡：越靠近线，边缘越透明
        float edgeFade = 1f - 0.5f * nearLine;
        int midAlpha = clamp((int) (alpha * 0.5f * edgeFade), 3, 200);

        // 沿对角线拉伸：中心越近拉伸越大
        float stretchFactor = 1f + 2.5f * nearLine * nearLine;
        float diagAngle = (float) Math.atan2(height, width);

        // ── 构建径向渐变（应用色相偏移） ──
        int ci = colorIdx[i];
        int[] colors = COLOR_SETS[ci];
        int c0 = shiftHue(colors[0], hueShift);
        int c1 = shiftHue(colors[1], hueShift);

        gradColors[0] = (c0 & 0x00FFFFFF) | ((centerAlpha << 24) & 0xFF000000);
        gradColors[1] = (c1 & 0x00FFFFFF) | ((midAlpha << 24) & 0xFF000000);
        gradColors[2] = 0x00000000;

        // 应用拉伸变换绘制椭圆色团
        canvas.save();
        canvas.translate(bx, by);
        canvas.rotate((float) Math.toDegrees(diagAngle));
        canvas.scale(stretchFactor, 1f);
        blobPaint.setShader(new RadialGradient(0, 0, r,
                gradColors, gradStops,
                Shader.TileMode.CLAMP));
        canvas.drawCircle(0, 0, r, blobPaint);
        canvas.restore();
    }

    /** 色相偏移：将颜色在 HSV 空间旋转指定角度 */
    private int shiftHue(int color, float degrees) {
        if (degrees == 0f) return color;
        Color.colorToHSV(color, hsvTemp);
        hsvTemp[0] = (hsvTemp[0] + degrees) % 360f;
        if (hsvTemp[0] < 0f) hsvTemp[0] += 360f;
        return Color.HSVToColor(Color.alpha(color), hsvTemp);
    }

    /** 值域裁剪 */
    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
