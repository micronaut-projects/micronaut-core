package org.particleframework.discovery.cloud;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author rvanderwerf
 * @since 1.0
 */
public enum EC2MetadataKeys {

    instanceId("instance-id"),  // always have this first as we use it as a fail fast mechanism
    amiId("ami-id"),
    instanceType("instance-type"),
    localIpv4("local-ipv4"),
    localHostname("local-hostname"),
    availabilityZone("availability-zone", "placement/"),
    publicHostname("public-hostname"),
    publicIpv4("public-ipv4"),
    mac("mac"),  // mac is declared above vpcId so will be found before vpcId (where it is needed)
    vpcId("vpc-id", "network/interfaces/macs/") {
        @Override
        public URL getURL(String prepend, String mac) throws MalformedURLException {
            return new URL(AWS_METADATA_URL + this.path + mac + "/" + this.name);
        }
    },
    accountId("accountId");

    protected String name;
    protected String path;

    EC2MetadataKeys(String name) {
        this(name, "");
    }

    EC2MetadataKeys(String name, String path) {
        this.name = name;
        this.path = path;
    }

    public String getName() {
        return name;
    }

    // override to apply prepend and append
    public URL getURL(String prepend, String append) throws MalformedURLException {
        return new URL(AWS_METADATA_URL + path + name);
    }

    public String toString() {
        return getName();
    }



    public static final String AWS_API_VERSION = "latest";
    public static final String AWS_METADATA_URL = "http://169.254.169.254/" + AWS_API_VERSION + "/meta-data/";


}
