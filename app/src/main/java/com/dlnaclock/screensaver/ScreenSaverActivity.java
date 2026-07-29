package com.dlnaclock.screensaver;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
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
import com.dlnaclock.screensaver.wallpaper.ParamDef;
import com.dlnaclock.screensaver.wallpaper.ParamStore;
import com.dlnaclock.screensaver.wallpaper.WallpaperRenderer;
import com.dlnaclock.screensaver.wallpaper.WallpaperFactory;
import com.dlnaclock.util.PreferenceHelper;
import com.dlnaclock.util.FullScreenHelper;

import android.widget.FrameLayout;

/**
 * ScreenSaverActivity - 屏保主界面（Launcher Activity）
 * 全屏沉浸式显示时钟屏保，监听 DLNA 投屏事件，收到投屏时启动播放器 Activity
 * 实现 DlnaEventListener 接口接收媒体设置/状态变化/完成/错误等事件
 */
public class ScreenSaverActivity extends AppCompatActivity
        implements DlnaManager.DlnaEventListener {

    private static final int REQUEST_PERMISSIONS = 100;
    public static final String EXTRA_SCROLL_TO = "scroll_to";

    private ScreenSaverView screenSaverView;
    private LinearLayout menuBar;
    private ImageButton btnSwitchStyle;
    private ImageButton btnSwitchBg;
    private ImageButton btnSwitchWallpaper;
    private LinearLayout bottomRightGroup;
    private FrameLayout controlPanelContainer;
    private WallpaperControlPanel wallpaperControlPanel;
    private Handler hideHandler = new Handler(Looper.getMainLooper());
    private boolean controlsVisible = false;
    private DlnaManager dlnaManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Request runtime permissions for Android 6.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSIONS);
            }
        }

        // Full screen flags
        FullScreenHelper.setupFullScreen(this);

        setContentView(R.layout.activity_screen_saver);

        screenSaverView = (ScreenSaverView) findViewById(R.id.screen_saver_view);
        menuBar = (LinearLayout) findViewById(R.id.menu_bar);
        btnSwitchStyle = (ImageButton) findViewById(R.id.btn_switch_style);
        btnSwitchBg = (ImageButton) findViewById(R.id.btn_switch_bg);
        btnSwitchWallpaper = (ImageButton) findViewById(R.id.btn_switch_wallpaper);
        bottomRightGroup = (LinearLayout) findViewById(R.id.bottom_right_group);
        controlPanelContainer = (FrameLayout) findViewById(R.id.wallpaper_control_panel);

        // 初始化壁纸参数控制面板
        if (controlPanelContainer != null) {
            wallpaperControlPanel = new WallpaperControlPanel(this, controlPanelContainer);
        }

        // Setup menu bar buttons
        setupMenuButtons();

        // Setup bottom-left: switch clock style
        if (btnSwitchStyle != null) {
            btnSwitchStyle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchClockStyle();
                }
            });
        }

        // Setup bottom-right: switch wallpaper style
        if (btnSwitchWallpaper != null) {
            btnSwitchWallpaper.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchWallpaperStyle();
                }
            });
        }

        // Setup bottom-right: switch background mode
        if (btnSwitchBg != null) {
            btnSwitchBg.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchBackground();
                }
            });
        }

        // Click to toggle controls
        screenSaverView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleControls();
            }
        });

        dlnaManager = App.getInstance().getDlnaManager();
    }

    private void setupMenuButtons() {
        // 设置 - 打开完整设置
        ImageButton btnMenuSettings = (ImageButton) findViewById(R.id.btn_menu_settings);
        if (btnMenuSettings != null) {
            btnMenuSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(ScreenSaverActivity.this, SettingsActivity.class));
                }
            });
        }

        // 表盘设置 - 跳转到时钟设置卡片
        ImageButton btnMenuClock = (ImageButton) findViewById(R.id.btn_menu_clock);
        if (btnMenuClock != null) {
            btnMenuClock.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(ScreenSaverActivity.this, SettingsActivity.class);
                    intent.putExtra(EXTRA_SCROLL_TO, "clock");
                    startActivity(intent);
                }
            });
        }

        // 背景设置 - 跳转到背景卡片
        ImageButton btnMenuBackground = (ImageButton) findViewById(R.id.btn_menu_background);
        if (btnMenuBackground != null) {
            btnMenuBackground.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(ScreenSaverActivity.this, SettingsActivity.class);
                    intent.putExtra(EXTRA_SCROLL_TO, "background");
                    startActivity(intent);
                }
            });
        }

        // DLNA/AirPlay设置 - 跳转到DLNA卡片
        ImageButton btnMenuDlnaAirplay = (ImageButton) findViewById(R.id.btn_menu_dlna_airplay);
        if (btnMenuDlnaAirplay != null) {
            btnMenuDlnaAirplay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(ScreenSaverActivity.this, SettingsActivity.class);
                    intent.putExtra(EXTRA_SCROLL_TO, "dlna");
                    startActivity(intent);
                }
            });
        }

        // 退出
        ImageButton btnExit = (ImageButton) findViewById(R.id.btn_exit);
        if (btnExit != null) {
            btnExit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        dlnaManager.addEventListener(this);
        screenSaverView.reloadConfig();
        screenSaverView.start();
        FullScreenHelper.onResumeFullScreen(this);
        // 刷新壁纸参数控制面板
        refreshWallpaperControlPanel();
    }

    @Override
    protected void onPause() {
        super.onPause();
        screenSaverView.stop();
        dlnaManager.removeEventListener(this);
        hideControls();
    }

    private void toggleControls() {
        if (controlsVisible) {
            hideControls();
        } else {
            showControls();
        }
    }

    private void showControls() {
        controlsVisible = true;
        if (menuBar != null) menuBar.setVisibility(View.VISIBLE);
        if (btnSwitchStyle != null) btnSwitchStyle.setVisibility(View.VISIBLE);
        if (bottomRightGroup != null) bottomRightGroup.setVisibility(View.VISIBLE);
        // 仅在动态壁纸模式下显示切换壁纸按钮
        updateWallpaperSwitchVisibility();
        // 仅在动态壁纸模式下显示参数控制面板
        if (controlPanelContainer != null && screenSaverView != null) {
            BackgroundManager bgMgr = screenSaverView.getBackgroundManager();
            if (bgMgr != null && bgMgr.isWallpaperMode()) {
                controlPanelContainer.setVisibility(View.VISIBLE);
                refreshWallpaperControlPanel();
            }
        }
        // Auto-hide after 5 seconds
        hideHandler.removeCallbacksAndMessages(null);
        hideHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                hideControls();
            }
        }, 5000);
    }

    private void hideControls() {
        controlsVisible = false;
        if (menuBar != null) menuBar.setVisibility(View.GONE);
        if (btnSwitchStyle != null) btnSwitchStyle.setVisibility(View.GONE);
        if (bottomRightGroup != null) bottomRightGroup.setVisibility(View.GONE);
        if (controlPanelContainer != null) controlPanelContainer.setVisibility(View.GONE);
    }

    private void switchClockStyle() {
        int currentStyle = PreferenceHelper.getClockStyle();
        int nextStyle = (currentStyle + 1) % 3;
        PreferenceHelper.setClockStyle(nextStyle);
        screenSaverView.reloadConfig();

        String[] styleNames = {"数字时钟", "模拟时钟", "自定义时钟"};
        Toast.makeText(this, styleNames[nextStyle], Toast.LENGTH_SHORT).show();
    }

    private void switchBackground() {
        int currentMode = PreferenceHelper.getBackgroundMode();
        int nextMode = (currentMode + 1) % 4;
        PreferenceHelper.setBackgroundMode(nextMode);
        screenSaverView.reloadConfig();

        String[] modeNames = {"纯色背景", "图片背景", "视频背景", "动态壁纸"};
        Toast.makeText(this, modeNames[nextMode], Toast.LENGTH_SHORT).show();
        // 切换后更新壁纸按钮可见性
        updateWallpaperSwitchVisibility();
    }

    private void switchWallpaperStyle() {
        int currentType = PreferenceHelper.getWallpaperType();
        int totalCount = WallpaperFactory.getWallpaperNames().size();
        if (totalCount <= 0) return;
        // 在 0..totalCount-1 范围内循环
        int nextType;
        if (currentType < 0 || currentType >= totalCount) {
            nextType = 0;
        } else {
            nextType = (currentType + 1) % totalCount;
        }
        PreferenceHelper.setWallpaperType(nextType);
        screenSaverView.reloadConfig();

        java.util.List<String> names = WallpaperFactory.getWallpaperNames();
        Toast.makeText(this, names.get(nextType), Toast.LENGTH_SHORT).show();
        // 刷新壁纸参数控制面板
        refreshWallpaperControlPanel();
    }

    private void updateWallpaperSwitchVisibility() {
        if (btnSwitchWallpaper == null || screenSaverView == null) return;
        BackgroundManager bgMgr = screenSaverView.getBackgroundManager();
        boolean isWallpaper = bgMgr != null && bgMgr.isWallpaperMode();
        btnSwitchWallpaper.setVisibility(isWallpaper ? View.VISIBLE : View.GONE);
    }

    /**
     * 刷新壁纸参数控制面板 - 从 BackgroundManager 获取当前壁纸并绑定参数
     */
    private void refreshWallpaperControlPanel() {
        if (wallpaperControlPanel == null || screenSaverView == null) return;
        BackgroundManager bgMgr = screenSaverView.getBackgroundManager();
        if (bgMgr == null || !bgMgr.isWallpaperMode()) return;
        WallpaperRenderer renderer = bgMgr.getWallpaperRenderer();
        if (renderer == null) return;
        int wallpaperType = PreferenceHelper.getWallpaperType();
        wallpaperControlPanel.bind(renderer, wallpaperType);
    }

    // DlnaManager.DlnaEventListener implementation
    @Override
    public void onMediaUriSet(final MediaInfo mediaInfo) {
        // 播放器由 DlnaManager 自动启动，无需在此处重复启动
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

