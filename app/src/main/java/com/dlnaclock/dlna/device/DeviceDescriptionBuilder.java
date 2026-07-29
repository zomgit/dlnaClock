package com.dlnaclock.dlna.device;

import com.dlnaclock.dlna.ssdp.SsdpConstants;

public class DeviceDescriptionBuilder {

    public static String build(DmrDevice device, String localIp, int httpPort) {
        String baseUrl = "http://" + localIp + ":" + httpPort;

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<root xmlns=\"urn:schemas-upnp-org:device-1-0\" config-id=\"1\">\n");
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

    public static String buildAVTransportSCPD() {
        return buildSCPD("AVTransport",
                new String[]{"SetAVTransportURI", "GetTransportInfo", "GetMediaInfo",
                        "GetPositionInfo", "Play", "Pause", "Stop", "Seek", "Next", "Previous"});
    }

    public static String buildRenderingControlSCPD() {
        return buildSCPD("RenderingControl",
                new String[]{"SetVolume", "GetVolume", "SetMute", "GetMute"});
    }

    public static String buildConnectionManagerSCPD() {
        return buildSCPD("ConnectionManager",
                new String[]{"GetProtocolInfo", "GetCurrentConnectionIDs", "GetCurrentConnectionInfo"});
    }

    private static String buildSCPD(String serviceName, String[] actions) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<scpd xmlns=\"urn:schemas-upnp-org:service-1-0\">\n");
        sb.append("  <specVersion><major>1</major><minor>0</minor></specVersion>\n");
        sb.append("  <actionList>\n");
        for (String action : actions) {
            sb.append("    <action><name>").append(action).append("</name></action>\n");
        }
        sb.append("  </actionList>\n");
        sb.append("  <serviceStateTable>\n");
        sb.append("    <stateVariable sendEvents=\"no\"><name>TransportState</name><dataType>string</dataType></stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"no\"><name>CurrentTransportActions</name><dataType>string</dataType></stateVariable>\n");
        sb.append("    <stateVariable sendEvents=\"yes\"><name>LastChange</name><dataType>string</dataType></stateVariable>\n");
        sb.append("  </serviceStateTable>\n");
        sb.append("</scpd>\n");
        return sb.toString();
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
