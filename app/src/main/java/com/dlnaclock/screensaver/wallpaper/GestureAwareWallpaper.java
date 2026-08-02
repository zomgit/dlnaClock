package com.dlnaclock.screensaver.wallpaper;

/**
 * GestureAwareWallpaper - 手势感知壁纸可选接口
 * 实现此接口的壁纸可自行应用手势变换（如将旋转叠加到自身 3D 矩阵，效果更真实）；
 * 未实现的壁纸由 BackgroundManager 以 Canvas 整体变换兜底
 */
public interface GestureAwareWallpaper {

    /**
     * applyGesture - 应用手势变换参数
     * @param rotX    绕 X 轴旋转角度（度）
     * @param rotY    绕 Y 轴旋转角度（度）
     * @param offsetX 平移偏移（像素）
     * @param offsetY 平移偏移（像素）
     * @param scale   缩放倍率
     */
    void applyGesture(float rotX, float rotY, float offsetX, float offsetY, float scale);
}
