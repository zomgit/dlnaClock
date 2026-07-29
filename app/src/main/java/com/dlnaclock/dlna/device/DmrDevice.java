package com.dlnaclock.dlna.device;

/**
 * DmrDevice - DMR (Digital Media Renderer) 设备模型
 * 存储设备的基本信息：UDN、名称、制造商、型号、HTTP 端口等
 */
public class DmrDevice {

    private String udn;              // 设备唯一标识符
    private String friendlyName;     // 用户可见的设备名称
    private String manufacturer;     // 制造商
    private String modelName;        // 型号名称
    private String modelNumber;      // 型号编号
    private String modelDescription; // 型号描述
    private String deviceType;       // 设备类型 URN
    private int httpPort;            // HTTP 服务器端口

    /** DmrDevice - 构造函数，设置默认设备类型和端口 */
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
