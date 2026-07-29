package com.dlnaclock.screensaver.wallpaper;

import java.util.List;

/**
 * WallpaperFactory - 动态壁纸工厂类
 * 根据类型 ID 创建对应的壁纸渲染器实例
 * 支持内置 12 种壁纸 + Lua 自定义脚本（ID >= 100）
 */
public class WallpaperFactory {

    /** 壁纸类型总数（内置） */
    public static final int WALLPAPER_COUNT = 13;

    /** Lua 脚本壁纸起始 ID */
    public static final int LUA_WALLPAPER_ID = 100;

    /** 内置 Lua 脚本名称常量 */
    public static final String LUA_PARTICLES = "builtin:particles";

    /**
     * create - 根据类型创建壁纸渲染器
     * @param type 壁纸类型 (0-10 内置, >=100 Lua脚本)
     * @return 对应的 WallpaperRenderer 实例
     */
    public static WallpaperRenderer create(int type) {
        if (type >= LUA_WALLPAPER_ID) {
            return createLua(type);
        }
        switch (type) {
            case 0: return new HoloSpiralWallpaper();
            case 1: return new AuroraWallpaper();
            case 2: return new Aurora2Wallpaper();
            case 3: return new PhaseBeamWallpaper();
            case 4: return new NightSkyWallpaper();
            case 5: return new ForestWallpaper();
            case 6: return new DeepSeaWallpaper();
            case 7: return new MagicSmokeWallpaper();
            case 8: return new GalaxyWallpaper();
            case 9: return new CubeWallpaper();
            case 10: return new DynamicGradientWallpaper();
            case 11: return new DynamicGradient2Wallpaper();
            case 12: return new MatrixRainWallpaper();
            default: return new HoloSpiralWallpaper();
        }
    }

    /**
     * create - 根据脚本路径创建 Lua 壁纸渲染器
     * @param scriptPath 脚本文件路径
     * @return LuaWallpaper 实例
     */
    public static WallpaperRenderer create(String scriptPath) {
        return new LuaWallpaper(scriptPath);
    }

    /**
     * createFromScriptInfo - 从 ScriptManager.ScriptInfo 创建壁纸
     */
    public static WallpaperRenderer create(ScriptManager.ScriptInfo info) {
        if (info.path.startsWith("builtin:")) {
            return new LuaWallpaper(info.source, info.paramDefs);
        }
        return new LuaWallpaper(info.path);
    }

    /**
     * 根据 Lua 壁纸的脚本路径/ID 创建渲染器
     */
    private static WallpaperRenderer createLua(int type) {
        List<ScriptManager.ScriptInfo> scripts = ScriptManager.getAvailableScripts();
        int index = type - LUA_WALLPAPER_ID;
        if (index >= 0 && index < scripts.size()) {
            return create(scripts.get(index));
        }
        // 回退到内置第一个脚本
        if (!scripts.isEmpty()) {
            return create(scripts.get(0));
        }
        return new HoloSpiralWallpaper();
    }

    /**
     * getAvailableScripts - 获取所有可用 Lua 脚本列表
     */
    public static List<ScriptManager.ScriptInfo> getAvailableScripts() {
        return ScriptManager.getAvailableScripts();
    }

    /**
     * getParamDefs - 获取指定壁纸类型的参数定义（不创建完整渲染器）
     * 用于 UI 生成参数控件时的高效查询
     */
    public static ParamDef[] getParamDefs(int type) {
        if (type >= LUA_WALLPAPER_ID) {
            // Lua 脚本：从 ScriptManager 获取参数定义
            List<ScriptManager.ScriptInfo> scripts = ScriptManager.getAvailableScripts();
            int index = type - LUA_WALLPAPER_ID;
            if (index >= 0 && index < scripts.size()) {
                return scripts.get(index).paramDefs;
            }
            return new ParamDef[0];
        }
        // 内置壁纸：创建轻量实例获取参数定义
        WallpaperRenderer r = create(type);
        if (r != null) {
            ParamDef[] defs = r.getParamDefs();
            r.release();
            return defs;
        }
        return new ParamDef[0];
    }

    /**
     * getWallpaperNames - 获取所有可用壁纸名称列表（含 Lua 脚本）
     */
    public static List<String> getWallpaperNames() {
        List<String> names = new java.util.ArrayList<>();
        // 内置 12 个壁纸名称
        String[] builtin = {
            "全息螺旋", "极光 V1", "极光 V2", "相位光束", "星空",
            "森林", "深海", "魔烟", "银河", "立方体", "动态渐变", "动态渐变V2",
            "字符雨"
        };
        for (String name : builtin) {
            names.add(name);
        }
        // Lua 脚本壁纸名称
        for (ScriptManager.ScriptInfo info : getAvailableScripts()) {
            names.add(info.name + " [Lua]");
        }
        return names;
    }
}
