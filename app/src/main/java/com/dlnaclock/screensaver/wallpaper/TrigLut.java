package com.dlnaclock.screensaver.wallpaper;

/**
 * TrigLut - 预计算三角函数查找表
 *
 * 目的：消除每帧数百次 Math.sin/Math.cos 调用，
 * 在动态壁纸渲染中将 CPU 密集型三角函数转换为数组查表。
 *
 * 精度：1024 条目覆盖 0~2π（≈0.35°/步），对于粒子/波浪动画足够。
 * 线程安全：所有方法为纯函数，无副作用。
 *
 * 使用方式：
 *   float s = TrigLut.sin(angleRad);
 *   float c = TrigLut.cos(angleRad);
 */
public final class TrigLut {

    /** 查找表大小（2 的幂，便于位掩码取模） */
    private static final int SIZE = 1024;

    /** 弧度→索引缩放因子：SIZE / (2π) */
    private static final float RAD_TO_IDX = 162.97466f;

    /** 索引掩码：SIZE - 1，用位运算替代取模 */
    private static final int MASK = SIZE - 1;

    private static final float[] SIN = new float[SIZE];

    static {
        final double step = 2.0 * Math.PI / SIZE;
        for (int i = 0; i < SIZE; i++) {
            SIN[i] = (float) Math.sin(i * step);
        }
    }

    private TrigLut() {}

    /**
     * 快速正弦（弧度→[-1, 1]）
     */
    public static float sin(float radians) {
        return SIN[((int)(radians * RAD_TO_IDX)) & MASK];
    }

    /**
     * 快速余弦（弧度→[-1, 1]）
     */
    public static float cos(float radians) {
        // cos(x) = sin(x + π/2)
        return SIN[((int)(radians * RAD_TO_IDX + SIZE / 4)) & MASK];
    }

    /**
     * 快速正弦（角度制）
     */
    public static float sinDeg(float degrees) {
        float rad = degrees * 0.017453292f; // PI/180
        return SIN[((int)(rad * RAD_TO_IDX)) & MASK];
    }

    /**
     * 快速余弦（角度制）
     */
    public static float cosDeg(float degrees) {
        float rad = degrees * 0.017453292f;
        return SIN[((int)(rad * RAD_TO_IDX + SIZE / 4)) & MASK];
    }

    /**
     * ColorLut - 预计算 HSV 色相偏移查找表
     * 消除 shiftHue 中每帧的 Color.colorToHSV + Color.HSVToColor 调用。
     *
     * 原理：色相偏移本质是 RGB→HSV→旋转 H→HSV→RGB，可预计算。
     * 对于固定调色板的壁纸（Aurora/Galaxy/DynamicGradient 等），
     * 将整个调色板的颜色在 360° 范围内预计算，draw 时直接查表。
     */
    public static final class ColorLut {

        /** 360 度 × 调色板颜色数 */
        private static final int HUE_STEPS = 360;

        private ColorLut() {}

        /**
         * 为固定调色板预计算色相偏移表
         * @param palette 原始 ARGB 颜色数组
         * @return palette.length × 360 的二维 int 数组 [colorIndex][hueDeg]
         */
        public static int[][] buildHueTable(int[] palette) {
            int[][] table = new int[palette.length][HUE_STEPS];
            float[] hsv = new float[3];
            for (int c = 0; c < palette.length; c++) {
                int base = palette[c];
                android.graphics.Color.colorToHSV(base, hsv);
                int alpha = (base >> 24) & 0xFF;
                float origH = hsv[0];
                for (int h = 0; h < HUE_STEPS; h++) {
                    hsv[0] = (origH + h) % 360f;
                    table[c][h] = android.graphics.Color.HSVToColor(alpha, hsv);
                }
            }
            return table;
        }

        /**
         * 根据已构建的色相表获取偏移后的颜色
         * @param table buildHueTable 返回的表
         * @param colorIndex 颜色在调色板中的索引
         * @param hueShift 色相偏移角度（自动取模 360）
         */
        public static int getShifted(int[][] table, int colorIndex, float hueShift) {
            int h = ((int) hueShift) % HUE_STEPS;
            if (h < 0) h += HUE_STEPS;
            return table[colorIndex][h];
        }
    }
}
