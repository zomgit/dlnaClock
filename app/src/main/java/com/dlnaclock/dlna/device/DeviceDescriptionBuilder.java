package com.dlnaclock.dlna.device;

import com.dlnaclock.dlna.ssdp.SsdpConstants;

/**
 * DeviceDescriptionBuilder - UPnP 设备描述 XML 生成器
 * 生成 description.xml（设备描述）和 SCPD 文档（服务定义）
 * 供控制点获取设备能力和服务信息
 */
public class DeviceDescriptionBuilder {

    /**
     * build - 构建设备描述 XML (description.xml)
     * 包含设备类型、名称、制造商、3 个服务定义
     */
    public static String build(DmrDevice device, String localIp, int httpPort) {
        String baseUrl = "http://" + localIp + ":" + httpPort;

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<root xmlns=\"urn:schemas-upnp-org:device-1-0\"");
        sb.append(" xmlns:dlna=\"urn:schemas-dlna-org:device-1-0\"");
        sb.append(" xmlns:sec=\"http://www.sec.co.kr/dlna\"");
        sb.append(" config-id=\"1\">\n");
        sb.append("  <specVersion>\n");
        sb.append("    <major>1</major>\n");
        sb.append("    <minor>1</minor>\n");
        sb.append("  </specVersion>\n");
        sb.append("  <device>\n");
        sb.append("    <deviceType>").append(device.getDeviceType()).append("</deviceType>\n");
        sb.append("    <friendlyName>").append(escapeXml(device.getFriendlyName())).append("</friendlyName>\n");
        sb.append("    <manufacturer>").append(escapeXml(device.getManufacturer())).append("</manufacturer>\n");
        sb.append("    <manufacturerURL>").append(baseUrl).append("</manufacturerURL>\n");
        sb.append("    <modelDescription>").append(escapeXml(device.getModelDescription())).append("</modelDescription>\n");
        sb.append("    <modelName>").append(escapeXml(device.getModelName())).append("</modelName>\n");
        sb.append("    <modelNumber>").append(device.getModelNumber()).append("</modelNumber>\n");
        sb.append("    <modelURL>").append(baseUrl).append("</modelURL>\n");
        sb.append("    <UDN>").append(device.getUdn()).append("</UDN>\n");
        // 设备图标列表（控制点渲染设备列表时使用）
        sb.append("    <iconList>\n");
        sb.append("      <icon>\n");
        sb.append("        <mimetype>image/png</mimetype>\n");
        sb.append("        <width>48</width>\n");
        sb.append("        <height>48</height>\n");
        sb.append("        <depth>32</depth>\n");
        sb.append("        <url>/icon.png</url>\n");
        sb.append("      </icon>\n");
        sb.append("    </iconList>\n");
        // DLNA 设备分类标识（控制点依赖此元素识别 DMR 设备）
        sb.append("    <dlna:X_DLNADOC>DMR-1.50</dlna:X_DLNADOC>\n");
        sb.append("    <sec:ProductCap>smi,DCM10,getMediaInfo.sec,getCaptionInfo.sec</sec:ProductCap>\n");
        sb.append("    <serviceList>\n");

        // AVTransport Service
        sb.append("      <service>\n");
        sb.append("        <serviceType>").append(SsdpConstants.SERVICE_TYPE_AVTRANSPORT).append("</serviceType>\n");
        sb.append("        <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>\n");
        sb.append("        <controlURL>/ctl/AVTransport</controlURL>\n");
        sb.append("        <eventSubURL>/event/AVTransport</eventSubURL>\n");
        sb.append("        <SCPDURL>/scpd/AVTransport.xml</SCPDURL>\n");
        sb.append("      </service>\n");

        // RenderingControl Service
        sb.append("      <service>\n");
        sb.append("        <serviceType>").append(SsdpConstants.SERVICE_TYPE_RENDERING_CONTROL).append("</serviceType>\n");
        sb.append("        <serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>\n");
        sb.append("        <controlURL>/ctl/RenderingControl</controlURL>\n");
        sb.append("        <eventSubURL>/event/RenderingControl</eventSubURL>\n");
        sb.append("        <SCPDURL>/scpd/RenderingControl.xml</SCPDURL>\n");
        sb.append("      </service>\n");

        // ConnectionManager Service
        sb.append("      <service>\n");
        sb.append("        <serviceType>").append(SsdpConstants.SERVICE_TYPE_CONNECTION_MANAGER).append("</serviceType>\n");
        sb.append("        <serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId>\n");
        sb.append("        <controlURL>/ctl/ConnectionManager</controlURL>\n");
        sb.append("        <eventSubURL>/event/ConnectionManager</eventSubURL>\n");
        sb.append("        <SCPDURL>/scpd/ConnectionManager.xml</SCPDURL>\n");
        sb.append("      </service>\n");

        sb.append("    </serviceList>\n");
        sb.append("  </device>\n");
        sb.append("</root>\n");

