package com.dlnaclock.screensaver.wallpaper;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

/**
 * LuaCanvasBridge - Lua 脚本可调用的 Canvas 绘图桥接
 * 封装 Paint/Path（复用，不每帧创建），遵守零分配渲染原则
 * 暴露 drawColor / drawCircle / drawRect / drawLine / beginPath / fillPath / strokePath 等方法
 * 每帧开始时通过 reset() 重置内部状态
 * 所有 API 使用 API ≤ 19
 */
public class LuaCanvasBridge {

    private Canvas canvas;
    private final Paint fillPaint;
    private final Paint strokePaint;
    private final Path path;

    // 当前画笔状态
    private float strokeWidth = 2f;
    private int fillColor = 0xFFFFFFFF;
    private int strokeColor = 0xFFFFFFFF;
    private int alpha = 255;

    public LuaCanvasBridge() {
        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);

        path = new Path();
    }

    /** 每帧开始时调用，设置 Canvas 并重置状态 */
    public void reset(Canvas canvas) {
        this.canvas = canvas;
        this.alpha = 255;
        this.strokeWidth = 2f;
        this.fillColor = 0xFFFFFFFF;
        this.strokeColor = 0xFFFFFFFF;
        path.reset();
        fillPaint.setColor(fillColor);
        fillPaint.setAlpha(255);
        strokePaint.setColor(strokeColor);
        strokePaint.setAlpha(255);
        strokePaint.setStrokeWidth(strokeWidth);
    }

    // === 背景 ===

    /** drawColor - 填充背景色 */
    public void drawColor(int color) {
        canvas.drawColor(color);
    }

    /** drawColorARGB - 填充背景色 */
    public void drawColorARGB(int a, int r, int g, int b) {
        canvas.drawColor(Color.argb(a, r, g, b));
    }

    // === 画笔设置 ===

    /** setFillColor - 设置填充颜色 */
    public void setFillColor(int color) {
        fillColor = color;
        fillPaint.setColor(color);
    }

    /** setFillColorARGB */
    public void setFillColorARGB(int a, int r, int g, int b) {
        setFillColor(Color.argb(a, r, g, b));
    }

    /** setStrokeColor */
    public void setStrokeColor(int color) {
        strokeColor = color;
        strokePaint.setColor(color);
    }

    /** setStrokeColorARGB */
    public void setStrokeColorARGB(int a, int r, int g, int b) {
        setStrokeColor(Color.argb(a, r, g, b));
    }

    /** setAlpha - 设置全局透明度 0-255 */
    public void setAlpha(int a) {
        alpha = a;
        fillPaint.setAlpha(a);
        strokePaint.setAlpha(a);
    }

    /** setStrokeWidth */
    public void setStrokeWidth(float w) {
        strokeWidth = w;
        strokePaint.setStrokeWidth(w);
    }

    /** setBlendMode - 设置混合模式 (0=SRC_OVER, 1=ADD, 2=SCREEN) */
    public void setBlendMode(int mode) {
        PorterDuff.Mode[] modes = {
                PorterDuff.Mode.SRC_OVER,
                PorterDuff.Mode.ADD,
                PorterDuff.Mode.SCREEN
        };
        if (mode >= 0 && mode < modes.length) {
            fillPaint.setXfermode(new PorterDuffXfermode(modes[mode]));
            strokePaint.setXfermode(new PorterDuffXfermode(modes[mode]));
        }
    }

    // === 基本绘图 ===

    /** drawCircle */
    public void drawCircle(float cx, float cy, float radius) {
        canvas.drawCircle(cx, cy, radius, fillPaint);
    }

    /** strokeCircle */
    public void strokeCircle(float cx, float cy, float radius) {
        canvas.drawCircle(cx, cy, radius, strokePaint);
    }

    /** drawRect */
    public void drawRect(float left, float top, float right, float bottom) {
        canvas.drawRect(left, top, right, bottom, fillPaint);
    }

    /** strokeRect */
    public void strokeRect(float left, float top, float right, float bottom) {
        canvas.drawRect(left, top, right, bottom, strokePaint);
    }

    /** drawLine */
    public void drawLine(float x1, float y1, float x2, float y2) {
        canvas.drawLine(x1, y1, x2, y2, strokePaint);
    }

    /** drawRoundRect */
    public void drawRoundRect(float left, float top, float right, float bottom, float rx, float ry) {
        canvas.drawRoundRect(left, top, right, bottom, rx, ry, fillPaint);
    }

    // === Path 操作 ===

    /** beginPath - 开始新路径 */
    public void beginPath() {
        path.reset();
    }

    /** moveTo */
    public void moveTo(float x, float y) {
        path.moveTo(x, y);
    }

    /** lineTo */
    public void lineTo(float x, float y) {
        path.lineTo(x, y);
    }

    /** cubicTo */
    public void cubicTo(float cp1x, float cp1y, float cp2x, float cp2y, float x, float y) {
        path.cubicTo(cp1x, cp1y, cp2x, cp2y, x, y);
    }

    /** quadTo */
    public void quadTo(float cpx, float cpy, float x, float y) {
        path.quadTo(cpx, cpy, x, y);
    }

    /** closePath */
    public void closePath() {
        path.close();
    }

    /** fillPath - 填充当前路径 */
    public void fillPath() {
        canvas.drawPath(path, fillPaint);
    }

    /** strokePath - 描边当前路径 */
    public void strokePath() {
        canvas.drawPath(path, strokePaint);
    }

    // === 工具方法 ===

    /** 获取 Canvas 宽度（供 Lua 使用） */
    public float canvasWidth() {
        return canvas != null ? canvas.getWidth() : 0;
    }

    /** 获取 Canvas 高度 */
    public float canvasHeight() {
        return canvas != null ? canvas.getHeight() : 0;
    }

    /** ARGB 颜色合成 */
    public static int argb(int a, int r, int g, int b) {
        return Color.argb(a, r, g, b);
    }
}
