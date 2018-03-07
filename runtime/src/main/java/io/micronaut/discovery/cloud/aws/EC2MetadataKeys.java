/*
 * Copyright 2018 original authors
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
 * A enum of Amazon EC2 metadata
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

    private String name;
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
