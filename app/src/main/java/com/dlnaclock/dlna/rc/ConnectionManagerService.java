package com.dlnaclock.dlna.rc;

import com.dlnaclock.dlna.soap.SoapConstants;

/**
 * ConnectionManagerService - 连接管理服务
 * 提供协议信息（支持的 MIME 类型）和连接状态查询
 */
public class ConnectionManagerService {

    /** getProtocolInfo - 获取支持的协议信息（Source/Sink） */
    public String getProtocolInfo() {
        return SoapConstants.PROTOCOL_INFO_SINK;
    }

    /** getCurrentConnectionIDs - 获取当前连接 ID 列表（固定返回 "0"） */
    public String getCurrentConnectionIDs() {
        return "0";
    }
}
