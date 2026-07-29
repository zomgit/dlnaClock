package com.dlnaclock.screensaver.wallpaper;

import android.graphics.Canvas;
import android.os.Bundle;
import android.util.Log;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * LuaWallpaper - Lua 脚本壁纸渲染器
 * 实现 WallpaperRenderer，加载并执行 Lua 脚本
 * 解析脚本头部的 @param 声明，生成 ParamDef 数组
 * 安全监控：指令计数限制，崩溃回退黑色
 */
public class LuaWallpaper implements WallpaperRenderer {

    private static final String TAG = "LuaWallpaper";

    private final String scriptPath;
    private final String scriptSource;
    private final ParamDef[] paramDefs;

    private Globals globals;
    private LuaFunction luaInit;
    private LuaFunction luaDraw;
    private LuaFunction luaRelease;
    private LuaTable paramsTable;
    private LuaCanvasBridge bridge;

    private int width;
    private int height;
    private boolean initialized = false;
    private boolean error = false;
    private String errorMessage = null;
    private LuaFunction hookResetter; // 每帧重置指令计数的预编译函数

    /**
     * 从脚本文件路径构造 LuaWallpaper
     */
    public LuaWallpaper(String scriptPath) {
        this.scriptPath = scriptPath;
        this.scriptSource = loadScript(scriptPath);
        this.paramDefs = parseParamDefs(scriptSource);
    }

    /**
     * 从源代码字符串构造 LuaWallpaper（用于内置脚本）
     */
    public LuaWallpaper(String source, ParamDef[] defs) {
        this.scriptPath = null;
        this.scriptSource = source;
        this.paramDefs = defs;
    }

    private String loadScript(String path) {
        try {
            File file = new File(path);
            if (!file.exists()) return null;
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to load script: " + path, e);
            return null;
        }
    }

