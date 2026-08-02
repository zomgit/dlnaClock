package com.dlnaclock.screensaver;

import java.util.Random;

/**
 * BounceBurnInManager - 弹射式防烧屏管理器
 * 时钟内容在屏幕内沿直线匀速运动，撞到屏幕边缘时反弹：
 * 1. 反弹方向 = 镜面反射方向 ± 随机角度扰动（扰动幅度 0~maxBounceAngleDeg）
 * 2. 移动速度 = speedPercent% 屏幕宽度每秒
 * 支持手指拖动：dragTo 移动时钟中心到手指位置，release 后恢复弹射
 * 偏移值应用到 Canvas.translate()
 */
public class BounceBurnInManager {

    private Random random = new Random();
    private boolean enabled = false;          // 弹射总开关（跟随防烧屏总开关）
    private int maxBounceAngleDeg = 30;       // 每次弹射随机偏移角度（0~90°）
    private int speedPercent = 8;             // 移动速度（%屏幕宽/秒）

    private float offsetX = 0;                // 时钟相对默认位置的位移（px）
    private float offsetY = 0;
    private float velX = 0;                   // 速度分量（px/s）
    private float velY = 0;

    // 内容边界（无偏移时时钟内容矩形，相对画布左上角）
    private float left = 0, top = 0, right = 0, bottom = 0;
    private float screenWidth = 0, screenHeight = 0;
    private boolean boundsReady = false;

    // 允许的偏移范围（由内容边界与屏幕尺寸推导）
    private float minX = 0, maxX = 0, minY = 0, maxY = 0;

    // 拖动状态
    private boolean dragging = false;

    private OffsetChangeListener listener;

    /** OffsetChangeListener - 偏移量变化监听接口 */
    public interface OffsetChangeListener {
        void onOffsetChanged(float offsetX, float offsetY);
    }

    public BounceBurnInManager() {
    }

    public void setListener(OffsetChangeListener listener) {
        this.listener = listener;
    }

    /** start - 读取设置并初始化弹射（随机初始方向） */
    public void start(int screenWidth, int screenHeight, boolean enabled,
                      int maxBounceAngleDeg, int speedPercent) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.enabled = enabled;
        this.maxBounceAngleDeg = maxBounceAngleDeg;
        this.speedPercent = speedPercent;
        this.boundsReady = false;

