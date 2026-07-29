package com.dlnaclock.dlna.rc;

import com.dlnaclock.dlna.soap.SoapConstants;

public class ConnectionManagerService {

    public String getProtocolInfo() {
        return SoapConstants.PROTOCOL_INFO_SOURCE;
    }

    public String getCurrentConnectionIDs() {
        return "0";
    }
}
