package com.dlnaclock.screensaver.wallpaper;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ScriptManager - Lua 壁纸脚本管理器
 * 扫描 /sdcard/DlnaClock/wallpapers/*.lua
 * 解析头部 @name/@author/@param 注释
 * 缓存编译后的脚本信息
 * 提供内置示例脚本
 */
public class ScriptManager {

    private static final String TAG = "ScriptManager";
    private static final String SCRIPTS_DIR = "DlnaClock/wallpapers";
    private static Context appContext;  // 由 App.onCreate 初始化

    /** 脚本信息 */
    public static class ScriptInfo {
        public final String path;
        public final String name;
        public final String author;
        public final ParamDef[] paramDefs;
        public final String source;

        public ScriptInfo(String path, String name, String author, ParamDef[] paramDefs, String source) {
            this.path = path;
            this.name = name;
            this.author = author;
            this.paramDefs = paramDefs;
            this.source = source;
        }
    }

    /** 初始化上下文（由 App.onCreate 调用） */
    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    /** 判断脚本路径是否为内置壁纸 */
    public static boolean isBuiltin(String path) {
        return path != null && path.startsWith("builtin:");
    }

    /** 根据路径查找 ScriptInfo（内置 + 外部） */
    public static ScriptInfo findScript(String path) {
        for (ScriptInfo info : getAvailableScripts()) {
            if (info.path.equals(path)) return info;
        }
        return null;
    }

    /** 获取外部脚本目录（优先使用应用专属目录，无需存储权限） */
    public static File getExternalScriptsDir() {
        if (appContext != null) {
            File dir = appContext.getExternalFilesDir("wallpapers");
            if (dir != null) return dir;
        }
        return new File(Environment.getExternalStorageDirectory(), SCRIPTS_DIR);
    }

    /** 删除外部脚本文件 */
    public static boolean deleteExternalScript(String path) {
        if (isBuiltin(path)) return false;
        File file = new File(path);
        return file.exists() && file.delete();
    }

    /** 保存外部脚本文件（新增或覆盖） */
    public static String saveExternalScript(String fileName, String content) throws Exception {
        File dir = getExternalScriptsDir();
        if (!dir.exists()) {
            boolean ok = dir.mkdirs();
            if (!ok && !dir.exists()) {
                throw new java.io.IOException("无法创建目录: " + dir.getAbsolutePath());
            }
        }
        File file = new File(dir, fileName);
        java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(
                new java.io.FileOutputStream(file), "UTF-8");
        writer.write(content);
        writer.flush();
        writer.close();
        return file.getAbsolutePath();
    }

    /** 获取所有可用脚本（内置 + 外部） */
    public static List<ScriptInfo> getAvailableScripts() {
        List<ScriptInfo> scripts = new ArrayList<>();

        // 内置示例脚本
        scripts.addAll(getBuiltinScripts());

        // 外部脚本
        scripts.addAll(scanExternalScripts());

        return scripts;
    }

    /** 获取内置示例脚本（仅保留第一个） */
    static List<ScriptInfo> getBuiltinScripts() {
        List<ScriptInfo> scripts = new ArrayList<>();

        // 示例 1：粒子流
        scripts.add(new ScriptInfo(
                "builtin:particles",
                "粒子流",
                "内置",
                new ParamDef[]{
                        new ParamDef("speed", "速度", 0.5f, 5f, 1.0f, 0.1f),
                        new ParamDef("count", "粒子数", 20, 200, 60),
                        new ParamDef("hue", "色调", 0, 360, 200),
                },
                BUILTIN_PARTICLES
        ));

        return scripts;
    }

