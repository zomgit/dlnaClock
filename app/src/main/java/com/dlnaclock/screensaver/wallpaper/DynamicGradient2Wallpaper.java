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
 * DynamicGradient2Wallpaper - 动态渐变 V2（双色团轨道拖尾 + 棱锥折射对角线）
 *
 * 两个大色团围绕屏幕中心做椭圆轨道运动，
 * 距中心距离按正弦在 [0.45×短边, 1.35×长边] 之间变化。
 * 每个色团由多层渐变叠层形成运动拖尾效果。
 * 左上→右下虚拟对角线产生棱锥折射感，色团靠近线时沿对角线方向拉伸。
 */
public class DynamicGradient2Wallpaper implements WallpaperRenderer {

    // ── 固定双色团颜色（高饱和） ──
    // 色团 A: 紫罗兰 #9338D8
    private static final int COLOR_A = 0x9338D8;
    // 色团 B: 青蓝 #4AA7B4
    private static final int COLOR_B = 0x4AA7B4;

    // ── 拖尾层数 ──
    private int layers = 4;

    // ── 画布尺寸 ──
    private int width;
    private int height;
    private float cx;
    private float cy;
    private float maxDim;   // 长边
    private float minDim;   // 短边
    private boolean initialized;

    // ── 渲染资源 ──
    private Paint blobPaint;
    private Paint bgPaint;

    // ── 可调参数 ──
    private float speed = 1.5f;
    private float blobScale = 0.9f;
    private float orbitDist = 0.75f;
    private float userAlpha = 98f;
    private float hueShift = 0f;

    /** HSV 复用数组 */
    private final float[] hsvTemp = new float[3];

    private static final ParamDef[] PARAMS = {
            new ParamDef("speed", "旋转速度", 0.1f, 5f, 1.5f, 0.1f),
            new ParamDef("blobScale", "色团大小", 0.3f, 4f, 0.9f, 0.1f),
            new ParamDef("orbitDist", "轨道距离", 0.3f, 3f, 0.75f, 0.05f),
            new ParamDef("alpha", "透明度", 20, 100, 98),
            new ParamDef("hue", "色相偏移", 0, 360, 0),
            new ParamDef("layers", "拖尾层数", 3, 10, 4),
    };

    // ═══════════════════════════════════════════════════════════
    //  WallpaperRenderer 接口
    // ═══════════════════════════════════════════════════════════

