package com.dlnaclock.screensaver.wallpaper;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;

import java.util.Random;

/**
 * MatrixRainWallpaper - 黑客帝国字符雨壁纸
 * 多列字符独立向下滚动，头部高亮，尾部渐暗，带拖影残影效果
 * 兼容 Android 4.4，零分配渲染，CPU 占用低
 */
public class MatrixRainWallpaper implements WallpaperRenderer {

    // ===== 保守默认参数（防止低端设备崩溃） =====
    private static final int DEFAULT_CHAR_SIZE = 32;       // 字符像素大小
    private static final int DEFAULT_MIN_LENGTH = 6;       // 最短列字符数
    private static final int DEFAULT_MAX_LENGTH = 39;      // 最长列字符数
    private static final float DEFAULT_MIN_SPEED = 60f;    // 最慢速度 px/s
    private static final float DEFAULT_MAX_SPEED = 160f;   // 最快速度 px/s
    private static final int DEFAULT_TRAIL_ALPHA = 51;     // 拖影透明度(越小残影越长)
    private static final float DEFAULT_CHAR_CHANGE_RATE = 0.06f; // 每帧字符变化比例

    // 最大列数上限（防止OOM）
    private static final int MAX_COLUMNS = 80;
    // 最大列长度上限
    private static final int MAX_COLUMN_LENGTH = 40;

    // 字符集：拉丁 + 数字 + 片假名
    private static final char[] CHARSET = (
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
            "0123456789" +
            "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉ" +
            "ﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ"
    ).toCharArray();

    // 颜色梯度：头 -> 尾
    private static final int COLOR_HEAD = 0xFFFFFFFF;   // 白
    private static final int COLOR_NEAR = 0xFFA8FFA8;   // 浅绿
    private static final int COLOR_MID = 0xFF55FF55;    // 绿
    private static final int COLOR_TAIL = 0xFF009900;   // 深绿

    // ===== 列数据（结构体数组，避免对象分配） =====
    private int columnCount;
    private float[] colX;          // 每列X坐标
    private float[] colHeadY;      // 每列头部Y位置
    private float[] colSpeed;      // 每列速度 px/s
    private int[] colLength;       // 每列字符数量
    private char[][] colChars;     // 每列字符内容
    private float[] colAccum;      // 每列累计位移（用于判断滚动一格）

    // ===== 绘制资源（init中预分配） =====
    private Paint textPaint;
    private Paint trailPaint;
    private Random random;

    private int width;
    private int height;
    private int charSize;
    private int charHeight;
    private boolean initialized = false;
    private long lastTimeMs = -1;

    // ===== 可调参数 =====
    private float speedMultiplier = 2.2f;
    private int trailAlpha = DEFAULT_TRAIL_ALPHA;
    private float charChangeRate = DEFAULT_CHAR_CHANGE_RATE;
    private int fontSize = DEFAULT_CHAR_SIZE;
    private float headGlowIntensity = 0f;  // 头部发光强度 0~1
    private int paramColumns = 76;            // 0=自动(按屏宽计算)
    private int paramColLength = DEFAULT_MAX_LENGTH; // 列长度

    private static final ParamDef[] PARAMS = {
            new ParamDef("speed", "下落速度", 0.2f, 3f, 2.2f, 0.1f),
            new ParamDef("columns", "列数", 0, MAX_COLUMNS, 76),  // 0=自动
            new ParamDef("colLength", "列长度", 4, MAX_COLUMN_LENGTH, DEFAULT_MAX_LENGTH),
            new ParamDef("fontSize", "字符大小", 12, 32, DEFAULT_CHAR_SIZE),
            new ParamDef("trail", "拖影长度", 10, 60, DEFAULT_TRAIL_ALPHA),
            new ParamDef("changeRate", "字符变化率", 0.01f, 0.15f, DEFAULT_CHAR_CHANGE_RATE, 0.01f),
            new ParamDef("headGlow", "头部发光", 0f, 1f, 0f, 0.05f),
    };

