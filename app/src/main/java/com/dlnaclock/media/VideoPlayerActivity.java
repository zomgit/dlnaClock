package com.dlnaclock.media;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.dlnaclock.App;
import com.dlnaclock.R;
import com.dlnaclock.dlna.DlnaManager;
import com.dlnaclock.dlna.avt.AVTransportService;
import com.dlnaclock.dlna.avt.MediaInfo;
import com.dlnaclock.dlna.avt.TransportState;
import com.dlnaclock.dlna.rc.CastAction;
import com.dlnaclock.dlna.rc.RenderControl;
import com.dlnaclock.dlna.rc.RenderingControlService;
import com.dlnaclock.util.FullScreenHelper;

import java.util.Locale;

/**
 * VideoPlayerActivity - 视频投屏界面
 * 全屏 SurfaceView + OSD 叠加层，使用 IjkPlayerWrapper (FFmpeg) 播放视频
 * 实现 DlnaEventListener 和 SurfaceHolder.Callback
 * 向 AVTransportService 报告播放进度
 */
public class VideoPlayerActivity extends AppCompatActivity
        implements DlnaManager.DlnaEventListener, SurfaceHolder.Callback, RenderControl {

    private static final String TAG = "VideoPlayer";
    private static final long CONTROL_BAR_TIMEOUT = 3000; // 3秒后自动隐藏控制栏

    private AspectRatioSurfaceView surfaceView;
    private OsdOverlayView osdView;
    private FrameLayout container;
    private View controlBar;
    private ImageButton btnPlayPause;
    private ImageButton btnStop;
    private SeekBar seekBar;
    private TextView textCurrentTime;
    private TextView textTotalTime;
    private View topRightButtons;
    private Button btnDecoderToggle;
    private Button btnMediaInfo;

    private DlnaManager dlnaManager;
    private AVTransportService avtService;
    private RenderingControlService rcService;
    private IjkPlayerWrapper playerWrapper;

    /** 音量变化监听：控制端 SetVolume/SetMute 后在主线程显示音量 OSD */
    private final RenderingControlService.OnVolumeChangedListener volumeListener =
            new RenderingControlService.OnVolumeChangedListener() {
                @Override
                public void onVolumeChanged(final int percent, final boolean mute) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (osdView != null) {
                                osdView.showVolume(percent, mute);
                            }
                        }
                    });
                }
            };
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean surfaceReady = false;
    private Runnable progressUpdater;
    private Runnable controlBarHider;
    private boolean isControlBarVisible = false;
    private boolean isSeekBarTracking = false;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.i(TAG, "onNewIntent: switching to new media");
        setIntent(intent);

        // 先暂停旧播放器，避免竞态条件下短暂播放旧媒体
        if (playerWrapper != null) {
            playerWrapper.pause();
        }

        CastAction newAction = CastAction.fromIntent(intent);
        if (newAction != null && playerWrapper != null) {
            // 切换媒体源：release 旧播放器并 prepareAsync 新媒体
            if (newAction.getUri() != null) {
                playerWrapper.setDataSource(newAction.getUri());
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FullScreenHelper.setupFullScreen(this);
        setContentView(R.layout.activity_video_player);

        initViews();
        dlnaManager = App.getInstance().getDlnaManager();
        avtService = dlnaManager.getAvtService();
        rcService = dlnaManager.getRcService();

        // Register as RenderControl
        dlnaManager.setRenderControl(this);

        // 注册音量变化监听，控制端调音量时显示 OSD
        if (rcService != null) {
            rcService.setOnVolumeChangedListener(volumeListener);
        }

        playerWrapper = new IjkPlayerWrapper(this);
        playerWrapper.setListener(new IjkPlayerWrapper.MediaPlayerListener() {
            @Override
            public void onPrepared() {
                // 更新总时长显示
                long dur = playerWrapper.getDuration();
                avtService.updatePlaybackPosition(0, dur);
                if (textTotalTime != null) {
                    textTotalTime.setText(formatTime(dur));
                }
                // 如果 AVTransport 已经是 PLAYING 状态（Play 命令先到），且 surface 就绪，开始播放
                if (avtService.getCurrentState() == TransportState.PLAYING && surfaceReady) {
                    playerWrapper.start();
                    updatePlayPauseButton(true);
                    startProgressUpdate();
                } else {
                    updatePlayPauseButton(false);
                }
            }

            @Override
            public void onCompletion() {
                avtService.notifyCompleted();
                finish();
            }

            @Override
            public void onError(int what, int extra) {
                avtService.notifyPlayerError(what, extra);
                Toast.makeText(VideoPlayerActivity.this, "播放错误: " + what, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onBufferingUpdate(int percent) {
                // Could show buffering indicator
            }

            @Override
            public void onVideoSizeChanged(int width, int height) {
                // 调整 SurfaceView 尺寸以保持视频宽高比
                if (surfaceView != null) {
                    surfaceView.setVideoSize(width, height);
                }
            }
        });
    }

    private void initViews() {
        surfaceView = (AspectRatioSurfaceView) findViewById(R.id.video_surface);
        osdView = (OsdOverlayView) findViewById(R.id.osd_overlay);
        container = (FrameLayout) findViewById(R.id.video_container);
        controlBar = findViewById(R.id.control_bar);
        btnPlayPause = (ImageButton) findViewById(R.id.btn_play_pause);
        btnStop = (ImageButton) findViewById(R.id.btn_stop);
        seekBar = (SeekBar) findViewById(R.id.seek_bar);
        textCurrentTime = (TextView) findViewById(R.id.text_current_time);
        textTotalTime = (TextView) findViewById(R.id.text_total_time);
        topRightButtons = findViewById(R.id.top_right_buttons);
        btnDecoderToggle = (Button) findViewById(R.id.btn_decoder_toggle);
        btnMediaInfo = (Button) findViewById(R.id.btn_media_info);

        if (surfaceView != null) {
            surfaceView.getHolder().addCallback(this);
        }

        // 控制按钮事件
        if (btnPlayPause != null) {
            btnPlayPause.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    togglePlayPause();
                }
            });
        }

        if (btnStop != null) {
            btnStop.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    stopPlayback();
                }
            });
        }

        // 进度条拖动事件
        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && playerWrapper != null && playerWrapper.isPrepared()) {
                        long duration = playerWrapper.getDuration();
                        long position = (long) (progress / 1000.0 * duration);
                        textCurrentTime.setText(formatTime(position));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    isSeekBarTracking = true;
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    if (playerWrapper != null && playerWrapper.isPrepared()) {
                        long duration = playerWrapper.getDuration();
                        long position = (long) (seekBar.getProgress() / 1000.0 * duration);
                        playerWrapper.seekTo((int) position);
                    }
                    isSeekBarTracking = false;
                }
            });
        }

        // 解码器切换按钮
        if (btnDecoderToggle != null) {
            updateDecoderButton();
            btnDecoderToggle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleDecoder();
                }
            });
        }

        // 媒体信息按钮
        if (btnMediaInfo != null) {
            btnMediaInfo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showMediaInfo();
                }
            });
        }

        // 点击视频区域切换控制栏显示/隐藏
        if (surfaceView != null) {
            surfaceView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleControlBar();
                }
            });
        }

        // OSD 点击回调：OsdOverlayView 拦截了触摸事件，通过此回调触发控制栏切换
        if (osdView != null) {
            osdView.setOnTapListener(new OsdOverlayView.OnTapListener() {
                @Override
                public void onTap() {
                    toggleControlBar();
                }
            });
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        playerWrapper.setSurface(holder.getSurface());

        // Start playback
        MediaInfo info = avtService.getMediaInfoObject();
        if (info != null && info.getCurrentUri() != null && !info.getCurrentUri().isEmpty()) {
            playerWrapper.setDataSource(info.getCurrentUri());
        }

        // 如果 AVT 已经是 PLAYING 状态（Play 命令在播放器就绪前到达），
        // 或者状态是 STOPPED（等待自动播放），都尝试启动播放
        TransportState state = avtService.getCurrentState();
        if (state == TransportState.STOPPED || state == TransportState.PLAYING) {
            avtService.play("1");
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // No-op
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        dlnaManager.addEventListener(this);
        FullScreenHelper.onResumeFullScreen(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        dlnaManager.removeEventListener(this);
        stopProgressUpdate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopProgressUpdate();
        if (playerWrapper != null) {
            playerWrapper.release();
        }
        // Unregister RenderControl（条件清除，避免误清新 Activity 设置的 renderControl）
        dlnaManager.clearRenderControlIfOwner(this);
        // 反注册音量监听，避免泄漏
        if (rcService != null) {
            rcService.removeOnVolumeChangedListener(volumeListener);
        }
    }

    private void startProgressUpdate() {
        if (progressUpdater == null) {
            progressUpdater = new Runnable() {
                @Override
                public void run() {
                    if (playerWrapper != null) {
                        long pos = playerWrapper.getCurrentPosition();
                        long dur = playerWrapper.getDuration();
                        avtService.updatePlaybackPosition(pos, dur);

                        // 更新控制栏进度条和时间（仅在非拖动状态）
                        if (!isSeekBarTracking) {
                            updateProgressBar(pos, dur);
                        }

                        // 检测播放完成（兜底机制）：某些设备 onCompletion 不触发
                        if (playerWrapper.isPrepared() && dur > 0
                                && !playerWrapper.isPlaying()
                                && avtService.getCurrentState() == TransportState.PLAYING
                                && pos >= dur - 2000) {
                            Log.i(TAG, "Playback completed detected by progress updater");
                            avtService.notifyCompleted();
                        }
                    }
                    handler.postDelayed(this, 500); // 每 500ms 更新一次
                }
            };
        }
        handler.removeCallbacks(progressUpdater);
        handler.post(progressUpdater);
    }

    private void stopProgressUpdate() {
        if (progressUpdater != null) {
            handler.removeCallbacks(progressUpdater);
        }
    }

    /**
     * togglePlayPause - 切换播放/暂停状态
     */
    private void togglePlayPause() {
        if (playerWrapper == null || !playerWrapper.isPrepared()) return;

        if (playerWrapper.isPlaying()) {
            playerWrapper.pause();
            updatePlayPauseButton(false);
        } else {
            playerWrapper.start();
            updatePlayPauseButton(true);
            startProgressUpdate();
        }
    }

    /**
     * stopPlayback - 停止播放并关闭 Activity
     */
    private void stopPlayback() {
        if (playerWrapper != null) {
            playerWrapper.stop();
        }
        finish();
    }

    /**
     * toggleControlBar - 切换控制栏显示/隐藏
     */
    private void toggleControlBar() {
        if (isControlBarVisible) {
            hideControlBar();
        } else {
            showControlBar();
        }
    }

    /**
     * showControlBar - 显示控制栏，3秒后自动隐藏
     */
    private void showControlBar() {
        if (controlBar != null) {
            controlBar.setVisibility(View.VISIBLE);
            if (topRightButtons != null) topRightButtons.setVisibility(View.VISIBLE);
            isControlBarVisible = true;

            // 更新播放/暂停按钮状态
            if (playerWrapper != null) {
                updatePlayPauseButton(playerWrapper.isPlaying());
            }

            // 3秒后自动隐藏
            if (controlBarHider == null) {
                controlBarHider = new Runnable() {
                    @Override
                    public void run() {
                        hideControlBar();
                    }
                };
            }
            handler.removeCallbacks(controlBarHider);
            handler.postDelayed(controlBarHider, CONTROL_BAR_TIMEOUT);
        }
    }

    /**
     * hideControlBar - 隐藏控制栏
     */
    private void hideControlBar() {
        if (controlBar != null) {
            controlBar.setVisibility(View.GONE);
            if (topRightButtons != null) topRightButtons.setVisibility(View.GONE);
            isControlBarVisible = false;
            if (controlBarHider != null) {
                handler.removeCallbacks(controlBarHider);
            }
        }
    }

    /**
     * updatePlayPauseButton - 更新播放/暂停按钮图标
     */
    private void updatePlayPauseButton(boolean isPlaying) {
        if (btnPlayPause != null) {
            btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        }
    }

    /**
     * toggleDecoder - 切换硬件/软件解码器
     */
    private void toggleDecoder() {
        if (playerWrapper == null) return;
        boolean newHardware = !playerWrapper.isHardwareDecoder();
        playerWrapper.switchDecoder(newHardware);
        updateDecoderButton();
        Toast.makeText(this, newHardware ? "已切换为硬解 (MediaCodec)" : "已切换为软解 (AVCodec)",
                Toast.LENGTH_SHORT).show();
    }

    /**
     * updateDecoderButton - 更新解码器切换按钮文字
     */
    private void updateDecoderButton() {
        if (btnDecoderToggle != null && playerWrapper != null) {
            btnDecoderToggle.setText(playerWrapper.isHardwareDecoder() ? "硬解" : "软解");
        }
    }

    /**
     * showMediaInfo - 显示视频/解码详细信息
     */
    private void showMediaInfo() {
        if (playerWrapper == null) return;
        String info = playerWrapper.getMediaInfoText();
        // 使用 AlertDialog 展示详细信息
        new android.app.AlertDialog.Builder(this)
                .setTitle("视频信息")
                .setMessage(info)
                .setPositiveButton("确定", null)
                .show();
    }

    /**
     * updateProgressBar - 更新进度条和时间标签
     */
    private void updateProgressBar(long position, long duration) {
        if (seekBar != null && duration > 0) {
            int progress = (int) (position / (double) duration * 1000);
            seekBar.setProgress(progress);
        }

        if (textCurrentTime != null) {
            textCurrentTime.setText(formatTime(position));
        }

        if (textTotalTime != null) {
            textTotalTime.setText(formatTime(duration));
        }
    }

    /**
     * formatTime - 格式化时间为 mm:ss 或 hh:mm:ss
     */
    private String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }

    // DlnaManager.DlnaEventListener
    @Override
    public void onMediaUriSet(MediaInfo mediaInfo) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                // 类型不匹配时（如音频推送到视频播放器），退出让新 Activity 接管
                if (mediaInfo != null && !mediaInfo.isVideo()) {
                    Log.i(TAG, "onMediaUriSet: audio detected, finishing VideoPlayerActivity");
                    finish();
                    return;
                }
                if (mediaInfo != null && surfaceReady) {
                    playerWrapper.setDataSource(mediaInfo.getCurrentUri());
                }
            }
        });
    }

    @Override
    public void onTransportStateChanged(final TransportState state) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                switch (state) {
                    case PLAYING:
                        if (playerWrapper != null && surfaceReady && playerWrapper.isPrepared()) {
                            playerWrapper.start();
                            updatePlayPauseButton(true);
                            startProgressUpdate();
                        }
                        break;
                    case PAUSED_PLAYBACK:
                        if (playerWrapper != null) {
                            playerWrapper.pause();
                            updatePlayPauseButton(false);
                        }
                        break;
                    case STOPPED:
                    case NO_MEDIA_PRESENT:
                        finish();
                        break;
                }
            }
        });
    }

    @Override
    public void onPlaybackPositionChanged(long positionMs, long durationMs) {
        // Update OSD if needed
    }

    @Override
    public void onMediaCompleted() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        });
    }

    @Override
    public void onDlnaError(int what, int extra) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(VideoPlayerActivity.this, "播放错误", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // RenderControl implementation
    @Override
    public boolean play() {
        if (playerWrapper != null && surfaceReady && playerWrapper.isPrepared()) {
            playerWrapper.start();
            updatePlayPauseButton(true);
            startProgressUpdate();
            return true;
        }
        return false;
    }

    @Override
    public boolean pause() {
        if (playerWrapper != null) {
            playerWrapper.pause();
            updatePlayPauseButton(false);
            return true;
        }
        return false;
    }

    @Override
    public boolean stop() {
        if (playerWrapper != null) {
            playerWrapper.stop();
        }
        return true;
    }

    @Override
    public boolean seekTo(long positionMs) {
        if (playerWrapper != null) {
            playerWrapper.seekTo((int) positionMs);
            return true;
        }
        return false;
    }

    @Override
    public String getState() {
        if (playerWrapper != null && playerWrapper.isPlaying()) {
            return "PLAYING";
        }
        return "STOPPED";
    }

    @Override
    public long getPosition() {
        if (playerWrapper != null) {
            return playerWrapper.getCurrentPosition();
        }
        return 0;
    }

    @Override
    public long getDuration() {
        if (playerWrapper != null) {
            return playerWrapper.getDuration();
        }
        return 0;
    }

    @Override
    public void setMediaUri(String uri, String metadata) {
        if (playerWrapper != null && uri != null) {
            playerWrapper.setDataSource(uri);
        }
    }

    @Override
    public boolean isPlaying() {
        return playerWrapper != null && playerWrapper.isPlaying();
    }
}
