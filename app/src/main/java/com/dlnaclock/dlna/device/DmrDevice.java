package com.dlnaclock.dlna.device;

public class DmrDevice {

    private String udn;
    private String friendlyName;
    private String manufacturer;
    private String modelName;
    private String modelNumber;
    private String modelDescription;
    private String deviceType;
    private int httpPort;

    public DmrDevice() {
        this.deviceType = "urn:schemas-upnp-org:device:MediaRenderer:1";
        this.httpPort = 49152;
    }

    public String getUdn() { return udn; }
    public void setUdn(String udn) { this.udn = udn; }

    public String getFriendlyName() { return friendlyName; }
    public void setFriendlyName(String name) { this.friendlyName = name; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getModelNumber() { return modelNumber; }
    public void setModelNumber(String modelNumber) { this.modelNumber = modelNumber; }

    public String getModelDescription() { return modelDescription; }
    public void setModelDescription(String desc) { this.modelDescription = desc; }

    public String getDeviceType() { return deviceType; }

    public int getHttpPort() { return httpPort; }
    public void setHttpPort(int port) { this.httpPort = port; }
}
