package com.dlnaclock.util;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

public class XmlUtil {

    public static String getTagValue(String xml, String tagName) {
        if (xml == null || tagName == null) return null;
        try {
            // Simple regex-like extraction for common cases
            String startTag = "<" + tagName;
            String endTag = "</" + tagName + ">";
            int startIdx = xml.indexOf(startTag);
            if (startIdx < 0) return null;
            // Find the end of the opening tag (handle attributes)
            int tagEnd = xml.indexOf(">", startIdx);
            if (tagEnd < 0) return null;
            int endIdx = xml.indexOf(endTag, tagEnd);
            if (endIdx < 0) return null;
            return xml.substring(tagEnd + 1, endIdx).trim();
        } catch (Exception e) {
            return null;
        }
    }

    public static Map<String, String> parseDidlLite(String metadata) {
        Map<String, String> result = new HashMap<>();
        if (metadata == null || metadata.isEmpty()) return result;

        // Common DIDL-Lite fields
        String[] fields = {"dc:title", "dc:creator", "upnp:artist", "upnp:album",
                "upnp:albumArtURI", "upnp:albumArtUri", "res", "upnp:class",
                "dc:creator", "upnp:genre"};

        for (String field : fields) {
            String value = getTagValue(metadata, field);
            if (value != null) {
                // Handle HTML entities
                value = decodeXmlEntities(value);
                result.put(field, value);
            }
        }

        // Try alternative tag names used by some Chinese apps
        if (!result.containsKey("upnp:albumArtURI") && !result.containsKey("upnp:albumArtUri")) {
            String artUri = getTagValue(metadata, "upnp:albumArtURI");
            if (artUri == null) artUri = getTagValue(metadata, "upnp:albumArtUri");
            if (artUri == null) artUri = getTagValue(metadata, "albumArtURI");
            if (artUri != null) {
                result.put("upnp:albumArtURI", decodeXmlEntities(artUri));
            }
        }

        return result;
    }

    public static String decodeXmlEntities(String text) {
        if (text == null) return null;
        return text.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&#39;", "'");
    }

    public static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