        if (enabled) {
            resetOffsets();
            // 随机初始方向，速度大小由 speedPercent 决定
            double angle = random.nextDouble() * 2 * Math.PI;
            float speedPx = speedPercent / 100f * screenWidth;
            velX = (float) (speedPx * Math.cos(angle));
            velY = (float) (speedPx * Math.sin(angle));
        } else {
            velX = 0;
            velY = 0;
        }
    }

    public void stop() {
        enabled = false;
        velX = 0;
        velY = 0;
        resetOffsets();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * setContentBounds - 设置时钟内容边界（无偏移时，相对画布左上角）
     * @param bounds [left, top, right, bottom]，null 时使用整屏作为边界
     */
    public void setContentBounds(float[] bounds) {
        if (bounds != null && bounds.length >= 4 && bounds[2] > bounds[0] && bounds[3] > bounds[1]) {
            this.left = bounds[0];
            this.top = bounds[1];
            this.right = bounds[2];
            this.bottom = bounds[3];
            boundsReady = true;
        } else {
            // 未知内容尺寸：退回整屏边界（内容中心=屏幕中心）
            this.left = screenWidth / 2f;
            this.top = screenHeight / 2f;
            this.right = screenWidth / 2f;
            this.bottom = screenHeight / 2f;
            boundsReady = false;
        }
        // 推导允许的偏移范围：内容矩形必须保持在屏幕内
        minX = -left;
        maxX = screenWidth - right;
        minY = -top;
        maxY = screenHeight - bottom;
        // 内容大于屏幕时钳制到 0
        if (minX >= maxX) { minX = 0; maxX = 0; }
        if (minY >= maxY) { minY = 0; maxY = 0; }
        // 确保当前偏移在合法范围内
        offsetX = clamp(offsetX, minX, maxX);
        offsetY = clamp(offsetY, minY, maxY);
        notifyOffsetChanged();
    }

    /**
     * step - 每帧推进弹射运动
     * @param dtMillis 距上帧的毫秒数
     */
    public void step(long dtMillis) {
        if (!enabled || dragging) return;
        if (dtMillis <= 0 || dtMillis > 200) return; // 防御异常间隔

        float dt = dtMillis / 1000f;
        float newX = offsetX + velX * dt;
        float newY = offsetY + velY * dt;

        boolean bounced = false;
        // X 方向碰撞：取反后统一扰动一次（角落双碰撞也只扰动一次）
        if (newX < minX) {
            newX = minX;
            if (velX < 0) { velX = -velX; bounced = true; }
        } else if (newX > maxX) {
            newX = maxX;
            if (velX > 0) { velX = -velX; bounced = true; }
        }
        // Y 方向碰撞
        if (newY < minY) {
            newY = minY;
            if (velY < 0) { velY = -velY; bounced = true; }
        } else if (newY > maxY) {
            newY = maxY;
            if (velY > 0) { velY = -velY; bounced = true; }
        }
        // 每次弹射只随机扰动一次方向
        if (bounced) {
            perturbAngle();
        }
        offsetX = clamp(newX, minX, maxX);
        offsetY = clamp(newY, minY, maxY);
        notifyOffsetChanged();
    }

    /**
     * perturbAngle - 在镜面反射基础上随机偏转方向（±maxBounceAngleDeg）
     * 保持速度大小不变
     */
    private void perturbAngle() {
        double angle = Math.atan2(velY, velX);
        double perturb = (random.nextDouble() * 2 - 1) * maxBounceAngleDeg;
        angle += Math.toRadians(perturb);
        float speedPx = speedPercent / 100f * screenWidth;
        velX = (float) (speedPx * Math.cos(angle));
        velY = (float) (speedPx * Math.sin(angle));
    }

    /** dragTo - 拖动时钟，使其内容中心跟随手指（屏幕坐标） */
    public void dragTo(float fingerX, float fingerY) {
        if (!enabled) return;
        dragging = true;
        // 无偏移时内容中心
        float centerX = (left + right) / 2f;
        float centerY = (top + bottom) / 2f;
        offsetX = clamp(fingerX - centerX, minX, maxX);
        offsetY = clamp(fingerY - centerY, minY, maxY);
        notifyOffsetChanged();
    }

    /** release - 松手，沿手指最后移动方向恢复弹射；无方向时用随机方向 */
    public void release(float lastMoveX, float lastMoveY) {
        if (!enabled) return;
        dragging = false;
        float speedPx = speedPercent / 100f * screenWidth;
        float len = (float) Math.hypot(lastMoveX, lastMoveY);
        if (len > 1f) {
            // 以设置的速度沿手指最后移动方向弹射
            velX = lastMoveX / len * speedPx;
            velY = lastMoveY / len * speedPx;
        } else {
            // 原地松手：随机方向
            double angle = random.nextDouble() * 2 * Math.PI;
            velX = (float) (speedPx * Math.cos(angle));
            velY = (float) (speedPx * Math.sin(angle));
        }
    }

    /** isDragging - 是否正在拖动 */
    public boolean isDragging() {
        return dragging;
    }

    public float getOffsetX() {
        return offsetX;
    }

    public float getOffsetY() {
        return offsetY;
    }

    private void resetOffsets() {
        offsetX = 0;
        offsetY = 0;
        notifyOffsetChanged();
    }

    private void notifyOffsetChanged() {
        if (listener != null) {
            listener.onOffsetChanged(offsetX, offsetY);
        }
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
