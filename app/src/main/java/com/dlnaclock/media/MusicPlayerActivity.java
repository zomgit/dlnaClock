package com.dlnaclock.media;

import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
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
import com.dlnaclock.screensaver.ScreenSaverView;
import com.dlnaclock.util.ImageLoader;

public class MusicPlayerActivity extends AppCompatActivity
        implements DlnaManager.DlnaEventListener {

    private ScreenSaverView clockView;
    private ImageView albumArtView;
    private TextView titleView;
    private TextView artistView;
    private TextView albumView;
    private TextView currentTimeView;
    private TextView totalTimeView;
    private SeekBar seekBar;
    private ImageButton playPauseBtn;

    private DlnaManager dlnaManager;
    private AVTransportService avtService;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isTracking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupFullScreen();
        setContentView(R.layout.activity_music_player);

        initViews();
        dlnaManager = App.getInstance().getDlnaManager();
        avtService = dlnaManager.getAvtService();

        // Load current media info
        updateMediaInfo(avtService.getMediaInfoObject());
        updateTransportState(avtService.getCurrentState());

        // Start playback if not already playing
        if (avtService.getCurrentState() == TransportState.STOPPED) {
            avtService.play("1");
        }
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
        clockView = (ScreenSaverView) findViewById(R.id.clock_view);
        albumArtView = (ImageView) findViewById(R.id.album_art);
        titleView = (TextView) findViewById(R.id.song_title);
        artistView = (TextView) findViewById(R.id.song_artist);
        albumView = (TextView) findViewById(R.id.song_album);
        currentTimeView = (TextView) findViewById(R.id.current_time);
        totalTimeView = (TextView) findViewById(R.id.total_time);
        seekBar = (SeekBar) findViewById(R.id.seek_bar);
        playPauseBtn = (ImageButton) findViewById(R.id.btn_play_pause);

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
                    } else {
                        avtService.play("1");
                    }
                }
            });
        }

        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        int duration = avtService.getDurationMs();
                        if (duration > 0) {
                            int targetMs = (int) (progress / 100.0 * duration);
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
                    int duration = avtService.getDurationMs();
                    if (duration > 0) {
                        int targetMs = (int) (seekBar.getProgress() / 100.0 * duration);
                        avtService.seek("REL_TIME", formatTime(targetMs));
                    }
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        dlnaManager.addEventListener(this);
        if (clockView != null) {
            clockView.start();
        }
        startProgressUpdate();
    }

    @Override
    protected void onPause() {
        super.onPause();
        dlnaManager.removeEventListener(this);
        if (clockView != null) {
            clockView.stop();
        }
        stopProgressUpdate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (avtService.getCurrentState() == TransportState.STOPPED
                || avtService.getCurrentState() == TransportState.NO_MEDIA_PRESENT) {
            // Media stopped, could return to screensaver
        }
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
            if (!isTracking) {
                long pos = avtService.getCurrentPositionMs();
                long dur = avtService.getDurationMs();

                if (currentTimeView != null) {
                    currentTimeView.setText(formatTime(pos));
                }
                if (totalTimeView != null) {
                    totalTimeView.setText(formatTime(dur));
                }
                if (seekBar != null && dur > 0) {
                    seekBar.setProgress((int) (pos * 100.0 / dur));
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
                updateMediaInfo(mediaInfo);
            }
        });
    }

    @Override
    public void onTransportStateChanged(final TransportState state) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateTransportState(state);
                if (state == TransportState.STOPPED || state == TransportState.NO_MEDIA_PRESENT) {
                    // Return to screensaver
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
}
