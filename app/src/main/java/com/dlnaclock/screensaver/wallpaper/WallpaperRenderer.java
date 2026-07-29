package com.dlnaclock.screensaver.wallpaper;

import android.graphics.Canvas;
import android.os.Bundle;

/**
 * WallpaperRenderer - 动态壁纸策略接口
 * 所有壁纸实现类必须遵循零分配渲染原则：
 * Paint/Path/float[] 等资源在 init() 中预分配，draw() 中不创建任何对象
 */
public interface WallpaperRenderer {

    /**
     * init - 尺寸变化时预计算资源
     * @param width 画布宽度
     * @param height 画布高度
     */
    void init(int width, int height);

    /**
     * draw - 每帧绘制
     * @param canvas 目标画布
     * @param width 画布宽度
     * @param height 画布高度
     * @param elapsedMs 自壁纸启动以来经过的毫秒数
     */
    void draw(Canvas canvas, int width, int height, long elapsedMs);

    /**
     * release - 释放资源
     */
    void release();

    /**
     * getParamDefs - 返回可调参数定义
     * @return 参数数组，无参数时返回空数组
     */
    ParamDef[] getParamDefs();

    /**
     * applyParams - 运行时应用参数（主屏滑条拖动时调用）
     * @param params 参数键值对
     */
    void applyParams(Bundle params);
}
