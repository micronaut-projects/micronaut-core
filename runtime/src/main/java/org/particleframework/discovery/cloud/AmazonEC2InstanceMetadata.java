package org.particleframework.discovery.cloud;

import org.particleframework.context.env.ComputePlatform;

import java.util.List;
import java.util.Map;

public class AmazonEC2InstanceMetadata implements ComputeInstanceMetadata {

    // anything non-standard goes in here
    private Map<String, String> metadata;

    // quick way to lookup tags
    private Map<String, String> tags;

    //network interfaces to get ip addresses
    private List<NetworkInterface> interfaces;



    private String name;
    String availabilityZone;
    String localHostname;
    String publicHostname;
    String description;
    String machineType;
    String instanceId;
    String region;
    ComputePlatform computePlatform;
    String account;
    String imageId;


    // should we keep these broken out or require people to look in the interfaces?
    String publicIpV4;
    String publicIpV6;
    String privateIpV4;
    String privateIpV6;


    @Override
    public String getImageId() {
        return imageId;
    }

    @Override
    public String getAccount() {
        return account;
    }

    @Override
    public Map<String, String> getMetadata() {
        return null;
    }

    @Override
    public List<NetworkInterface> getInterfaces() {
        return null;
    }

    @Override
    public ComputePlatform getComputePlatform() {
        return null;
    }

    @Override
    public Map<String, String> getTags() {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public String getInstanceId() {
        return null;
    }

    @Override
    public String getMachineType() {
        return null;
    }

    @Override
    public String getAvailabilityZone() {
        return null;
    }

    @Override
    public String getRegion() {
        return null;
    }

    @Override
    public String getLocalHostname() {
        return null;
    }

    @Override
    public String getPrivateHosname() {
        return null;
    }

    @Override
    public String getPublicIpV4() {
        return null;
    }

    @Override
    public String getPublicIpV6() {
        return null;
    }

    @Override
    public String getPrivateIpV4() {
        return null;
    }

    @Override
    public String getPrivateIpV6() {
        return null;
    }

    @Override
    public String getDescription() {
        return null;
    }
}
