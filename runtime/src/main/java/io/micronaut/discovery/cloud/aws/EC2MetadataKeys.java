/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.discovery.cloud.aws;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * A enum of Amazon EC2 metadata.
 *
 * @author rvanderwerf
 * @author Graeme Rocher
 * @since 1.0
 */
public enum EC2MetadataKeys {

    instanceId("instance-id"),  // always have this first as we use it as a fail fast mechanism
    amiId("ami-id"),
    region("region"),
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
            return new URL(AWS_METADATA_URL + this.path + mac + "/" + getName());
        }
    },
    accountId("accountId");

    public static final String AWS_API_VERSION = "latest";
    public static final String AWS_METADATA_URL = "http://169.254.169.254/" + AWS_API_VERSION + "/meta-data/";

    protected String path;

    private String name;

    /**
     * Construct a EC2 Metadata key with the given name.
     *
     * @param name The name of key
     */
    EC2MetadataKeys(String name) {
        this(name, "");
    }

    /**
     * Construct EC2 Metadata key with given name and path.
     *
     * @param name The name of key
     * @param path The path in EC2 Metadata
     */
    EC2MetadataKeys(String name, String path) {
        this.name = name;
        this.path = path;
    }

    /**
     * @return The name of key
     */
    public String getName() {
        return name;
    }

    /**
     * The URL for metadata information.
     * Override to apply prepend and append.
     *
     * @param prepend Building the URL endpoints
     * @param append  Region
     * @return The URL for the Metadata information of specific {@link #name}.
     * @throws MalformedURLException If the URL is invalid
     */
    public URL getURL(String prepend, String append) throws MalformedURLException {
        return new URL(AWS_METADATA_URL + path + name);
    }

    /**
     * Returns the name of this enum constant, as contained in the
     * declaration.  This method may be overridden, though it typically
     * isn't necessary or desirable.  An enum type should override this
     * method when a more "programmer-friendly" string form exists.
     *
     * @return the name of this enum constant
     */
    @Override
    public String toString() {
        return getName();
    }
}
