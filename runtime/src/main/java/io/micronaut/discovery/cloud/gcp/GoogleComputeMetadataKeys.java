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
package io.micronaut.discovery.cloud.gcp;

/**
 * Models common Google compute instance metadata keys.
 *
 * @author rvanderwerf
 * @author Graeme Rocher
 * @since 1.0
 */
public enum GoogleComputeMetadataKeys {

    DESCRIPTION("description"),
    HOSTNAME("hostname"),
    ID("id"),
    ATTRIBUTES("attributes"),
    CPU_PLATFORM("cpuPlatform"),
    DISKS("disks"),
    DNS_SERVERS("dnsServers"),
    FORWARDED_IPS("forewardedIps"),
    GATEWAY("gateway"),
    IP("ip"),
    IP_ALIASES("ipAliases"),
    MAC("mac"),
    NETWORK("network"),
    SCOPES("scopes"),
    MACHINE_TYPE("machineType"),
    MAINTENANCE_EVENT("maintenanceEvent"),
    NAME("name"),
    NETWORK_INTERFACES("networkInterfaces"),
    SERVICE_ACCOUNTS("serviceAccounts"),
    DEFAULTS("default"),
    PROJECT_ID("projectId"),
    NUMERIC_PROJECT_ID("numericProjectId"),
    ZONE("zone"),
    TAGS("tags"),
    VIRTUAL_CLOCK("virtualClock"),
    IMAGE("image"),
    LICENSES("licenses"),
    ACCESS_CONFIGS("accessConfigs"),
    NETMASK("subnetmask");

    private final String name;

    /**
     * @param name The name of the metadata key represented in AWS Metadata.
     */
    GoogleComputeMetadataKeys(String name) {
        this.name = name;
    }

    /**
     * @return The name of the metadata key represented in AWS Metadata.
     */
    public String getName() {
        return name;
    }
}
