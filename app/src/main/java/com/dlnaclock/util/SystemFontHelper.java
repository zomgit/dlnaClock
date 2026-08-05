package com.dlnaclock.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SystemFontHelper - 系统字体扫描与缓存
 * 扫描 /system/fonts/ 目录、assets/fonts/ 自定义字体、以及常见系统字体族名
 * 每个字体项包含名称、存储值和实际 Typeface 对象
 *
 * 字体族（font-family）机制：
 * - 字体配置值支持 "字体A,字体B,字体C" 形式，最多 3 项，按顺序优先渲染
 * - 渲染时对每个字符选择第一个支持该字符的字体，缺失字形依次向后回退
 * - 渲染器统一使用 measureTextWithFallback / drawTextWithFallback，与单字体测量/绘制 API 兼容
 */
public class SystemFontHelper {

    /** FontItem - 单个字体条目 */
    public static class FontItem {
        public String name;       // 显示名称
        public String value;      // 存储值（字体族名或 "file:path" 标识）
        public Typeface typeface; // 实际 Typeface 对象

        public FontItem(String name, String value, Typeface typeface) {
            this.name = name;
            this.value = value;
            this.typeface = typeface;
        }
    }

    /** ResolvedFont - 解析后的字体（含来源元数据，用于低版本字形支持判断） */
    private static class ResolvedFont {
        String value;        // 存储值
        Typeface typeface;   // 实际 Typeface
        boolean fromAssets;  // 是否来自 assets 自定义字体（拉丁字体，通常不含 CJK）

        ResolvedFont(String value, Typeface typeface, boolean fromAssets) {
            this.value = value;
            this.typeface = typeface;
            this.fromAssets = fromAssets;
        }
    }

    /** 字体族分隔符 */
    public static final String FONT_SEPARATOR = ",";
    /** 字体族最大字体数量 */
    public static final int MAX_FONT_FAMILY = 3;

    private static List<FontItem> cachedFonts = null;
    private static List<FontItem> cachedDefaultFonts = null;
    private static HashMap<String, Typeface> typefaceCache = new HashMap<>();
    /** assets 自定义字体值集合（低版本字形启发式判断依赖） */
    private static final Set<String> assetFontValues = new HashSet<>();
    /** 字形支持检测缓存（key: 字体值#字符码） */
    private static final HashMap<String, Boolean> glyphSupportCache = new HashMap<>();

    /** getAllFonts - 获取所有可用字体列表（首次调用时扫描并缓存） */
    public static List<FontItem> getAllFonts(Context context) {
        if (cachedFonts != null) return cachedFonts;

        // 使用 LinkedHashMap 去重（value 作为 key）
        Map<String, FontItem> fontMap = new LinkedHashMap<>();

        // 1. 常见系统字体族名（优先显示）
        addSystemFamily(fontMap, "系统默认", "default", Typeface.DEFAULT);
        addSystemFamily(fontMap, "Sans Serif", "sans-serif", Typeface.create("sans-serif", Typeface.NORMAL));
        addSystemFamily(fontMap, "Serif", "serif", Typeface.SERIF);
        addSystemFamily(fontMap, "Monospace", "monospace", Typeface.MONOSPACE);

        // 2. assets/fonts/ 中的自定义字体
        loadAssetFonts(context, fontMap);

        // 3. 扫描 /system/fonts/ 目录
        scanSystemFonts(fontMap, "/system/fonts/");
        scanSystemFonts(fontMap, "/system/font/");

        // 4. 转为排序列表
        List<FontItem> result = new ArrayList<>(fontMap.values());
        Collections.sort(result, new Comparator<FontItem>() {
            @Override
            public int compare(FontItem a, FontItem b) {
                // 系统默认排最前
                if ("default".equals(a.value)) return -1;
                if ("default".equals(b.value)) return 1;
                return a.name.compareToIgnoreCase(b.name);
            }
        });

        cachedFonts = result;
        return cachedFonts;
    }

