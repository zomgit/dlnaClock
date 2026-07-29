package com.dlnaclock.dlna.soap;

/**
 * SoapConstants - SOAP 协议常量定义
 * 包含 SOAP 信封模板、Action 名称、播放状态、协议信息、错误码等
 */
public class SoapConstants {

    /** SOAP 信封模板（开始/结束标签） */
    public static final String SOAP_ENVELOPE_START =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"" +
            " s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body>";

    public static final String SOAP_ENVELOPE_END =
            "</s:Body></s:Envelope>";

    /** AVTransport 服务的 Action 名称（10个） */
    public static final String ACTION_SET_AV_TRANSPORT_URI = "SetAVTransportURI";
    public static final String ACTION_GET_TRANSPORT_INFO = "GetTransportInfo";
    public static final String ACTION_GET_MEDIA_INFO = "GetMediaInfo";
    public static final String ACTION_GET_POSITION_INFO = "GetPositionInfo";
    public static final String ACTION_PLAY = "Play";
    public static final String ACTION_PAUSE = "Pause";
    public static final String ACTION_STOP = "Stop";
    public static final String ACTION_SEEK = "Seek";
    public static final String ACTION_NEXT = "Next";
    public static final String ACTION_PREVIOUS = "Previous";

    /** RenderingControl 服务的 Action 名称（4个） */
    public static final String ACTION_SET_VOLUME = "SetVolume";
    public static final String ACTION_GET_VOLUME = "GetVolume";
    public static final String ACTION_SET_MUTE = "SetMute";
    public static final String ACTION_GET_MUTE = "GetMute";

    /** ConnectionManager 服务的 Action 名称（3个） */
    public static final String ACTION_GET_PROTOCOL_INFO = "GetProtocolInfo";
    public static final String ACTION_GET_CURRENT_CONNECTION_IDS = "GetCurrentConnectionIDs";
    public static final String ACTION_GET_CURRENT_CONNECTION_INFO = "GetCurrentConnectionInfo";

    /** 播放状态值 */
    public static final String TRANSPORT_STATE_STOPPED = "STOPPED";
    public static final String TRANSPORT_STATE_PLAYING = "PLAYING";
    public static final String TRANSPORT_STATE_PAUSED = "PAUSED_PLAYBACK";
    public static final String TRANSPORT_STATE_TRANSITIONING = "TRANSITIONING";
    public static final String TRANSPORT_STATE_NO_MEDIA = "NO_MEDIA_PRESENT";

    /** 播放状态码 */
    public static final String TRANSPORT_STATUS_OK = "OK";
    public static final String TRANSPORT_STATUS_ERROR = "ERROR_OCCURRED";

    /** Seek 模式 */
    public static final String SEEK_REL_TIME = "REL_TIME";
    public static final String SEEK_ABS_TIME = "ABS_TIME";
    public static final String SEEK_ABS_COUNT = "ABS_COUNT";

    /** 协议信息（DMR 作为接收端，Source 应为空，Sink 列出支持的接收格式） */
    public static final String PROTOCOL_INFO_SOURCE = "";

