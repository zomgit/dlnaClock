package com.dlnaclock.screensaver.wallpaper;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;

/**
 * NightSkyWallpaper - 星空壁纸
 * 300 颗闪烁星点 + 偶尔流星
 * init() 预生成星点数组，每帧仅更新 alpha
 */
public class NightSkyWallpaper implements WallpaperRenderer {

    private int starCount = 146;
    private static final int METEOR_COUNT = 2;
    private static final int MAX_STARS = 350;  // 小米3骁龙800安全上限
    private static final String TAG = "NightSkyWallpaper";

    private Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint meteorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // 星点预计算数据
    private float[] starX;
    private float[] starY;
    private float[] starSize;
    private float[] starBaseAlpha;
    private float[] starBaseSpeed; // 基础闪烁速度，不含 speedMultiplier
    private float[] starPhase;

    // 流星数据
    private float[] meteorX = new float[METEOR_COUNT];
    private float[] meteorY = new float[METEOR_COUNT];
    private float[] meteorVX = new float[METEOR_COUNT];
    private float[] meteorVY = new float[METEOR_COUNT];
    private float[] meteorLife = new float[METEOR_COUNT];
    private float[] meteorMaxLife = new float[METEOR_COUNT];
    private boolean[] meteorActive = new boolean[METEOR_COUNT];
    private float[] meteorNextSpawn = new float[METEOR_COUNT];

    private int width;
    private int height;
    private boolean initialized = false;

    // 可调参数
    private float speedMultiplier = 1.0f;
    private float meteorChance = 14f;
    private int bgColor = 0xFF000000;
    private float starBrightness = 1.5f;
    private float scale = 1.0f;
    private float maxStarSize = 6f;

    private static final ParamDef[] PARAMS = {
            new ParamDef("speed", "闪烁速度", 0.1f, 3f, 1.0f, 0.1f),
            new ParamDef("starCount", "星星数量", 50, MAX_STARS, 146),
            new ParamDef("maxStarSize", "星星最大大小", 4f, 40f, 6f, 1f),
            new ParamDef("starBrightness", "星星亮度", 0.2f, 1.5f, 1.5f, 0.05f),
            new ParamDef("meteorChance", "流星频率", 1, 20, 14),
            new ParamDef("bgColor", "背景色", 0xFF000000),
    };

    @Override
    public void init(int width, int height) {
        if (width <= 0 || height <= 0) return;
        this.width = width;
        this.height = height;

        starPaint.setStyle(Paint.Style.FILL);
        meteorPaint.setStyle(Paint.Style.STROKE);
        meteorPaint.setStrokeWidth(2f);
        meteorPaint.setStrokeCap(Paint.Cap.ROUND);

        allocateStars();

        // 初始化流星
        for (int i = 0; i < METEOR_COUNT; i++) {
            meteorActive[i] = false;
            meteorNextSpawn[i] = 3f + (float) Math.random() * 8f;
            meteorMaxLife[i] = 0.8f + (float) Math.random() * 0.5f;
        }

        initialized = true;
    }

    private void allocateStars() {
        // 安全限制：防止粒子数过多导致OOM
        if (starCount > MAX_STARS) starCount = MAX_STARS;
        try {
            starX = new float[starCount];
            starY = new float[starCount];
            starSize = new float[starCount];
            starBaseAlpha = new float[starCount];
            starBaseSpeed = new float[starCount];
            starPhase = new float[starCount];
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "OOM allocating stars, resetting to default", e);
            starCount = 300;
            starX = new float[starCount];
            starY = new float[starCount];
            starSize = new float[starCount];
            starBaseAlpha = new float[starCount];
            starBaseSpeed = new float[starCount];
            starPhase = new float[starCount];
        }

        for (int i = 0; i < starCount; i++) {
            starX[i] = (float) Math.random() * width;
            starY[i] = (float) Math.random() * height;
            // 正态分布：大量小星，少量大星（Box-Muller取绝对值）
            float u1 = (float) Math.random();
            float u2 = (float) Math.random();
            float normal = (float) Math.abs(Math.sqrt(-2 * Math.log(Math.max(u1, 0.001))) * Math.cos(2 * Math.PI * u2));
            starSize[i] = 2f * (0.3f + normal * maxStarSize * 0.35f);
            if (starSize[i] > maxStarSize) starSize[i] = maxStarSize;
            starBaseAlpha[i] = 0.3f + (float) Math.random() * 0.5f;
            starBaseSpeed[i] = (0.5f + (float) Math.random() * 2f);
            starPhase[i] = (float) Math.random() * (float) Math.PI * 2f;
        }

