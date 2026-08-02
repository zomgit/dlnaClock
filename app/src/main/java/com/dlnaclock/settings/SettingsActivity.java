package com.dlnaclock.settings;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.Toast;

import com.dlnaclock.App;
import com.dlnaclock.R;
import com.dlnaclock.clock.ClockConfig;
import com.dlnaclock.clock.MinimalRowConfig;
import com.dlnaclock.airplay.AirPlayManager;
import com.dlnaclock.dlna.DlnaManager;
import com.dlnaclock.dlna.DlnaService;
import com.dlnaclock.util.NetworkUtil;
import com.dlnaclock.util.PreferenceHelper;
import com.dlnaclock.util.ServiceCompat;
import com.dlnaclock.util.SystemFontHelper;
import com.dlnaclock.screensaver.wallpaper.ParamDef;
import com.dlnaclock.screensaver.wallpaper.ParamStore;
import com.dlnaclock.screensaver.wallpaper.ScriptManager;
import com.dlnaclock.screensaver.wallpaper.WallpaperFactory;
import com.dlnaclock.screensaver.wallpaper.WallpaperRenderer;

import android.text.Editable;
import android.text.TextWatcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * SettingsActivity - 设置主界面
 * 自定义时钟：5档案切换 + 3行独立配置（内容类型/格式/字体/颜色）
 * 使用 SystemFontHelper 读取系统字体，ColorPickerDialog 色环选色
 * 卡片折叠/展开、背景联动、图片适应模式
 */
public class SettingsActivity extends AppCompatActivity {

    private static final int PICK_IMAGE = 100;
    private static final int PICK_VIDEO = 101;
    private static final String TAG = "SettingsActivity";
    private boolean isLoadingSettings = false;

    // Clock settings
    private Spinner clockStyleSpinner;
    private SeekBar fontSizeSeekBar;
    private TextView fontSizeValue;
    private SeekBar posXSeekBar;
    private SeekBar posYSeekBar;
    private TextView posXValue;
    private TextView posYValue;
    private LinearLayout clockFormatLayout;
    private Spinner clockFormatSpinner;
    private EditText clockFormatEdit;
    private Button clockColorBtn;
    private TextView clockColorValue;

    // Digital clock font settings
    private LinearLayout digitalFontLayout;
    private Spinner numberFontSpinner;
    private Spinner englishFontSpinner;
    private Spinner chineseFontSpinner;

    // Minimal clock settings (new profile-based)
    private LinearLayout minimalSettingsLayout;
    private LinearLayout profileBarLayout;
    private LinearLayout rowSettingsContainer;
    private int activeProfile = 0;
    private MinimalRowConfig[] rowConfigs = new MinimalRowConfig[3];
    private Button[] profileButtons = new Button[5];
    private RowViewHolder[] rowHolders = new RowViewHolder[3];

    // Anti burn-in settings
    private CheckBox antiBurnInCheck;
    private SeekBar burnInIntervalSeekBar;
    private TextView burnInIntervalValue;
    private SeekBar burnInOffsetRangeSeekBar;
    private TextView burnInOffsetRangeValue;
    private RadioGroup burnInModeRadioGroup;
    private LinearLayout layoutBurnInShift;
    private LinearLayout layoutBurnInBounce;
    private SeekBar bounceAngleSeekBar;
    private TextView bounceAngleValue;
    private SeekBar bounceSpeedSeekBar;
    private TextView bounceSpeedValue;

    // Background settings
    private RadioGroup bgModeRadioGroup;
    private LinearLayout bgColorRow;
    private LinearLayout bgImageRow;
    private LinearLayout bgVideoRow;
    private Button bgColorBtn;
    private Button bgImageBtn;
    private Button bgVideoBtn;
    private TextView bgImageName;
    private TextView bgVideoName;
    private Spinner bgImageFitSpinner;
    private Spinner wallpaperTypeSpinner;
    private TextView wallpaperTypeLabel;
    private LinearLayout wallpaperParamsContainer;
    private WallpaperParamSection wallpaperParamSection;

    // Lua 壁纸管理（自定义模式 mode==4）
    private LinearLayout luaWallpaperLayout;
    private Spinner luaWallpaperSpinner;
    private Button btnLuaNew, btnLuaEdit, btnLuaDelete, btnLuaCopy;
    private List<ScriptManager.ScriptInfo> luaScriptsCache;

    private int currentBgColor = 0xFF000000;
    private int currentClockColor = 0xFFFFFFFF;

    // DLNA settings
    private CheckBox dlnaEnableCheck;
    private LinearLayout dlnaDetailsLayout;
    private EditText deviceNameEdit;
    private TextView statusText;
    private TextView dlnaDisabledWarning;
    private Spinner videoPlayerSpinner;
    private List<String> videoPlayerPackages = new ArrayList<>();
    private List<String> videoPlayerNames = new ArrayList<>();

    // AirPlay settings
    private CheckBox airPlayEnableCheck;
    private LinearLayout airPlayDetailsLayout;
    private EditText airPlayDeviceNameEdit;
    private TextView airPlayStatusText;

    // Advanced/Debug
    private CheckBox debugLogCheck;

    // OSD settings
    private CheckBox osdTimeEnabledCheck;
    private SeekBar osdFontSizeSeekBar;
    private TextView osdFontSizeValue;

