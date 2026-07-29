package com.dlnaclock.media;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceView;

/**
 * AspectRatioSurfaceView - 保持视频宽高比的 SurfaceView
 * 根据视频宽高比和可用空间，自动计算最佳显示尺寸（居中 + 黑边）
 * 适配屏幕宽度或高度，保证内容完整显示不拉伸
 */
public class AspectRatioSurfaceView extends SurfaceView {

    private static final String TAG = "AspectRatioSurface";

    private int videoWidth = 0;
    private int videoHeight = 0;

    public AspectRatioSurfaceView(Context context) {
        super(context);
    }

    public AspectRatioSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AspectRatioSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * 设置视频尺寸，触发布局重新计算
     * 通常在 onVideoSizeChanged 回调中调用
     */
    public void setVideoSize(int width, int height) {
        if (width > 0 && height > 0) {
            Log.i(TAG, "setVideoSize: " + width + "x" + height);
            this.videoWidth = width;
            this.videoHeight = height;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int availableWidth = MeasureSpec.getSize(widthMeasureSpec);
        int availableHeight = MeasureSpec.getSize(heightMeasureSpec);

        if (videoWidth <= 0 || videoHeight <= 0) {
            // 未知视频尺寸，填满父容器
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // 计算视频宽高比和屏幕宽高比
        float videoAspect = (float) videoWidth / videoHeight;
        float screenAspect = (float) availableWidth / availableHeight;

        int measuredWidth;
        int measuredHeight;

        if (videoAspect > screenAspect) {
            // 视频更宽 → 宽度撑满，高度按比例（上下黑边）
            measuredWidth = availableWidth;
            measuredHeight = (int) (availableWidth / videoAspect);
        } else {
            // 视频更高 → 高度撑满，宽度按比例（左右黑边）
            measuredHeight = availableHeight;
            measuredWidth = (int) (availableHeight * videoAspect);
        }

        Log.d(TAG, "onMeasure: video=" + videoWidth + "x" + videoHeight
                + ", available=" + availableWidth + "x" + availableHeight
                + ", measured=" + measuredWidth + "x" + measuredHeight);

        setMeasuredDimension(measuredWidth, measuredHeight);
    }
}