    @Override
    public void init(int width, int height) {
        this.width = width;
        this.height = height;
        this.charSize = fontSize;
        this.charHeight = (int) (charSize * 1.3f);

        // 初始化画笔
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTypeface(Typeface.MONOSPACE);
        textPaint.setTextSize(charSize);
        textPaint.setColor(COLOR_MID);

        trailPaint = new Paint();
        trailPaint.setStyle(Paint.Style.FILL);

        random = new Random();

        // 计算列数：参数>0时使用参数值，否则自动
        if (paramColumns > 0) {
            columnCount = paramColumns;
        } else {
            columnCount = width / charSize;
        }
        if (columnCount > MAX_COLUMNS) columnCount = MAX_COLUMNS;
        if (columnCount < 1) columnCount = 1;

        // 分配列数组
        colX = new float[columnCount];
        colHeadY = new float[columnCount];
        colSpeed = new float[columnCount];
        colLength = new int[columnCount];
        colChars = new char[columnCount][];
        colAccum = new float[columnCount];

        // 初始化每列
        for (int i = 0; i < columnCount; i++) {
            colX[i] = i * charSize + charSize * 0.5f;
            resetColumn(i, true);
        }

        lastTimeMs = -1;
        initialized = true;
    }

    /** 重置一列（随机起始位置、速度、长度） */
    private void resetColumn(int i, boolean initial) {
        int minLen = Math.max(3, paramColLength / 2);
        int maxLen = paramColLength;
        int len = minLen + random.nextInt(maxLen - minLen + 1);
        if (len > MAX_COLUMN_LENGTH) len = MAX_COLUMN_LENGTH;
        colLength[i] = len;
        colSpeed[i] = DEFAULT_MIN_SPEED + random.nextFloat() * (DEFAULT_MAX_SPEED - DEFAULT_MIN_SPEED);
        colAccum[i] = 0f;

        // 初始时随机分布在屏幕上方，避免同步
        if (initial) {
            colHeadY[i] = -random.nextInt(height + charHeight * len);
        } else {
            colHeadY[i] = -(charHeight * len) - random.nextInt(charHeight * 5);
        }

        // 分配字符数组并填充随机字符
        if (colChars[i] == null || colChars[i].length != len) {
            colChars[i] = new char[len];
        }
        for (int j = 0; j < len; j++) {
            colChars[i][j] = CHARSET[random.nextInt(CHARSET.length)];
        }
    }

    @Override
    public void draw(Canvas canvas, int width, int height, long elapsedMs) {
        if (!initialized) {
            init(width, height);
        }

        // 计算 deltaTime
        float dt;
        if (lastTimeMs < 0) {
            dt = 0.016f; // 首帧假设16ms
        } else {
            dt = (elapsedMs - lastTimeMs) / 1000f;
        }
        lastTimeMs = elapsedMs;
        // 限制dt防止跳帧过大
        if (dt > 0.1f) dt = 0.1f;
        if (dt <= 0f) dt = 0.016f;

        // 更新逻辑
        update(dt);

        // 绘制拖影背景（半透明黑色覆盖）
        trailPaint.setColor(Color.BLACK);
        trailPaint.setAlpha(trailAlpha);
        canvas.drawRect(0, 0, width, height, trailPaint);

        // 绘制所有列
        for (int i = 0; i < columnCount; i++) {
            drawColumn(canvas, i);
        }
    }

