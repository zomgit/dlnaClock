# DLNA 时钟屏保 (DlnaClock)

中文 | **[English](README_EN.md)**

一款面向 Android TV / 旧手机 / 平板的 **DLNA + AirPlay 投屏接收器**，同时兼具多功能时钟屏保与动态壁纸引擎。将闲置设备变身为桌面时钟、氛围屏保或无线投屏终端。

## 功能特性

### 🕐 时钟屏保
- **多种表盘样式**：数字时钟、模拟时钟、自定义多行时钟（Minimal）
- **高度可定制**：字体、颜色、大小、位置、12/24 小时制、自定义格式
- **防烧屏**：位置随机移动 + 像素微偏移，保护 OLED/AMOLED 屏幕
- **开机自启**：支持 BOOT_COMPLETED 自动启动

### 🎨 动态壁纸引擎
- **13 种内置壁纸**：全息螺旋、极光 V1/V2、相位光束、星空、森林、深海、魔烟、银河、立方体、动态渐变 V1/V2、字符雨
- **Lua 脚本扩展**：通过 LuaJ 引擎加载用户自定义 Lua 壁纸脚本
- **实时参数控制**：主屏浮层控制面板，SeekBar 拖动即时生效
- **零分配渲染**：Canvas 2D 纯 CPU 绘制，兼容低端设备（minSdk 19）
- **背景模式**：纯色 / 静态图片（5 种适应方式）/ 视频循环 / 动态壁纸

### 📺 DLNA 投屏接收
- 完整实现 UPnP/DLNA DMR（Digital Media Renderer）协议
- 支持视频、音乐、图片投屏
- GENA 事件订阅与通知
- 前台 Service 常驻，支持通知栏控制

### 🍎 AirPlay 接收  (没测试，等于不能用)
- 基于 NanoHTTPD + JmDNS 实现 AirPlay 协议
- 支持图片/视频 AirPlay 投屏

### 🎵 媒体播放 （只是给接收投屏用的，避免没有安装好的视频播放器，当然设置里也可以选你装好的媒体播放器）
- **视频播放器**：IJKPlayer (FFmpeg) 硬解/软解切换，宽高比自适应
- **音乐播放器**：独立 Activity，支持横竖屏布局

## 技术栈

| 组件 | 说明 |
|------|------|
| 语言 | Java (100%) |
| 最低 SDK | Android 4.4 (API 19) |
| 目标 SDK | Android 9 (API 28) |
| 编译 SDK | Android 13 (API 33) |
| 构建工具 | Gradle 8.5 + AGP 8.2.2 |
| 视频播放 | IJKPlayer k0.8.8 (本地 .so) |
| HTTP 服务 | NanoHTTPD 2.3.1 |
| mDNS 发现 | JmDNS 3.5.9 |
| 脚本引擎 | LuaJ 3.0.1 |
| 支持库 | Android Support v7 28.0.0 |

## 项目结构

```
app/src/main/java/com/dlnaclock/
├── airplay/          # AirPlay 接收服务
├── clock/            # 时钟渲染器（数字/模拟/Minimal/霓虹）
├── dlna/             # DLNA/UPnP 协议栈（SSDP、GENA、AVT、RC）
├── media/            # 音乐/视频播放器 Activity
├── screensaver/      # 屏保主界面 + 动态壁纸引擎
│   └── wallpaper/    # 13 种壁纸 + Lua 引擎 + 参数系统
├── settings/         # 设置界面
└── util/             # 工具类
```

## 构建

### 环境要求
- JDK 17
- Android SDK (compileSdk 33)
- Gradle 8.5（项目自带 wrapper）

## 权限说明

| 权限 | 用途 |
|------|------|
| INTERNET | DLNA/AirPlay 网络通信 |
| ACCESS_WIFI_STATE | 获取网络状态 |
| CHANGE_WIFI_MULTICAST_STATE | SSDP/mDNS 组播 |
| WAKE_LOCK | 屏保常亮 |
| RECEIVE_BOOT_COMPLETED | 开机自启 |
| FOREGROUND_SERVICE | DLNA 服务常驻 |
| READ_EXTERNAL_STORAGE | 读取本地图片/视频背景 |

## 第三方组件

详见 [THIRD-PARTY-NOTICES](THIRD-PARTY-NOTICES) 文件，主要包含：

- **IJKPlayer** (Apache-2.0) — 视频播放引擎
- **FFmpeg** (LGPL-2.1) — 多媒体编解码
- **NanoHTTPD** (BSD-3-Clause) — 轻量 HTTP 服务器
- **JmDNS** (Apache-2.0) — mDNS/DNS-SD 服务发现
- **LuaJ** (MIT) — Lua 脚本引擎
- **Google Fonts** (Apache-2.0 / OFL-1.1) — 时钟字体

## 许可证

本项目采用 [Apache License 2.0](LICENSE) 开源。

```
Copyright 2024-2026 ZOMBIZ
```
