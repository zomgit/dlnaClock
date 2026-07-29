package com.dlnaclock.dlna.soap;

public class SoapConstants {

    // SOAP envelope templates
    public static final String SOAP_ENVELOPE_START =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"" +
            " s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body>";

    public static final String SOAP_ENVELOPE_END =
            "</s:Body></s:Envelope>";

    // AVTransport Actions
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

    // RenderingControl Actions
    public static final String ACTION_SET_VOLUME = "SetVolume";
    public static final String ACTION_GET_VOLUME = "GetVolume";
    public static final String ACTION_SET_MUTE = "SetMute";
    public static final String ACTION_GET_MUTE = "GetMute";

    // ConnectionManager Actions
    public static final String ACTION_GET_PROTOCOL_INFO = "GetProtocolInfo";
    public static final String ACTION_GET_CURRENT_CONNECTION_IDS = "GetCurrentConnectionIDs";
    public static final String ACTION_GET_CURRENT_CONNECTION_INFO = "GetCurrentConnectionInfo";

    // Transport states
    public static final String TRANSPORT_STATE_STOPPED = "STOPPED";
    public static final String TRANSPORT_STATE_PLAYING = "PLAYING";
    public static final String TRANSPORT_STATE_PAUSED = "PAUSED_PLAYBACK";
    public static final String TRANSPORT_STATE_TRANSITIONING = "TRANSITIONING";
    public static final String TRANSPORT_STATE_NO_MEDIA = "NO_MEDIA_PRESENT";

    // Transport status
    public static final String TRANSPORT_STATUS_OK = "OK";
    public static final String TRANSPORT_STATUS_ERROR = "ERROR_OCCURRED";

    // Seek modes
    public static final String SEEK_REL_TIME = "REL_TIME";
    public static final String SEEK_ABS_TIME = "ABS_TIME";
    public static final String SEEK_ABS_COUNT = "ABS_COUNT";

    // Protocol info
    public static final String PROTOCOL_INFO_SOURCE =
            "http-get:*:audio/mpeg:*,http-get:*:audio/mp3:*,http-get:*:audio/mp4:*,http-get:*:audio/x-wav:*," +
            "http-get:*:audio/flac:*,http-get:*:audio/aac:*," +
            "http-get:*:video/mp4:*,http-get:*:video/mpeg:*,http-get:*:video/x-matroska:*," +
            "http-get:*:video/webm:*,http-get:*:video/avi:*," +
            "http-get:*:image/jpeg:*,http-get:*:image/png:*,http-get:*:image/gif:*";

    public static final String PROTOCOL_INFO_SINK = PROTOCOL_INFO_SOURCE;

    // Error codes
    public static final int ERROR_INVALID_ACTION = 401;
    public static final int ERROR_NO_SUCH_INSTANCE = 402;
    public static final int ERROR_INVALID_VAR = 404;
    public static final int ERROR_ACTION_FAILED = 501;
}