    /** getDefaultFonts - 获取精简字体列表（系统默认 + APP附带字体） */
    public static List<FontItem> getDefaultFonts(Context context) {
        if (cachedDefaultFonts != null) return cachedDefaultFonts;

        Map<String, FontItem> fontMap = new LinkedHashMap<>();

        // 1. 系统默认
        addSystemFamily(fontMap, "系统默认", "default", Typeface.DEFAULT);

        // 2. assets/fonts/ 中的自定义字体
        loadAssetFonts(context, fontMap);

        cachedDefaultFonts = new ArrayList<>(fontMap.values());
        return cachedDefaultFonts;
    }

    /**
     * getFontsBuiltInFirst - 获取字体列表，按优先级排序：
     * 1. 系统默认
     * 2. assets 内置字体（字母序）
     * 3. 系统字体族名（sans-serif, serif, monospace）
     * 4. 扫描的系统字体（sys:xxx，字母序）
     */
    public static List<FontItem> getFontsBuiltInFirst(Context context) {
        List<FontItem> all = getAllFonts(context);
        List<FontItem> result = new ArrayList<>();
        // 1. 系统默认
        for (FontItem f : all) { if ("default".equals(f.value)) { result.add(f); break; } }
        // 2. assets 内置字体
        for (FontItem f : all) { if (assetFontValues.contains(f.value) && !result.contains(f)) result.add(f); }
        // 3. 系统字体族名
        for (FontItem f : all) { if (isSystemFamily(f.value) && !result.contains(f)) result.add(f); }
        // 4. 其余（sys: 扫描字体等）
        for (FontItem f : all) { if (!result.contains(f)) result.add(f); }
        return result;
    }

    /** isSystemFamily - 判断是否为系统字体族名（非 assets 非 sys: 扫描） */
    private static boolean isSystemFamily(String value) {
        return value != null && !value.equals("default")
                && !value.startsWith("sys:")
                && !assetFontValues.contains(value);
    }

