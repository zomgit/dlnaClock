package com.dlnaclock.util;

import android.content.Context;
import android.graphics.Typeface;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SystemFontHelper - 系统字体扫描与缓存
 * 扫描 /system/fonts/ 目录、assets/fonts/ 自定义字体、以及常见系统字体族名
 * 每个字体项包含名称、存储值和实际 Typeface 对象
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

    private static List<FontItem> cachedFonts = null;
    private static List<FontItem> cachedDefaultFonts = null;
    private static HashMap<String, Typeface> typefaceCache = new HashMap<>();

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

    /**
     * resolveTypeface - 统一的 Typeface 解析工厂
     * 优先查缓存，然后在已扫描字体列表中查找，最后尝试系统族名回退
     * 渲染器应统一使用此方法，不再各自实现 createTypeface()
     */
    public static Typeface resolveTypeface(Context context, String fontValue) {
        if (fontValue == null || fontValue.isEmpty() || fontValue.equals("default")) {
            return Typeface.DEFAULT;
        }
        // 1. 查缓存
        Typeface cached = typefaceCache.get(fontValue);
        if (cached != null) return cached;

        // 2. 在已扫描字体列表中查找
        List<FontItem> fonts = getAllFonts(context);
        for (FontItem item : fonts) {
            if (item.value.equals(fontValue) && item.typeface != null) {
                typefaceCache.put(fontValue, item.typeface);
                return item.typeface;
            }
        }

        // 3. 尝试系统族名回退
        try {
            Typeface tf = Typeface.create(fontValue, Typeface.NORMAL);
            if (tf != null) {
                typefaceCache.put(fontValue, tf);
                return tf;
            }
        } catch (Exception e) { /* ignore */ }

        return Typeface.DEFAULT;
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
}