    @Override
    public void init(int width, int height) {
        if (width <= 0 || height <= 0) return;
        this.width = width;
        this.height = height;
        this.cx = width / 2f;
        this.cy = height / 2f;
        this.maxDim = Math.max(width, height);
        this.minDim = Math.min(width, height);

        blobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blobPaint.setStyle(Paint.Style.FILL);
        blobPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));

        bgPaint = new Paint();
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(0xFF050814);

        initialized = true;
    }

    @Override
    public void draw(Canvas canvas, int width, int height, long elapsedMs) {
        if (!initialized) {
            init(width, height);
        }

        canvas.drawRect(0, 0, width, height, bgPaint);

        float time = elapsedMs / 1000f * speed;

        // 双色团：相位相差 π（对称分布）
        drawBlobOrbit(canvas, time, 0f, COLOR_A);
        drawBlobOrbit(canvas, time, (float) Math.PI, COLOR_B);
    }

    @Override
    public void release() {
        initialized = false;
        blobPaint = null;
        bgPaint = null;
    }

    @Override
    public ParamDef[] getParamDefs() {
        return PARAMS;
    }

    @Override
    public void applyParams(Bundle params) {
        speed = params.getFloat("speed", 1.5f);
        blobScale = params.getFloat("blobScale", 0.9f);
        orbitDist = params.getFloat("orbitDist", 0.75f);
        userAlpha = params.getFloat("alpha", 98f);
        hueShift = params.getFloat("hue", 0f);
        layers = (int) params.getFloat("layers", 4);
    }

    // ═══════════════════════════════════════════════════════════
    //  内部方法
    // ═══════════════════════════════════════════════════════════

    /**
     * 绘制一个色团的完整轨道拖尾
     * @param phaseOffset 初始相位偏移（双色团相差 π）
     * @param baseColor   色团基础颜色 (RGB)
     */
    private void drawBlobOrbit(Canvas canvas, float time, float phaseOffset, int baseColor) {
        // ── 轨道参数 ──
        // 距中心距离：正弦变化，由 orbitDist 缩放
        float distMin = minDim * 0.45f * orbitDist;
        float distMax = maxDim * 1.35f * orbitDist;
        float distFreq = 0.15f; // 距离变化频率（慢呼吸）

        // 当前角度（匀速旋转）
        float angle = time * 0.4f + phaseOffset;
        // 当前距离（正弦呼吸）
        float dist = distMin + (distMax - distMin) * (0.5f + 0.5f * TrigLut.sin(time * distFreq + phaseOffset));

        // 当前位置
        float bx = cx + dist * TrigLut.cos(angle);
        float by = cy + dist * TrigLut.sin(angle);

        // ── 计算运动方向（切线 + 径向分量） ──
        float tangentX = -TrigLut.sin(angle);
        float tangentY = TrigLut.cos(angle);
        float distVel = (distMax - distMin) * 0.5f * distFreq * TrigLut.cos(time * distFreq + phaseOffset);
        float radialX = TrigLut.cos(angle);
        float radialY = TrigLut.sin(angle);
        float vx = tangentX * dist * 0.4f + radialX * distVel;
        float vy = tangentY * dist * 0.4f + radialY * distVel;
        float vLen = (float) Math.sqrt(vx * vx + vy * vy);
        if (vLen > 0.001f) {
            vx /= vLen;
            vy /= vLen;
        } else {
            vx = tangentX;
            vy = tangentY;
        }

        // ── 应用色相偏移 ──
        int color = shiftHue(baseColor | 0xFF000000, hueShift) & 0x00FFFFFF;

        // ── 对角线预计算（左上→右下棱锥参考线） ──
        float diagLen = (float) Math.sqrt(width * width + height * height);
        float diagAngle = (float) Math.atan2(height, width);

        // ── 逐层绘制拖尾 ──
        // 色团基础半径
        float baseRadius = minDim * 0.54f * blobScale;
        float alpha = userAlpha * 2.55f; // 0-100 → 0-255

        for (int layer = 0; layer < layers; layer++) {
            // 每层半径递增
            float r = baseRadius + layer * 150f * blobScale;
            // 每层透明度衰减
            float layerAlpha = alpha * (float) Math.pow(0.82, layer);

            // 每层沿运动反方向偏移（形成拖尾）
            float offset = layer * 60f;
            float px = bx - vx * offset + TrigLut.sin(time * 0.2f + layer) * 8f;
            float py = by - vy * offset + TrigLut.cos(time * 0.15f + layer) * 6f;

            // ── 对角线参考线效果（仿棱锥折射） ──
            // 计算当前层位置到对角线的归一化距离
            float maxDistFromDiag = width * height / diagLen;
            float distToDiag = Math.abs(py * width - px * height) / diagLen;
            float normalizedDist = Math.min(distToDiag / maxDistFromDiag, 1f);
            float nearLine = 1f - normalizedDist; // 1=在线上, 0=远离线

            // 沿对角线拉伸：越靠近线拉伸越大（棱锥折射感）
            float stretchFactor = 1f + 2.5f * nearLine * nearLine;

            // 中心颜色加深：越靠近线，中心越亮
            float centerDeepen = 1f + 1.5f * nearLine;
            int a = clamp((int) (layerAlpha * centerDeepen), 5, 220);

            // 构建径向渐变
            int centerColor = (color) | ((a << 24) & 0xFF000000);
            int midColor = (color) | (((a / 2) << 24) & 0xFF000000);
            int[] gradColors = {centerColor, midColor, 0x00000000};
            float[] gradStops = {0f, 0.45f, 1f};

            // 应用拉伸变换：沿对角线方向拉长色团
            canvas.save();
            canvas.translate(px, py);
            canvas.rotate((float) Math.toDegrees(diagAngle));
            canvas.scale(stretchFactor, 1f);
            blobPaint.setShader(new RadialGradient(0, 0, r,
                    gradColors, gradStops, Shader.TileMode.CLAMP));
            canvas.drawCircle(0, 0, r, blobPaint);
            canvas.restore();
        }
    }

    /** 色相偏移 */
    private int shiftHue(int color, float degrees) {
        if (degrees == 0f) return color;
        Color.colorToHSV(color, hsvTemp);
        hsvTemp[0] = (hsvTemp[0] + degrees) % 360f;
        if (hsvTemp[0] < 0f) hsvTemp[0] += 360f;
        return Color.HSVToColor(Color.alpha(color), hsvTemp);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
