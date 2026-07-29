package com.dlnaclock.util;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XmlUtil - XML 工具类
 * 提供标签值提取、DIDL-Lite 元数据解析、XML 实体编解码等功能
 * 用于 SOAP 请求解析和 DLNA 元数据处理
 */
public class XmlUtil {

    /** getTagValue - 从 XML 字符串中提取指定标签的值 */
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

    /** 匹配数字字符引用：&#NNNN; 或 &#xHHHH; */
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?)([0-9a-fA-F]+);");

    /**
     * decodeXmlEntities - 解码 XML 实体和数字字符引用
     * 处理标准实体（&amp; &lt; 等）和数字引用（&#38632; → 雨, &#x96E8; → 雨）
     */
    public static String decodeXmlEntities(String text) {
        if (text == null) return null;

        // 先处理标准 XML 实体
        String result = text.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&#39;", "'");

        // 处理数字字符引用：&#NNNN; (十进制) 和 &#xHHHH; (十六进制)
        Matcher matcher = NUMERIC_ENTITY.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            boolean isHex = "x".equals(matcher.group(1));
            try {
                int codePoint = Integer.parseInt(matcher.group(2), isHex ? 16 : 10);
                if (codePoint > 0 && codePoint <= 0x10FFFF) {
                    matcher.appendReplacement(sb, new String(Character.toChars(codePoint)));
                }
            } catch (NumberFormatException e) {
                // 无效引用，保留原文
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
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
