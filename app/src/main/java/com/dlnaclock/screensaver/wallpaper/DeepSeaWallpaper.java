package com.dlnaclock.screensaver.wallpaper;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.Bundle;

/**
 * DeepSeaWallpaper - 深海水母壁纸
 * 深海水母群漂浮 + 粒子上升 + 暗蓝渐变
 * 30 水母（贝塞尔曲线轮廓）+ 50 粒子
 */
public class DeepSeaWallpaper implements WallpaperRenderer {

    private int jellyCount = 30;

    private Paint bgPaint = new Paint();
    private Paint jellyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Path jellyPath = new Path();

    // 水母数据
    private float[] jellyX;
    private float[] jellyY;
    private float[] jellySize;
    private float[] jellySpeed;
    private float[] jellyPhase;
    private float[] jellyDrift;
    private int[] jellyAlpha;

    // 粒子数据（动态分配）
    private float[] partX;
    private float[] partY;
    private float[] partSpeed;
    private float[] partSize;

    private int width;
    private int height;
    private boolean initialized = false;

    // 可调参数
    private float speedMultiplier = 1.0f;
    private int jellyfish = 9;
    private int userAlpha = 70;
    private int particleCount = 21;
    private float maxJellySize = 110f;
    private float maxBubbleSize = 4f;

    private static final ParamDef[] PARAMS = {
            new ParamDef("speed", "漂浮速度", 0.1f, 3f, 1.0f, 0.1f),
            new ParamDef("jellyfish", "水母数量", 3, 20, 9),
            new ParamDef("maxJellySize", "水母最大大小", 40f, 320f, 110f, 10f),
            new ParamDef("alpha", "透明度", 20, 100, 70),
            new ParamDef("particles", "气泡数量", 0, 150, 21),
            new ParamDef("maxBubbleSize", "气泡最大大小", 1f, 10f, 4f, 0.5f),
    };

    @Override
    public void init(int width, int height) {
        this.width = width;
        this.height = height;

        bgPaint.setStyle(Paint.Style.FILL);
        jellyPaint.setStyle(Paint.Style.FILL);
        particlePaint.setStyle(Paint.Style.FILL);
        particlePaint.setColor(0x6088CCFF);

        allocateJellies();
        allocateParticles();

        initialized = true;
    }

    private float randomNormalSize(float maxSize) {
        float u1 = (float) Math.random();
        float u2 = (float) Math.random();
        float normal = (float) Math.abs(Math.sqrt(-2 * Math.log(Math.max(u1, 0.001))) * Math.cos(2 * Math.PI * u2));
        // 整体 x2：最小水母更多，呈正态分布
        float size = 2f * (maxSize * 0.08f + normal * maxSize * 0.30f);
        if (size > maxSize) size = maxSize;
        if (size < maxSize * 0.04f) size = maxSize * 0.04f;
        return size;
    }

    private void allocateParticles() {
        int pc = Math.max(particleCount, 1);
        partX = new float[pc];
        partY = new float[pc];
        partSpeed = new float[pc];
        partSize = new float[pc];
        for (int i = 0; i < pc; i++) {
            partX[i] = (float) Math.random() * width;
            partY[i] = (float) Math.random() * height;
            partSpeed[i] = 10f + (float) Math.random() * 20f;
            partSize[i] = randomNormalSize(maxBubbleSize);
        }
    }

    private void allocateJellies() {
        jellyCount = jellyfish;
        jellyX = new float[jellyCount];
        jellyY = new float[jellyCount];
        jellySize = new float[jellyCount];
        jellySpeed = new float[jellyCount];
        jellyPhase = new float[jellyCount];
        jellyDrift = new float[jellyCount];
        jellyAlpha = new int[jellyCount];

        for (int i = 0; i < jellyCount; i++) {
            jellyX[i] = (float) Math.random() * width;
            jellyY[i] = (float) Math.random() * height;
            jellySize[i] = randomNormalSize(maxJellySize);
            jellySpeed[i] = (8f + (float) Math.random() * 15f) * speedMultiplier;
            jellyPhase[i] = (float) Math.random() * (float) Math.PI * 2f;
            jellyDrift[i] = (float) Math.random() * 20f - 10f;
            jellyAlpha[i] = 40 + (int) (Math.random() * 80);
        }
    }