    /** loadAssetFonts - 加载 assets/fonts/ 目录下的字体 */
    private static void loadAssetFonts(Context context, Map<String, FontItem> fontMap) {
        if (context == null) return;
        try {
            String[] assetFiles = context.getAssets().list("fonts");
            if (assetFiles != null) {
                for (String fileName : assetFiles) {
                    if (fileName.endsWith(".ttf") || fileName.endsWith(".otf")) {
                        String displayName = fileName.replace(".ttf", "").replace(".otf", "");
                        displayName = displayName.replace("-", " ");
                        displayName = capitalizeWords(displayName);
                        String value = displayName;
                        try {
                            Typeface tf = Typeface.createFromAsset(context.getAssets(), "fonts/" + fileName);
                            if (tf != null && !fontMap.containsKey(value)) {
                                fontMap.put(value, new FontItem(displayName, value, tf));
                                assetFontValues.add(value);
                            }
                        } catch (Exception e) {
                            // 加载失败跳过
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    /** scanSystemFonts - 扫描指定目录下的字体文件 */
    private static void scanSystemFonts(Map<String, FontItem> fontMap, String dirPath) {
        try {
            File dir = new File(dirPath);
            if (!dir.exists() || !dir.isDirectory()) return;
            File[] files = dir.listFiles();
            if (files == null) return;

            for (File file : files) {
                String name = file.getName().toLowerCase();
                if (!name.endsWith(".ttf") && !name.endsWith(".otf")) continue;
                // 跳过变体文件（Bold, Italic 等）以减少列表噪音
                if (name.contains("bold") || name.contains("italic") || name.contains("thin")
                        || name.contains("light") || name.contains("medium") || name.contains("black")
                        || name.contains("semibold") || name.contains("extralight")
                        || name.contains("-") && !name.endsWith("-regular.ttf")) {
                    continue;
                }

                String baseName = file.getName().replace(".ttf", "").replace(".otf", "");
                String displayName = baseName.replace("_", " ").replace("-", " ");
                displayName = capitalizeWords(displayName);

                // 用文件名（不含扩展名）作为存储值
                String value = "sys:" + baseName;

                if (!fontMap.containsKey(value)) {
                    try {
                        Typeface tf = Typeface.createFromFile(file);
                        if (tf != null) {
                            fontMap.put(value, new FontItem(displayName, value, tf));
                        }
                    } catch (Exception e) {
                        // 加载失败跳过
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    /** addSystemFamily - 添加系统字体族名 */
    private static void addSystemFamily(Map<String, FontItem> fontMap, String displayName, String value, Typeface typeface) {
        if (!fontMap.containsKey(value)) {
            fontMap.put(value, new FontItem(displayName, value, typeface));
        }
    }

    /** capitalizeWords - 首字母大写 */
    private static String capitalizeWords(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (char c : s.toCharArray()) {
            if (c == ' ' || c == '_' || c == '-') {
                nextUpper = true;
                sb.append(' ');
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString().trim();
    }

    // ========== 字体族（font-family）解析 ==========

    /** parseFontFamily - 解析字体族字符串为最多 MAX_FONT_FAMILY 个字体值 */
    public static String[] parseFontFamily(String fontFamily) {
        if (fontFamily == null || fontFamily.isEmpty()) {
            return new String[]{"default"};
        }
        String[] parts = fontFamily.split(FONT_SEPARATOR);
        List<String> list = new ArrayList<>();
        for (String part : parts) {
            String v = part.trim();
            if (!v.isEmpty() && !list.contains(v)) list.add(v);
            if (list.size() >= MAX_FONT_FAMILY) break;
        }
        if (list.isEmpty()) list.add("default");
        return list.toArray(new String[list.size()]);
    }

    /** resolveTypefaceList - 将字体族值解析为 Typeface 列表（按优先级顺序，解析失败的项跳过） */
    public static List<Typeface> resolveTypefaceList(Context context, String fontFamily) {
        List<ResolvedFont> resolved = resolveResolvedFonts(context, fontFamily);
        List<Typeface> result = new ArrayList<>();
        for (ResolvedFont rf : resolved) result.add(rf.typeface);
        return result;
    }

    /** resolveResolvedFonts - 解析字体族为带元数据的字体列表 */
    private static List<ResolvedFont> resolveResolvedFonts(Context context, String fontFamily) {
        List<ResolvedFont> result = new ArrayList<>();
        for (String value : parseFontFamily(fontFamily)) {
            if (result.size() >= MAX_FONT_FAMILY) break;
            ResolvedFont rf = resolveSingleFont(context, value);
            if (rf != null) result.add(rf);
        }
        if (result.isEmpty()) {
            result.add(new ResolvedFont("default", Typeface.DEFAULT, false));
        }
        return result;
    }

    /** resolveSingleFont - 解析单个字体值 */
    private static ResolvedFont resolveSingleFont(Context context, String fontValue) {
        if (fontValue == null || fontValue.isEmpty() || fontValue.equals("default")) {
            return new ResolvedFont("default", Typeface.DEFAULT, false);
        }
        // 1. 查缓存
        Typeface cached = typefaceCache.get(fontValue);
        if (cached != null) {
            return new ResolvedFont(fontValue, cached, assetFontValues.contains(fontValue));
        }
        // 2. 在已扫描字体列表中查找
        List<FontItem> fonts = getAllFonts(context);
        for (FontItem item : fonts) {
            if (item.value.equals(fontValue) && item.typeface != null) {
                typefaceCache.put(fontValue, item.typeface);
                return new ResolvedFont(fontValue, item.typeface, assetFontValues.contains(fontValue));
            }
        }
        // 3. 尝试系统族名回退
        try {
            Typeface tf = Typeface.create(fontValue, Typeface.NORMAL);
            if (tf != null) {
                typefaceCache.put(fontValue, tf);
                return new ResolvedFont(fontValue, tf, false);
            }
        } catch (Exception e) { /* ignore */ }

        return new ResolvedFont(fontValue, Typeface.DEFAULT, false);
    }

    /**
     * resolveTypeface - 统一的 Typeface 解析工厂（兼容单字体）
     * 字体族值（含逗号）时返回其中第一个可用字体
     * 渲染器应统一使用此方法，不再各自实现 createTypeface()
     */
    public static Typeface resolveTypeface(Context context, String fontValue) {
        List<ResolvedFont> resolved = resolveResolvedFonts(context, fontValue);
        return resolved.get(0).typeface;
    }

    /** getTypefaceByValue - 根据存储值获取对应的 Typeface（委托到 resolveTypeface） */
    public static Typeface getTypefaceByValue(Context context, String value) {
        return resolveTypeface(context, value);
    }

    /** getFontNames - 获取字体显示名称列表（供 Spinner 使用） */
    public static String[] getFontNames(Context context) {
        List<FontItem> fonts = getAllFonts(context);
        String[] names = new String[fonts.size()];
        for (int i = 0; i < fonts.size(); i++) {
            names[i] = fonts.get(i).name;
        }
        return names;
    }

    /** getFontValues - 获取字体存储值列表（供 Spinner 使用） */
    public static String[] getFontValues(Context context) {
        List<FontItem> fonts = getAllFonts(context);
        String[] values = new String[fonts.size()];
        for (int i = 0; i < fonts.size(); i++) {
            values[i] = fonts.get(i).value;
        }
        return values;
    }

    /** findIndexByValue - 在值列表中查找索引 */
    public static int findIndexByValue(Context context, String value) {
        String[] values = getFontValues(context);
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) return i;
        }
        return 0;
    }

    /** findIndexInDefaultFonts - 在精简字体列表中查找索引，找不到返回-1 */
    public static int findIndexInDefaultFonts(Context context, String value) {
        List<FontItem> fonts = getDefaultFonts(context);
        for (int i = 0; i < fonts.size(); i++) {
            if (fonts.get(i).value.equals(value)) return i;
        }
        return -1;
    }

    /** getFontDisplayName - 根据存储值获取显示名称 */
    public static String getFontDisplayName(Context context, String value) {
        if (value == null || value.isEmpty() || "default".equals(value)) return "系统默认";
        List<FontItem> fonts = getAllFonts(context);
        for (FontItem item : fonts) {
            if (item.value.equals(value)) return item.name;
        }
        return value;
    }

    /** formatFontFamilyDisplay - 将字体族值格式化为显示文本（按优先级用 → 连接） */
    public static String formatFontFamilyDisplay(Context context, String fontFamily) {
        String[] values = parseFontFamily(fontFamily);
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (sb.length() > 0) sb.append(" → ");
            sb.append(getFontDisplayName(context, v));
        }
        return sb.toString();
    }

    // ========== 带字体族回退的测量与绘制 ==========

    /**
     * supportsGlyph - 检测字体是否包含指定字符
     * API 23+ 使用 Paint.hasGlyph 精确检测；低版本使用启发式：
     * 宽度为 0 视为缺失；ASCII 字符宽度>0 视为支持；
     * 非 ASCII 字符对 assets 自定义字体视为缺失（拉丁字体），系统字体视为支持
     */
    private static boolean supportsGlyph(ResolvedFont rf, char c, Paint paint) {
        // 空白与控制字符视为支持（避免误回退影响排版）
        if (c < 0x20 || Character.isWhitespace(c)) return true;
        String key = rf.value + "#" + (int) c;
        Boolean cached = glyphSupportCache.get(key);
        if (cached != null) return cached;

        boolean supported;
        paint.setTypeface(rf.typeface);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            supported = paint.hasGlyph(String.valueOf(c));
        } else {
            float w = paint.measureText(String.valueOf(c));
            if (w <= 0f) {
                supported = false;
            } else if (c <= 0x7E) {
                supported = true;
            } else {
                supported = !rf.fromAssets;
            }
        }
        glyphSupportCache.put(key, supported);
        return supported;
    }

    /** buildRuns - 为文本每个字符选择第一个支持它的字体，返回字体索引数组 */
    private static int[] buildRuns(String text, int start, int end, Paint paint, List<ResolvedFont> fonts) {
        int[] runs = new int[end - start];
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            int fontIndex = 0;
            for (int f = 0; f < fonts.size(); f++) {
                if (supportsGlyph(fonts.get(f), c, paint)) {
                    fontIndex = f;
                    break;
                }
            }
            runs[i - start] = fontIndex;
        }
        return runs;
    }

    /** measureTextWithFallback - 带字体族回退的文本测量（与绘制宽度一致） */
    public static float measureTextWithFallback(Context context, String text, Paint paint, String fontFamily) {
        if (text == null || text.isEmpty()) return 0f;
        return measureTextWithFallback(context, text, 0, text.length(), paint, fontFamily);
    }

    /** measureTextWithFallback - 带字体族回退的子串测量 */
    public static float measureTextWithFallback(Context context, String text, int start, int end, Paint paint, String fontFamily) {
        if (text == null || start >= end) return 0f;
        List<ResolvedFont> fonts = resolveResolvedFonts(context, fontFamily);
        if (fonts.size() == 1) {
            paint.setTypeface(fonts.get(0).typeface);
            return paint.measureText(text, start, end);
        }
        int[] runs = buildRuns(text, start, end, paint, fonts);
        float total = 0f;
        int i = 0;
        int n = runs.length;
        while (i < n) {
            int f = runs[i];
            int j = i + 1;
            while (j < n && runs[j] == f) j++;
            paint.setTypeface(fonts.get(f).typeface);
            total += paint.measureText(text, start + i, start + j);
            i = j;
        }
        return total;
    }

    /**
     * drawTextWithFallback - 带字体族回退的文本绘制
     * 按字符选择字体分段绘制，自动适配 LEFT / CENTER / RIGHT 对齐方式
     */
    public static void drawTextWithFallback(Context context, Canvas canvas, String text, float x, float y, Paint paint, String fontFamily) {
        if (text == null || text.isEmpty()) return;
        List<ResolvedFont> fonts = resolveResolvedFonts(context, fontFamily);
        if (fonts.size() == 1) {
            paint.setTypeface(fonts.get(0).typeface);
            canvas.drawText(text, x, y, paint);
            return;
        }

        int[] runs = buildRuns(text, 0, text.length(), paint, fonts);
        Paint.Align align = paint.getTextAlign();
        int n = runs.length;

        // 计算总宽度，根据对齐方式确定起始 x
        float totalWidth = 0f;
        int i = 0;
        while (i < n) {
            int f = runs[i];
            int j = i + 1;
            while (j < n && runs[j] == f) j++;
            paint.setTypeface(fonts.get(f).typeface);
            totalWidth += paint.measureText(text, i, j);
            i = j;
        }
        float startX;
        if (align == Paint.Align.CENTER) startX = x - totalWidth / 2f;
        else if (align == Paint.Align.RIGHT) startX = x - totalWidth;
        else startX = x;

        // 逐段绘制（统一 LEFT 对齐，段间累加 x 偏移）
        paint.setTextAlign(Paint.Align.LEFT);
        float curX = startX;
        i = 0;
        while (i < n) {
            int f = runs[i];
            int j = i + 1;
            while (j < n && runs[j] == f) j++;
            paint.setTypeface(fonts.get(f).typeface);
            canvas.drawText(text, i, j, curX, y, paint);
            curX += paint.measureText(text, i, j);
            i = j;
        }
        paint.setTextAlign(align); // 恢复原对齐方式
    }
}
