package com.dlnaclock.clock;

import android.graphics.Canvas;

import java.util.Calendar;

/**
 * ClockRenderer - 时钟渲染接口
 * 所有时钟样式（Digital/Analog/Minimal）实现此接口
 * 在 ScreenSaverView 的 onDraw 中调用
 */
public interface ClockRenderer {
    /**
     * draw - 绘制时钟
     * @param canvas 画布
     * @param width 屏幕宽度
     * @param height 屏幕高度
     * @param time 当前时间
     * @param config 时钟配置（样式/字号/位置等）
     */
    void draw(Canvas canvas, int width, int height, Calendar time, ClockConfig config);
}
