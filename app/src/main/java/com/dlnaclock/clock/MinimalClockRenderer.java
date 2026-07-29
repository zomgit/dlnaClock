package com.dlnaclock.clock;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import com.dlnaclock.App;
import com.dlnaclock.util.SystemFontHelper;

import java.io.BufferedReader;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * MinimalClockRenderer - 自定义时钟渲染器（基于行配置的动态布局）
 * 遍历 config.minimalRows[]，根据每行的 ContentType 动态解析文本、字体、颜色
 * 第一个可见行为主行（主字号），其余行按 0.3x 比例缩小
 */
public class MinimalClockRenderer implements ClockRenderer {

    private static final String TAG = "MinimalClockRenderer";

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // 格式化缓存（避免每帧重建）
    private HashMap<String, SimpleDateFormat> formatterCache = new HashMap<>();

    // CPU 使用率缓存（避免每帧都读取）
    private float lastCpuUsage = -1f;
    private long lastCpuReadTime = 0;
    private long[] lastCpuTimes = null;

    // APP 启动时间戳
    private static long appStartTime = System.currentTimeMillis();

    /** setTabularNumbers - 为 Paint 启用等宽数字（tnum OpenType 特性） */
    private void setTabularNumbers(Paint paint) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            // "tnum" = tabular figures (monospaced digits), 避免数字宽度变化
            paint.setFontFeatureSettings("\"tnum\"");
        }
    }

    // 电量缓存
    private int cachedBatteryLevel = -1;
    private long lastBatteryUpdateTime = 0;
    private static final long BATTERY_UPDATE_INTERVAL = 30000; // 30秒缓存

    /** RowDrawData - 单行绘制数据 */
    private static class RowDrawData {
        String text;
        Typeface typeface;
        float size;
        int color;

        RowDrawData(String text, Typeface typeface, float size, int color) {
            this.text = text;
            this.typeface = typeface;
            this.size = size;
            this.color = color;
        }
    }

    @Override
    public void draw(Canvas canvas, int width, int height, Calendar time, ClockConfig config) {
        // === 遍历行配置，收集可见行 ===
        List<RowDrawData> visibleRows = new ArrayList<>();
        MinimalRowConfig[] rows = config.getMinimalRows();

        for (int i = 0; i < 3; i++) {
            MinimalRowConfig row = rows[i];
            if (row == null || row.getContentType() == MinimalRowConfig.ContentType.NONE) continue;

            String text = resolveRowText(row, time, config);
            if (text == null || text.isEmpty()) continue;

            Typeface tf = SystemFontHelper.resolveTypeface(App.getInstance(), row.getFontName());

            // 第一个可见行使用主字号，其余行使用 0.3x
            float size;
            if (visibleRows.isEmpty()) {
                size = -1; // placeholder, computed below
            } else {
                size = -1; // placeholder
            }
            visibleRows.add(new RowDrawData(text, tf, size, row.getColor()));
        }

        if (visibleRows.isEmpty()) return;

        // === 基于第一行计算主字号 ===
        RowDrawData firstRow = visibleRows.get(0);
        paint.setTypeface(firstRow.typeface);
        float orientScale = ClockConfig.getFontScaleForOrientation(width, height, ClockConfig.ClockStyle.MINIMAL);
        config.setFontScale(orientScale);

        // 用参考字符串计算字号，避免跳动
        String refStr = ClockConfig.generateReferenceString(rows[0] != null ? rows[0].getFormat() : "HH:mm:ss");
        int mainFontSize = config.getFontSizePx(width, refStr, paint);

        // 设置实际字号
        firstRow.size = mainFontSize;
        for (int i = 1; i < visibleRows.size(); i++) {
            visibleRows.get(i).size = mainFontSize * 0.3f;
        }

        // === 计算布局 ===
        float lineSpacing = mainFontSize * 0.3f;
        float totalHeight = 0;
        float maxWidth = 0;

        for (int i = 0; i < visibleRows.size(); i++) {
            RowDrawData rd = visibleRows.get(i);
            if (i > 0) totalHeight += lineSpacing * 0.6f;
            totalHeight += rd.size;

            // 用参考串测量宽度，确保布局框架稳定不随文本内容抖动
            // 第一行复用字号计算的 refStr，其余行用通用参考串
            paint.setTextSize(rd.size);
            paint.setTypeface(rd.typeface);
            setTabularNumbers(paint); // 在测量时启用等宽数字，确保宽度准确
            String measureRef = (i == 0) ? refStr : "00:00:00";
            float w = paint.measureText(measureRef);
            if (w > maxWidth) maxWidth = w;
        }

        // 基于用户位置计算坐标
        float blockLeft = config.getPositionX() * (width - maxWidth);
        float blockTop = config.getPositionY() * (height - totalHeight);
        // 居中点基于参考串宽度，位置稳定不随实际文本变化
        float centerX = blockLeft + maxWidth / 2f;

        // === 检测第一行是否为时间行（分钟锚点策略仅对时间行生效） ===
        boolean firstRowIsTime = rows[0] != null
                && rows[0].getContentType() == MinimalRowConfig.ContentType.TIME;

        // === 绘制每行 ===
        float currentY = blockTop;
        for (int i = 0; i < visibleRows.size(); i++) {
            RowDrawData rd = visibleRows.get(i);
            if (i > 0) currentY += lineSpacing * 0.6f;

            paint.setColor(rd.color);
            paint.setTextSize(rd.size);
            paint.setTypeface(rd.typeface);
            setTabularNumbers(paint); // 启用等宽数字
            paint.setAlpha(255);
            paint.setFakeBoldText(false);

            // 防抖动策略：时间行含时分秒（两个冒号）时以分钟段为锚点居中，
            // 即让 mm 段中心对准区域中心；HH:mm 宽度在一分钟内恒定，锚点不随秒跳动。
            // 其它行直接居中。
            float drawX;
            int c1 = (i == 0 && firstRowIsTime) ? rd.text.indexOf(':') : -1;
            int c2 = (c1 > 0) ? rd.text.lastIndexOf(':') : -1;
            if (c1 > 0 && c2 > c1) {
                paint.setTextAlign(Paint.Align.LEFT);
                float prefixW = paint.measureText(rd.text, 0, c1 + 1);  // "HH:"
                float minuteW = paint.measureText(rd.text, c1 + 1, c2); // "mm"
                drawX = centerX - prefixW - minuteW / 2f;
            } else {
                paint.setTextAlign(Paint.Align.CENTER);
                drawX = centerX;
            }

            float baseline = currentY + rd.size * 0.85f;
            canvas.drawText(rd.text, drawX, baseline, paint);
            currentY += rd.size;
        }
    }

    /**
     * resolveRowText - 根据行配置的内容类型解析显示文本
     */
    private String resolveRowText(MinimalRowConfig row, Calendar time, ClockConfig config) {
        switch (row.getContentType()) {
            case TIME:
                return formatTime(row, time);
            case DATE:
                return formatDate(row, time);
            case STATUS:
                return resolveStatusLine(row, time);
            case CUSTOM:
                String ct = row.getCustomText();
                return (ct != null && !ct.isEmpty()) ? ct : "";
            default:
                return "";
        }
    }

    /** formatTime - 格式化时间（支持12/24小时制） */
    private String formatTime(MinimalRowConfig row, Calendar time) {
        String format = row.getFormat();
        if (format == null || format.isEmpty()) format = "HH:mm:ss";

        // 根据12/24小时制调整格式
        if (row.isUse12Hour()) {
            format = format.replace("HH", "hh").replace("H", "h");
            if (!format.contains("a")) format += " a";
        } else {
            format = format.replace("hh", "HH").replace("h", "H");
            format = format.replace(" a", "").replace("a", "");
        }

        SimpleDateFormat sdf = getFormatter(format);
        return sdf != null ? sdf.format(time.getTime()) : "00:00:00";
    }

    /** formatDate - 格式化日期 */
    private String formatDate(MinimalRowConfig row, Calendar time) {
        String format = row.getFormat();
        if (format == null || format.isEmpty()) format = "yyyy-MM-dd";
        SimpleDateFormat sdf = getFormatter(format);
        return sdf != null ? sdf.format(time.getTime()) : "0000-00-00";
    }

    /**
     * resolveStatusLine - 解析状态信息轮播行
     */
    private String resolveStatusLine(MinimalRowConfig row, Calendar time) {
        int items = row.getStatusItems();
        if (items == 0) return "";

        List<Integer> selectedItems = new ArrayList<>();
        if ((items & ClockConfig.STATUS_CPU) != 0) selectedItems.add(ClockConfig.STATUS_CPU);
        if ((items & ClockConfig.STATUS_BATTERY) != 0) selectedItems.add(ClockConfig.STATUS_BATTERY);
        if ((items & ClockConfig.STATUS_APP_TIME) != 0) selectedItems.add(ClockConfig.STATUS_APP_TIME);
        if ((items & ClockConfig.STATUS_DEV_TIME) != 0) selectedItems.add(ClockConfig.STATUS_DEV_TIME);
        if ((items & ClockConfig.STATUS_CUSTOM) != 0) selectedItems.add(ClockConfig.STATUS_CUSTOM);

        if (selectedItems.isEmpty()) return "";

        int rotateInterval = row.getRotateInterval();
        if (rotateInterval < 1) rotateInterval = 5;
        long seconds = time.getTimeInMillis() / 1000;
        int currentIndex = (int) ((seconds / rotateInterval) % selectedItems.size());
        int currentItem = selectedItems.get(currentIndex);

        switch (currentItem) {
            case ClockConfig.STATUS_CPU:
                return "CPU: " + String.format(Locale.getDefault(), "%.1f%%", getCpuUsage());
            case ClockConfig.STATUS_BATTERY:
                return "电量: " + getBatteryLevel() + "%";
            case ClockConfig.STATUS_APP_TIME:
                return "已运行: " + formatDuration(System.currentTimeMillis() - appStartTime);
            case ClockConfig.STATUS_DEV_TIME:
                return "开机: " + formatDuration(SystemClock.elapsedRealtime());
            case ClockConfig.STATUS_CUSTOM:
                String customText = row.getCustomText();
                return (customText != null && !customText.isEmpty()) ? customText : "";
            default:
                return "";
        }
    }

    /** getFormatter - 从缓存获取或创建 SimpleDateFormat */
    private SimpleDateFormat getFormatter(String format) {
        SimpleDateFormat sdf = formatterCache.get(format);
        if (sdf == null) {
            try {
                sdf = new SimpleDateFormat(format, Locale.getDefault());
                formatterCache.put(format, sdf);
            } catch (Exception e) {
                return null;
            }
        }
        return sdf;
    }

    /** getCpuUsage - 获取 CPU 使用率（每 3 秒刷新一次） */
    private float getCpuUsage() {
        long now = System.currentTimeMillis();
        if (now - lastCpuReadTime < 3000 && lastCpuUsage >= 0) {
            return lastCpuUsage;
        }
        lastCpuReadTime = now;

        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"));
            String line = reader.readLine();
            reader.close();
            if (line == null) return lastCpuUsage >= 0 ? lastCpuUsage : 0f;

            String[] parts = line.split("\\s+");
            if (parts.length < 8) return lastCpuUsage >= 0 ? lastCpuUsage : 0f;

            long[] currentTimes = new long[7];
            for (int i = 0; i < 7; i++) {
                currentTimes[i] = Long.parseLong(parts[i + 1]);
            }

            if (lastCpuTimes != null) {
                long totalDiff = 0;
                long idleDiff = currentTimes[3] - lastCpuTimes[3];
                for (int i = 0; i < 7; i++) {
                    totalDiff += currentTimes[i] - lastCpuTimes[i];
                }
                if (totalDiff > 0) {
                    lastCpuUsage = (1f - (float) idleDiff / totalDiff) * 100f;
                }
            }
            lastCpuTimes = currentTimes;
        } catch (Exception e) {
            if (lastCpuUsage < 0) lastCpuUsage = 0f;
        }
        return lastCpuUsage >= 0 ? lastCpuUsage : 0f;
    }

    /** 获取电池电量百分比（三层备选：Sticky Intent → BatteryManager API → 系统文件） */
    private int getBatteryLevel() {
        // 缓存检查：30秒内不重复读取
        long now = System.currentTimeMillis();
        if (cachedBatteryLevel >= 0 && (now - lastBatteryUpdateTime) < BATTERY_UPDATE_INTERVAL) {
            return cachedBatteryLevel;
        }

        // ========== 方案1：Sticky Intent（兼容所有Android版本，无需特殊权限） ==========
        // 使用 ACTION_BATTERY_CHANGED 粘性广播（null Receiver 表示不真正注册）
        try {
            Context context = App.getInstance();
            if (context != null) {
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = context.registerReceiver(null, ifilter);
                if (batteryStatus != null) {
                    int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    if (level >= 0 && scale > 0) {
                        int batteryPct = (level * 100) / scale;
                        if (batteryPct >= 0 && batteryPct <= 100) {
                            Log.d(TAG, "Sticky Intent获取电量: " + batteryPct + "%");
                            cachedBatteryLevel = batteryPct;
                            lastBatteryUpdateTime = now;
                            return batteryPct;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Sticky Intent获取电量失败", e);
        }

        // ========== 方案2：BatteryManager.getIntProperty（API 21+） ==========
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                Context context = App.getInstance();
                if (context != null) {
                    BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
                    if (bm != null) {
                        int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                        if (level >= 0 && level <= 100) {
                            Log.d(TAG, "BatteryManager获取电量: " + level + "%");
                            cachedBatteryLevel = level;
                            lastBatteryUpdateTime = now;
                            return level;
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "BatteryManager API获取电量失败", e);
            }
        }

        // ========== 方案3：系统文件多路径 fallback ==========
        String[] paths = {
            "/sys/class/power_supply/battery/capacity",
            "/sys/class/power_supply/BAT0/capacity",
            "/sys/class/power_supply/batt/capacity"
        };
        for (String path : paths) {
            try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
                String line = reader.readLine();
                if (line != null) {
                    int value = Integer.parseInt(line.trim());
                    if (value >= 0 && value <= 100) {
                        Log.d(TAG, "系统文件获取电量 (" + path + "): " + value + "%");
                        cachedBatteryLevel = value;
                        lastBatteryUpdateTime = now;
                        return value;
                    }
                }
            } catch (Exception e) {
                // 继续尝试下一个路径
            }
        }

        Log.w(TAG, "所有电量获取方案都失败，返回0");
        return 0;
    }

    /** formatDuration - 格式化运行时长为可读字符串 */
    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d时%02d分%02d秒", hours, minutes, secs);
        } else {
            return String.format(Locale.getDefault(), "%d分%02d秒", minutes, secs);
        }
    }
}
