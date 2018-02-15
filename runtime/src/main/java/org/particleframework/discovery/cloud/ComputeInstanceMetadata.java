package org.particleframework.discovery.cloud;

import org.particleframework.context.env.ComputePlatform;

import java.util.List;
import java.util.Map;

public interface ComputeInstanceMetadata {

    public Map<String,String> getMetadata();

    public List<NetworkInterface> getInterfaces();

    public ComputePlatform getComputePlatform();

    public Map<String,String> getTags();

    public String getName();

    public String getInstanceId();

    public String getMachineType();

    public String getAvailabilityZone();

    public String getRegion();

    public String getLocalHostname();

    public String getPrivateHosname();

    public String getPublicIpV4();

    public String getPublicIpV6();

    public String getPrivateIpV4();

    public String getPrivateIpV6();

    public String getDescription();

    public String getAccount();

    public String getImageId();


}