        return sb.toString();
    }

    /** buildAVTransportSCPD - 构建 AVTransport 服务的 SCPD 文档（含 10 个 Action 和状态变量表） */
    public static String buildAVTransportSCPD() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<scpd xmlns=\"urn:schemas-upnp-org:service-1-0\">\n");
        sb.append("  <specVersion><major>1</major><minor>0</minor></specVersion>\n");
        sb.append("  <actionList>\n");
        // SetAVTransportURI
        sb.append("    <action><name>SetAVTransportURI</name><argumentList>\n");
        sb.append("      <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>CurrentURI</name><direction>in</direction><relatedStateVariable>AVTransportURI</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>CurrentURIMetaData</name><direction>in</direction><relatedStateVariable>AVTransportURIMetaData</relatedStateVariable></argument>\n");
        sb.append("    </argumentList></action>\n");
        // GetTransportInfo
        sb.append("    <action><name>GetTransportInfo</name><argumentList>\n");
        sb.append("      <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>CurrentTransportState</name><direction>out</direction><relatedStateVariable>TransportState</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>CurrentTransportStatus</name><direction>out</direction><relatedStateVariable>TransportStatus</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>CurrentSpeed</name><direction>out</direction><relatedStateVariable>TransportPlaySpeed</relatedStateVariable></argument>\n");
        sb.append("    </argumentList></action>\n");
        // GetMediaInfo
        sb.append("    <action><name>GetMediaInfo</name><argumentList>\n");
        sb.append("      <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>NrTracks</name><direction>out</direction><relatedStateVariable>NumberOfTracks</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>MediaDuration</name><direction>out</direction><relatedStateVariable>CurrentMediaDuration</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>CurrentURI</name><direction>out</direction><relatedStateVariable>AVTransportURI</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>CurrentURIMetaData</name><direction>out</direction><relatedStateVariable>AVTransportURIMetaData</relatedStateVariable></argument>\n");
        sb.append("    </argumentList></action>\n");
        // GetPositionInfo
        sb.append("    <action><name>GetPositionInfo</name><argumentList>\n");
        sb.append("      <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>Track</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_Track</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>TrackDuration</name><direction>out</direction><relatedStateVariable>CurrentTrackDuration</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>RelTime</name><direction>out</direction><relatedStateVariable>RelativeTimeCounter</relatedStateVariable></argument>\n");
        sb.append("    </argumentList></action>\n");
        // Play
        sb.append("    <action><name>Play</name><argumentList>\n");
        sb.append("      <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>Speed</name><direction>in</direction><relatedStateVariable>TransportPlaySpeed</relatedStateVariable></argument>\n");
        sb.append("    </argumentList></action>\n");
        // Pause
        sb.append("    <action><name>Pause</name><argumentList>\n");
        sb.append("      <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\n");
        sb.append("    </argumentList></action>\n");
        // Stop
        sb.append("    <action><name>Stop</name><argumentList>\n");
        sb.append("      <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\n");
        sb.append("    </argumentList></action>\n");
        // Seek
        sb.append("    <action><name>Seek</name><argumentList>\n");
        sb.append("      <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>Unit</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_SeekMode</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>Target</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_SeekTarget</relatedStateVariable></argument>\n");
        sb.append("    </argumentList></action>\n");
        // Next
        sb.append("    <action><name>Next</name><argumentList>\n");
        sb.append("      <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\n");
        sb.append("    </argumentList></action>\n");
        // Previous
        sb.append("    <action><name>Previous</name><argumentList>\n");
        sb.append("      <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\n");
        sb.append("    </argumentList></action>\n");
        sb.append("  </actionList>\n");
        // State variables
        sb.append("  <serviceStateTable>\n");
        sb.append("    <stateVariable sendEvents=\"no\"><name>TransportState</name><dataType>string</dataType>\n");
        sb.append("      <allowedValueList><value>STOPPED</value><value>PLAYING</value><value>PAUSED_PLAYBACK</value><value>TRANSITIONING</value><value>NO_MEDIA_PRESENT</value></allowedValueList>\n");
        sb.append("    </stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"no\"><name>TransportStatus</name><dataType>string</dataType></stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"no\"><name>TransportPlaySpeed</name><dataType>string</dataType></stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"no\"><name>NumberOfTracks</name><dataType>ui4</dataType></stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"no\"><name>CurrentMediaDuration</name><dataType>string</dataType></stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"no\"><name>AVTransportURI</name><dataType>string</dataType></stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"no\"><name>AVTransportURIMetaData</name><dataType>string</dataType></stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_InstanceID</name><dataType>ui4</dataType></stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_Track</name><dataType>ui4</dataType></stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"no\"><name>CurrentTrackDuration</name><dataType>string</dataType></stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"no\"><name>RelativeTimeCounter</name><dataType>string</dataType></stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_SeekMode</name><dataType>string</dataType></stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_SeekTarget</name><dataType>string</dataType></stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"yes\"><name>LastChange</name><dataType>string</dataType></stateVariable>\n");
        sb.append("  </serviceStateTable>\n");
        sb.append("</scpd>\n");
        return sb.toString();
    }

    /** buildRenderingControlSCPD - 构建 RenderingControl 服务的 SCPD 文档（含 4 个 Action） */
    public static String buildRenderingControlSCPD() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<scpd xmlns=\"urn:schemas-upnp-org:service-1-0\">\n");
        sb.append("  <specVersion><major>1</major><minor>0</minor></specVersion>\n");
        sb.append("  <actionList>\n");
        sb.append("    <action><name>SetVolume</name><argumentList>\n");
        sb.append("      <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>Channel</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Channel</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>DesiredVolume</name><direction>in</direction><relatedStateVariable>Volume</relatedStateVariable></argument>\n");
        sb.append("    </argumentList></action>\n");
        sb.append("    <action><name>GetVolume</name><argumentList>\n");
        sb.append("      <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>Channel</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Channel</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>CurrentVolume</name><direction>out</direction><relatedStateVariable>Volume</relatedStateVariable></argument>\n");
        sb.append("    </argumentList></action>\n");
        sb.append("    <action><name>SetMute</name><argumentList>\n");
        sb.append("      <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>Channel</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Channel</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>DesiredMute</name><direction>in</direction><relatedStateVariable>Mute</relatedStateVariable></argument>\n");
        sb.append("    </argumentList></action>\n");
        sb.append("    <action><name>GetMute</name><argumentList>\n");
        sb.append("      <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>Channel</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Channel</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>CurrentMute</name><direction>out</direction><relatedStateVariable>Mute</relatedStateVariable></argument>\n");
        sb.append("    </argumentList></action>\n");
        sb.append("  </actionList>\n");
        sb.append("  <serviceStateTable>\n");
        sb.append("    <stateVariable sendEvents=\"no\"><name>Volume</name><dataType>ui2</dataType></stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"no\"><name>Mute</name><dataType>boolean</dataType></stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_InstanceID</name><dataType>ui4</dataType></stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_Channel</name><dataType>string</dataType></stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"yes\"><name>LastChange</name><dataType>string</dataType></stateVariable>\n");
        sb.append("  </serviceStateTable>\n");
        sb.append("</scpd>\n");
        return sb.toString();
    }

    /** buildConnectionManagerSCPD - 构建 ConnectionManager 服务的 SCPD 文档（含 3 个 Action） */
    public static String buildConnectionManagerSCPD() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<scpd xmlns=\"urn:schemas-upnp-org:service-1-0\">\n");
        sb.append("  <specVersion><major>1</major><minor>0</minor></specVersion>\n");
        sb.append("  <actionList>\n");
        sb.append("    <action><name>GetProtocolInfo</name><argumentList>\n");
        sb.append("      <argument><name>Source</name><direction>out</direction><relatedStateVariable>SourceProtocolInfo</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>Sink</name><direction>out</direction><relatedStateVariable>SinkProtocolInfo</relatedStateVariable></argument>\n");
        sb.append("    </argumentList></action>\n");
        sb.append("    <action><name>GetCurrentConnectionIDs</name><argumentList>\n");
        sb.append("      <argument><name>ConnectionIDs</name><direction>out</direction><relatedStateVariable>CurrentConnectionIDs</relatedStateVariable></argument>\n");
        sb.append("    </argumentList></action>\n");
        sb.append("    <action><name>GetCurrentConnectionInfo</name><argumentList>\n");
        sb.append("      <argument><name>ConnectionID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_ConnectionID</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>RcsID</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_RcsID</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>AVTransportID</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_AVTransportID</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>ProtocolInfo</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_ProtocolInfo</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>PeerConnectionManager</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_ConnectionManager</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>PeerConnectionID</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_ConnectionID</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>Direction</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_Direction</relatedStateVariable></argument>\n");
        sb.append("      <argument><name>Status</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_ConnectionStatus</relatedStateVariable></argument>\n");
        sb.append("    </argumentList></action>\n");
        sb.append("  </actionList>\n");
        sb.append("  <serviceStateTable>\n");
        sb.append("    <stateVariable sendEvents=\"yes\"><name>SourceProtocolInfo</name><dataType>string</dataType></stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"yes\"><name>SinkProtocolInfo</name><dataType>string</dataType></stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"yes\"><name>CurrentConnectionIDs</name><dataType>string</dataType></stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_ConnectionID</name><dataType>i4</dataType></stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_RcsID</name><dataType>i4</dataType></stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_AVTransportID</name><dataType>i4</dataType></stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_ProtocolInfo</name><dataType>string</dataType></stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_ConnectionManager</name><dataType>string</dataType></stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_Direction</name><dataType>string</dataType></stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_ConnectionStatus</name><dataType>string</dataType></stateVariable>\n");
        sb.append("  </serviceStateTable>\n");
        sb.append("</scpd>\n");
        return sb.toString();
    }

    /** escapeXml - XML 特殊字符转义 */
    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