    /**
     * 从脚本源代码头部解析 @param 声明，生成 ParamDef[]
     */
    static ParamDef[] parseParamDefs(String source) {
        if (source == null) return new ParamDef[0];
        List<ParamDef> defs = new ArrayList<>();
        String[] lines = source.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.startsWith("-- @param ")) continue;
            // 格式: -- @param key type min max default "label"
            // 或:   -- @param key type default "label"
            // 或:   -- @param key color "label"
            // 或:   -- @param key select "opt1,opt2,opt3" default "label"
            // 或:   -- @param key bool default "label"
            try {
                String content = line.substring("-- @param ".length()).trim();
                ParamDef def = parseSingleParam(content);
                if (def != null) defs.add(def);
            } catch (Exception e) {
                Log.w(TAG, "Failed to parse param line: " + line, e);
            }
        }
        return defs.toArray(new ParamDef[0]);
    }

    private static ParamDef parseSingleParam(String content) {
        // Split by spaces, but preserve quoted strings
        List<String> parts = splitRespectingQuotes(content);
        if (parts.size() < 2) return null;

        String key = parts.get(0);
        String type = parts.get(1).toLowerCase();

        switch (type) {
            case "float": {
                // @param key float min max default "label"
                if (parts.size() < 5) return null;
                float min = Float.parseFloat(parts.get(2));
                float max = Float.parseFloat(parts.get(3));
                float defVal = Float.parseFloat(parts.get(4));
                String label = parts.size() > 5 ? unquote(parts.get(5)) : key;
                return new ParamDef(key, label, min, max, defVal, Math.max(0.01f, (max - min) / 100f));
            }
            case "int": {
                // @param key int min max default "label"
                if (parts.size() < 5) return null;
                int min = Integer.parseInt(unquote(parts.get(2)));
                int max = Integer.parseInt(unquote(parts.get(3)));
                int defVal = Integer.parseInt(unquote(parts.get(4)));
                String label = parts.size() > 5 ? unquote(parts.get(5)) : key;
                return new ParamDef(key, label, min, max, defVal);
            }
            case "color": {
                // @param key color "label"
                String label = parts.size() > 2 ? unquote(parts.get(2)) : key;
                return new ParamDef(key, label, 0xFFFFFFFF);
            }
            case "bool": {
                // @param key bool default "label"
                boolean defVal = parts.size() > 2 && (parts.get(2).equals("1") || parts.get(2).equalsIgnoreCase("true"));
                String label = parts.size() > 3 ? unquote(parts.get(3)) : key;
                return new ParamDef(key, label, defVal);
            }
            case "select": {
                // @param key select "opt1,opt2,opt3" defaultIndex "label"
                if (parts.size() < 4) return null;
                String optsStr = parts.size() > 2 ? parts.get(2) : "";
                optsStr = unquote(optsStr);
                String[] options = optsStr.split(",");
                int defIdx = parts.size() > 3 ? Integer.parseInt(unquote(parts.get(3))) : 0;
                String label = parts.size() > 4 ? unquote(parts.get(4)) : key;
                return new ParamDef(key, label, options, defIdx);
            }
            default:
                return null;
        }
    }

    private static List<String> splitRespectingQuotes(String input) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current = new StringBuilder();
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) result.add(current.toString());
        return result;
    }

    private static String unquote(String s) {
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    @Override
    public void init(int width, int height) {
        this.width = width;
        this.height = height;

        if (scriptSource == null) {
            error = true;
            errorMessage = "Script not found: " + scriptPath;
            Log.e(TAG, errorMessage);
            return;
        }

        try {
            globals = JsePlatform.standardGlobals();
            // 安全限制：限制指令数防止死循环（10000 指令后抛出错误）
            globals.load(scriptSource).call();

            // 预编译指令限制 hook 重置函数（10000 指令/帧）
            hookResetter = globals.load("debug.sethook(function() error('Instruction limit exceeded') end, '', 10000)").checkfunction();
            hookResetter.call(); // 立即应用

            // 获取 Lua 中的 init/draw/release 函数
            luaInit = globals.get("init").checkfunction();
            luaDraw = globals.get("draw").checkfunction();
            luaRelease = globals.get("release").isfunction() ? globals.get("release").checkfunction() : null;

            // 创建参数表
            paramsTable = new LuaTable();
            if (paramDefs != null) {
                Bundle defaults = ParamStore.loadParams(getWallpaperId(), paramDefs);
                for (ParamDef def : paramDefs) {
                    paramsTable.set(def.key, LuaValue.valueOf(defaults.getFloat(def.key, def.defaultValue)));
                }
            }

            // 调用 Lua init(w, h, params)
            luaInit.invoke(LuaValue.varargsOf(new LuaValue[]{
                    LuaValue.valueOf(width),
                    LuaValue.valueOf(height),
                    paramsTable
            }));

            bridge = new LuaCanvasBridge();
            initialized = true;
            error = false;
            errorMessage = null;
        } catch (LuaError e) {
            error = true;
            errorMessage = "Lua error: " + e.getMessage();
            Log.e(TAG, errorMessage, e);
        } catch (Exception e) {
            error = true;
            errorMessage = "Init error: " + e.getMessage();
            Log.e(TAG, errorMessage, e);
        }
    }

    @Override
    public void draw(Canvas canvas, int width, int height, long elapsedMs) {
        if (error) {
            // 回退黑色
            canvas.drawColor(0xFF000000);
            return;
        }
        if (!initialized || luaDraw == null) return;

        try {
            bridge.reset(canvas);
            float timeSec = elapsedMs / 1000f;

            // 每帧重置指令计数，防止累积超限
            if (hookResetter != null) {
                hookResetter.call();
            }

            luaDraw.invoke(LuaValue.varargsOf(new LuaValue[]{
                    LuaValue.userdataOf(bridge),
                    LuaValue.valueOf(width),
                    LuaValue.valueOf(height),
                    LuaValue.valueOf(timeSec),
                    paramsTable
            }));
        } catch (LuaError e) {
            error = true;
            errorMessage = "Draw error: " + e.getMessage();
            Log.e(TAG, errorMessage, e);
            canvas.drawColor(0xFF000000);
        } catch (Exception e) {
            error = true;
            errorMessage = "Draw error: " + e.getMessage();
            Log.e(TAG, errorMessage, e);
            canvas.drawColor(0xFF000000);
        }
    }

    @Override
    public void release() {
        if (luaRelease != null) {
            try {
                luaRelease.call();
            } catch (Exception e) {
                Log.w(TAG, "Error in Lua release()", e);
            }
        }
        initialized = false;
        globals = null;
        luaInit = null;
        luaDraw = null;
        luaRelease = null;
    }

    @Override
    public ParamDef[] getParamDefs() {
        return paramDefs != null ? paramDefs : new ParamDef[0];
    }

    @Override
    public void applyParams(Bundle params) {
        if (paramsTable == null) return;
        if (paramDefs == null) return;
        for (ParamDef def : paramDefs) {
            float val = params.getFloat(def.key, def.defaultValue);
            paramsTable.set(def.key, LuaValue.valueOf(val));
        }
        // 持久化
        int id = getWallpaperId();
        ParamStore.saveParams(id, paramDefs, params);
    }

    /** 获取错误信息（用于 Toast 提示） */
    public String getErrorMessage() {
        return errorMessage;
    }

    /** 是否发生了错误 */
    public boolean hasError() {
        return error;
    }

    /** 根据脚本路径生成壁纸 ID（用于参数存储） */
    private int getWallpaperId() {
        if (scriptPath != null) {
            return 100 + Math.abs(scriptPath.hashCode() % 900);
        }
        return 100 + Math.abs(scriptSource.hashCode() % 900);
    }
}
