package com.dlnaclock.screensaver;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.dlnaclock.App;
import com.dlnaclock.R;
import com.dlnaclock.clock.ClockConfig;
import com.dlnaclock.dlna.DlnaManager;
import com.dlnaclock.dlna.avt.MediaInfo;
import com.dlnaclock.dlna.avt.TransportState;
import com.dlnaclock.settings.SettingsActivity;
import com.dlnaclock.util.PreferenceHelper;

public class ScreenSaverActivity extends AppCompatActivity
        implements DlnaManager.DlnaEventListener {

    private ScreenSaverView screenSaverView;
    private LinearLayout controlBar;
    private Handler hideHandler = new Handler(Looper.getMainLooper());
    private boolean controlBarVisible = false;
    private DlnaManager dlnaManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full screen flags
        setupFullScreen();

        setContentView(R.layout.activity_screen_saver);

        screenSaverView = (ScreenSaverView) findViewById(R.id.screen_saver_view);
        controlBar = (LinearLayout) findViewById(R.id.control_bar);

        // Setup control bar buttons
        ImageButton btnSettings = (ImageButton) findViewById(R.id.btn_settings);
        ImageButton btnSwitchStyle = (ImageButton) findViewById(R.id.btn_switch_style);
        ImageButton btnExit = (ImageButton) findViewById(R.id.btn_exit);

        if (btnSettings != null) {
            btnSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(ScreenSaverActivity.this, SettingsActivity.class));
                }
            });
        }

        if (btnSwitchStyle != null) {
            btnSwitchStyle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchClockStyle();
                }
            });
        }

        if (btnExit != null) {
            btnExit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        // Click to toggle control bar
        screenSaverView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleControlBar();
            }
        });

        dlnaManager = App.getInstance().getDlnaManager();
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

    @Override
    protected void onResume() {
        super.onResume();
        dlnaManager.addEventListener(this);
        screenSaverView.reloadConfig();
        screenSaverView.start();
        setupFullScreen();
    }

    @Override
    protected void onPause() {
        super.onPause();
        screenSaverView.stop();
        dlnaManager.removeEventListener(this);
        hideControlBar();
    }

    private void toggleControlBar() {
        if (controlBarVisible) {
            hideControlBar();
        } else {
            showControlBar();
        }
    }

    private void showControlBar() {
        if (controlBar != null) {
            controlBar.setVisibility(View.VISIBLE);
            controlBarVisible = true;
            // Auto-hide after 5 seconds
            hideHandler.removeCallbacksAndMessages(null);
            hideHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    hideControlBar();
                }
            }, 5000);
        }
    }

    private void hideControlBar() {
        if (controlBar != null) {
            controlBar.setVisibility(View.GONE);
            controlBarVisible = false;
        }
    }

    private void switchClockStyle() {
        int currentStyle = PreferenceHelper.getClockStyle();
        int nextStyle = (currentStyle + 1) % 4;
        PreferenceHelper.setClockStyle(nextStyle);
        screenSaverView.reloadConfig();

        String[] styleNames = {"数字时钟", "模拟时钟", "霓虹灯时钟", "极简时钟"};
        Toast.makeText(this, styleNames[nextStyle], Toast.LENGTH_SHORT).show();
    }

    // DlnaManager.DlnaEventListener implementation
    @Override
    public void onMediaUriSet(final MediaInfo mediaInfo) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                dlnaManager.launchPlayerActivity(mediaInfo);
            }
        });
    }

    @Override
    public void onTransportStateChanged(TransportState state) {
        // Handle state changes if needed
    }

    @Override
    public void onPlaybackPositionChanged(long positionMs, long durationMs) {
        // Not used in screensaver
    }

    @Override
    public void onMediaCompleted() {
        // Return to screensaver - already here
    }

    @Override
    public void onDlnaError(int what, int extra) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ScreenSaverActivity.this, "投播放生错误", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