    /** RowViewHolder - 单行配置的 UI 控件集合 */
    static class RowViewHolder {
        int rowIndex;
        LinearLayout rootLayout;
        Spinner contentTypeSpinner;
        Spinner formatSpinner;
        EditText customFormatEdit;
        CheckBox use12HourCheck;
        Spinner fontSpinner;
        Button colorBtn;
        TextView colorValue;
        Button statusItemsBtn;
        LinearLayout rotateLayout;
        SeekBar rotateSeekBar;
        TextView rotateValue;
        LinearLayout customTextLayout;
        EditText customTextEdit;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.settings);
        }

        initViews();
        setupCollapsibleCards();
        loadSettings();
        handleScrollIntent();
    }

    // ========== Collapsible Cards ==========

    private void setupCollapsibleCards() {
        setupCardToggle(R.id.title_clock_settings, R.id.arrow_clock_settings, R.id.content_clock_settings);
        setupCardToggle(R.id.title_anti_burn_in, R.id.arrow_anti_burn_in, R.id.content_anti_burn_in);
        setupCardToggle(R.id.title_background, R.id.arrow_background, R.id.content_background);
        setupCardToggle(R.id.title_dlna, R.id.arrow_dlna, R.id.content_dlna);
        setupCardToggle(R.id.title_airplay, R.id.arrow_airplay, R.id.content_airplay);
        setupCardToggle(R.id.title_advanced, R.id.arrow_advanced, R.id.content_advanced);
    }

    private void setupCardToggle(int titleId, int arrowId, int contentId) {
        View titleRow = findViewById(titleId);
        final ImageView arrow = (ImageView) findViewById(arrowId);
        final View content = findViewById(contentId);
        if (titleRow == null || content == null) return;

        // 默认收起，箭头朝右（旋转-90°）
        if (arrow != null) arrow.setRotation(-90);

        titleRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isExpanded = content.getVisibility() == View.VISIBLE;
                content.setVisibility(isExpanded ? View.GONE : View.VISIBLE);
                if (arrow != null) {
                    arrow.setRotation(isExpanded ? -90 : 0);
                }
            }
        });
    }

    /** handleScrollIntent - 处理从主界面跳转来的intent，自动展开并滚动到对应卡片 */
    private void handleScrollIntent() {
        Intent intent = getIntent();
        if (intent == null) return;
        String scrollTo = intent.getStringExtra("scroll_to");
        if (scrollTo == null) return;

        int titleId = 0;
        int contentId = 0;
        switch (scrollTo) {
            case "clock":
                titleId = R.id.title_clock_settings;
                contentId = R.id.content_clock_settings;
                break;
            case "background":
                titleId = R.id.title_background;
                contentId = R.id.content_background;
                break;
            case "dlna":
                titleId = R.id.title_dlna;
                contentId = R.id.content_dlna;
                // DLNA和AirPlay合并，同时展开AirPlay卡片
                expandCard(R.id.title_airplay, R.id.arrow_airplay, R.id.content_airplay);
                break;
            default:
                return;
        }
        expandCard(titleId, 0, contentId);

        // 延迟滚动，等待布局完成
        final int targetId = titleId;
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                View target = findViewById(targetId);
                if (target != null) {
                    ScrollView scrollView = findParentScrollView(target);
                    if (scrollView != null) {
                        scrollView.smoothScrollTo(0, target.getTop());
                    }
                }
            }
        }, 200);
    }

    private void expandCard(int titleId, int arrowId, int contentId) {
        View content = findViewById(contentId);
        if (content != null) content.setVisibility(View.VISIBLE);
        if (arrowId != 0) {
            ImageView arrow = (ImageView) findViewById(arrowId);
            if (arrow != null) arrow.setRotation(0);
        }
    }

    private ScrollView findParentScrollView(View view) {
        android.view.ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent instanceof ScrollView) return (ScrollView) parent;
            parent = parent.getParent();
        }
        return null;
    }

    // ========== Init Views ==========

    private void initViews() {
        // Clock style
        clockStyleSpinner = (Spinner) findViewById(R.id.spinner_clock_style);
        if (clockStyleSpinner != null) {
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                    R.array.clock_style_names, android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            clockStyleSpinner.setAdapter(adapter);
            clockStyleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    PreferenceHelper.setClockStyle(position);
                    updateStyleVisibility(position);
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        // Font size
        fontSizeSeekBar = (SeekBar) findViewById(R.id.seekbar_font_size);
        fontSizeValue = (TextView) findViewById(R.id.tv_font_size_value);
        if (fontSizeSeekBar != null) {
            fontSizeSeekBar.setMax(95);
            fontSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int percent = progress + 5;
                    if (fontSizeValue != null) fontSizeValue.setText(percent + "%");
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    float scale = (seekBar.getProgress() + 5) / 100f;
                    boolean isLandscape = getResources().getConfiguration().orientation
                            == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
                    int clockStyle = PreferenceHelper.getClockStyle();
                    if (clockStyle == ClockConfig.ClockStyle.DIGITAL.getValue()) {
                        if (isLandscape) PreferenceHelper.setClockFontScaleDigitalLandscape(scale);
                        else PreferenceHelper.setClockFontScaleDigitalPortrait(scale);
                    } else if (clockStyle == ClockConfig.ClockStyle.ANALOG.getValue()) {
                        if (isLandscape) PreferenceHelper.setClockFontScaleAnalogLandscape(scale);
                        else PreferenceHelper.setClockFontScaleAnalogPortrait(scale);
                    } else {
                        if (isLandscape) PreferenceHelper.setClockFontScaleMinimalLandscape(scale);
                        else PreferenceHelper.setClockFontScaleMinimalPortrait(scale);
                    }
                    PreferenceHelper.setClockFontScale(scale);
                }
            });
        }

        // Position X
        posXSeekBar = (SeekBar) findViewById(R.id.seekbar_pos_x);
        posXValue = (TextView) findViewById(R.id.tv_pos_x_value);
        if (posXSeekBar != null) {
            posXSeekBar.setMax(100);
            posXSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    float pos = progress / 100f;
                    if (posXValue != null) posXValue.setText(String.format("%.0f%%", pos * 100));
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    PreferenceHelper.setClockPositionX(seekBar.getProgress() / 100f);
                }
            });
        }

        // Position Y
        posYSeekBar = (SeekBar) findViewById(R.id.seekbar_pos_y);
        posYValue = (TextView) findViewById(R.id.tv_pos_y_value);
        if (posYSeekBar != null) {
            posYSeekBar.setMax(100);
            posYSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    float pos = progress / 100f;
                    if (posYValue != null) posYValue.setText(String.format("%.0f%%", pos * 100));
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    PreferenceHelper.setClockPositionY(seekBar.getProgress() / 100f);
                }
            });
        }

        // Clock format
        clockFormatLayout = (LinearLayout) findViewById(R.id.layout_clock_format);
        clockFormatSpinner = (Spinner) findViewById(R.id.spinner_clock_format);
        clockFormatEdit = (EditText) findViewById(R.id.edit_clock_format);
        clockColorBtn = (Button) findViewById(R.id.btn_clock_color);
        clockColorValue = (TextView) findViewById(R.id.tv_clock_color_value);

        if (clockFormatSpinner != null) {
            ArrayAdapter<CharSequence> formatAdapter = ArrayAdapter.createFromResource(this,
                    R.array.clock_format_preset_names, android.R.layout.simple_spinner_item);
            formatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            clockFormatSpinner.setAdapter(formatAdapter);
            clockFormatSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    String[] presets = getResources().getStringArray(R.array.clock_format_presets);
                    if (position < presets.length) {
                        if (clockFormatEdit != null) clockFormatEdit.setVisibility(View.GONE);
                        PreferenceHelper.setClockFormat(presets[position]);
                    } else {
                        if (clockFormatEdit != null) clockFormatEdit.setVisibility(View.VISIBLE);
                    }
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        if (clockFormatEdit != null) {
            clockFormatEdit.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (!hasFocus) {
                        String fmt = clockFormatEdit.getText().toString().trim();
                        if (!fmt.isEmpty()) {
                            PreferenceHelper.setClockFormat(fmt);
                        }
                    }
                }
            });
        }

        if (clockColorBtn != null) {
            clockColorBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ColorPickerDialog.show(SettingsActivity.this, "选择时钟颜色", currentClockColor,
                            new ColorPickerDialog.OnColorSelectedListener() {
                                @Override
                                public void onColorSelected(int color) {
                                    currentClockColor = color;
                                    PreferenceHelper.setClockFontColor(color);
                                    updateClockColorDisplay();
                                }
                            });
                }
            });
        }

        // === Digital clock font settings ===
        digitalFontLayout = (LinearLayout) findViewById(R.id.layout_digital_font);
        numberFontSpinner = (Spinner) findViewById(R.id.spinner_number_font);
        englishFontSpinner = (Spinner) findViewById(R.id.spinner_english_font);
        chineseFontSpinner = (Spinner) findViewById(R.id.spinner_chinese_font);

        setupFontSpinner(numberFontSpinner, new FontSelectionListener() {
            @Override
            public void onFontSelected(String fontValue) {
                PreferenceHelper.setNumberFont(fontValue);
            }
        });
        setupFontSpinner(englishFontSpinner, new FontSelectionListener() {
            @Override
            public void onFontSelected(String fontValue) {
                PreferenceHelper.setEnglishFont(fontValue);
            }
        });
        setupFontSpinner(chineseFontSpinner, new FontSelectionListener() {
            @Override
            public void onFontSelected(String fontValue) {
                PreferenceHelper.setChineseFont(fontValue);
            }
        });

        // === Minimal clock settings (profile-based) ===
        minimalSettingsLayout = (LinearLayout) findViewById(R.id.layout_minimal_settings);
        profileBarLayout = (LinearLayout) findViewById(R.id.layout_profile_bar);
        rowSettingsContainer = (LinearLayout) findViewById(R.id.layout_row_settings);

        buildProfileBar();
        buildRowSettings();

        // === Anti burn-in ===
        antiBurnInCheck = (CheckBox) findViewById(R.id.check_anti_burn_in);
        burnInIntervalSeekBar = (SeekBar) findViewById(R.id.seekbar_burn_in_interval);
        burnInIntervalValue = (TextView) findViewById(R.id.tv_burn_in_interval_value);
        burnInOffsetRangeSeekBar = (SeekBar) findViewById(R.id.seekbar_burn_in_offset_range);
        burnInOffsetRangeValue = (TextView) findViewById(R.id.tv_burn_in_offset_range_value);
        burnInModeRadioGroup = (RadioGroup) findViewById(R.id.radio_burn_in_mode);
        layoutBurnInShift = (LinearLayout) findViewById(R.id.layout_burn_in_shift);
        layoutBurnInBounce = (LinearLayout) findViewById(R.id.layout_burn_in_bounce);
        bounceAngleSeekBar = (SeekBar) findViewById(R.id.seekbar_bounce_angle);
        bounceAngleValue = (TextView) findViewById(R.id.tv_bounce_angle_value);
        bounceSpeedSeekBar = (SeekBar) findViewById(R.id.seekbar_bounce_speed);
        bounceSpeedValue = (TextView) findViewById(R.id.tv_bounce_speed_value);
        if (antiBurnInCheck != null) {
            antiBurnInCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    PreferenceHelper.setAntiBurnInEnabled(isChecked);
                }
            });
        }
        if (burnInIntervalSeekBar != null) {
            burnInIntervalSeekBar.setMax(118); // 2~120s
            burnInIntervalSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int interval = progress + 2;
                    if (burnInIntervalValue != null) burnInIntervalValue.setText(interval + "s");
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    PreferenceHelper.setBurnInInterval(seekBar.getProgress() + 2);
                }
            });
        }
        if (burnInOffsetRangeSeekBar != null) {
            burnInOffsetRangeSeekBar.setMax(29); // 1~30%
            burnInOffsetRangeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int range = progress + 1;
                    if (burnInOffsetRangeValue != null) burnInOffsetRangeValue.setText(range + "%");
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    PreferenceHelper.setBurnInOffsetRange(seekBar.getProgress() + 1);
                }
            });
        }
        if (burnInModeRadioGroup != null) {
            burnInModeRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    int mode = (checkedId == R.id.radio_burn_in_bounce) ? 1 : 0;
                    PreferenceHelper.setAntiBurnInMode(mode);
                    updateBurnInModeVisibility(mode);
                }
            });
        }
        if (bounceAngleSeekBar != null) {
            bounceAngleSeekBar.setMax(90); // 0~90°
            bounceAngleSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (bounceAngleValue != null) bounceAngleValue.setText(progress + "°");
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    PreferenceHelper.setBounceAngleRange(seekBar.getProgress());
                }
            });
        }
        if (bounceSpeedSeekBar != null) {
            bounceSpeedSeekBar.setMax(29); // 1~30%/s
            bounceSpeedSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int speed = progress + 1;
                    if (bounceSpeedValue != null) bounceSpeedValue.setText(speed + "%");
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    PreferenceHelper.setBounceSpeed(seekBar.getProgress() + 1);
                }
            });
        }

        // === Background ===
        bgModeRadioGroup = (RadioGroup) findViewById(R.id.radio_bg_mode);
        bgColorRow = (LinearLayout) findViewById(R.id.layout_bg_color_row);
        bgImageRow = (LinearLayout) findViewById(R.id.layout_bg_image_row);
        bgVideoRow = (LinearLayout) findViewById(R.id.layout_bg_video_row);
        bgColorBtn = (Button) findViewById(R.id.btn_bg_color);
        bgImageBtn = (Button) findViewById(R.id.btn_bg_image);
        bgVideoBtn = (Button) findViewById(R.id.btn_bg_video);
        bgImageName = (TextView) findViewById(R.id.tv_bg_image_name);
        bgVideoName = (TextView) findViewById(R.id.tv_bg_video_name);
        bgImageFitSpinner = (Spinner) findViewById(R.id.spinner_bg_image_fit);
        wallpaperTypeSpinner = (Spinner) findViewById(R.id.spinner_wallpaper_type);
        wallpaperTypeLabel = (TextView) findViewById(R.id.tv_wallpaper_type_label);
        wallpaperParamsContainer = (LinearLayout) findViewById(R.id.layout_wallpaper_params);
        if (wallpaperParamsContainer != null) {
            wallpaperParamSection = new WallpaperParamSection(this, wallpaperParamsContainer);
        }

        if (wallpaperTypeSpinner != null) {
            // 动态壁纸模式（mode==3）只显示内置壁纸（0~10）
            String[] builtinWpNames = getResources().getStringArray(R.array.wallpaper_type_names);
            ArrayAdapter<String> wpAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, builtinWpNames);
            wpAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            wallpaperTypeSpinner.setAdapter(wpAdapter);
            wallpaperTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    int wpType = spinnerPosToWallpaperType(position);
                    PreferenceHelper.setWallpaperType(wpType);
                    if (!isLoadingSettings) {
                        refreshWallpaperParamSection();
                    }
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        if (bgModeRadioGroup != null) {
            bgModeRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    int mode = radioIdToBgMode(checkedId);
                    if (mode >= 0) {
                        PreferenceHelper.setBackgroundMode(mode);
                        updateBackgroundUI();
                    }
                }
            });
        }

        // === Lua 壁纸管理区域（自定义模式） ===
        luaWallpaperLayout = (LinearLayout) findViewById(R.id.layout_lua_wallpaper);
        luaWallpaperSpinner = (Spinner) findViewById(R.id.spinner_lua_wallpaper);
        btnLuaNew = (Button) findViewById(R.id.btn_lua_new);
        btnLuaEdit = (Button) findViewById(R.id.btn_lua_edit);
        btnLuaDelete = (Button) findViewById(R.id.btn_lua_delete);
        btnLuaCopy = (Button) findViewById(R.id.btn_lua_copy);

        refreshLuaWallpaperSpinner();

        if (luaWallpaperSpinner != null) {
            luaWallpaperSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (luaScriptsCache != null && position >= 0 && position < luaScriptsCache.size()) {
                        ScriptManager.ScriptInfo info = luaScriptsCache.get(position);
                        PreferenceHelper.setSelectedLuaScript(info.path);
                        updateLuaButtonStates();
                    }
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        if (btnLuaNew != null) {
            btnLuaNew.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showLuaEditorDialog(null);
                }
            });
        }
        if (btnLuaEdit != null) {
            btnLuaEdit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ScriptManager.ScriptInfo info = getSelectedLuaScript();
                    if (info != null && !ScriptManager.isBuiltin(info.path)) {
                        showLuaEditorDialog(info);
                    }
                }
            });
        }
        if (btnLuaDelete != null) {
            btnLuaDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    deleteSelectedLuaScript();
                }
            });
        }
        if (btnLuaCopy != null) {
            btnLuaCopy.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    copySelectedLuaScript();
                }
            });
        }

        // Image fit spinner
        if (bgImageFitSpinner != null) {
            ArrayAdapter<CharSequence> fitAdapter = ArrayAdapter.createFromResource(this,
                    R.array.bg_image_fit_names, android.R.layout.simple_spinner_item);
            fitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            bgImageFitSpinner.setAdapter(fitAdapter);
            bgImageFitSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    PreferenceHelper.setBackgroundImageFit(position);
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        if (bgColorBtn != null) {
            bgColorBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ColorPickerDialog.show(SettingsActivity.this, "选择背景颜色", currentBgColor,
                            new ColorPickerDialog.OnColorSelectedListener() {
                                @Override
                                public void onColorSelected(int color) {
                                    currentBgColor = color;
                                    PreferenceHelper.setBackgroundColor(color);
                                    bgColorBtn.setBackgroundColor(color);
                                }
                            });
                }
            });
        }

        if (bgImageBtn != null) {
            bgImageBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("image/*");
                    startActivityForResult(intent, PICK_IMAGE);
                }
            });
        }

        if (bgVideoBtn != null) {
            bgVideoBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("video/*");
                    startActivityForResult(intent, PICK_VIDEO);
                }
            });
        }

        // === DLNA ===
        dlnaEnableCheck = (CheckBox) findViewById(R.id.check_dlna_enable);
        dlnaDetailsLayout = (LinearLayout) findViewById(R.id.layout_dlna_details);
        dlnaDisabledWarning = (TextView) findViewById(R.id.tv_dlna_disabled_warning);
        deviceNameEdit = (EditText) findViewById(R.id.edit_device_name);
        statusText = (TextView) findViewById(R.id.tv_dlna_status);
        videoPlayerSpinner = (Spinner) findViewById(R.id.spinner_video_player);

        if (dlnaEnableCheck != null) {
            dlnaEnableCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isLoadingSettings) return;
                    PreferenceHelper.setDlnaEnabled(isChecked);
                    updateDlnaDetailsVisibility(isChecked);
                    DlnaManager dlnaManager = App.getInstance().getDlnaManager();
                    if (isChecked) {
                        String name = deviceNameEdit != null ? deviceNameEdit.getText().toString().trim() : "";
                        if (!name.isEmpty()) {
                            PreferenceHelper.setDeviceName(name);
                            dlnaManager.getDevice().setFriendlyName(name);
                        }
                        Intent serviceIntent = new Intent(SettingsActivity.this, DlnaService.class);
                        ServiceCompat.startForegroundService(SettingsActivity.this, serviceIntent);
                        String playerPkg = PreferenceHelper.getVideoPlayerPackage();
                        if (playerPkg == null || playerPkg.isEmpty()) {
                            showVideoPlayerSelectionDialog();
                        }
                    } else {
                        stopService(new Intent(SettingsActivity.this, DlnaService.class));
                        dlnaManager.shutdown();
                    }
                    updateDlnaStatus();
                }
            });
        }

        if (deviceNameEdit != null) {
            deviceNameEdit.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (!hasFocus) {
                        String name = deviceNameEdit.getText().toString().trim();
                        if (!name.isEmpty()) {
                            PreferenceHelper.setDeviceName(name);
                            DlnaManager dlnaManager = App.getInstance().getDlnaManager();
                            if (dlnaManager.isRunning()) {
                                dlnaManager.getDevice().setFriendlyName(name);
                                dlnaManager.restart();
                                updateDlnaStatus();
                            }
                        }
                    }
                }
            });
        }

        setupVideoPlayerSpinner();

        // === AirPlay ===
        airPlayEnableCheck = (CheckBox) findViewById(R.id.check_airplay_enable);
        airPlayDetailsLayout = (LinearLayout) findViewById(R.id.layout_airplay_details);
        airPlayDeviceNameEdit = (EditText) findViewById(R.id.edit_airplay_device_name);
        airPlayStatusText = (TextView) findViewById(R.id.tv_airplay_status);

        if (airPlayEnableCheck != null) {
            airPlayEnableCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isLoadingSettings) return;
                    PreferenceHelper.setAirPlayEnabled(isChecked);
                    updateAirPlayDetailsVisibility(isChecked);
                    AirPlayManager airPlayManager = App.getInstance().getAirPlayManager();
                    if (isChecked) {
                        String name = airPlayDeviceNameEdit != null ? airPlayDeviceNameEdit.getText().toString().trim() : "";
                        if (!name.isEmpty()) {
                            PreferenceHelper.setAirPlayDeviceName(name);
                        }
                        Intent intent = new Intent(SettingsActivity.this, com.dlnaclock.airplay.AirPlayService.class);
                        ServiceCompat.startForegroundService(SettingsActivity.this, intent);
                    } else {
                        stopService(new Intent(SettingsActivity.this, com.dlnaclock.airplay.AirPlayService.class));
                    }
                    updateAirPlayStatus();
                }
            });
        }

        if (airPlayDeviceNameEdit != null) {
            airPlayDeviceNameEdit.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (!hasFocus) {
                        String name = airPlayDeviceNameEdit.getText().toString().trim();
                        if (!name.isEmpty()) {
                            PreferenceHelper.setAirPlayDeviceName(name);
                            AirPlayManager airPlayManager = App.getInstance().getAirPlayManager();
                            if (airPlayManager != null && airPlayManager.isRunning()) {
                                airPlayManager.shutdown();
                                airPlayManager.start();
                                updateAirPlayStatus();
                            }
                        }
                    }
                }
            });
        }

        // === 高级/调试 ===
        debugLogCheck = (CheckBox) findViewById(R.id.check_debug_log);
        if (debugLogCheck != null) {
            debugLogCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isLoadingSettings) return;
                    PreferenceHelper.setDebugLogEnabled(isChecked);
                }
            });
        }

        // === OSD 叠加层设置 ===
        osdTimeEnabledCheck = (CheckBox) findViewById(R.id.check_osd_time_enabled);
        osdFontSizeSeekBar = (SeekBar) findViewById(R.id.seekbar_osd_font_size);
        osdFontSizeValue = (TextView) findViewById(R.id.tv_osd_font_size_value);

        if (osdTimeEnabledCheck != null) {
            osdTimeEnabledCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    PreferenceHelper.setOsdTimeEnabled(isChecked);
                }
            });
        }

        if (osdFontSizeSeekBar != null) {
            osdFontSizeSeekBar.setMax(228); // 12~240
            osdFontSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int size = progress + 12;
                    if (osdFontSizeValue != null) osdFontSizeValue.setText(String.valueOf(size));
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    PreferenceHelper.setOsdFontSize(seekBar.getProgress() + 12);
                }
            });
        }
    }

    // ========== Profile Bar ==========

    private void buildProfileBar() {
        if (profileBarLayout == null) return;
        profileBarLayout.removeAllViews();
        for (int i = 0; i < 5; i++) {
            Button btn = new Button(this);
            btn.setText("栏位" + (i + 1));
            btn.setTextSize(12);
            btn.setBackgroundResource(R.drawable.bg_profile_button);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1);
            lp.setMargins(dp(2), 0, dp(2), 0);
            btn.setPadding(0, 0, 0, 0);
            profileBarLayout.addView(btn, lp);
            final int profileIndex = i;
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchProfile(profileIndex);
                }
            });
            profileButtons[i] = btn;
        }
    }

    private void switchProfile(int profile) {
        saveCurrentRowConfigs();
        activeProfile = profile;
        PreferenceHelper.setMinimalActiveProfile(profile);
        for (int i = 0; i < 3; i++) {
            rowConfigs[i] = PreferenceHelper.getMinimalRowConfig(profile, i);
        }
        updateProfileBarHighlight();
        refreshRowUI();
    }

    private void updateProfileBarHighlight() {
        for (int i = 0; i < 5; i++) {
            if (profileButtons[i] != null) {
                profileButtons[i].setSelected(i == activeProfile);
                profileButtons[i].setTextColor(ContextCompat.getColor(this,
                        i == activeProfile ? R.color.text_primary : R.color.text_secondary));
            }
        }
    }

    // ========== Row Settings ==========

    private void buildRowSettings() {
        if (rowSettingsContainer == null) return;
        rowSettingsContainer.removeAllViews();
        rowSettingsContainer.setBackgroundResource(R.drawable.bg_row_group);
        rowSettingsContainer.setPadding(dp(12), dp(12), dp(12), dp(12));

        activeProfile = PreferenceHelper.getMinimalActiveProfile();
        for (int i = 0; i < 3; i++) {
            rowConfigs[i] = PreferenceHelper.getMinimalRowConfig(activeProfile, i);
        }
        updateProfileBarHighlight();

        for (int i = 0; i < 3; i++) {
            buildSingleRow(i);
        }
    }

    private void buildSingleRow(final int rowIndex) {
        // Row divider
        View divider = new View(this);
        divider.setBackgroundColor(ContextCompat.getColor(this, R.color.card_stroke));
        rowSettingsContainer.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        ((LinearLayout.LayoutParams) divider.getLayoutParams()).bottomMargin =
                getResources().getDimensionPixelSize(R.dimen.item_spacing);

        // Row title
        TextView title = new TextView(this);
        title.setText(String.format("第%d行", rowIndex + 1));
        title.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        title.setTextSize(16);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = getResources().getDimensionPixelSize(R.dimen.label_spacing);
        rowSettingsContainer.addView(title, titleParams);

        final RowViewHolder holder = new RowViewHolder();
        holder.rowIndex = rowIndex;
        final MinimalRowConfig config = rowConfigs[rowIndex];

        // --- Content type spinner ---
        addLabel("内容类型");
        holder.contentTypeSpinner = new Spinner(this);
        holder.contentTypeSpinner.setBackgroundResource(R.drawable.bg_settings_spinner);
        ArrayAdapter<CharSequence> ctAdapter = ArrayAdapter.createFromResource(this,
                R.array.minimal_content_type_names, android.R.layout.simple_spinner_item);
        ctAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        holder.contentTypeSpinner.setAdapter(ctAdapter);
        holder.contentTypeSpinner.setSelection(config.getContentType().getValue());
        rowSettingsContainer.addView(holder.contentTypeSpinner, spinnerParams());
        holder.contentTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                config.setContentType(MinimalRowConfig.ContentType.fromValue(position));
                saveRowConfig(rowIndex, config);
                updateRowVisibility(holder, config);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // --- Format spinner (TIME/DATE only) ---
        addLabel("格式");
        holder.formatSpinner = new Spinner(this);
        holder.formatSpinner.setBackgroundResource(R.drawable.bg_settings_spinner);
        rowSettingsContainer.addView(holder.formatSpinner, spinnerParams());
        setupRowFormatSpinner(holder, rowIndex, config);

        // --- Custom format EditText ---
        holder.customFormatEdit = new EditText(this);
        holder.customFormatEdit.setHint("输入自定义格式");
        holder.customFormatEdit.setBackgroundResource(R.drawable.bg_settings_edittext);
        holder.customFormatEdit.setPadding(dp(12), 0, dp(12), 0);
        holder.customFormatEdit.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        holder.customFormatEdit.setHintTextColor(ContextCompat.getColor(this, R.color.text_hint));
        holder.customFormatEdit.setTextSize(14);
        holder.customFormatEdit.setSingleLine(true);
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        editParams.bottomMargin = getResources().getDimensionPixelSize(R.dimen.item_spacing);
        rowSettingsContainer.addView(holder.customFormatEdit, editParams);
        holder.customFormatEdit.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    String fmt = holder.customFormatEdit.getText().toString().trim();
                    if (!fmt.isEmpty()) {
                        config.setFormat(fmt);
                        saveRowConfig(rowIndex, config);
                    }
                }
            }
        });

        // --- 12h checkbox (TIME only) ---
        holder.use12HourCheck = new CheckBox(this);
        holder.use12HourCheck.setText("12小时制");
        holder.use12HourCheck.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        holder.use12HourCheck.setChecked(config.isUse12Hour());
        LinearLayout.LayoutParams cbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cbParams.bottomMargin = getResources().getDimensionPixelSize(R.dimen.item_spacing);
        rowSettingsContainer.addView(holder.use12HourCheck, cbParams);
        holder.use12HourCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                config.setUse12Hour(isChecked);
                saveRowConfig(rowIndex, config);
                setupRowFormatSpinner(holder, rowIndex, config);
            }
        });

        // --- Font spinner ---
        addLabel("字体");
        holder.fontSpinner = new Spinner(this);
        holder.fontSpinner.setBackgroundResource(R.drawable.bg_settings_spinner);
        rowSettingsContainer.addView(holder.fontSpinner, spinnerParams());
        setupRowFontSpinner(holder, rowIndex, config);

        // --- Color button + hex ---
        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams colorRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        colorRowParams.bottomMargin = getResources().getDimensionPixelSize(R.dimen.item_spacing);
        rowSettingsContainer.addView(colorRow, colorRowParams);

        TextView colorLabel = new TextView(this);
        colorLabel.setText("颜色");
        colorLabel.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        colorLabel.setTextSize(14);
        colorRow.addView(colorLabel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        holder.colorBtn = new Button(this);
        LinearLayout.LayoutParams cbp = new LinearLayout.LayoutParams(dp(48), dp(48));
        cbp.setMargins(dp(8), 0, dp(8), 0);
        colorRow.addView(holder.colorBtn, cbp);
        holder.colorBtn.setBackgroundColor(config.getColor());
        holder.colorBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ColorPickerDialog.show(SettingsActivity.this, "选择颜色", config.getColor(),
                        new ColorPickerDialog.OnColorSelectedListener() {
                            @Override
                            public void onColorSelected(int color) {
                                config.setColor(color);
                                saveRowConfig(rowIndex, config);
                                holder.colorBtn.setBackgroundColor(color);
                                holder.colorValue.setText(String.format("#%06X", color & 0xFFFFFF));
                            }
                        });
            }
        });

        holder.colorValue = new TextView(this);
        holder.colorValue.setText(String.format("#%06X", config.getColor() & 0xFFFFFF));
        holder.colorValue.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary));
        holder.colorValue.setTextSize(12);
        colorRow.addView(holder.colorValue, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // --- Status items button (STATUS only) ---
        holder.statusItemsBtn = new Button(this);
        holder.statusItemsBtn.setTextSize(14);
        holder.statusItemsBtn.setBackgroundResource(R.drawable.bg_settings_button);
        holder.statusItemsBtn.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        statusParams.bottomMargin = getResources().getDimensionPixelSize(R.dimen.item_spacing);
        rowSettingsContainer.addView(holder.statusItemsBtn, statusParams);
        updateStatusButtonText(holder, config);
        holder.statusItemsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRowStatusItemsDialog(rowIndex, holder, config);
            }
        });

        // --- Rotate interval (STATUS only) ---
        holder.rotateLayout = new LinearLayout(this);
        holder.rotateLayout.setOrientation(LinearLayout.HORIZONTAL);
        holder.rotateLayout.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowSettingsContainer.addView(holder.rotateLayout, rlParams);

        TextView rotateLabel = new TextView(this);
        rotateLabel.setText("轮播间隔");
        rotateLabel.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        rotateLabel.setTextSize(14);
        holder.rotateLayout.addView(rotateLabel, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        holder.rotateValue = new TextView(this);
        holder.rotateValue.setText(config.getRotateInterval() + "s");
        holder.rotateValue.setTextColor(ContextCompat.getColor(this, R.color.accent));
        holder.rotateValue.setTextSize(14);
        holder.rotateLayout.addView(holder.rotateValue, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        holder.rotateSeekBar = new SeekBar(this);
        holder.rotateSeekBar.setMax(19);
        holder.rotateSeekBar.setProgress(Math.max(0, config.getRotateInterval() - 1));
        LinearLayout.LayoutParams rsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rsParams.bottomMargin = getResources().getDimensionPixelSize(R.dimen.item_spacing);
        rowSettingsContainer.addView(holder.rotateSeekBar, rsParams);
        holder.rotateSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int interval = progress + 1;
                holder.rotateValue.setText(interval + "s");
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                config.setRotateInterval(seekBar.getProgress() + 1);
                saveRowConfig(rowIndex, config);
            }
        });

        // --- Custom text (CUSTOM only) ---
        holder.customTextLayout = new LinearLayout(this);
        holder.customTextLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ctlParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ctlParams.bottomMargin = getResources().getDimensionPixelSize(R.dimen.item_spacing);
        rowSettingsContainer.addView(holder.customTextLayout, ctlParams);

        TextView ctLabel = new TextView(this);
        ctLabel.setText("自定义文本");
        ctLabel.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        ctLabel.setTextSize(14);
        holder.customTextLayout.addView(ctLabel);

        holder.customTextEdit = new EditText(this);
        holder.customTextEdit.setHint("输入自定义显示内容");
        holder.customTextEdit.setBackgroundResource(R.drawable.bg_settings_edittext);
        holder.customTextEdit.setPadding(dp(12), 0, dp(12), 0);
        holder.customTextEdit.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        holder.customTextEdit.setHintTextColor(ContextCompat.getColor(this, R.color.text_hint));
        holder.customTextEdit.setTextSize(14);
        holder.customTextEdit.setSingleLine(true);
        holder.customTextEdit.setText(config.getCustomText());
        LinearLayout.LayoutParams cteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        holder.customTextLayout.addView(holder.customTextEdit, cteParams);
        // 实时同步自定义文本到 config（解决失焦才保存导致内容丢失的问题）
        holder.customTextEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                config.setCustomText(s.toString().trim());
                saveRowConfig(rowIndex, config);
            }
        });

        // Store holder reference
        rowHolders[rowIndex] = holder;

        // Apply initial visibility
        updateRowVisibility(holder, config);
    }

    private void setupRowFormatSpinner(final RowViewHolder holder, final int rowIndex, final MinimalRowConfig config) {
        MinimalRowConfig.ContentType ct = config.getContentType();
        String[] names;
        final String[] presets;

        if (ct == MinimalRowConfig.ContentType.TIME) {
            if (config.isUse12Hour()) {
                names = new String[]{"hh:mm:ss a", "hh:mm a", "hh:mm:ss a EEE", "自定义…"};
                presets = new String[]{"hh:mm:ss a", "hh:mm a", "hh:mm:ss a EEE"};
            } else {
                names = getResources().getStringArray(R.array.minimal_time_format_names);
                presets = getResources().getStringArray(R.array.minimal_time_format_presets);
            }
        } else if (ct == MinimalRowConfig.ContentType.DATE) {
            names = getResources().getStringArray(R.array.minimal_date_format_names);
            presets = getResources().getStringArray(R.array.minimal_date_format_presets);
        } else {
            names = new String[]{"N/A"};
            presets = new String[]{};
        }

        ArrayAdapter<String> fmtAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, names);
        fmtAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        holder.formatSpinner.setAdapter(fmtAdapter);

        String currentFormat = config.getFormat();
        int matchIndex = presets.length;
        for (int i = 0; i < presets.length; i++) {
            if (presets[i].equals(currentFormat)) {
                matchIndex = i;
                break;
            }
        }
        holder.formatSpinner.setSelection(matchIndex);

        holder.formatSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < presets.length) {
                    config.setFormat(presets[position]);
                    saveRowConfig(rowIndex, config);
                    holder.customFormatEdit.setVisibility(View.GONE);
                } else {
                    holder.customFormatEdit.setVisibility(View.VISIBLE);
                    holder.customFormatEdit.setText(config.getFormat());
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupRowFontSpinner(final RowViewHolder holder, final int rowIndex, final MinimalRowConfig config) {
        final List<SystemFontHelper.FontItem> fonts = SystemFontHelper.getDefaultFonts(this);
        FontAdapter fontAdapter = new FontAdapter(fonts, "ABCabc 123 你好");
        holder.fontSpinner.setAdapter(fontAdapter);

        // Match current font in default list
        int selectedIndex = SystemFontHelper.findIndexInDefaultFonts(this, config.getFontName());
        if (selectedIndex < 0) selectedIndex = 0;
        holder.fontSpinner.setSelection(selectedIndex);

        holder.fontSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < fonts.size()) {
                    config.setFontName(fonts.get(position).value);
                    saveRowConfig(rowIndex, config);
                } else if (position == fonts.size()) {
                    // "更多字体…" 选项
                    showAllFontsDialog(new FontSelectionListener() {
                        @Override
                        public void onFontSelected(String fontValue) {
                            config.setFontName(fontValue);
                            saveRowConfig(rowIndex, config);
                            // 刷新 spinner 显示
                            refreshRowFontSpinner(holder, config);
                        }
                    });
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void refreshRowFontSpinner(RowViewHolder holder, MinimalRowConfig config) {
        List<SystemFontHelper.FontItem> fonts = SystemFontHelper.getDefaultFonts(this);
        int idx = SystemFontHelper.findIndexInDefaultFonts(this, config.getFontName());
        if (idx >= 0 && holder.fontSpinner != null) {
            holder.fontSpinner.setSelection(idx);
        }
    }

    private void updateRowVisibility(RowViewHolder holder, MinimalRowConfig config) {
        MinimalRowConfig.ContentType ct = config.getContentType();
        boolean isTime = ct == MinimalRowConfig.ContentType.TIME;
        boolean isDate = ct == MinimalRowConfig.ContentType.DATE;
        boolean isStatus = ct == MinimalRowConfig.ContentType.STATUS;
        boolean isCustom = ct == MinimalRowConfig.ContentType.CUSTOM;
        boolean hasFormat = isTime || isDate;

        holder.formatSpinner.setVisibility(hasFormat ? View.VISIBLE : View.GONE);
        if (holder.formatSpinner.getParent() == rowSettingsContainer) {
            int idx = indexOfChild(rowSettingsContainer, holder.formatSpinner);
            if (idx > 0) {
                View labelBefore = rowSettingsContainer.getChildAt(idx - 1);
                if (labelBefore instanceof TextView && "格式".equals(((TextView) labelBefore).getText().toString())) {
                    labelBefore.setVisibility(hasFormat ? View.VISIBLE : View.GONE);
                }
            }
        }
        if (!hasFormat) {
            holder.customFormatEdit.setVisibility(View.GONE);
        }
        holder.use12HourCheck.setVisibility(isTime ? View.VISIBLE : View.GONE);
        holder.statusItemsBtn.setVisibility(isStatus ? View.VISIBLE : View.GONE);
        holder.rotateLayout.setVisibility(isStatus ? View.VISIBLE : View.GONE);
        holder.rotateSeekBar.setVisibility(isStatus ? View.VISIBLE : View.GONE);
        // 自定义文本输入框：CUSTOM 行始终显示；STATUS 行勾选"自定义内容"时显示
        boolean showCustomText = isCustom
                || (isStatus && (config.getStatusItems() & ClockConfig.STATUS_CUSTOM) != 0);
        holder.customTextLayout.setVisibility(showCustomText ? View.VISIBLE : View.GONE);
    }

    private int indexOfChild(ViewGroup parent, View child) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChildAt(i) == child) return i;
        }
        return -1;
    }

    private void refreshRowUI() {
        if (rowSettingsContainer == null) return;
        rowSettingsContainer.removeAllViews();
        for (int i = 0; i < 3; i++) {
            buildSingleRow(i);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 保存所有行的自定义文本编辑（防止用户直接按返回键未触发焦点变化）
        for (int i = 0; i < 3; i++) {
            RowViewHolder h = rowHolders[i];
            if (h != null && h.customTextEdit != null && rowConfigs[i] != null) {
                String text = h.customTextEdit.getText().toString().trim();
                if (!text.equals(rowConfigs[i].getCustomText())) {
                    rowConfigs[i].setCustomText(text);
                    PreferenceHelper.setMinimalRowConfig(activeProfile, i, rowConfigs[i]);
                }
            }
            // 同时保存自定义格式编辑
            if (h != null && h.customFormatEdit != null && rowConfigs[i] != null
                    && h.customFormatEdit.getVisibility() == View.VISIBLE) {
                String fmt = h.customFormatEdit.getText().toString().trim();
                if (!fmt.isEmpty() && !fmt.equals(rowConfigs[i].getFormat())) {
                    rowConfigs[i].setFormat(fmt);
                    PreferenceHelper.setMinimalRowConfig(activeProfile, i, rowConfigs[i]);
                }
            }
        }
    }

    private void saveRowConfig(int rowIndex, MinimalRowConfig config) {
        rowConfigs[rowIndex] = config;
        PreferenceHelper.setMinimalRowConfig(activeProfile, rowIndex, config);
    }

    private void saveCurrentRowConfigs() {
        for (int i = 0; i < 3; i++) {
            if (rowConfigs[i] != null) {
                PreferenceHelper.setMinimalRowConfig(activeProfile, i, rowConfigs[i]);
            }
        }
    }

    private void showRowStatusItemsDialog(final int rowIndex, final RowViewHolder holder, final MinimalRowConfig config) {
        final String[] itemNames = getResources().getStringArray(R.array.minimal_status_items);
        final int[] bitMasks = {
                ClockConfig.STATUS_CPU,
                ClockConfig.STATUS_BATTERY,
                ClockConfig.STATUS_APP_TIME,
                ClockConfig.STATUS_DEV_TIME,
                ClockConfig.STATUS_CUSTOM
        };
        int currentItems = config.getStatusItems();
        final boolean[] checked = new boolean[itemNames.length];
        for (int i = 0; i < bitMasks.length; i++) {
            checked[i] = (currentItems & bitMasks[i]) != 0;
        }

        new AlertDialog.Builder(this)
                .setTitle("选择状态信息")
                .setMultiChoiceItems(itemNames, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        checked[which] = isChecked;
                    }
                })
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int newItems = 0;
                        for (int i = 0; i < bitMasks.length; i++) {
                            if (checked[i]) newItems |= bitMasks[i];
                        }
                        config.setStatusItems(newItems);
                        saveRowConfig(rowIndex, config);
                        updateStatusButtonText(holder, config);
                        // 勾选/取消"自定义内容"时同步显示/隐藏自定义文本输入框
                        updateRowVisibility(holder, config);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateStatusButtonText(RowViewHolder holder, MinimalRowConfig config) {
        int items = config.getStatusItems();
        int count = 0;
        if ((items & ClockConfig.STATUS_CPU) != 0) count++;
        if ((items & ClockConfig.STATUS_BATTERY) != 0) count++;
        if ((items & ClockConfig.STATUS_APP_TIME) != 0) count++;
        if ((items & ClockConfig.STATUS_DEV_TIME) != 0) count++;
        if ((items & ClockConfig.STATUS_CUSTOM) != 0) count++;
        holder.statusItemsBtn.setText("选择状态信息" + (count > 0 ? " (已选" + count + "项)" : ""));
    }

    // ========== Font Helpers ==========

    private void addLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        label.setTextSize(13);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.bottomMargin = getResources().getDimensionPixelSize(R.dimen.label_spacing);
        rowSettingsContainer.addView(label, labelParams);
    }

    private LinearLayout.LayoutParams spinnerParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        lp.bottomMargin = getResources().getDimensionPixelSize(R.dimen.item_spacing);
        return lp;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** showAllFontsDialog - 弹出完整字体列表对话框 */
    private void showAllFontsDialog(final FontSelectionListener listener) {
        final List<SystemFontHelper.FontItem> allFonts = SystemFontHelper.getAllFonts(this);
        String[] names = new String[allFonts.size()];
        for (int i = 0; i < allFonts.size(); i++) {
            names[i] = allFonts.get(i).name;
        }

        new AlertDialog.Builder(this)
                .setTitle("选择字体")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (listener != null) {
                            listener.onFontSelected(allFonts.get(which).value);
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** FontAdapter - 字体 Spinner 适配器，显示字体名称 + 预览文本 */
    private static class FontAdapter extends BaseAdapter {
        private final List<SystemFontHelper.FontItem> fonts;
        private final String previewText;
        private static final String MORE_FONTS_LABEL = "更多字体…";

        FontAdapter(List<SystemFontHelper.FontItem> fonts, String previewText) {
            this.fonts = fonts;
            this.previewText = previewText;
        }

        @Override
        public int getCount() { return fonts.size() + 1; } // +1 for "更多字体…"
        @Override
        public Object getItem(int position) {
            if (position < fonts.size()) return fonts.get(position);
            return MORE_FONTS_LABEL;
        }
        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return buildView(position, convertView, parent, false);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return buildView(position, convertView, parent, true);
        }

        private View buildView(int position, View convertView, ViewGroup parent, boolean isDropdown) {
            LinearLayout layout;
            TextView nameView;
            TextView previewView;

            if (convertView instanceof LinearLayout && convertView.getTag() != null) {
                layout = (LinearLayout) convertView;
                nameView = (TextView) layout.getTag();
                previewView = (TextView) layout.findViewWithTag("preview");
            } else {
                layout = new LinearLayout(parent.getContext());
                layout.setOrientation(LinearLayout.VERTICAL);
                int padH = isDropdown ? 24 : 16;
                int padV = isDropdown ? 12 : 8;
                layout.setPadding(padH, padV, padH, padV);

                nameView = new TextView(parent.getContext());
                nameView.setTextSize(15);
                nameView.setTextColor(0xFFFFFFFF);
                nameView.setSingleLine(true);
                layout.addView(nameView);

                previewView = new TextView(parent.getContext());
                previewView.setTag("preview");
                previewView.setTextSize(12);
                previewView.setTextColor(0xFF888888);
                previewView.setSingleLine(true);
                layout.addView(previewView);

                layout.setTag(nameView);
            }

            if (position < fonts.size()) {
                SystemFontHelper.FontItem item = fonts.get(position);
                nameView.setText(item.name);
                if (item.typeface != null) {
                    nameView.setTypeface(item.typeface);
                    previewView.setTypeface(item.typeface);
                } else {
                    nameView.setTypeface(Typeface.DEFAULT);
                    previewView.setTypeface(Typeface.DEFAULT);
                }
                previewView.setText(previewText);
                previewView.setVisibility(View.VISIBLE);
            } else {
                // "更多字体…" 选项
                nameView.setText(MORE_FONTS_LABEL);
                nameView.setTypeface(Typeface.DEFAULT);
                previewView.setTypeface(Typeface.DEFAULT);
                previewView.setVisibility(View.GONE);
            }

            return layout;
        }
    }

    // ========== Load Settings ==========

    private void loadSettings() {
        isLoadingSettings = true;
        int clockStyle = PreferenceHelper.getClockStyle();
        if (clockStyleSpinner != null) {
            clockStyleSpinner.setSelection(clockStyle);
        }
        updateStyleVisibility(clockStyle);

        if (fontSizeSeekBar != null) {
            boolean isLandscape = getResources().getConfiguration().orientation
                    == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
            int currentStyle = PreferenceHelper.getClockStyle();
            float currentScale;
            if (currentStyle == ClockConfig.ClockStyle.DIGITAL.getValue()) {
                currentScale = isLandscape ?
                    PreferenceHelper.getClockFontScaleDigitalLandscape() :
                    PreferenceHelper.getClockFontScaleDigitalPortrait();
            } else if (currentStyle == ClockConfig.ClockStyle.ANALOG.getValue()) {
                currentScale = isLandscape ?
                    PreferenceHelper.getClockFontScaleAnalogLandscape() :
                    PreferenceHelper.getClockFontScaleAnalogPortrait();
            } else {
                currentScale = isLandscape ?
                    PreferenceHelper.getClockFontScaleMinimalLandscape() :
                    PreferenceHelper.getClockFontScaleMinimalPortrait();
            }
            int percent = Math.round(currentScale * 100);
            fontSizeSeekBar.setProgress(Math.max(0, percent - 5));
            if (fontSizeValue != null) fontSizeValue.setText(percent + "%");
        }
        if (posXSeekBar != null) {
            posXSeekBar.setProgress((int) (PreferenceHelper.getClockPositionX() * 100));
        }
        if (posYSeekBar != null) {
            posYSeekBar.setProgress((int) (PreferenceHelper.getClockPositionY() * 100));
        }

        loadClockFormatSetting();
        currentClockColor = PreferenceHelper.getClockFontColor();
        updateClockColorDisplay();

        loadFontSpinner(numberFontSpinner, PreferenceHelper.getNumberFont());
        loadFontSpinner(englishFontSpinner, PreferenceHelper.getEnglishFont());
        loadFontSpinner(chineseFontSpinner, PreferenceHelper.getChineseFont());

        updateProfileBarHighlight();

        if (antiBurnInCheck != null) {
            antiBurnInCheck.setChecked(PreferenceHelper.isAntiBurnInEnabled());
        }
        if (burnInIntervalSeekBar != null) {
            burnInIntervalSeekBar.setProgress(PreferenceHelper.getBurnInInterval() - 2);
        }
        if (burnInOffsetRangeSeekBar != null) {
            int offsetRange = PreferenceHelper.getBurnInOffsetRange();
            burnInOffsetRangeSeekBar.setProgress(offsetRange - 1);
            if (burnInOffsetRangeValue != null) burnInOffsetRangeValue.setText(offsetRange + "%");
        }
        if (burnInModeRadioGroup != null) {
            int mode = PreferenceHelper.getAntiBurnInMode();
            burnInModeRadioGroup.check(mode == 1 ? R.id.radio_burn_in_bounce : R.id.radio_burn_in_shift);
            updateBurnInModeVisibility(mode);
        }
        if (bounceAngleSeekBar != null) {
            int angle = PreferenceHelper.getBounceAngleRange();
            bounceAngleSeekBar.setProgress(angle);
            if (bounceAngleValue != null) bounceAngleValue.setText(angle + "°");
        }
        if (bounceSpeedSeekBar != null) {
            int speed = PreferenceHelper.getBounceSpeed();
            bounceSpeedSeekBar.setProgress(speed - 1);
            if (bounceSpeedValue != null) bounceSpeedValue.setText(speed + "%");
        }
        if (bgModeRadioGroup != null) {
            checkRadioByBgMode(PreferenceHelper.getBackgroundMode());
        }
        if (bgImageFitSpinner != null) {
            bgImageFitSpinner.setSelection(PreferenceHelper.getBackgroundImageFit());
        }
        if (wallpaperTypeSpinner != null) {
            wallpaperTypeSpinner.setSelection(wallpaperTypeToSpinnerPos(PreferenceHelper.getWallpaperType()));
        }
        currentBgColor = PreferenceHelper.getBackgroundColor();
        if (bgColorBtn != null) {
            bgColorBtn.setBackgroundColor(currentBgColor);
        }
        if (deviceNameEdit != null) {
            deviceNameEdit.setText(PreferenceHelper.getDeviceName());
        }
        if (dlnaEnableCheck != null) {
            dlnaEnableCheck.setChecked(PreferenceHelper.isDlnaEnabled());
        }
        updateDlnaDetailsVisibility(PreferenceHelper.isDlnaEnabled());

        if (airPlayEnableCheck != null) {
            airPlayEnableCheck.setChecked(PreferenceHelper.isAirPlayEnabled());
        }
        if (airPlayDeviceNameEdit != null) {
            airPlayDeviceNameEdit.setText(PreferenceHelper.getAirPlayDeviceName());
        }
        updateAirPlayDetailsVisibility(PreferenceHelper.isAirPlayEnabled());

        // 高级/调试
        if (debugLogCheck != null) {
            debugLogCheck.setChecked(PreferenceHelper.isDebugLogEnabled());
        }

        // OSD settings
        if (osdTimeEnabledCheck != null) {
            osdTimeEnabledCheck.setChecked(PreferenceHelper.getOsdTimeEnabled());
        }
        if (osdFontSizeSeekBar != null) {
            int osdSize = PreferenceHelper.getOsdFontSize();
            osdFontSizeSeekBar.setProgress(Math.max(0, osdSize - 12));
            if (osdFontSizeValue != null) osdFontSizeValue.setText(String.valueOf(osdSize));
        }

        updateBackgroundUI();
        updateDlnaStatus();
        updateAirPlayStatus();
        isLoadingSettings = false;
    }

    private void loadClockFormatSetting() {
        String currentFormat = PreferenceHelper.getClockFormat();
        String[] presets = getResources().getStringArray(R.array.clock_format_presets);
        int matchIndex = presets.length;
        for (int i = 0; i < presets.length; i++) {
            if (presets[i].equals(currentFormat)) {
                matchIndex = i;
                break;
            }
        }
        if (clockFormatSpinner != null) {
            clockFormatSpinner.setSelection(matchIndex);
        }
        if (clockFormatEdit != null) {
            if (matchIndex >= presets.length) {
                clockFormatEdit.setVisibility(View.VISIBLE);
                clockFormatEdit.setText(currentFormat);
            } else {
                clockFormatEdit.setVisibility(View.GONE);
            }
        }
    }

    private void updateStyleVisibility(int style) {
        if (digitalFontLayout != null) {
            digitalFontLayout.setVisibility(style == ClockConfig.ClockStyle.DIGITAL.getValue() ? View.VISIBLE : View.GONE);
        }
        if (minimalSettingsLayout != null) {
            minimalSettingsLayout.setVisibility(style == ClockConfig.ClockStyle.MINIMAL.getValue() ? View.VISIBLE : View.GONE);
        }
        if (clockFormatLayout != null) {
            clockFormatLayout.setVisibility(style == ClockConfig.ClockStyle.DIGITAL.getValue() ? View.VISIBLE : View.GONE);
        }
    }

    private void setupFontSpinner(Spinner spinner, final FontSelectionListener listener) {
        if (spinner == null) return;
        final List<SystemFontHelper.FontItem> fonts = SystemFontHelper.getDefaultFonts(this);
        FontAdapter fontAdapter = new FontAdapter(fonts, "ABCabc 123 你好");
        spinner.setAdapter(fontAdapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < fonts.size() && listener != null) {
                    listener.onFontSelected(fonts.get(position).value);
                } else if (position == fonts.size()) {
                    // "更多字体…"
                    showAllFontsDialog(listener);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadFontSpinner(Spinner spinner, String currentValue) {
        if (spinner == null) return;
        int index = SystemFontHelper.findIndexInDefaultFonts(this, currentValue);
        if (index < 0) index = 0;
        spinner.setSelection(index);
    }

    private void updateClockColorDisplay() {
        if (clockColorBtn != null) {
            clockColorBtn.setBackgroundColor(currentClockColor);
        }
        if (clockColorValue != null) {
            clockColorValue.setText(String.format("#%06X", currentClockColor & 0xFFFFFF));
        }
    }

    /** updateBackgroundUI - 背景模式联动：根据模式只显示对应的行 */
    private void updateBackgroundUI() {
        int mode = PreferenceHelper.getBackgroundMode();

        // 颜色行：mode==0 显示
        if (bgColorRow != null) bgColorRow.setVisibility(mode == 0 ? View.VISIBLE : View.GONE);
        // 图片行：mode==1 显示
        if (bgImageRow != null) bgImageRow.setVisibility(mode == 1 ? View.VISIBLE : View.GONE);
        // 视频行：mode==2 显示
        if (bgVideoRow != null) bgVideoRow.setVisibility(mode == 2 ? View.VISIBLE : View.GONE);
        // 动态壁纸样式选择：mode==3 显示（内置壁纸 0~10）
        if (wallpaperTypeLabel != null) wallpaperTypeLabel.setVisibility(mode == 3 ? View.VISIBLE : View.GONE);
        if (wallpaperTypeSpinner != null) wallpaperTypeSpinner.setVisibility(mode == 3 ? View.VISIBLE : View.GONE);
        // 壁纸参数区域：mode==3 时绑定
        if (mode == 3) {
            refreshWallpaperParamSection();
        } else if (wallpaperParamsContainer != null) {
            wallpaperParamsContainer.setVisibility(View.GONE);
        }
        // Lua 壁纸管理区域：mode==4 显示
        if (luaWallpaperLayout != null) {
            luaWallpaperLayout.setVisibility(mode == 4 ? View.VISIBLE : View.GONE);
        }
        if (mode == 4) {
            refreshLuaWallpaperSpinner();
            updateLuaButtonStates();
        }

        // 更新路径文本
        String imagePath = PreferenceHelper.getBackgroundImagePath();
        if (bgImageName != null) {
            if (imagePath != null && !imagePath.isEmpty()) {
                File f = new File(imagePath);
                bgImageName.setText(f.getName());
            } else {
                bgImageName.setText("未选择");
            }
        }

        String videoPath = PreferenceHelper.getBackgroundVideoPath();
        if (bgVideoName != null) {
            if (videoPath != null && !videoPath.isEmpty()) {
                File f = new File(videoPath);
                bgVideoName.setText(f.getName());
            } else {
                bgVideoName.setText("未选择");
            }
        }
    }

    /** radioIdToBgMode - RadioGroup 选中 ID 转背景模式 */
    private int radioIdToBgMode(int checkedId) {
        if (checkedId == R.id.radio_bg_color) return 0;
        if (checkedId == R.id.radio_bg_image) return 1;
        if (checkedId == R.id.radio_bg_video) return 2;
        if (checkedId == R.id.radio_bg_wallpaper) return 3;
        if (checkedId == R.id.radio_bg_custom) return 4;
        return -1;
    }

    /** updateBurnInModeVisibility - 根据防烧屏方式显示/隐藏对应参数组 */
    private void updateBurnInModeVisibility(int mode) {
        if (layoutBurnInShift != null) {
            layoutBurnInShift.setVisibility(mode == 1 ? View.GONE : View.VISIBLE);
        }
        if (layoutBurnInBounce != null) {
            layoutBurnInBounce.setVisibility(mode == 1 ? View.VISIBLE : View.GONE);
        }
    }

    /** checkRadioByBgMode - 背景模式转 RadioGroup 选中 */
    private void checkRadioByBgMode(int mode) {
        int radioId;
        switch (mode) {
            case 0: radioId = R.id.radio_bg_color; break;
            case 1: radioId = R.id.radio_bg_image; break;
            case 2: radioId = R.id.radio_bg_video; break;
            case 3: radioId = R.id.radio_bg_wallpaper; break;
            case 4: radioId = R.id.radio_bg_custom; break;
            default: radioId = R.id.radio_bg_color; break;
        }
        bgModeRadioGroup.check(radioId);
    }

    /**
     * 刷新壁纸参数区域 - 根据当前 wallpaerType 获取参数定义并生成控件
     */
    private void refreshWallpaperParamSection() {
        if (wallpaperParamSection == null) return;
        int type = PreferenceHelper.getWallpaperType();
        try {
            ParamDef[] defs = WallpaperFactory.getParamDefs(type);
            if (defs != null && defs.length > 0) {
                // 使用 ParamDef[] 直接绑定（不创建完整渲染器）
                wallpaperParamSection.bindFromDefs(defs, type);
            } else if (wallpaperParamsContainer != null) {
                wallpaperParamsContainer.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get wallpaper params", e);
            if (wallpaperParamsContainer != null) {
                wallpaperParamsContainer.setVisibility(View.GONE);
            }
        }
    }

    /** Spinner 位置 → 壁纸类型 ID */
    private int spinnerPosToWallpaperType(int position) {
        if (position < WallpaperFactory.WALLPAPER_COUNT) {
            return position;
        }
        return WallpaperFactory.LUA_WALLPAPER_ID + (position - WallpaperFactory.WALLPAPER_COUNT);
    }

    /** 壁纸类型 ID → Spinner 位置 */
    private int wallpaperTypeToSpinnerPos(int type) {
        if (type < WallpaperFactory.LUA_WALLPAPER_ID) {
            return Math.min(type, WallpaperFactory.WALLPAPER_COUNT - 1);
        }
        return WallpaperFactory.WALLPAPER_COUNT + (type - WallpaperFactory.LUA_WALLPAPER_ID);
    }

    // ========== Lua 壁纸管理 ==========

    /** 刷新 Lua 壁纸下拉列表（内置 + 外部） */
    private void refreshLuaWallpaperSpinner() {
        if (luaWallpaperSpinner == null) return;
        luaScriptsCache = ScriptManager.getAvailableScripts();

        List<String> displayNames = new ArrayList<>();
        int builtinCount = 0;
        int customCount = 0;
        for (ScriptManager.ScriptInfo info : luaScriptsCache) {
            String prefix = ScriptManager.isBuiltin(info.path) ? "【内置】" : "【自定义】";
            displayNames.add(prefix + info.name + " (" + info.author + ")");
            if (ScriptManager.isBuiltin(info.path)) builtinCount++;
            else customCount++;
        }

        LuaWallpaperAdapter adapter = new LuaWallpaperAdapter(displayNames, luaScriptsCache);
        luaWallpaperSpinner.setAdapter(adapter);

        // 恢复上次选中的壁纸
        String savedPath = PreferenceHelper.getSelectedLuaScript();
        int selectedPos = 0;
        for (int i = 0; i < luaScriptsCache.size(); i++) {
            if (luaScriptsCache.get(i).path.equals(savedPath)) {
                selectedPos = i;
                break;
            }
        }
        luaWallpaperSpinner.setSelection(selectedPos);
    }

    /** 获取当前选中的 Lua 壁纸 ScriptInfo */
    private ScriptManager.ScriptInfo getSelectedLuaScript() {
        if (luaWallpaperSpinner == null || luaScriptsCache == null) return null;
        int pos = luaWallpaperSpinner.getSelectedItemPosition();
        if (pos >= 0 && pos < luaScriptsCache.size()) {
            return luaScriptsCache.get(pos);
        }
        return null;
    }

    /** 根据当前选中状态更新编辑/删除按钮的可用性 */
    private void updateLuaButtonStates() {
        ScriptManager.ScriptInfo info = getSelectedLuaScript();
        boolean isUserScript = info != null && !ScriptManager.isBuiltin(info.path);
        if (btnLuaEdit != null) {
            btnLuaEdit.setEnabled(isUserScript);
            btnLuaEdit.setAlpha(isUserScript ? 1.0f : 0.4f);
        }
        if (btnLuaDelete != null) {
            btnLuaDelete.setEnabled(isUserScript);
            btnLuaDelete.setAlpha(isUserScript ? 1.0f : 0.4f);
        }
        if (btnLuaCopy != null) {
            btnLuaCopy.setEnabled(info != null);
        }
    }

    /** 显示 Lua 编辑器对话框（null 表示新增，非 null 表示编辑） */
    private void showLuaEditorDialog(final ScriptManager.ScriptInfo existingInfo) {
        final boolean isNew = (existingInfo == null);
        String title = isNew ? "新增 Lua 壁纸" : "编辑 Lua 壁纸";

        // 对话框布局：名称输入框 + 脚本内容输入框
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        dialogLayout.setPadding(pad, pad, pad, pad);

        // 名称输入框
        final EditText nameEdit = new EditText(this);
        nameEdit.setHint("壁纸名称");
        nameEdit.setBackgroundResource(R.drawable.bg_settings_edittext);
        nameEdit.setPadding(dp(12), 0, dp(12), 0);
        nameEdit.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        nameEdit.setHintTextColor(ContextCompat.getColor(this, R.color.text_hint));
        nameEdit.setTextSize(14);
        nameEdit.setSingleLine(true);
        dialogLayout.addView(nameEdit, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        // 脚本内容输入框（多行）
        final EditText codeEdit = new EditText(this);
        codeEdit.setHint("输入 Lua 脚本内容...");
        codeEdit.setBackgroundResource(R.drawable.bg_settings_edittext);
        codeEdit.setPadding(dp(12), dp(8), dp(12), dp(8));
        codeEdit.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        codeEdit.setHintTextColor(ContextCompat.getColor(this, R.color.text_hint));
        codeEdit.setTextSize(13);
        codeEdit.setMinLines(8);
        codeEdit.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        codeEdit.setHorizontallyScrolling(false);
        LinearLayout.LayoutParams codeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(240));
        codeLp.topMargin = dp(8);
        dialogLayout.addView(codeEdit, codeLp);

        if (!isNew) {
            nameEdit.setText(existingInfo.name);
            codeEdit.setText(existingInfo.source);
        } else {
            // 新增时提供默认模板
            codeEdit.setText("-- @name: \u6211\u7684\u58c1\u7eb8\n" +
                    "-- @author: \u7528\u6237\n" +
                    "-- @param speed float 0.5 5.0 1.0 \"\u901f\u5ea6\"\n" +
                    "\n" +
                    "function init(width, height, p)\n" +
                    "end\n" +
                    "\n" +
                    "function draw(bridge, width, height, t, p)\n" +
                    "    bridge:drawColorARGB(255, 0, 0, 0)\n" +
                    "end\n");
        }

        // 滚动容器
        ScrollView scroll = new ScrollView(this);
        scroll.addView(dialogLayout);

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = nameEdit.getText().toString().trim();
                        String code = codeEdit.getText().toString().trim();
                        if (name.isEmpty()) {
                            Toast.makeText(SettingsActivity.this, "请输入壁纸名称", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (code.isEmpty()) {
                            Toast.makeText(SettingsActivity.this, "请输入脚本内容", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        try {
                            // 在脚本头部注入 name 元数据
                            String finalCode = ensureNameMeta(code, name);
                            String fileName;
                            if (isNew) {
                                fileName = sanitizeFileName(name) + ".lua";
                            } else {
                                // 编辑时保留原文件名
                                File oldFile = new File(existingInfo.path);
                                fileName = oldFile.getName();
                            }
                            String savedPath = ScriptManager.saveExternalScript(fileName, finalCode);
                            Toast.makeText(SettingsActivity.this, "已保存", Toast.LENGTH_SHORT).show();
                            // 刷新列表并选中刚保存的壁纸
                            refreshLuaWallpaperSpinner();
                            selectLuaScriptByPath(savedPath);
                        } catch (Exception e) {
                            Log.e(TAG, "保存 Lua 脚本失败", e);
                            Toast.makeText(SettingsActivity.this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 删除当前选中的用户自定义 Lua 壁纸 */
    private void deleteSelectedLuaScript() {
        final ScriptManager.ScriptInfo info = getSelectedLuaScript();
        if (info == null || ScriptManager.isBuiltin(info.path)) return;

        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除壁纸「" + info.name + "」吗？此操作不可恢复。")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        boolean ok = ScriptManager.deleteExternalScript(info.path);
                        if (ok) {
                            Toast.makeText(SettingsActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                            // 如果删除的是当前选中的，切换到第一个
                            PreferenceHelper.setSelectedLuaScript("builtin:particles");
                            refreshLuaWallpaperSpinner();
                        } else {
                            Toast.makeText(SettingsActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 复制当前选中的壁纸为用户自定义副本 */
    private void copySelectedLuaScript() {
        final ScriptManager.ScriptInfo info = getSelectedLuaScript();
        if (info == null) return;

        // 弹出对话框让用户确认副本名称
        final EditText nameEdit = new EditText(this);
        nameEdit.setText(info.name + "_副本");
        nameEdit.setBackgroundResource(R.drawable.bg_settings_edittext);
        nameEdit.setPadding(dp(12), 0, dp(12), 0);
        nameEdit.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        nameEdit.setTextSize(14);
        nameEdit.setSingleLine(true);
        nameEdit.selectAll();

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        layout.setPadding(pad, pad, pad, pad);
        layout.addView(nameEdit);

        new AlertDialog.Builder(this)
                .setTitle("复制壁纸")
                .setView(layout)
                .setPositiveButton("复制", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String newName = nameEdit.getText().toString().trim();
                        if (newName.isEmpty()) {
                            Toast.makeText(SettingsActivity.this, "请输入壁纸名称", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        try {
                            // 替换脚本中的 name 元数据
                            String newCode = ensureNameMeta(info.source, newName);
                            // 替换 author 为"用户"
                            newCode = newCode.replaceAll("-- @author:.*", "-- @author: 用户");
                            String fileName = sanitizeFileName(newName) + ".lua";
                            String savedPath = ScriptManager.saveExternalScript(fileName, newCode);
                            Toast.makeText(SettingsActivity.this, "已复制", Toast.LENGTH_SHORT).show();
                            refreshLuaWallpaperSpinner();
                            selectLuaScriptByPath(savedPath);
                        } catch (Exception e) {
                            Log.e(TAG, "复制 Lua 脚本失败", e);
                            Toast.makeText(SettingsActivity.this, "复制失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 在下拉框中选中指定路径的壁纸 */
    private void selectLuaScriptByPath(String path) {
        if (luaScriptsCache == null || luaWallpaperSpinner == null) return;
        for (int i = 0; i < luaScriptsCache.size(); i++) {
            if (luaScriptsCache.get(i).path.equals(path)) {
                luaWallpaperSpinner.setSelection(i);
                PreferenceHelper.setSelectedLuaScript(path);
                break;
            }
        }
    }

    /** 确保脚本中包含 @name 元数据 */
    private String ensureNameMeta(String code, String name) {
        if (code.contains("-- @name:")) {
            return code.replaceAll("-- @name:.*", "-- @name: " + name);
        }
        return "-- @name: " + name + "\n" + code;
    }

    /** 将名称转为安全的文件名 */
    private String sanitizeFileName(String name) {
        return name.replaceAll("[^\\w\\u4e00-\\u9fff_-]", "_");
    }

    /** Lua 壁纸下拉框适配器（分组显示内置/自定义） */
    private static class LuaWallpaperAdapter extends BaseAdapter {
        private final List<String> names;
        private final List<ScriptManager.ScriptInfo> scripts;

        LuaWallpaperAdapter(List<String> names, List<ScriptManager.ScriptInfo> scripts) {
            this.names = names;
            this.scripts = scripts;
        }

        @Override
        public int getCount() { return names.size(); }
        @Override
        public Object getItem(int position) { return names.get(position); }
        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return buildView(position, convertView, parent, false);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return buildView(position, convertView, parent, true);
        }

        private View buildView(int position, View convertView, ViewGroup parent, boolean isDropdown) {
            TextView tv;
            if (convertView instanceof TextView) {
                tv = (TextView) convertView;
            } else {
                tv = new TextView(parent.getContext());
                tv.setTextSize(14);
                tv.setSingleLine(true);
                tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                int padH = isDropdown ? 16 : 8;
                int padV = isDropdown ? 12 : 8;
                tv.setPadding(padH, padV, padH, padV);
            }
            tv.setText(names.get(position));
            if (isDropdown) {
                boolean isBuiltin = position < scripts.size() && ScriptManager.isBuiltin(scripts.get(position).path);
                tv.setTextColor(isBuiltin ? 0xFF80CBC4 : 0xFFFFFFFF);
            } else {
                tv.setTextColor(0xFFFFFFFF);
            }
            return tv;
        }
    }

    private void updateDlnaStatus() {
        if (statusText != null) {
            boolean enabled = PreferenceHelper.isDlnaEnabled();
            if (!enabled) {
                statusText.setText("服务状态: " + getString(R.string.dlna_disabled));
            } else {
                DlnaManager dlnaManager = App.getInstance().getDlnaManager();
                String ip = NetworkUtil.getLocalIpAddress(this);
                boolean running = dlnaManager.isRunning();
                int port = 0;
                if (running) {
                    try {
                        port = dlnaManager.getHttpPort();
                    } catch (Exception e) {
                        port = 0;
                    }
                }
                statusText.setText("服务状态: " + (running ? "运行中" : "已停止") +
                        "\nIP地址: " + ip +
                        (port > 0 ? "\n端口: " + port : ""));
            }
        }
    }

    private void updateDlnaDetailsVisibility(boolean enabled) {
        if (dlnaDetailsLayout != null) {
            dlnaDetailsLayout.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
        if (dlnaDisabledWarning != null) {
            dlnaDisabledWarning.setVisibility(enabled ? View.GONE : View.VISIBLE);
        }
    }

    private void updateAirPlayStatus() {
        if (airPlayStatusText != null) {
            boolean enabled = PreferenceHelper.isAirPlayEnabled();
            if (!enabled) {
                airPlayStatusText.setText("服务状态: " + getString(R.string.airplay_disabled));
            } else {
                AirPlayManager airPlayManager = App.getInstance().getAirPlayManager();
                String ip = NetworkUtil.getLocalIpAddress(this);
                boolean running = airPlayManager != null && airPlayManager.isRunning();
                airPlayStatusText.setText("服务状态: " + (running ? "运行中" : "已停止") +
                        "\nIP地址: " + ip +
                        "\n端口: 7000");
            }
        }
    }

    private void updateAirPlayDetailsVisibility(boolean enabled) {
        if (airPlayDetailsLayout != null) {
            airPlayDetailsLayout.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
    }

    private void setupVideoPlayerSpinner() {
        if (videoPlayerSpinner == null) return;

        PackageManager pm = getPackageManager();
        Intent testIntent = new Intent(Intent.ACTION_VIEW);
        testIntent.setDataAndType(Uri.parse("http://example.com/test.mp4"), "video/*");
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(testIntent, 0);

        videoPlayerNames.add("内置视频播放器");
        videoPlayerPackages.add("");

        String currentPkg = PreferenceHelper.getVideoPlayerPackage();
        int selectedIndex = 0;
        for (ResolveInfo info : resolveInfos) {
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            String appName = info.loadLabel(pm).toString();
            if (!videoPlayerPackages.contains(pkg)) {
                videoPlayerNames.add(appName);
                videoPlayerPackages.add(pkg);
                if (pkg.equals(currentPkg)) {
                    selectedIndex = videoPlayerPackages.size() - 1;
                }
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, videoPlayerNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        videoPlayerSpinner.setAdapter(adapter);
        videoPlayerSpinner.setSelection(selectedIndex);

        videoPlayerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < videoPlayerPackages.size()) {
                    PreferenceHelper.setVideoPlayerPackage(videoPlayerPackages.get(position));
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void showVideoPlayerSelectionDialog() {
        if (videoPlayerNames.isEmpty()) {
            Toast.makeText(this, "未检测到视频播放器", Toast.LENGTH_LONG).show();
            return;
        }

        final String[] names = new String[videoPlayerNames.size()];
        for (int i = 0; i < videoPlayerNames.size(); i++) {
            names[i] = videoPlayerNames.get(i);
        }

        new AlertDialog.Builder(this)
                .setTitle("选择视频播放器")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which < videoPlayerPackages.size()) {
                            String pkg = videoPlayerPackages.get(which);
                            PreferenceHelper.setVideoPlayerPackage(pkg);
                            if (videoPlayerSpinner != null) {
                                videoPlayerSpinner.setSelection(which);
                            }
                            Toast.makeText(SettingsActivity.this,
                                    "已选择: " + names[which], Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("稍后选择", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                if (requestCode == PICK_IMAGE) {
                    // 先验证图片可解码
                    if (!validateImageUri(uri)) {
                        Toast.makeText(this, "无法解析该图片文件，请选择有效的图片", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String savedPath = copyUriToInternalStorage(uri, "bg_image");
                    if (savedPath != null) {
                        PreferenceHelper.setBackgroundImagePath(savedPath);
                        updateBackgroundUI();
                    } else {
                        Toast.makeText(this, "保存图片失败", Toast.LENGTH_SHORT).show();
                    }
                } else if (requestCode == PICK_VIDEO) {
                    String savedPath = copyUriToInternalStorage(uri, "bg_video");
                    if (savedPath != null) {
                        PreferenceHelper.setBackgroundVideoPath(savedPath);
                        updateBackgroundUI();
                    } else {
                        Toast.makeText(this, "保存视频失败", Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error saving file", e);
                Toast.makeText(this, "选择文件失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /** validateImageUri - 验证图片 URI 可以被解码 */
    private boolean validateImageUri(Uri uri) {
        InputStream is = null;
        try {
            is = getContentResolver().openInputStream(uri);
            if (is == null) return false;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, opts);
            return opts.outWidth > 0 && opts.outHeight > 0;
        } catch (Exception e) {
            return false;
        } finally {
            try { if (is != null) is.close(); } catch (Exception e) { /* ignore */ }
        }
    }

    private String copyUriToInternalStorage(Uri uri, String prefix) {
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        try {
            inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) return null;

            String extension = "dat";
            String mimeType = getContentResolver().getType(uri);
            if (mimeType != null) {
                if (mimeType.contains("jpeg") || mimeType.contains("jpg")) extension = "jpg";
                else if (mimeType.contains("png")) extension = "png";
                else if (mimeType.contains("gif")) extension = "gif";
                else if (mimeType.contains("webp")) extension = "webp";
                else if (mimeType.contains("bmp")) extension = "bmp";
                else if (mimeType.contains("mp4")) extension = "mp4";
                else if (mimeType.contains("avi")) extension = "avi";
                else if (mimeType.contains("mkv")) extension = "mkv";
            }

            File dir = new File(getFilesDir(), "backgrounds");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, prefix + "_" + System.currentTimeMillis() + "." + extension);

            outputStream = new FileOutputStream(file);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
            return file.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "copyUriToInternalStorage failed", e);
            return null;
        } finally {
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private interface FontSelectionListener {
        void onFontSelected(String fontValue);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
