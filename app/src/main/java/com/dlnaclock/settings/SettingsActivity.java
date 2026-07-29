package com.dlnaclock.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.dlnaclock.App;
import com.dlnaclock.R;
import com.dlnaclock.dlna.DlnaManager;
import com.dlnaclock.util.NetworkUtil;
import com.dlnaclock.util.PreferenceHelper;

public class SettingsActivity extends AppCompatActivity {

    private static final int PICK_IMAGE = 100;
    private static final int PICK_VIDEO = 101;

    // Clock settings
    private Spinner clockStyleSpinner;
    private SeekBar fontSizeSeekBar;
    private TextView fontSizeValue;
    private SeekBar posXSeekBar;
    private SeekBar posYSeekBar;
    private TextView posXValue;
    private TextView posYValue;
    private CheckBox showSecondsCheck;
    private CheckBox showDateCheck;

    // Anti burn-in settings
    private CheckBox antiBurnInCheck;
    private CheckBox pixelShiftCheck;
    private SeekBar burnInIntervalSeekBar;
    private TextView burnInIntervalValue;

    // Background settings
    private Spinner bgModeSpinner;
    private Button bgColorBtn;
    private Button bgImageBtn;
    private Button bgVideoBtn;
    private TextView bgImageName;
    private TextView bgVideoName;