    /** 扫描外部脚本目录 */
    static List<ScriptInfo> scanExternalScripts() {
        List<ScriptInfo> scripts = new ArrayList<>();
        try {
            File dir = getExternalScriptsDir();
            if (!dir.exists() || !dir.isDirectory()) return scripts;

            File[] files = dir.listFiles();
            if (files == null) return scripts;

            for (File file : files) {
                if (!file.getName().endsWith(".lua")) continue;
                try {
                    String source = readFile(file);
                    String name = extractMeta(source, "name", file.getName());
                    String author = extractMeta(source, "author", "Unknown");
                    ParamDef[] defs = LuaWallpaper.parseParamDefs(source);
                    scripts.add(new ScriptInfo(file.getAbsolutePath(), name, author, defs, source));
                } catch (Exception e) {
                    Log.w(TAG, "Failed to parse script: " + file.getName(), e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to scan scripts directory", e);
        }
        return scripts;
    }

    private static String readFile(File file) throws Exception {
        java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(file), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    /** 从脚本头部注释提取元数据 */
    static String extractMeta(String source, String key, String defaultVal) {
        String marker = "-- @" + key + ":";
        for (String line : source.split("\n")) {
            line = line.trim();
            if (line.startsWith(marker)) {
                return line.substring(marker.length()).trim();
            }
        }
        return defaultVal;
    }

    // === 内置示例脚本 ===

    /** 示例 1：粒子流 */
    private static final String BUILTIN_PARTICLES =
            "-- @name: 粒子流\n" +
            "-- @author: 内置\n" +
            "-- @param speed float 0.5 5.0 1.0 \"速度\"\n" +
            "-- @param count int 20 200 60 \"粒子数\"\n" +
            "-- @param hue int 0 360 200 \"色调\"\n" +
            "\n" +
            "local particles = {}\n" +
            "local w, h = 0, 0\n" +
            "local params = {}\n" +
            "\n" +
            "function init(width, height, p)\n" +
            "    w = width\n" +
            "    h = height\n" +
            "    params = p\n" +
            "    -- Pre-allocate particles\n" +
            "    for i = 1, 200 do\n" +
            "        particles[i] = {\n" +
            "            x = math.random() * w,\n" +
            "            y = math.random() * h,\n" +
            "            vx = (math.random() - 0.5) * 40,\n" +
            "            vy = (math.random() - 0.5) * 40 - 30,\n" +
            "            size = 1 + math.random() * 3,\n" +
            "            life = math.random()\n" +
            "        }\n" +
            "    end\n" +
            "end\n" +
            "\n" +
            "function draw(bridge, width, height, t, p)\n" +
            "    local speed = p.speed or 1\n" +
            "    local count = p.count or 60\n" +
            "    local hue = p.hue or 200\n" +
            "    \n" +
            "    bridge:drawColorARGB(255, 2, 2, 10)\n" +
            "    \n" +
            "    local dt = 0.033 * speed\n" +
            "    for i = 1, count do\n" +
            "        local pt = particles[i]\n" +
            "        pt.x = pt.x + pt.vx * dt\n" +
            "        pt.y = pt.y + pt.vy * dt\n" +
            "        if pt.x < -10 then pt.x = w + 10 end\n" +
            "        if pt.x > w + 10 then pt.x = -10 end\n" +
            "        if pt.y < -10 then pt.y = h + 10 end\n" +
            "        if pt.y > h + 10 then pt.y = -10 end\n" +
            "        \n" +
            "        local alpha = 80 + 175 * (0.5 + 0.5 * math.sin(t * 2 + pt.life * 6.28))\n" +
            "        local hVal = (hue + pt.life * 60) % 360\n" +
            "        local r, g, b = hsvToRgb(hVal, 0.8, 1.0)\n" +
            "        bridge:setFillColorARGB(alpha, r, g, b)\n" +
            "        bridge:drawCircle(pt.x, pt.y, pt.size)\n" +
            "    end\n" +
            "end\n" +
            "\n" +
            "function hsvToRgb(h, s, v)\n" +
            "    local c = v * s\n" +
            "    local x = c * (1 - math.abs((h / 60) % 2 - 1))\n" +
            "    local m = v - c\n" +
            "    local r, g, b = 0, 0, 0\n" +
            "    if h < 60 then r, g, b = c, x, 0\n" +
            "    elseif h < 120 then r, g, b = x, c, 0\n" +
            "    elseif h < 180 then r, g, b = 0, c, x\n" +
            "    elseif h < 240 then r, g, b = 0, x, c\n" +
            "    elseif h < 300 then r, g, b = x, 0, c\n" +
            "    else r, g, b = c, 0, x end\n" +
            "    return math.floor((r+m)*255), math.floor((g+m)*255), math.floor((b+m)*255)\n" +
            "end\n";
}
