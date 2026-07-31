# DLNA Clock Screensaver (DlnaClock)

**[中文](README.md)** | English

A **DLNA + AirPlay casting receiver** designed for Android TV / old phones / tablets, featuring a multi-functional clock screensaver and dynamic wallpaper engine. Turn idle devices into desktop clocks, ambient screensavers, or wireless casting endpoints.

## Features

### 🕐 Clock Screensaver
- **Multiple clock styles**: Digital, Analog, Custom multi-row (Minimal)
- **Highly customizable**: Font, color, size, position, 12/24-hour format, custom format strings
- **Anti burn-in**: Random position shifting + pixel micro-offset, protecting OLED/AMOLED screens
- **Auto-start on boot**: Supports BOOT_COMPLETED auto-launch

### 🎨 Dynamic Wallpaper Engine
- **13 built-in wallpapers**: Holo Spiral, Aurora V1/V2, Phase Beam, Night Sky, Forest, Deep Sea, Magic Smoke, Galaxy, Cube, Dynamic Gradient V1/V2, Matrix Rain
- **Lua script extension**: Load user-defined Lua wallpaper scripts via LuaJ engine
- **Real-time parameter control**: On-screen floating control panel with instant SeekBar feedback
- **Zero-allocation rendering**: Canvas 2D pure CPU drawing, compatible with low-end devices (minSdk 19)
- **Background modes**: Solid color / Static image (5 fit modes) / Video loop / Dynamic wallpaper

### 📺 DLNA Casting Receiver
- Full UPnP/DLNA DMR (Digital Media Renderer) protocol implementation
- Supports video, music, and image casting
- GENA event subscription and notification
- Foreground Service with notification bar control

### 🍎 AirPlay Receiver (untested, essentially non-functional)
- AirPlay protocol based on NanoHTTPD + JmDNS
- Supports image/video AirPlay casting

### 🎵 Media Playback (built-in for casting; you can also select your own installed player in settings)
- **Video player**: IJKPlayer (FFmpeg) with hardware/software decode switching, aspect ratio adaptation
- **Music player**: Dedicated Activity with landscape/portrait layouts

## Tech Stack

| Component | Details |
|-----------|---------|
| Language | Java (100%) |
| Min SDK | Android 4.4 (API 19) |
| Target SDK | Android 9 (API 28) |
| Compile SDK | Android 13 (API 33) |
| Build Tools | Gradle 8.5 + AGP 8.2.2 |
| Video Playback | IJKPlayer k0.8.8 (local .so) |
| HTTP Server | NanoHTTPD 2.3.1 |
| mDNS Discovery | JmDNS 3.5.9 |
| Script Engine | LuaJ 3.0.1 |
| Support Lib | Android Support v7 28.0.0 |

## Project Structure

```
app/src/main/java/com/dlnaclock/
├── airplay/          # AirPlay receiver service
├── clock/            # Clock renderers (Digital/Analog/Minimal/Neon)
├── dlna/             # DLNA/UPnP protocol stack (SSDP, GENA, AVT, RC)
├── media/            # Music/Video player Activities
├── screensaver/      # Screensaver main UI + dynamic wallpaper engine
│   └── wallpaper/    # 13 wallpapers + Lua engine + param system
├── settings/         # Settings UI
└── util/             # Utilities
```

## Build

### Requirements
- JDK 17
- Android SDK (compileSdk 33)
- Gradle 8.5 (wrapper included)

## Permissions

| Permission | Purpose |
|------------|---------|
| INTERNET | DLNA/AirPlay network communication |
| ACCESS_WIFI_STATE | Network state detection |
| CHANGE_WIFI_MULTICAST_STATE | SSDP/mDNS multicast |
| WAKE_LOCK | Keep screen on for screensaver |
| RECEIVE_BOOT_COMPLETED | Auto-start on boot |
| FOREGROUND_SERVICE | Persistent DLNA service |
| READ_EXTERNAL_STORAGE | Read local image/video backgrounds |

## Third-Party Components

See [THIRD-PARTY-NOTICES](THIRD-PARTY-NOTICES) for full details:

- **IJKPlayer** (Apache-2.0) — Video playback engine
- **FFmpeg** (LGPL-2.1) — Multimedia codec
- **NanoHTTPD** (BSD-3-Clause) — Lightweight HTTP server
- **JmDNS** (Apache-2.0) — mDNS/DNS-SD service discovery
- **LuaJ** (MIT) — Lua script engine
- **Google Fonts** (Apache-2.0 / OFL-1.1) — Clock fonts

## License

This project is licensed under the [Apache License 2.0](LICENSE).

```
Copyright 2024-2026 ZOMBIZ
```
