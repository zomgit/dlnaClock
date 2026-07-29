package com.dlnaclock.dlna.avt;

import java.util.Map;

/**
 * MediaInfo - 媒体信息数据模型
 * 存储当前媒体的 URI、标题、艺术家、专辑、封面等元数据
 * 提供 DIDL-Lite 元数据解析、音频/视频类型判断、MIME 推断等功能
 */
public class MediaInfo {

    private String currentUri = "";          // 媒体播放地址
    private String currentUriMetadata = "";  // 原始元数据 XML
    private String title = "";               // 标题
    private String artist = "";              // 艺术家/歌手
    private String album = "";               // 专辑名
    private String albumArtUri = "";         // 专辑封面图片 URL
    private String duration = "00:00:00";    // 时长（HH:MM:SS）
    private String mimeType = "";            // MIME 类型
    private String upnpClass = "";           // UPnP class（用于判断媒体类型）

    /** MediaInfo - 构造函数 */
    public MediaInfo() {
    }

    /**
     * parseMetadata - 解析 DIDL-Lite 元数据 XML
     * 提取 title/artist/album/albumArtURI/upnp:class 等字段
     * 无 metadata 时从 URI 推断标题
     */
    public void parseMetadata(String metadata) {
        if (metadata == null || metadata.isEmpty()) {
            // Try to infer from URI
            inferFromUri();
            return;
        }

        this.currentUriMetadata = metadata;
        Map<String, String> fields = com.dlnaclock.util.XmlUtil.parseDidlLite(metadata);

        if (fields.containsKey("dc:title")) {
            this.title = fields.get("dc:title");
        }
        if (fields.containsKey("upnp:artist")) {
            this.artist = fields.get("upnp:artist");
        } else if (fields.containsKey("dc:creator")) {
            this.artist = fields.get("dc:creator");
        }
        if (fields.containsKey("upnp:album")) {
            this.album = fields.get("upnp:album");
        }
        if (fields.containsKey("upnp:albumArtURI")) {
            this.albumArtUri = fields.get("upnp:albumArtURI");
        }
        if (fields.containsKey("upnp:class")) {
            this.upnpClass = fields.get("upnp:class");
        }

        // If title is still empty, try to infer from URI
        if (this.title.isEmpty()) {
            inferFromUri();
        }
    }

    /** inferFromUri - 从 URI 中推断标题（提取文件名） */
    private void inferFromUri() {
        if (currentUri != null && !currentUri.isEmpty()) {
            try {
                // Extract filename from URL
                String path = currentUri;
                int queryIdx = path.indexOf('?');
                if (queryIdx > 0) path = path.substring(0, queryIdx);
                int lastSlash = path.lastIndexOf('/');
                if (lastSlash >= 0 && lastSlash < path.length() - 1) {
                    String filename = path.substring(lastSlash + 1);
                    // URL decode
                    filename = java.net.URLDecoder.decode(filename, "UTF-8");
                    // Remove extension
                    int dotIdx = filename.lastIndexOf('.');
                    if (dotIdx > 0) {
                        this.title = filename.substring(0, dotIdx);
                    } else {
                        this.title = filename;
                    }
                }
            } catch (Exception e) {
                this.title = "Unknown";
            }
        }
        if (this.title.isEmpty()) {
            this.title = "Unknown";
        }
    }

    /** isAudio - 判断当前媒体是否为音频（通过 MIME/upnpClass/URI 综合判断） */
    public boolean isAudio() {
        if (mimeType != null && mimeType.startsWith("audio")) return true;
        if (upnpClass != null && upnpClass.contains("audio")) return true;
        if (upnpClass != null && upnpClass.contains("music")) return true;
        // Check URI extension
        if (currentUri != null) {
            String uri = currentUri.toLowerCase();
            if (uri.contains(".mp3") || uri.contains(".aac") || uri.contains(".flac")
                    || uri.contains(".wav") || uri.contains(".ogg") || uri.contains(".m4a")
                    || uri.contains("audio") || uri.contains("music")) {
                return true;
            }
        }
        return false;
    }

    /** isVideo - 判断当前媒体是否为视频（通过 MIME/upnpClass/URI 综合判断） */
    public boolean isVideo() {
        if (mimeType != null && mimeType.startsWith("video")) return true;
        if (upnpClass != null && upnpClass.contains("video")) return true;
        if (upnpClass != null && upnpClass.contains("movie")) return true;
        // Check URI extension
        if (currentUri != null) {
            String uri = currentUri.toLowerCase();
            if (uri.contains(".mp4") || uri.contains(".mkv") || uri.contains(".avi")
                    || uri.contains(".webm") || uri.contains(".flv") || uri.contains(".wmv")
                    || uri.contains("video") || uri.contains("movie")) {
                return true;
            }
        }
        return false;
    }

    // Getters and setters
    public String getCurrentUri() { return currentUri; }
    public void setCurrentUri(String uri) {
        this.currentUri = uri;
        inferFromUri();
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getAlbum() { return album; }
    public void setAlbum(String album) { this.album = album; }

    public String getAlbumArtUri() { return albumArtUri; }
    public void setAlbumArtUri(String uri) { this.albumArtUri = uri; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public String getUpnpClass() { return upnpClass; }

    /** clear - 清空所有媒体信息 */
    public void clear() {
        currentUri = "";
        currentUriMetadata = "";
        title = "";
        artist = "";
        album = "";
        albumArtUri = "";
        duration = "00:00:00";
        mimeType = "";
        upnpClass = "";
    }
}
