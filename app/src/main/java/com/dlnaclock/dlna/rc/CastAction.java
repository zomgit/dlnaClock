package com.dlnaclock.dlna.rc;

import android.content.Intent;
import android.os.Bundle;

/**
 * CastAction - 投屏动作数据类
 * 参考 DLNA-Cast 的 CastAction 设计，通过 Intent 传递投屏信息到播放器 Activity
 * 包含媒体 URI、元数据、标题、MIME 类型等信息
 */
public class CastAction {

    private static final String EXTRA_URI = "cast_uri";
    private static final String EXTRA_METADATA = "cast_metadata";
    private static final String EXTRA_TITLE = "cast_title";
    private static final String EXTRA_MIME_TYPE = "cast_mime_type";
    private static final String EXTRA_ALBUM_ART_URI = "cast_album_art";
    private static final String EXTRA_ARTIST = "cast_artist";
    private static final String EXTRA_ALBUM = "cast_album";

    private String uri;           // 媒体播放地址
    private String metadata;      // DIDL-Lite 元数据 XML
    private String title;         // 标题
    private String mimeType;      // MIME 类型
    private String albumArtUri;   // 专辑封面 URL
    private String artist;        // 艺术家
    private String album;         // 专辑名

    /** CastAction - 构造函数 */
    public CastAction() {
    }

    /** CastAction - 从 URI 和元数据构造 */
    public CastAction(String uri, String metadata) {
        this.uri = uri;
        this.metadata = metadata;
    }

    // Getters and Setters
    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public String getAlbumArtUri() { return albumArtUri; }
    public void setAlbumArtUri(String albumArtUri) { this.albumArtUri = albumArtUri; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getAlbum() { return album; }
    public void setAlbum(String album) { this.album = album; }

    /**
     * toIntent - 将投屏信息附加到 Intent 中
     * @param intent 目标 Intent
     * @return 附加了投屏信息的 Intent
     */
    public Intent toIntent(Intent intent) {
        Bundle extras = new Bundle();
        if (uri != null) extras.putString(EXTRA_URI, uri);
        if (metadata != null) extras.putString(EXTRA_METADATA, metadata);
        if (title != null) extras.putString(EXTRA_TITLE, title);
        if (mimeType != null) extras.putString(EXTRA_MIME_TYPE, mimeType);
        if (albumArtUri != null) extras.putString(EXTRA_ALBUM_ART_URI, albumArtUri);
        if (artist != null) extras.putString(EXTRA_ARTIST, artist);
        if (album != null) extras.putString(EXTRA_ALBUM, album);
        intent.putExtras(extras);
        return intent;
    }

    /**
     * fromIntent - 从 Intent 中提取投屏信息
     * @param intent 包含投屏信息的 Intent
     * @return CastAction 实例，如果 Intent 中没有投屏信息则返回 null
     */
    public static CastAction fromIntent(Intent intent) {
        if (intent == null || intent.getExtras() == null) return null;

        Bundle extras = intent.getExtras();
        String uri = extras.getString(EXTRA_URI);
        if (uri == null) return null; // 没有 URI 则不是有效的投屏 Intent

        CastAction action = new CastAction();
        action.uri = uri;
        action.metadata = extras.getString(EXTRA_METADATA);
        action.title = extras.getString(EXTRA_TITLE);
        action.mimeType = extras.getString(EXTRA_MIME_TYPE);
        action.albumArtUri = extras.getString(EXTRA_ALBUM_ART_URI);
        action.artist = extras.getString(EXTRA_ARTIST);
        action.album = extras.getString(EXTRA_ALBUM);
        return action;
    }
}
