package com.dlnaclock.dlna.ssdp;

public class SsdpConstants {

    public static final String SSDP_MULTICAST_ADDRESS = "239.255.255.250";
    public static final int SSDP_PORT = 1900;

    // Device types
    public static final String DEVICE_TYPE_MEDIA_RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1";
    public static final String DEVICE_TYPE_MEDIA_SERVER = "urn:schemas-upnp-org:device:MediaServer:1";

    // Service types
    public static final String SERVICE_TYPE_AVTRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1";
    public static final String SERVICE_TYPE_RENDERING_CONTROL = "urn:schemas-upnp-org:service:RenderingControl:1";
    public static final String SERVICE_TYPE_CONNECTION_MANAGER = "urn:schemas-upnp-org:service:ConnectionManager:1";

    // SSDP methods
    public static final String METHOD_NOTIFY = "NOTIFY";
    public static final String METHOD_MSEARCH = "M-SEARCH";
    public static final String METHOD_HTTP_OK = "HTTP/1.1 200 OK";

    // NT (Notification Type) values
    public static final String NT_ROOT_DEVICE = "upnp:rootdevice";
    public static final String NT_ALL = "ssdp:all";

    // NTS (Notification Sub Type) values
    public static final String NTS_ALIVE = "ssdp:alive";
    public static final String NTS_BYEBYE = "ssdp:byebye";
    public static final String NTS_UPDATE = "ssdp:update";

    // Headers
    public static final String HEADER_HOST = "HOST";
    public static final String HEADER_CACHE_CONTROL = "CACHE-CONTROL";
    public static final String HEADER_LOCATION = "LOCATION";
    public static final String HEADER_NT = "NT";
    public static final String HEADER_NTS = "NTS";
    public static final String HEADER_USN = "USN";
    public static final String HEADER_SERVER = "SERVER";
    public static final String HEADER_ST = "ST";
    public static final String HEADER_MX = "MX";
    public static final String HEADER_MAN = "MAN";

    // Default values
    public static final int DEFAULT_CACHE_CONTROL = 1800; // seconds
    public static final int NOTIFY_INTERVAL = 300; // seconds
    public static final int MSEARCH_RESPONSE_DELAY = 1000; // ms

    // Server info
    public static final String SERVER_INFO = "Android/4.4 UPnP/1.1 DlnaClock/1.0";
}
