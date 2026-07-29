package com.dlnaclock.media;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
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
import com.dlnaclock.screensaver.ScreenSaverView;
import com.dlnaclock.util.FullScreenHelper;
import com.dlnaclock.util.ImageLoader;

/**
 * MusicPlayerActivity - 音乐投屏界面
 * 横屏左右分屏（时钟 + 音乐信息），竖屏上下布局（时钟 + 封面/标题/歌手/专辑/进度条）
 * 使用 MediaPlayerWrapper 播放音频，向 AVTransportService 报告进度
 * 实现 DlnaEventListener 响应 DLNA 控制命令
 */
public class MusicPlayerActivity extends AppCompatActivity
        implements DlnaManager.DlnaEventListener, RenderControl {

    private static final String TAG = "MusicPlayer";

    private ScreenSaverView clockView;
    private ImageView albumArtView;
    private TextView titleView;
    private TextView artistView;
    private TextView albumView;
    private TextView currentTimeView;
    private TextView totalTimeView;
    private SeekBar seekBar;
    private ImageButton playPauseBtn;
    private ImageButton closeBtn;
    private OsdOverlayView osdView;

    private DlnaManager dlnaManager;
    private AVTransportService avtService;
    private RenderingControlService rcService;
    private MediaPlayerWrapper playerWrapper;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isTracking = false;
    private boolean isForeground = false;

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

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.i(TAG, "onNewIntent: switching to new media");
        setIntent(intent); // 保存新 Intent

        // 先暂停旧播放器，避免在加载新媒体前短暂播放旧音频
        if (playerWrapper != null) {
            playerWrapper.pause();
        }

        // 从新 Intent 中提取 CastAction 更新 UI 和媒体源
        CastAction newAction = CastAction.fromIntent(intent);
        if (newAction != null) {
            // 更新媒体信息显示
            MediaInfo info = avtService.getMediaInfoObject();
            updateMediaInfo(info);

            // 切换媒体源：setDataSource 内部会 release 旧播放器并 prepareAsync 新媒体
            // onPrepared 回调中会自动检查 AVT 状态并开始播放
            if (newAction.getUri() != null && playerWrapper != null) {
                playerWrapper.setDataSource(newAction.getUri());
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FullScreenHelper.setupFullScreen(this);
        setContentView(R.layout.activity_music_player);

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

        // Load current media info from CastAction or AVT
        updateMediaInfo(avtService.getMediaInfoObject());
        updateTransportState(avtService.getCurrentState());

        // Initialize MediaPlayer
        playerWrapper = new MediaPlayerWrapper(this);
        playerWrapper.setListener(new MediaPlayerWrapper.MediaPlayerListener() {
            @Override
            public void onPrepared() {
                Log.i(TAG, "onPrepared fired, AVT state=" + avtService.getCurrentState());
                // 不再自动 start — 由 AVTransport 状态机控制播放时机
                avtService.updatePlaybackPosition(0, playerWrapper.getDuration());
                // 如果 AVTransport 已经是 PLAYING 状态，开始播放
                if (avtService.getCurrentState() == TransportState.PLAYING) {
                    Log.i(TAG, "AVT is PLAYING, starting MediaPlayer");
                    playerWrapper.start();
                } else {
                    Log.w(TAG, "AVT not PLAYING (state=" + avtService.getCurrentState() + "), waiting...");
                }
            }

            @Override
            public void onCompletion() {
                avtService.notifyCompleted();
            }

            @Override
            public void onError(int what, int extra) {
                Log.e(TAG, "MediaPlayer error: what=" + what + ", extra=" + extra);
                avtService.notifyPlayerError(what, extra);
            }

            @Override
            public void onBufferingUpdate(int percent) {
            }

            @Override
            public void onVideoSizeChanged(int width, int height) {
            }
        });

        // Start playback
        MediaInfo info = avtService.getMediaInfoObject();
        Log.i(TAG, "onCreate: AVT state=" + avtService.getCurrentState()
                + ", uri=" + (info != null ? info.getCurrentUri() : "null")
                + ", mimeType=" + (info != null ? info.getMimeType() : "null"));
        if (info != null && info.getCurrentUri() != null && !info.getCurrentUri().isEmpty()) {
            playerWrapper.setDataSource(info.getCurrentUri());
        } else {
            Log.w(TAG, "No media URI available from AVTransportService");
        }
        if (avtService.getCurrentState() == TransportState.STOPPED) {
            Log.i(TAG, "AVT is STOPPED, calling play()");
            avtService.play("1");
        }
    }

    private void initViews() {
        clockView = (ScreenSaverView) findViewById(R.id.clock_view);
        albumArtView = (ImageView) findViewById(R.id.album_art);
        titleView = (TextView) findViewById(R.id.song_title);
        artistView = (TextView) findViewById(R.id.song_artist);
        albumView = (TextView) findViewById(R.id.song_album);
        currentTimeView = (TextView) findViewById(R.id.current_time);
        totalTimeView = (TextView) findViewById(R.id.total_time);
        seekBar = (SeekBar) findViewById(R.id.seek_bar);
        playPauseBtn = (ImageButton) findViewById(R.id.btn_play_pause);
        closeBtn = (ImageButton) findViewById(R.id.btn_close);
        osdView = (OsdOverlayView) findViewById(R.id.volume_osd);

        // 音乐界面已有时钟，OSD 仅用于音量显示，且不拦截触摸（避免遮挡按钮/进度条）
        if (osdView != null) {
            osdView.setTimeOsdEnabled(false);
            osdView.setTouchable(false);
        }

        // 封面尺寸按屏幕短边比例计算，避免固定大尺寸在受限容器中溢出
        applyAlbumArtSize();

        // Round corners for album art
        GradientDrawable roundBg = new GradientDrawable();
        roundBg.setCornerRadius(16);
        roundBg.setColor(0xFF333333);
        albumArtView.setBackground(roundBg);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            albumArtView.setClipToOutline(true);
        }

        if (playPauseBtn != null) {
            playPauseBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (avtService.getCurrentState() == TransportState.PLAYING) {
                        avtService.pause();
                        if (playerWrapper != null) playerWrapper.pause();
                    } else {
                        avtService.play("1");
                        if (playerWrapper != null) playerWrapper.start();
                    }
                }
            });
        }

        if (closeBtn != null) {
            closeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    avtService.stop();
                    finish();
                }
            });
        }

        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        long duration = avtService.getDurationMs();
                        if (duration > 0) {
                            long targetMs = (int) (progress / 100.0 * duration);
                            String time = formatTime(targetMs);
                            currentTimeView.setText(time);
                        }
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    isTracking = true;
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    isTracking = false;
                    long duration = avtService.getDurationMs();
                    if (duration > 0) {
                        long targetMs = (int) (seekBar.getProgress() / 100.0 * duration);
                        avtService.seek("REL_TIME", formatTime(targetMs));
                        if (playerWrapper != null) {
                            playerWrapper.seekTo((int) targetMs);
                        }
                    }
                }
            });
        }
    }

    /** applyAlbumArtSize - 按屏幕短边比例计算封面尺寸（竖屏 50%，横屏 32%） */
    private void applyAlbumArtSize() {
        if (albumArtView == null) return;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int shortEdge = Math.min(dm.widthPixels, dm.heightPixels);
        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        int size = (int) (shortEdge * (landscape ? 0.38f : 0.55f));
        ViewGroup.LayoutParams lp = albumArtView.getLayoutParams();
        if (lp != null) {
            lp.width = size;
            lp.height = size;
            albumArtView.setLayoutParams(lp);
        }
    }

    /**
     * onConfigurationChanged - Manifest 配置了 configChanges，旋转不重建 Activity，
     * 这里手动重新加载对应方向的布局并恢复播放状态/元数据/进度到新视图。
     * 播放器（MediaPlayerWrapper）不依赖视图，播放不中断。
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 停止旧时钟与进度刷新，避免旧视图被 Handler 持有导致泄漏
        if (clockView != null) {
            clockView.stop();
        }
        stopProgressUpdate();

        // 重新加载布局（系统按新方向选择 layout / layout-land）并重新绑定视图
        setContentView(R.layout.activity_music_player);
        initViews();

        // 恢复元数据/封面/播放状态到新视图
        updateMediaInfo(avtService.getMediaInfoObject());
        updateTransportState(avtService.getCurrentState());

        if (isForeground) {
            if (clockView != null) {
                clockView.start();
            }
            startProgressUpdate();
        }
        FullScreenHelper.onResumeFullScreen(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isForeground = true;
        dlnaManager.addEventListener(this);
        if (clockView != null) {
            clockView.start();
        }
        startProgressUpdate();
        FullScreenHelper.onResumeFullScreen(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isForeground = false;
        dlnaManager.removeEventListener(this);
        if (clockView != null) {
            clockView.stop();
        }
        stopProgressUpdate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (playerWrapper != null) {
            playerWrapper.release();
        }
        // 反注册音量监听，避免泄漏
        if (rcService != null) {
            rcService.removeOnVolumeChangedListener(volumeListener);
        }
        // Unregister RenderControl（条件清除，避免误清新 Activity 设置的 renderControl）
        dlnaManager.clearRenderControlIfOwner(this);
    }

    private void updateMediaInfo(MediaInfo info) {
        if (info == null) return;

        if (titleView != null) {
            titleView.setText(info.getTitle() != null ? info.getTitle() : "未知歌曲");
        }
        if (artistView != null) {
            artistView.setText(info.getArtist() != null ? info.getArtist() : "未知歌手");
        }
        if (albumView != null) {
            albumView.setText(info.getAlbum() != null ? info.getAlbum() : "");
        }

        // Load album art
        String artUri = info.getAlbumArtUri();
        if (artUri != null && !artUri.isEmpty() && albumArtView != null) {
            ImageLoader.getInstance().load(artUri, albumArtView);
        }
    }

    private void updateTransportState(TransportState state) {
        if (playPauseBtn != null) {
            if (state == TransportState.PLAYING) {
                playPauseBtn.setImageResource(android.R.drawable.ic_media_pause);
            } else {
                playPauseBtn.setImageResource(android.R.drawable.ic_media_play);
            }
        }
    }

    private Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            if (!isTracking && playerWrapper != null) {
                long pos = playerWrapper.getCurrentPosition();
                long dur = playerWrapper.getDuration();

                // Report position back to AVT for control point queries
                avtService.updatePlaybackPosition(pos, dur);

                if (currentTimeView != null) {
                    currentTimeView.setText(formatTime(pos));
                }
                if (totalTimeView != null) {
                    totalTimeView.setText(formatTime(dur));
                }
                if (seekBar != null && dur > 0) {
                    seekBar.setProgress((int) (pos * 100.0 / dur));
                }

                // 检测播放完成（兑底机制）：某些设备 onCompletion 不触发，
                // 当播放器已准备、有有效时长、未在播放且位置在末尾 2 秒内时，视为播放完成
                if (playerWrapper.isPrepared() && dur > 0
                        && !playerWrapper.isPlaying()
                        && avtService.getCurrentState() == TransportState.PLAYING
                        && pos >= dur - 2000) {
                    Log.i(TAG, "Playback completed detected by progress updater, pos=" + pos + " dur=" + dur);
                    avtService.notifyCompleted();
                }
            }
            handler.postDelayed(this, 1000);
        }
    };

    private void startProgressUpdate() {
        handler.removeCallbacks(progressUpdater);
        handler.post(progressUpdater);
    }

    private void stopProgressUpdate() {
        handler.removeCallbacks(progressUpdater);
    }

    private String formatTime(long ms) {
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    // DlnaManager.DlnaEventListener
    @Override
    public void onMediaUriSet(MediaInfo mediaInfo) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // 类型不匹配时（如视频推送到音乐播放器），退出让新 Activity 接管
                if (mediaInfo != null && mediaInfo.isVideo()) {
                    Log.i(TAG, "onMediaUriSet: video detected, finishing MusicPlayerActivity");
                    finish();
                    return;
                }
                updateMediaInfo(mediaInfo);
                if (playerWrapper != null && mediaInfo != null && mediaInfo.getCurrentUri() != null) {
                    // 重置播放器，加载新媒体（prepareAsync 完成后 onPrepared 会自动 start）
                    playerWrapper.setDataSource(mediaInfo.getCurrentUri());
                    // 确保 AVT 状态转为 PLAYING（setAVTransportURI 不再通知 STOPPED）
                    if (avtService.getCurrentState() == TransportState.STOPPED) {
                        avtService.play("1");
                    }
                }
            }
        });
    }

    @Override
    public void onTransportStateChanged(final TransportState state) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "onTransportStateChanged: " + state
                        + ", isPrepared=" + (playerWrapper != null ? playerWrapper.isPrepared() : "null"));
                updateTransportState(state);
                if (state == TransportState.PLAYING) {
                    if (playerWrapper != null && playerWrapper.isPrepared()) {
                        Log.i(TAG, "State=PLAYING + prepared, starting MediaPlayer");
                        playerWrapper.start();
                    }
                } else if (state == TransportState.PAUSED_PLAYBACK) {
                    if (playerWrapper != null) playerWrapper.pause();
                } else if (state == TransportState.STOPPED || state == TransportState.NO_MEDIA_PRESENT) {
                    finish();
                }
            }
        });
    }

    @Override
    public void onPlaybackPositionChanged(final long positionMs, final long durationMs) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isTracking) {
                    if (currentTimeView != null) currentTimeView.setText(formatTime(positionMs));
                    if (totalTimeView != null) totalTimeView.setText(formatTime(durationMs));
                    if (seekBar != null && durationMs > 0) {
                        seekBar.setProgress((int) (positionMs * 100.0 / durationMs));
                    }
                }
            }
        });
    }

    @Override
    public void onMediaCompleted() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        });
    }

    @Override
    public void onDlnaError(int what, int extra) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MusicPlayerActivity.this, "播放错误", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // RenderControl implementation
    @Override
    public boolean play() {
        if (playerWrapper != null) {
            playerWrapper.start();
            return true;
        }
        return false;
    }

    @Override
    public boolean pause() {
        if (playerWrapper != null) {
            playerWrapper.pause();
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
