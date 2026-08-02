package com.dlnaclock.screensaver;

import android.content.Context;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;

/**
 * GestureController - 动态背景手势控制器
 * 一指拖动：背景绕 X/Y 轴 3D 旋转（跟手）
 * 双指拖动：背景整体平移（双指焦点位移）
 * 双指捏合：背景缩放（ScaleGestureDetector）
 * 轻点（未触发手势）不消费事件，由 ScreenSaverView 触发点击（切换控件）
 */
public class GestureController {

    /** 手势模式 */
    private enum Mode { NONE, ROTATE, SCALE_PAN }

    /**
     * 旋转状态监听 - 手势旋转角变化时通知外部（参数面板实时显示）
     */
    public interface RotationStateListener {
        /** 旋转角变化（角度制） */
        void onRotationChanged(float rotX, float rotY);
    }

    /** 滑满整屏对应旋转角度 */
    private static final float ROT_DEG_PER_SCREEN = 180f;

    private final GestureTransform transform = new GestureTransform();
    private final ScaleGestureDetector scaleDetector;
    private final float touchSlop;

    // 按壁纸类型保存的手势快照（叠加式壁纸切换后互不干扰）
    private final SparseArray<GestureTransform> savedTransforms = new SparseArray<>();
    private int currentWallpaperType = -1;

    private RotationStateListener rotationListener;

    private Mode mode = Mode.NONE;
    private int viewWidth;
    private int viewHeight;
    private float downX;     // 单指按下位置（滑动阈值判定）
    private float downY;
    private float lastX;     // 单指上次位置
    private float lastY;
    private float prevFocusX; // 双指上次焦点（平移增量）
    private float prevFocusY;

    public GestureController(Context context) {
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                mode = Mode.SCALE_PAN;
                prevFocusX = detector.getFocusX();
                prevFocusY = detector.getFocusY();
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                // 缩放 + 焦点位移即双指平移
                transform.scaleBy(detector.getScaleFactor());
                transform.translateBy(detector.getFocusX() - prevFocusX,
                        detector.getFocusY() - prevFocusY, viewWidth, viewHeight);
                prevFocusX = detector.getFocusX();
                prevFocusY = detector.getFocusY();
                return true;
            }
        });
    }

    public GestureTransform getTransform() {
        return transform;
    }

    /** setRotationStateListener - 设置旋转状态监听（面板实时显示旋转角） */
    public void setRotationStateListener(RotationStateListener listener) {
        this.rotationListener = listener;
    }

    /**
     * setCurrentWallpaper - 切换壁纸时保存当前手势状态并恢复目标壁纸的状态
     * （各壁纸手势结果互相独立）
     */
    public void setCurrentWallpaper(int wallpaperType) {
        if (currentWallpaperType >= 0) {
            savedTransforms.put(currentWallpaperType, new GestureTransform(transform));
        }
        currentWallpaperType = wallpaperType;
        GestureTransform saved = savedTransforms.get(wallpaperType);
        if (saved != null) {
            transform.copyFrom(saved);
        } else {
            transform.reset();
        }
    }

    /** resetRotation - 仅还原旋转（保留平移/缩放），面板“还原旋转”使用 */
    public void resetRotation() {
        transform.resetRotation();
        if (currentWallpaperType >= 0) {
            savedTransforms.put(currentWallpaperType, new GestureTransform(transform));
        }
    }

    /** resetCurrentWallpaperTransform - 一键还原：清零当前壁纸的手势状态 */
    public void resetCurrentWallpaperTransform() {
        transform.reset();
        if (currentWallpaperType >= 0) {
            savedTransforms.put(currentWallpaperType, new GestureTransform(transform));
        }
    }

    /**
     * onTouch - 处理触摸事件
     * @return true 表示手势已消费（旋转/平移/缩放），false 表示可视为点击
     */
    public boolean onTouch(MotionEvent event, int width, int height) {
        viewWidth = width;
        viewHeight = height;
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mode = Mode.NONE;
                downX = lastX = event.getX();
                downY = lastY = event.getY();
                scaleDetector.onTouchEvent(event);
                return false; // 尚未判定，点击候选

            case MotionEvent.ACTION_POINTER_DOWN:
                scaleDetector.onTouchEvent(event);
                return true; // 第二指落下，不再可能是点击

            case MotionEvent.ACTION_MOVE:
                scaleDetector.onTouchEvent(event);
                if (mode == Mode.SCALE_PAN) {
                    return true; // 缩放/平移已在 onScale 回调中应用
                }
                if (event.getPointerCount() == 1 && mode == Mode.NONE) {
                    float x = event.getX();
                    float y = event.getY();
                    // 超过滑动阈值才判定为一指旋转，与点击区分
                    if (Math.abs(x - downX) > touchSlop || Math.abs(y - downY) > touchSlop) {
                        mode = Mode.ROTATE;
                    }
                }
                if (mode == Mode.ROTATE) {
                    float x = event.getX();
                    float y = event.getY();
                    float dx = x - lastX;
                    float dy = y - lastY;
                    // 跟手方向：上滑 → 绕 X 轴顶部转远；右滑 → 绕 Y 轴右缘转近
                    transform.rotateBy(-dy / viewHeight * ROT_DEG_PER_SCREEN,
                            dx / viewWidth * ROT_DEG_PER_SCREEN);
                    // 通知外部旋转角变化（参数面板实时显示）
                    if (rotationListener != null) {
                        rotationListener.onRotationChanged(transform.getRotX(), transform.getRotY());
                    }
                    lastX = x;
                    lastY = y;
                }
                return mode != Mode.NONE;

            case MotionEvent.ACTION_POINTER_UP:
                scaleDetector.onTouchEvent(event);
                // 双指抬起一指后若仍剩一指，切回单指旋转（从剩余指位置继续）
                if (event.getPointerCount() == 1) {
                    int remaining = event.getActionIndex() == 0 ? 1 : 0;
                    lastX = event.getX(remaining);
                    lastY = event.getY(remaining);
                    mode = Mode.ROTATE;
                }
                return true;

            case MotionEvent.ACTION_UP:
                scaleDetector.onTouchEvent(event);
                boolean wasGesture = mode != Mode.NONE;
                mode = Mode.NONE;
                return wasGesture;

            case MotionEvent.ACTION_CANCEL:
                scaleDetector.onTouchEvent(event);
                mode = Mode.NONE;
                return true;

            default:
                return false;
        }
    }
}