    @Override
    public void draw(Canvas canvas, int width, int height, long elapsedMs) {
        if (!initialized) {
            init(width, height);
        }

        float timeSec = elapsedMs / 1000f;
        float dt = 0.033f;

        // 暗蓝渐变背景
        bgPaint.setShader(new LinearGradient(0, 0, 0, height,
                new int[]{0xFF020818, 0xFF041228, 0xFF061830},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, bgPaint);

        // 绘制上升粒子
        int pc = partX != null ? partX.length : 0;
        for (int i = 0; i < pc; i++) {
            partY[i] -= partSpeed[i] * dt;
            partX[i] += (float) Math.sin(timeSec + i) * 0.3f;
            if (partY[i] < -5) {
                partY[i] = height + 5;
                partX[i] = (float) Math.random() * width;
            }
            canvas.drawCircle(partX[i], partY[i], partSize[i], particlePaint);
        }

        // 绘制水母
        for (int i = 0; i < jellyCount; i++) {
            // 水母缓慢上升 + 水平漂移
            jellyY[i] -= jellySpeed[i] * dt;
            jellyX[i] += (float) Math.sin(timeSec * 0.3f + jellyPhase[i]) * jellyDrift[i] * dt;

            if (jellyY[i] < -jellySize[i] * 3) {
                jellyY[i] = height + jellySize[i] * 2;
                jellyX[i] = (float) Math.random() * width;
            }

            drawJellyfish(canvas, jellyX[i], jellyY[i], jellySize[i],
                    timeSec, jellyPhase[i], jellyAlpha[i]);
        }
    }

    private void drawJellyfish(Canvas canvas, float x, float y, float size,
                               float timeSec, float phase, int alpha) {
        // 脉动效果
        float pulse = 1f + 0.15f * (float) Math.sin(timeSec * 2f + phase);
        float w = size * pulse;
        float h = size * 0.7f * (2f - pulse);

        jellyPath.reset();
        // 伞盖（贝塞尔曲线）
        jellyPath.moveTo(x - w, y);
        jellyPath.cubicTo(x - w, y - h * 1.2f, x - w * 0.3f, y - h * 1.6f, x, y - h * 1.6f);
        jellyPath.cubicTo(x + w * 0.3f, y - h * 1.6f, x + w, y - h * 1.2f, x + w, y);
        // 底部波浪
        float tentacleWave = (float) Math.sin(timeSec * 3f + phase) * w * 0.1f;
        jellyPath.cubicTo(x + w * 0.6f, y + h * 0.3f + tentacleWave,
                x + w * 0.2f, y + h * 0.2f - tentacleWave, x, y + h * 0.3f);
        jellyPath.cubicTo(x - w * 0.2f, y + h * 0.2f + tentacleWave,
                x - w * 0.6f, y + h * 0.3f - tentacleWave, x - w, y);
        jellyPath.close();

        // 半透明青蓝色
        jellyPaint.setColor(0x4088EEFF);
        jellyPaint.setAlpha(alpha * userAlpha / 80);
        canvas.drawPath(jellyPath, jellyPaint);

        // 触须（简单线条）
        jellyPaint.setStyle(Paint.Style.STROKE);
        jellyPaint.setStrokeWidth(2.5f);
        jellyPaint.setAlpha(alpha * userAlpha / 160);
        for (int t = -2; t <= 2; t++) {
            float tx = x + t * w * 0.3f;
            float tentLen = size * (1.2f + 0.3f * (float) Math.sin(timeSec * 2.5f + phase + t));
            float sway = (float) Math.sin(timeSec * 1.5f + phase + t * 0.8f) * w * 0.2f;
            jellyPath.reset();
            jellyPath.moveTo(tx, y + h * 0.2f);
            jellyPath.cubicTo(tx + sway * 0.5f, y + tentLen * 0.4f,
                    tx + sway, y + tentLen * 0.7f,
                    tx + sway * 0.8f, y + tentLen);
            canvas.drawPath(jellyPath, jellyPaint);
        }
        jellyPaint.setStyle(Paint.Style.FILL);
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
        int newJellyfish = (int) params.getFloat("jellyfish", 9);
        userAlpha = (int) params.getFloat("alpha", 70);
        int newParticles = (int) params.getFloat("particles", 21);
        float newMaxJelly = params.getFloat("maxJellySize", 110f);
        float newMaxBubble = params.getFloat("maxBubbleSize", 4f);
        boolean sizeChanged = (newMaxJelly != maxJellySize || newMaxBubble != maxBubbleSize);
        maxJellySize = newMaxJelly;
        maxBubbleSize = newMaxBubble;
        if (newJellyfish != jellyfish || newParticles != particleCount || sizeChanged) {
            jellyfish = newJellyfish;
            particleCount = newParticles;
            if (initialized) {
                allocateJellies();
                allocateParticles();
            }
        }
    }
}
