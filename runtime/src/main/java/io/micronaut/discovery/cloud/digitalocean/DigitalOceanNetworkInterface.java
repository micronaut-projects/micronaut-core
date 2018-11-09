package io.micronaut.discovery.cloud.digitalocean;

import io.micronaut.discovery.cloud.NetworkInterface;

public class DigitalOceanNetworkInterface extends NetworkInterface {

    private int cidr;

    private String ipv6Gateway;

    @Override
    protected void setIpv4(String ipv4) {
        super.setIpv4(ipv4);
    }

    @Override
    protected void setIpv6(String ipv6) {
        super.setIpv6(ipv6);
    }

    @Override
    protected void setName(String name) {
        super.setName(name);
    }

    @Override
    protected void setMac(String mac) {
        super.setMac(mac);
    }

    @Override
    protected void setId(String id) {
        super.setId(id);
    }

    @Override
    protected void setGateway(String gateway) {
        super.setGateway(gateway);
    }

    @Override
    protected void setNetwork(String network) {
        super.setNetwork(network);
    }

    @Override
    protected void setNetmask(String netmask) {
        super.setNetmask(netmask);
    }

    public int getCidr() {
        return cidr;
    }

    public void setCidr(int cidr) {
        this.cidr = cidr;
    }

    public String getIpv6Gateway() {
        return ipv6Gateway;
    }

    public void setIpv6Gateway(String ipv6Gateway) {
        this.ipv6Gateway = ipv6Gateway;
    }


}
