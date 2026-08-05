# DLNA 时钟屏保 (DlnaClock)

中文 | **[English](README_EN.md)**

一款面向 Android TV / 旧手机 / 平板的 **DLNA + AirPlay 投屏接收器**，同时兼具多功能时钟屏保与动态壁纸引擎。将闲置设备变身为桌面时钟、氛围屏保或无线投屏终端。

## 功能特性

### 🕐 时钟屏保
- **多种表盘样式**：数字时钟、模拟时钟、自定义多行时钟（Minimal）
- **高度可定制**：字体族（每项最多 3 个字体按优先级回退）、颜色、大小、位置、12/24 小时制、自定义格式
- **防烧屏**：随机偏移 / 弹射运动两种模式（可调偏移幅度、弹射角度与速度），弹射模式支持手指拖动时钟，保护 OLED/AMOLED 屏幕
- **开机自启**：支持 BOOT_COMPLETED 自动启动

### 🎨 动态壁纸引擎
- **13 种内置壁纸**：全息螺旋、极光 V1/V2、相位光束、星空、森林、深海、魔烟、银河、立方体、动态渐变 V1/V2、字符雨
- **Lua 脚本扩展**：通过 LuaJ 引擎加载用户自定义 Lua 壁纸脚本
- **实时参数控制**：浮层参数面板独立开关，SeekBar 拖动即时生效，点击面板外部区域关闭
- **手势操控**：一指拖动 3D 透视旋转、双指平移 / 捏合缩放；各壁纸手势状态独立保存，切换互不干扰；面板实时显示旋转角，支持一键还原旋转
- **零分配渲染**：Canvas 2D 纯 CPU 绘制，兼容低端设备（minSdk 19）
- **背景模式**：纯色 / 静态图片（5 种适应方式）/ 视频循环（FFmpeg 全格式）/ 动态壁纸 / Lua 自定义壁纸

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

## 更新日志

### v1.0.3（最新）

#### ➕ 新增

- **字体族（Font Family）回退机制**：数字 / 英文 / 中文字体及自定义时钟各行均可配置最多 3 个字体，渲染时逐字符按优先级回退，缺失字形自动使用下一字体渲染（API 23+ 精确字形检测，低版本启发式判断）
- **字体选择器升级**：字体下拉框改为按钮 + 字体族设置对话框，内置字体优先展示、实时预览（"0123456789 你好 ABC"），支持添加 / 删除 / 优先级排序
- **自定义时钟行字号比例**：Minimal 时钟每行新增独立"字体大小"调节（5%–100%），默认主行:副行 = 3:1:1
- **Lua 自定义壁纸模式**：背景模式新增第 5 种"自定义壁纸"，直接运行用户 Lua 脚本壁纸
- **DSEG7 Classic 液晶字体**：新增内置七段液晶数字字体（OFL-1.1）

#### 🔧 修改

- **背景视频引擎升级**：由系统 MediaPlayer 换为 IJKPlayer (FFmpeg)，支持 mkv/avi 等全格式视频背景；退到后台自动暂停播放
- **壁纸渲染性能优化**：新增 TrigLut 三角函数查找表，壁纸动画中大量 sin/cos 调用改为查表，降低 CPU 开销
- **默认字体族调整**：数字 / 英文 / 中文字体默认值改为 "Rajdhani Medium, Microsoft YaHei"，拉丁与中文字形自动互补

### v1.0.2

#### ➕ 新增

- **壁纸手势操控**：一指拖动壁纸 3D 透视旋转（X 轴 ±90° / Y 轴 ±180° 钳制）、双指平移、双指捏合缩放（0.5x–3x）；各壁纸手势状态独立保存，切换互不干扰
- **壁纸参数面板增强**：新增"壁纸参数"按钮独立开关面板（不再随控件栏自动隐藏）；实时显示手势旋转角；"还原旋转"仅清零旋转角（保留平移/缩放），与"重置默认"全量还原分离；点击面板外部空白区域关闭；屏幕旋转自动适配尺寸
- **防烧屏弹射模式**：时钟在屏幕内沿直线匀速运动，撞边镜面反射 + 随机角度偏转反弹；支持手指拖动时钟，松手后沿拖动方向继续弹射；弹射角度（0–90°）与移动速度（1–30% 屏宽/秒）可调
- **设置页新增"版本信息"卡片**：展示版本、开发者、开源协议与开源地址

#### 🔧 修改

- **壁纸 3D 旋转统一为手势控制**：移除 HoloSpiral / Galaxy 壁纸的静态"旋转轴 + 旋转角"参数
- **防烧屏设置重构**：原"位置随机移动"与"像素微偏移"两个开关合并为"启用防烧屏"总开关，新增"偏移幅度"（1–30%）设置，像素微移幅度由偏移幅度自动推导
- **OSD 时间显示默认关闭**
- **自定义时钟设置**：自定义文本 / 自定义格式改为实时保存，修复直接按返回键导致内容丢失的问题
- **时钟渲染器**：所有渲染器新增内容边界计算接口（getContentBounds），用于弹射防烧屏的碰撞检测
- **刷新策略**：屏保在壁纸 / 弹射模式下以 30fps 刷新，保证手势操作与动画流畅

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
│   ├── GestureController.java   # 手势操控（3D 旋转 / 平移 / 缩放）
│   ├── GestureTransform.java    # 手势变换状态与 Canvas 应用
│   ├── BounceBurnInManager.java # 弹射式防烧屏（含拖动）
│   └── wallpaper/    # 13 种壁纸 + Lua 引擎 + 参数系统 + TrigLut 查表
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
- **DSEG7** (OFL-1.1) — 七段液晶数字字体

## 许可证

本项目采用 [Apache License 2.0](LICENSE) 开源。

```
Copyright 2024-2026 ZOMBIZ
```
