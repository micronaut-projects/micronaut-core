package io.micronaut.discovery.cloud.digitalocean;

import io.micronaut.context.env.ComputePlatform;
import io.micronaut.discovery.cloud.AbstractComputeInstanceMetadata;

public class DigitalOceanInstanceMetadata extends AbstractComputeInstanceMetadata {

    private final ComputePlatform computePlatform = ComputePlatform.DIGITAL_OCEAN;

    private String userData;
    private String vendorData;

    @Override
    public ComputePlatform getComputePlatform() {
        return computePlatform;
    }

    public String getUserData() {
        return userData;
    }

    public void setUserData(String userData) {
        this.userData = userData;
    }

    public String getVendorData() {
        return vendorData;
    }

    public void setVendorData(String vendorData) {
        this.vendorData = vendorData;
    }

}