        // 初始化流星
        for (int i = 0; i < METEOR_COUNT; i++) {
            meteorActive[i] = false;
            meteorNextSpawn[i] = 3f + (float) Math.random() * 8f;
            meteorMaxLife[i] = 0.8f + (float) Math.random() * 0.5f;
        }

        initialized = true;
    }

    @Override
    public void draw(Canvas canvas, int width, int height, long elapsedMs) {
        if (!initialized) {
            init(width, height);
        }

        // 深蓝黑渐变背景
        canvas.drawColor(bgColor);

        canvas.save();
        canvas.scale(scale, scale, width / 2f, height / 2f);

        float timeSec = elapsedMs / 1000f;

        // 绘制星点
        for (int i = 0; i < starCount; i++) {
            float alpha = starBaseAlpha[i] + 0.3f * (float) Math.sin(timeSec * starBaseSpeed[i] * speedMultiplier + starPhase[i]);
            if (alpha < 0) alpha = 0;
            if (alpha > 1) alpha = 1;

            starPaint.setColor(0xFFFFFF);
            starPaint.setAlpha((int) (alpha * 255 * starBrightness));
            canvas.drawCircle(starX[i], starY[i], starSize[i], starPaint);
        }

        // 更新和绘制流星
        float dt = 0.033f;
        for (int i = 0; i < METEOR_COUNT; i++) {
            if (!meteorActive[i]) {
                meteorNextSpawn[i] -= dt;
                if (meteorNextSpawn[i] <= 0 && Math.random() < meteorChance * 0.03f) {
                    spawnMeteor(i);
                }
            } else {
                meteorLife[i] -= dt;
                if (meteorLife[i] <= 0) {
                    meteorActive[i] = false;
                    meteorNextSpawn[i] = 5f + (float) Math.random() * 10f;
                } else {
                    meteorX[i] += meteorVX[i] * dt;
                    meteorY[i] += meteorVY[i] * dt;

                    // 绘制流星拖尾
                    float lifeRatio = meteorLife[i] / meteorMaxLife[i];
                    int alpha = (int) (lifeRatio * 220);
                    meteorPaint.setColor(0xFFFFFF);
                    meteorPaint.setAlpha(alpha);

                    float tailLen = 40f * lifeRatio;
                    float norm = (float) Math.sqrt(meteorVX[i] * meteorVX[i] + meteorVY[i] * meteorVY[i]);
                    float tx = meteorX[i] - (meteorVX[i] / norm) * tailLen;
                    float ty = meteorY[i] - (meteorVY[i] / norm) * tailLen;
                    canvas.drawLine(meteorX[i], meteorY[i], tx, ty, meteorPaint);

                    // 流星头部亮点
                    starPaint.setColor(0xFFFFFF);
                    starPaint.setAlpha(alpha);
                    canvas.drawCircle(meteorX[i], meteorY[i], 2.5f, starPaint);
                }
            }
        }

        canvas.restore();
    }

    private void spawnMeteor(int index) {
        meteorActive[index] = true;
        meteorLife[index] = meteorMaxLife[index];
        // 从上方随机位置出发，向右下或左下飞行
        meteorX[index] = (float) Math.random() * width;
        meteorY[index] = (float) Math.random() * height * 0.3f;
        float angle = (float) Math.PI * (0.15f + (float) Math.random() * 0.2f);
        float speed = 300f + (float) Math.random() * 200f;
        float dir = Math.random() > 0.5f ? 1f : -1f;
        meteorVX[index] = dir * speed * (float) Math.cos(angle);
        meteorVY[index] = speed * (float) Math.sin(angle);
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
        int newCount = (int) params.getFloat("starCount", 146);
        starBrightness = params.getFloat("starBrightness", 1.5f);
        meteorChance = params.getFloat("meteorChance", 14f);
        bgColor = ((int) params.getFloat("bgColor", 0xFF000000)) | 0xFF000000;
        float newMaxStarSize = params.getFloat("maxStarSize", 6f);
        boolean sizeChanged = (newMaxStarSize != maxStarSize);
        maxStarSize = newMaxStarSize;
        if (newCount != starCount || sizeChanged) {
            starCount = newCount;
            if (initialized) allocateStars();
        }
    }
}
