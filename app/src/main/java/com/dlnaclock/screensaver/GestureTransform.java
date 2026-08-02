package com.dlnaclock.screensaver;

import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;

/**
 * GestureTransform - 背景手势操控的变换状态
 * 一指旋转（rotX/rotY，绕屏幕中心 3D 透视旋转）、双指平移（offsetX/offsetY）、
 * 双指缩放（scale），以及将状态应用到 Canvas 的方法
 * 零分配：Camera/Matrix 预分配复用，draw 中不创建对象
 */
public class GestureTransform {

    /** 绕 X 轴最大角度（±90°，防止翻穿背面） */
    private static final float MAX_ROT_X = 90f;
    /** 绕 Y 轴最大角度（±180°） */
    private static final float MAX_ROT_Y = 180f;
    /** 最小/最大缩放倍率 */
    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 3.0f;
    /** 平移上限（相对屏幕宽/高的比例） */
    private static final float MAX_OFFSET_RATIO = 0.75f;

    private float rotX;
    private float rotY;
    private float offsetX;
    private float offsetY;
    private float scale = 1f;

    private final Camera camera = new Camera();
    private final Matrix matrix = new Matrix();

    public GestureTransform() {}

    /** 拷贝构造（用于按壁纸保存/恢复手势快照） */
    public GestureTransform(GestureTransform src) {
        copyFrom(src);
    }

    /** 从另一状态复制全部字段 */
    public void copyFrom(GestureTransform src) {
        this.rotX = src.rotX;
        this.rotY = src.rotY;
        this.offsetX = src.offsetX;
        this.offsetY = src.offsetY;
        this.scale = src.scale;
    }

    public float getRotX() { return rotX; }
    public float getRotY() { return rotY; }
    public float getOffsetX() { return offsetX; }
    public float getOffsetY() { return offsetY; }
    public float getScale() { return scale; }

    /** 增量旋转（带钳制） */
    public void rotateBy(float dRotX, float dRotY) {
        rotX = clamp(rotX + dRotX, -MAX_ROT_X, MAX_ROT_X);
        rotY = clamp(rotY + dRotY, -MAX_ROT_Y, MAX_ROT_Y);
    }

    /** 增量平移（带钳制，需屏幕尺寸） */
    public void translateBy(float dx, float dy, int viewWidth, int viewHeight) {
        float limitX = viewWidth * MAX_OFFSET_RATIO;
        float limitY = viewHeight * MAX_OFFSET_RATIO;
        offsetX = clamp(offsetX + dx, -limitX, limitX);
        offsetY = clamp(offsetY + dy, -limitY, limitY);
    }

    /** 增量缩放（带钳制） */
    public void scaleBy(float factor) {
        scale = clamp(scale * factor, MIN_SCALE, MAX_SCALE);
    }

    /** 重置为初始状态 */
    public void reset() {
        rotX = 0f;
        rotY = 0f;
        offsetX = 0f;
        offsetY = 0f;
        scale = 1f;
    }

    /** 仅重置旋转（保留平移/缩放，面板“还原旋转”单独使用） */
    public void resetRotation() {
        rotX = 0f;
        rotY = 0f;
    }

    /** 是否为无变换初始状态 */
    public boolean isIdentity() {
        return rotX == 0f && rotY == 0f && offsetX == 0f && offsetY == 0f && scale == 1f;
    }

    /**
     * applyTo - 将变换应用到 Canvas（绕屏幕中心）
     * 顺序：中心化 → Camera 3D 旋转 → 缩放 → 平移（屏幕像素，不随缩放变化）→ 还原
     */
    public void applyTo(Canvas canvas, int width, int height) {
        float cx = width / 2f;
        float cy = height / 2f;

        canvas.translate(cx, cy);
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);

        // Camera 旋转角度是累加的，每次 save/restore 保证状态干净
        camera.save();
        camera.rotateX(rotX);
        camera.rotateY(rotY);
        camera.getMatrix(matrix);
        camera.restore();
        canvas.concat(matrix);

        canvas.translate(-cx, -cy);
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
