package org.particleframework.discovery.cloud;

import java.io.Serializable;

public class NetworkInterface implements Serializable
{
    String ipv4;
    String ipv6;
    String name;
    String mac;
    String id;
    String gateway;
    String network;
    String netmask;

    public String getIpv4() {
        return ipv4;
    }

    public String getIpv6() {
        return ipv6;
    }

    public String getName() {
        return name;
    }

    public String getMac() {
        return mac;
    }

    public String getId() {
        return id;
    }

    public String getGateway() {
        return gateway;
    }

    public String getNetwork() {
        return network;
    }

    public String getNetmask() {
        return netmask;
    }
}
