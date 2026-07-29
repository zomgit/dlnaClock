package com.dlnaclock.media;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.dlnaclock.App;
import com.dlnaclock.R;
import com.dlnaclock.dlna.DlnaManager;
import com.dlnaclock.dlna.avt.AVTransportService;
import com.dlnaclock.dlna.avt.MediaInfo;
import com.dlnaclock.dlna.avt.TransportState;

public class VideoPlayerActivity extends AppCompatActivity
        implements DlnaManager.DlnaEventListener, SurfaceHolder.Callback {

    private SurfaceView surfaceView;
    private OsdOverlayView osdView;
    private FrameLayout container;

    private DlnaManager dlnaManager;
    private AVTransportService avtService;
    private MediaPlayerWrapper playerWrapper;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean surfaceReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupFullScreen();
        setContentView(R.layout.activity_video_player);

        initViews();
        dlnaManager = App.getInstance().getDlnaManager();
        avtService = dlnaManager.getAvtService();

        playerWrapper = new MediaPlayerWrapper();
        playerWrapper.setListener(new MediaPlayerWrapper.MediaPlayerListener() {
            @Override
            public void onPrepared() {
                playerWrapper.start();
            }

            @Override
            public void onCompletion() {
                finish();
            }

            @Override
            public void onError(int what, int extra) {
                Toast.makeText(VideoPlayerActivity.this, "播放错误: " + what, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onBufferingUpdate(int percent) {
                // Could show buffering indicator
            }

            @Override
            public void onVideoSizeChanged(int width, int height) {
                // Adjust surface view aspect ratio if needed
            }
        });
    }

    private void setupFullScreen() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void initViews() {
        surfaceView = (SurfaceView) findViewById(R.id.video_surface);
        osdView = (OsdOverlayView) findViewById(R.id.osd_overlay);
        container = (FrameLayout) findViewById(R.id.video_container);

        if (surfaceView != null) {
            surfaceView.getHolder().addCallback(this);
        }

        // Touch to toggle OSD visibility
        if (surfaceView != null) {
            surfaceView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (osdView != null) {
                        osdView.toggleVisibility();
                    }
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

        // Start playback if not already
        if (avtService.getCurrentState() == TransportState.STOPPED) {
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        dlnaManager.removeEventListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (playerWrapper != null) {
            playerWrapper.release();
        }
    }

    // DlnaManager.DlnaEventListener
    @Override
    public void onMediaUriSet(MediaInfo mediaInfo) {
        // New media URI - restart playback
        handler.post(new Runnable() {
            @Override
            public void run() {
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
                        if (playerWrapper != null && surfaceReady) {
                            playerWrapper.start();
                        }
                        break;
                    case PAUSED_PLAYBACK:
                        if (playerWrapper != null) {
                            playerWrapper.pause();
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
}
