package org.particleframework.discovery.cloud;

import org.particleframework.context.env.ComputePlatform;

import java.util.List;
import java.util.Map;

public class GoogleComputeInstanceMetadata implements ComputeInstanceMetadata {

    // anything non-standard goes in here
    Map<String, String> metadata;

    // quick way to lookup tags
    private Map<String, String> tags;

    //network interfaces to get ip addresses
    List<NetworkInterface> interfaces;




    String name;
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
    public Map<String,String> getMetadata() {
        return metadata;
    }

    @Override
    public List<NetworkInterface> getInterfaces() {
        return interfaces;
    }

    @Override
    public ComputePlatform getComputePlatform() {
        return computePlatform;
    }

    @Override
    public Map<String, String> getTags() {
        return tags;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public String getMachineType() {
        return machineType;
    }

    @Override
    public String getAvailabilityZone() {
        return availabilityZone;
    }

    @Override
    public String getRegion() {
        if (availabilityZone !=null) {
            return availabilityZone.substring(0,availabilityZone.length()-2);
        }
        return region;
    }

    @Override
    public String getLocalHostname() {
        return localHostname;
    }

    @Override
    public String getPrivateHosname() {
        return localHostname;
    }

    @Override
    public String getPublicIpV4() {
        return publicIpV4;
    }

    @Override
    public String getPublicIpV6() {
        return publicIpV6;
    }

    @Override
    public String getPrivateIpV4() {
        return privateIpV4;
    }

    @Override
    public String getPrivateIpV6() {
        return privateIpV6;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String publicHostname() {
        return publicHostname;
    }
}