    public static final String PROTOCOL_INFO_SINK =
            // 音频格式（含 Kodi 全部音频类型）
            "http-get:*:audio/mpeg:*" +
            ",http-get:*:audio/mp3:*" +
            ",http-get:*:audio/mp4:*" +
            ",http-get:*:audio/x-m4a:*" +
            ",http-get:*:audio/x-wav:*" +
            ",http-get:*:audio/wav:*" +
            ",http-get:*:audio/flac:*" +
            ",http-get:*:audio/x-flac:*" +
            ",http-get:*:audio/aac:*" +
            ",http-get:*:audio/ogg:*" +
            ",http-get:*:audio/x-ms-wma:*" +
            ",http-get:*:audio/L16;rate=44100;channels=2:*" +
            ",http-get:*:audio/L16;rate=48000;channels=2:*" +
            ",http-get:*:audio/x-vorbis+ogg:*" +
            // Kodi 额外音频格式
            ",http-get:*:audio/ac3:*" +
            ",http-get:*:audio/aiff:*" +
            ",http-get:*:audio/x-aiff:*" +
            ",http-get:*:audio/x-matroska:*" +
            ",http-get:*:audio/vnd.rn-realaudio:*" +
            ",http-get:*:audio/x-realaudio:*" +
            ",http-get:*:audio/basic:*" +
            ",http-get:*:audio/dvi4:*" +
            ",http-get:*:audio/g722:*" +
            ",http-get:*:audio/g723:*" +
            ",http-get:*:audio/g726-16:*" +
            ",http-get:*:audio/g726-24:*" +
            ",http-get:*:audio/g726-32:*" +
            ",http-get:*:audio/g726-40:*" +
            ",http-get:*:audio/g728:*" +
            ",http-get:*:audio/g729:*" +
            ",http-get:*:audio/g729d:*" +
            ",http-get:*:audio/g729e:*" +
            ",http-get:*:audio/gsm:*" +
            ",http-get:*:audio/gsm-efr:*" +
            ",http-get:*:audio/l8:*" +
            ",http-get:*:audio/lpc:*" +
            ",http-get:*:audio/midi:*" +
            ",http-get:*:audio/mkv:*" +
            ",http-get:*:audio/mpa:*" +
            ",http-get:*:audio/mpegurl:*" +
            ",http-get:*:audio/x-mpegurl:*" +
            ",http-get:*:audio/pcma:*" +
            ",http-get:*:audio/pcmu:*" +
            ",http-get:*:audio/qcelp:*" +
            ",http-get:*:audio/red:*" +
            ",http-get:*:audio/speex:*" +
            ",http-get:*:audio/ulaw:*" +
            ",http-get:*:audio/vdvi:*" +
            ",http-get:*:audio/webm:*" +
            // 视频格式（含 Kodi 全部视频类型）
            ",http-get:*:video/mp4:*" +
            ",http-get:*:video/mpeg:*" +
            ",http-get:*:video/x-matroska:*" +
            ",http-get:*:video/webm:*" +
            ",http-get:*:video/avi:*" +
            ",http-get:*:video/x-msvideo:*" +
            ",http-get:*:video/x-flv:*" +
            ",http-get:*:video/3gpp:*" +
            ",http-get:*:video/x-ms-wmv:*" +
            ",http-get:*:video/x-ms-asf:*" +
            // Kodi 额外视频格式
            ",http-get:*:video/x-ms-asf:*" +
            ",http-get:*:video/x-ms-asf-plugin:*" +
            ",http-get:*:video/x-ms-wvx:*" +
            ",http-get:*:video/x-ms-wmx:*" +
            ",http-get:*:video/x-ms-asx:*" +
            ",http-get:*:video/x-divx:*" +
            ",http-get:*:video/divx:*" +
            ",http-get:*:video/x-msvideo:*" +
            ",http-get:*:video/x-mkv:*" +
            ",http-get:*:video/x-mov:*" +
            ",http-get:*:video/quicktime:*" +
            ",http-get:*:video/x-ogm:*" +
            ",http-get:*:video/x-rmvb:*" +
            ",http-get:*:video/vnd.rn-realvideo:*" +
            ",http-get:*:video/x-realvideo:*" +
            ",http-get:*:video/x-rv:*" +
            ",http-get:*:video/mp2t:*" +
            ",http-get:*:video/vnd.dlna.mpeg-tts:*" +
            ",http-get:*:video/x-vnd.vivo:*" +
            ",http-get:*:video/vnd.vivo:*" +
            ",http-get:*:video/h261:*" +
            ",http-get:*:video/h263:*" +
            ",http-get:*:video/h263-1998:*" +
            ",http-get:*:video/h263-2000:*" +
            ",http-get:*:video/x-xvid:*" +
            ",http-get:*:video/xvid:*" +
            ",http-get:*:video/vc1:*" +
            ",http-get:*:video/wvc1:*" +
            ",http-get:*:video/bmpeg:*" +
            ",http-get:*:video/bt656:*" +
            ",http-get:*:video/celb:*" +
            ",http-get:*:video/fli:*" +
            ",http-get:*:video/x-fli:*" +
            ",http-get:*:video/jpeg:*" +
            ",http-get:*:video/mp1s:*" +
            ",http-get:*:video/mp2p:*" +
            ",http-get:*:video/mpv:*" +
            // 图片格式（含 Kodi 额外类型）
            ",http-get:*:image/jpeg:*" +
            ",http-get:*:image/jpg:*" +
            ",http-get:*:image/png:*" +
            ",http-get:*:image/gif:*" +
            ",http-get:*:image/tiff:*" +
            ",http-get:*:image/webp:*" +
            ",http-get:*:image/ief:*" +
            // 通配符（兜底，兼容 Kodi Other 列表中的 *）
            ",http-get:*:*/*:*";

    /** UPnP 错误码 */
    public static final int ERROR_INVALID_ACTION = 401;
    public static final int ERROR_NO_SUCH_INSTANCE = 402;
    public static final int ERROR_INVALID_VAR = 404;
    public static final int ERROR_ACTION_FAILED = 501;
}