    private void update(float dt) {
        float effectiveDt = dt * speedMultiplier;
        for (int i = 0; i < columnCount; i++) {
            float move = colSpeed[i] * effectiveDt;
            colHeadY[i] += move;
            colAccum[i] += move;

            // 滚动字符：每移动一个字符高度，整体后移并插入新字符
            while (colAccum[i] >= charHeight) {
                colAccum[i] -= charHeight;
                scrollChars(i);
            }

            // 随机变化少量字符（更自然）
            if (charChangeRate > 0f) {
                int changeCount = (int) (colLength[i] * charChangeRate);
                if (changeCount < 1 && random.nextFloat() < charChangeRate) changeCount = 1;
                for (int c = 0; c < changeCount; c++) {
                    int idx = random.nextInt(colLength[i]);
                    colChars[i][idx] = CHARSET[random.nextInt(CHARSET.length)];
                }
            }

            // 头部超出屏幕底部+列长度，重置
            if (colHeadY[i] > height + colLength[i] * charHeight) {
                resetColumn(i, false);
            }
        }
    }

    /** 字符滚动：整体后移一位，头部插入新随机字符 */
    private void scrollChars(int col) {
        char[] chars = colChars[col];
        int len = chars.length;
        // 从尾部向前移动
        for (int j = len - 1; j > 0; j--) {
            chars[j] = chars[j - 1];
        }
        chars[0] = CHARSET[random.nextInt(CHARSET.length)];
    }

    private void drawColumn(Canvas canvas, int col) {
        char[] chars = colChars[col];
        int len = colLength[col];
        float x = colX[col];
        float headY = colHeadY[col];

        for (int j = 0; j < len; j++) {
            float y = headY - j * charHeight;

            // 跳过屏幕外的字符
            if (y < -charHeight || y > height + charHeight) continue;

            // 计算 alpha：头部最亮，尾部渐暗
            float ratio = (float) j / len;
            int alpha = (int) (255 * (1f - ratio));
            if (alpha < 15) alpha = 15;

            // 颜色梯度
            int color;
            if (j == 0) {
                color = COLOR_HEAD;
            } else if (j == 1) {
                color = COLOR_NEAR;
            } else if (ratio < 0.5f) {
                color = COLOR_MID;
            } else {
                color = COLOR_TAIL;
            }

            textPaint.setColor(color);
            textPaint.setAlpha(alpha);

            // 头部发光效果：强度可调，绘制大号低透明度光晕
            if (headGlowIntensity > 0.01f && j == 0) {
                int glowAlpha = (int) (100 * headGlowIntensity);
                float glowSize = charSize * (1f + headGlowIntensity * 0.6f);
                textPaint.setAlpha(glowAlpha);
                textPaint.setTextSize(glowSize);
                canvas.drawText(chars, j, 1, x, y, textPaint);
                textPaint.setTextSize(charSize);
                textPaint.setAlpha(alpha);
            }

            canvas.drawText(chars, j, 1, x, y, textPaint);
        }
    }

    @Override
    public void release() {
        initialized = false;
        colChars = null;
        colX = null;
        colHeadY = null;
        colSpeed = null;
        colLength = null;
        colAccum = null;
    }

    @Override
    public ParamDef[] getParamDefs() {
        return PARAMS;
    }

    @Override
    public void applyParams(Bundle params) {
        speedMultiplier = params.getFloat("speed", 2.2f);
        trailAlpha = (int) params.getFloat("trail", DEFAULT_TRAIL_ALPHA);
        charChangeRate = params.getFloat("changeRate", DEFAULT_CHAR_CHANGE_RATE);
        headGlowIntensity = params.getFloat("headGlow", 0f);

        boolean needReinit = false;

        int newFontSize = (int) params.getFloat("fontSize", DEFAULT_CHAR_SIZE);
        if (newFontSize != fontSize) {
            fontSize = newFontSize;
            needReinit = true;
        }

        int newColumns = (int) params.getFloat("columns", 76);
        if (newColumns != paramColumns) {
            paramColumns = newColumns;
            needReinit = true;
        }

        int newColLength = (int) params.getFloat("colLength", DEFAULT_MAX_LENGTH);
        if (newColLength != paramColLength) {
            paramColLength = newColLength;
            needReinit = true;
        }

        if (needReinit && initialized) {
            init(width, height);
        }
    }
}
