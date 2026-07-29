package com.dlnaclock.dlna.avt;

import java.util.Map;

public class MediaInfo {

    private String currentUri = "";
    private String currentUriMetadata = "";
    private String title = "";
    private String artist = "";
    private String album = "";
    private String albumArtUri = "";
    private String duration = "00:00:00";
    private String mimeType = "";
    private String upnpClass = "";

    public MediaInfo() {
    }

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
