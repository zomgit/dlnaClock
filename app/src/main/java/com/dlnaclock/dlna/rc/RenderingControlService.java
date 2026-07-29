package com.dlnaclock.dlna.rc;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * RenderingControlService - 渲染控制服务
 * 处理 DLNA 音量控制和静音命令，映射到系统 AudioManager
 */
public class RenderingControlService {

    private static final String TAG = "RenderingControl";
    private Context context;
    private AudioManager audioManager; // 系统音频管理器
    private volatile OnVolumeChangedListener volumeChangedListener; // 音量变化监听器（UI 反馈用），volatile 保证跨线程可见性
    private final Handler mainHandler = new Handler(Looper.getMainLooper()); // 主线程 Handler，回调统一切到主线程

    /** OnVolumeChangedListener - 音量/静音变化回调，供播放界面显示 OSD */
    public interface OnVolumeChangedListener {
        /** onVolumeChanged - percent 为 0-100 百分比，mute 为当前静音状态 */
        void onVolumeChanged(int percent, boolean mute);
    }

    /** setOnVolumeChangedListener - 注册音量变化监听器 */
    public void setOnVolumeChangedListener(OnVolumeChangedListener listener) {
        this.volumeChangedListener = listener;
    }

    /** removeOnVolumeChangedListener - 反注册（仅当当前监听器是自己时清除，避免误清新界面的监听器） */
    public void removeOnVolumeChangedListener(OnVolumeChangedListener listener) {
        if (this.volumeChangedListener == listener) {
            this.volumeChangedListener = null;
        }
    }

    /** notifyVolumeChanged - 通知监听器音量变化（切到主线程回调，避免阻塞/污染 HTTP 线程） */
    private void notifyVolumeChanged(final int percent, final boolean mute) {
        final OnVolumeChangedListener listener = volumeChangedListener;
        if (listener != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    // 重新校验监听器仍是同一实例，防止界面销毁反注册后仍回调
                    if (volumeChangedListener != listener) {
                        return;
                    }
                    try {
                        listener.onVolumeChanged(percent, mute);
                    } catch (Exception e) {
                        Log.w(TAG, "Volume changed listener error", e);
                    }
                }
            });
        }
    }

    /** getVolumePercent - 获取当前音量的 0-100 百分比 */
    private int getVolumePercent() {
        if (audioManager != null) {
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            if (maxVolume > 0) {
                return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / maxVolume;
            }
        }
        return 0;
    }

    /** RenderingControlService - 构造函数，获取 AudioManager 实例 */
    public RenderingControlService(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    /** getVolume - 获取当前音量（0-100 百分比，与 setVolume 保持一致） */
    public int getVolume() {
        return getVolumePercent();
    }

    /** setVolume - 设置音量（0-100 百分比，内部转换为系统音量范围） */
    public void setVolume(int volume) {
        if (audioManager != null) {
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int targetVolume = Math.max(0, Math.min(volume * maxVolume / 100, maxVolume));
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0);
            Log.d(TAG, "Volume set to " + targetVolume + " (max: " + maxVolume + ")");
            notifyVolumeChanged(Math.max(0, Math.min(volume, 100)), getMute());
        }
    }

    /** getMute - 获取静音状态（音量为0则返回true） */
    public boolean getMute() {
        if (audioManager != null) {
            return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0;
        }
        return false;
    }

    /** setMute - 设置静音/取消静音 */
    public void setMute(boolean mute) {
        if (audioManager != null) {
            if (mute) {
                audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
            } else {
                audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
            }
            Log.d(TAG, "Mute set to " + mute);
            notifyVolumeChanged(getVolumePercent(), mute);
        }
    }
}
