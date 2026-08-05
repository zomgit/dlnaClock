package com.dlnaclock.screensaver.wallpaper;

import android.graphics.Canvas;
import android.graphics.Camera;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Bundle;
import android.util.Log;

/**
 * GalaxyWallpaper - 银河壁纸
 * 旋转星场 + 色彩渐变 + 螺旋臂
 * 星点沿螺旋臂分布，整体缓慢旋转
 */
public class GalaxyWallpaper implements WallpaperRenderer, GestureAwareWallpaper {

    private static final int ARM_COUNT = 3;

    private Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);  // 大粒子光晕

    // 3D 旋转
    private Camera camera3d;
    private Matrix rotationMatrix;

    // 星点数据（极坐标）
    private float[] starAngle;
    private float[] starDist;
    private float[] starSize;
    private int[] starColorIdx;
    private float[] starTwinklePhase;

    private float centerX;
    private float centerY;
    private float maxRadius;
    private int width;
    private int height;
    private boolean initialized = false;

    private static final String TAG = "GalaxyWallpaper";
    private static final int MAX_STARS = 350;  // 小米3骁龙800安全上限

    // 可调参数
    private int starCount = 300;
    private float speedMultiplier = 1.0f;
    private float rotationSpeed = 1.0f;
    private float hueShift = 133f;
    private float scale = 2.7f;
    private float coreSize = 0.49f;  // 银河中心发光球体大小

    // 手势叠加
    private float gestureRotX, gestureRotY;
    private float gestureScale = 1f;

    /** 渐变缓存脏标记（coreSize 变化时设为 true，强制重建 RadialGradient） */
    private boolean coreGradientDirty = true;
    private float lastCoreRadius = -1f;

    // 色相偏移后的调色板（每帧更新，通过查表填充）
    private int[] shiftedPalette = new int[PALETTE.length];

    // 颜色调色板（暖白-蓝-紫）
    private static final int[] PALETTE = {
            0xFFFFF8E1, 0xFFBBDEFB, 0xFFCE93D8,
            0xFFFFE0B2, 0xFF90CAF9, 0xFFB39DDB
    };

    /** 预计算色相偏移表（一次性构建，draw 中查表替代 shiftHue） */
    private int[][] hueTable;

    private static final ParamDef[] PARAMS = {
            new ParamDef("scale", "整体大小", 1.0f, 4f, 2.7f, 0.1f),
            new ParamDef("speed", "速度", 0.5f, 5f, 1.0f, 0.1f),
            new ParamDef("particleCount", "粒子数量", 50, MAX_STARS, 200),
            new ParamDef("rotationSpeed", "旋转速度", 0.5f, 3f, 1.0f, 0.1f),
            new ParamDef("coreSize", "银河中心大小", 0.05f, 0.5f, 0.49f, 0.01f),
            new ParamDef("hue", "色调", 0, 360, 133),
    };

    @Override
    public void init(int width, int height) {
        this.width = width;
        this.height = height;
        this.centerX = width / 2f;
        this.centerY = height / 2f;
        this.maxRadius = Math.min(width, height) * 0.45f;

        starPaint.setStyle(Paint.Style.FILL);
        corePaint.setStyle(Paint.Style.FILL);
        glowPaint.setStyle(Paint.Style.FILL);

        // 预构建色相偏移表（一次性，避免每帧 shiftHue 调用 Color.colorToHSV）
        hueTable = TrigLut.ColorLut.buildHueTable(PALETTE);

        camera3d = new Camera();
        rotationMatrix = new Matrix();

        allocateStars();

        initialized = true;
    }

    private void allocateStars() {
        // 安全限制：防止粒子数过多导致OOM
        if (starCount > MAX_STARS) starCount = MAX_STARS;
        try {
            starAngle = new float[starCount];
            starDist = new float[starCount];
            starSize = new float[starCount];
            starColorIdx = new int[starCount];
            starTwinklePhase = new float[starCount];
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "OOM allocating stars, resetting to default", e);
            starCount = 300;
            starAngle = new float[starCount];
            starDist = new float[starCount];
            starSize = new float[starCount];
            starColorIdx = new int[starCount];
            starTwinklePhase = new float[starCount];
        }

        for (int i = 0; i < starCount; i++) {
            int arm = i % ARM_COUNT;
            float armOffset = (float) arm / ARM_COUNT * (float) Math.PI * 2f;
            // 距离：平方分布 → 中心稀疏(多数粒子在外部)
            float dist = (float) Math.random();
            dist = 1f - dist * dist;  // 反转：更多粒子在外围
            starDist[i] = dist;
            float spiralAngle = dist * 4f * (float) Math.PI;
            float scatter = (1f - dist) * 0.4f + 0.1f;
            starAngle[i] = armOffset + spiralAngle + ((float) Math.random() - 0.5f) * scatter * (float) Math.PI * 2f;
            // 幂律衰减：离中心越远越小（整体 x2）
            // size = 2 * (maxSize * (1 - dist)^2 + minSize)
            float maxS = 4.5f;
            float minS = 0.3f;
            starSize[i] = 2f * (minS + maxS * (1f - dist) * (1f - dist));
            starColorIdx[i] = (int) (Math.random() * PALETTE.length);
            starTwinklePhase[i] = (float) Math.random() * (float) Math.PI * 2f;
        }
        
        // 重置渐变缓存标记（尺寸变化后强制重建）
        coreGradientDirty = true;
    }

    @Override
    public void draw(Canvas canvas, int width, int height, long elapsedMs) {
        if (!initialized) {
            init(width, height);
        }

        // 深空背景
        canvas.drawColor(0xFF020208);

        canvas.save();
        canvas.scale(scale * gestureScale, scale * gestureScale, width / 2f, height / 2f);

        float timeSec = elapsedMs / 1000f;
        float rotation = timeSec * 0.05f * rotationSpeed;

        canvas.save();
        canvas.translate(centerX, centerY);

        // 应用手势 3D 旋转透视（角度制，仅手势控制）
        camera3d.save();
        if (gestureRotX != 0f) camera3d.rotateX(gestureRotX);
        if (gestureRotY != 0f) camera3d.rotateY(gestureRotY);
        camera3d.getMatrix(rotationMatrix);
        camera3d.restore();
        canvas.concat(rotationMatrix);

        canvas.translate(-centerX, -centerY);

        // 银河中心发光球体（缓存 RadialGradient，仅在 coreSize 变化时重建）
        float coreRadius = maxRadius * coreSize;
        float cachedCoreRadius = coreRadius * 1.8f;
        if (coreGradientDirty || corePaint.getShader() == null) {
            corePaint.setShader(new RadialGradient(centerX, centerY, coreRadius,
                    new int[]{0xA0FFFDE0, 0x60FFF8C0, 0x20FFE0B2, 0x00000000},
                    new float[]{0f, 0.3f, 0.7f, 1f},
                    Shader.TileMode.CLAMP));
            coreGradientDirty = false;
            lastCoreRadius = coreRadius;
        }
        canvas.drawCircle(centerX, centerY, cachedCoreRadius, corePaint);

        // 应用色相偏移到调色板（查表替代 shiftHue）
        int hueIdx = ((int)(hueShift - 220f)) % 360;
        if (hueIdx < 0) hueIdx += 360;
        for (int p = 0; p < PALETTE.length; p++) {
            shiftedPalette[p] = hueTable[p][hueIdx];
        }

        // 绘制星点（使用 TrigLut 替代 Math.sin/cos）
        for (int i = 0; i < starCount; i++) {
            float angle = starAngle[i] + rotation;
            float dist = starDist[i] * maxRadius;

            float x = centerX + dist * TrigLut.cos(angle);
            float y = centerY + dist * TrigLut.sin(angle) * 0.6f;

            float twinkle = 0.6f + 0.4f * TrigLut.sin(timeSec * 1.5f * speedMultiplier + starTwinklePhase[i]);
            int alpha = (int) (twinkle * 220);

            starPaint.setColor(shiftedPalette[starColorIdx[i]]);
            starPaint.setAlpha(alpha);

            float sz = starSize[i];

            // 大粒子（近中心）绘制光晕
            if (sz > 2f) {
                float glowR = sz * 2.5f;
                glowPaint.setColor(shiftedPalette[starColorIdx[i]]);
                glowPaint.setAlpha((int)(alpha * 0.25f));
                canvas.drawCircle(x, y, glowR, glowPaint);
            }

            canvas.drawCircle(x, y, sz, starPaint);
        }
        canvas.restore();  // restore translate/3D
        canvas.restore();  // restore scale
    }

    @Override
    public void release() {
        initialized = false;
        hueTable = null;
    }

    @Override
    public ParamDef[] getParamDefs() { return PARAMS; }

    @Override
    public void applyParams(Bundle params) {
        scale = params.getFloat("scale", 2.7f);
        speedMultiplier = params.getFloat("speed", 1.0f);
        int newCount = (int) params.getFloat("particleCount", 200);
        rotationSpeed = params.getFloat("rotationSpeed", 1.0f);
        float newCoreSize = params.getFloat("coreSize", 0.49f);
        hueShift = params.getFloat("hue", 133f);
        if (newCoreSize != coreSize) {
            coreSize = newCoreSize;
            coreGradientDirty = true;
        }
        if (newCount != starCount) {
            starCount = newCount;
            if (initialized) allocateStars();
        }
    }

    @Override
    public void applyGesture(float rotX, float rotY, float offsetX, float offsetY, float scale) {
        gestureRotX = rotX;
        gestureRotY = rotY;
        gestureScale = scale;
        // 平移：中心偏移（星点与旋转都基于中心，整体跟随移动）
        if (initialized) {
            centerX = width / 2f + offsetX;
            centerY = height / 2f + offsetY;
        }
    }
}
