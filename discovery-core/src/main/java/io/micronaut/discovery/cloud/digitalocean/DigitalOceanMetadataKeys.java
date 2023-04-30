/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.discovery.cloud.digitalocean;

/**
 * A enum of Digital Ocean metadata.
 *
 * @author Alvaro Sanchez-Mariscal
 * @since 1.1
 */
public enum DigitalOceanMetadataKeys {

    DROPLET_ID("droplet_id"),
    HOSTNAME("hostname"),
    VENDOR_DATA("vendor_data"),
    USER_DATA("user_data"),
    PUBLIC_KEYS("public_keys"),
    REGION("region"),
    INTERFACES("interfaces"),
    PRIVATE_INTERFACES("private"),
    PUBLIC_INTERFACES("public"),
    IPV4("ipv4"),
    IPV6("ipv6"),
    MAC("mac"),
    INTERFACE_TYPE("type"),
    IP_ADDRESS("ip_address"),
    NETMASK("netmask"),
    GATEWAY("gateway"),
    CIDR("cidr"),
    FLOATING_IP("floating_ip"),
    FLOATING_IP_ACTIVE("active"),
    DNS("dns"),
    NAMESERVERS("nameservers"),
    FEATURES("features");

    private final String name;

    /**
     * @param name The name of the metadata key represented in Digital Ocean Metadata JSON.
     */
    DigitalOceanMetadataKeys(String name) {
        this.name = name;
    }

    /**
     * @return The name of the metadata key represented in Digital Ocean Metadata JSON.
     */
    public String getName() {
        return name;
    }

}