    // DLNA settings
    private EditText deviceNameEdit;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.settings);
        }

        initViews();
        loadSettings();
    }

    private void initViews() {
        // Clock style
        clockStyleSpinner = (Spinner) findViewById(R.id.spinner_clock_style);
        if (clockStyleSpinner != null) {
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                    R.array.clock_style_names, android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            clockStyleSpinner.setAdapter(adapter);
        }

        // Font size
        fontSizeSeekBar = (SeekBar) findViewById(R.id.seekbar_font_size);
        fontSizeValue = (TextView) findViewById(R.id.tv_font_size_value);
        if (fontSizeSeekBar != null) {
            fontSizeSeekBar.setMax(200);
            fontSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int size = progress + 20;
                    if (fontSizeValue != null) fontSizeValue.setText(size + "px");
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    PreferenceHelper.setClockFontSize(seekBar.getProgress() + 20);
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

        // Show seconds / date
        showSecondsCheck = (CheckBox) findViewById(R.id.check_show_seconds);
        showDateCheck = (CheckBox) findViewById(R.id.check_show_date);
        if (showSecondsCheck != null) {
            showSecondsCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    PreferenceHelper.setShowSeconds(isChecked);
                }
            });
        }
        if (showDateCheck != null) {
            showDateCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    PreferenceHelper.setShowDate(isChecked);
                }
            });
        }

        // Anti burn-in
        antiBurnInCheck = (CheckBox) findViewById(R.id.check_anti_burn_in);
        pixelShiftCheck = (CheckBox) findViewById(R.id.check_pixel_shift);
        burnInIntervalSeekBar = (SeekBar) findViewById(R.id.seekbar_burn_in_interval);
        burnInIntervalValue = (TextView) findViewById(R.id.tv_burn_in_interval_value);
        if (antiBurnInCheck != null) {
            antiBurnInCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    PreferenceHelper.setAntiBurnInEnabled(isChecked);
                }
            });
        }
        if (pixelShiftCheck != null) {
            pixelShiftCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    PreferenceHelper.setPixelShiftEnabled(isChecked);
                }
            });
        }
        if (burnInIntervalSeekBar != null) {
            burnInIntervalSeekBar.setMax(110); // 10-120 seconds
            burnInIntervalSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int interval = progress + 10;
                    if (burnInIntervalValue != null) burnInIntervalValue.setText(interval + "s");
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    PreferenceHelper.setBurnInInterval(seekBar.getProgress() + 10);
                }
            });
        }

        // Background
        bgModeSpinner = (Spinner) findViewById(R.id.spinner_bg_mode);
        bgColorBtn = (Button) findViewById(R.id.btn_bg_color);
        bgImageBtn = (Button) findViewById(R.id.btn_bg_image);
        bgVideoBtn = (Button) findViewById(R.id.btn_bg_video);
        bgImageName = (TextView) findViewById(R.id.tv_bg_image_name);
        bgVideoName = (TextView) findViewById(R.id.tv_bg_video_name);

        if (bgModeSpinner != null) {
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                    R.array.bg_mode_names, android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            bgModeSpinner.setAdapter(adapter);
            bgModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    PreferenceHelper.setBackgroundMode(position);
                    updateBackgroundUI();
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
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

        // DLNA
        deviceNameEdit = (EditText) findViewById(R.id.edit_device_name);
        statusText = (TextView) findViewById(R.id.tv_dlna_status);

        Button btnApply = (Button) findViewById(R.id.btn_apply_dlna);
        if (btnApply != null) {
            btnApply.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyDlnaSettings();
                }
            });
        }
    }

    private void loadSettings() {
        if (clockStyleSpinner != null) {
            clockStyleSpinner.setSelection(PreferenceHelper.getClockStyle());
        }
        if (fontSizeSeekBar != null) {
            fontSizeSeekBar.setProgress(PreferenceHelper.getClockFontSize() - 20);
        }
        if (posXSeekBar != null) {
            posXSeekBar.setProgress((int) (PreferenceHelper.getClockPositionX() * 100));
        }
        if (posYSeekBar != null) {
            posYSeekBar.setProgress((int) (PreferenceHelper.getClockPositionY() * 100));
        }
        if (showSecondsCheck != null) {
            showSecondsCheck.setChecked(PreferenceHelper.getShowSeconds());
        }
        if (showDateCheck != null) {
            showDateCheck.setChecked(PreferenceHelper.getShowDate());
        }
        if (antiBurnInCheck != null) {
            antiBurnInCheck.setChecked(PreferenceHelper.isAntiBurnInEnabled());
        }
        if (pixelShiftCheck != null) {
            pixelShiftCheck.setChecked(PreferenceHelper.isPixelShiftEnabled());
        }
        if (burnInIntervalSeekBar != null) {
            burnInIntervalSeekBar.setProgress(PreferenceHelper.getBurnInInterval() - 10);
        }
        if (bgModeSpinner != null) {
            bgModeSpinner.setSelection(PreferenceHelper.getBackgroundMode());
        }
        if (deviceNameEdit != null) {
            deviceNameEdit.setText(PreferenceHelper.getDeviceName());
        }

        updateBackgroundUI();
        updateDlnaStatus();
    }

    private void updateBackgroundUI() {
        int mode = PreferenceHelper.getBackgroundMode();
        if (bgColorBtn != null) bgColorBtn.setVisibility(mode == 0 ? View.VISIBLE : View.GONE);
        if (bgImageBtn != null) bgImageBtn.setVisibility(mode == 1 ? View.VISIBLE : View.GONE);
        if (bgVideoBtn != null) bgVideoBtn.setVisibility(mode == 2 ? View.VISIBLE : View.GONE);

        String imagePath = PreferenceHelper.getBackgroundImagePath();
        if (bgImageName != null) {
            bgImageName.setText(imagePath != null && !imagePath.isEmpty() ? imagePath : "未选择");
        }

        String videoPath = PreferenceHelper.getBackgroundVideoPath();
        if (bgVideoName != null) {
            bgVideoName.setText(videoPath != null && !videoPath.isEmpty() ? videoPath : "未选择");
        }
    }

    private void updateDlnaStatus() {
        if (statusText != null) {
            DlnaManager dlnaManager = App.getInstance().getDlnaManager();
            String ip = NetworkUtil.getLocalIpAddress();
            boolean running = dlnaManager.isRunning();
            statusText.setText("服务状态: " + (running ? "运行中" : "已停止") +
                    "\nIP地址: " + ip +
                    "\n端口: 49152");
        }
    }

    private void applyDlnaSettings() {
        if (deviceNameEdit != null) {
            String name = deviceNameEdit.getText().toString().trim();
            if (!name.isEmpty()) {
                PreferenceHelper.setDeviceName(name);
                App.getInstance().getDlnaManager().getDevice().setFriendlyName(name);
                App.getInstance().getDlnaManager().restart();
                Toast.makeText(this, "DLNA设置已应用", Toast.LENGTH_SHORT).show();
                updateDlnaStatus();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                String path = uri.getPath();
                if (requestCode == PICK_IMAGE) {
                    PreferenceHelper.setBackgroundImagePath(path);
                    updateBackgroundUI();
                } else if (requestCode == PICK_VIDEO) {
                    PreferenceHelper.setBackgroundVideoPath(path);
                    updateBackgroundUI();
                }
            } catch (Exception e) {
                Toast.makeText(this, "选择文件失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
