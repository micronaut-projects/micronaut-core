package io.micronaut.context.env;

public enum ComputePlatform {

    /**
     * Google Compute Platform
     */
    GOOGLE_COMPUTE,
    /**
     * Amazon EC2
     */
    AMAZON_EC2,
    /**
     * Microsoft Azure
     */
    AZURE,
    /**
     * Cloud or non cloud provider on bare metal (unknown)
     */
    BARE_METAL,
    /**
     * IBM Cloud
     */
    IBM,
    /**
     * Other
     */
    OTHER;
}
