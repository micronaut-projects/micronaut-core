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
package io.micronaut.discovery.cloud.oraclecloud;

/**
 * Models common Oracle Cloud compute instance metadata keys.
 *
 * @author Todd Sharp
 * @since 1.2.0
 */
public enum OracleCloudMetadataKeys {

    AVAILABILITY_DOMAIN("availabilityDomain"),
    FAULT_DOMAIN("faultDomain"),
    ID("id"),
    COMPARTMENT_ID("compartmentId"),
    DISPLAY_NAME("displayName"),
    IMAGE("image"),
    REGION("region"),
    CANONICAL_REGION_NAME("canonicalRegionName"),
    SHAPE("shape"),
    STATE("state"),
    AGENT_CONFIG("agentConfig"),
    MONITORING_DISABLED("monitoringDisabled"),
    USER_METADATA("metadata"),
    MAC("macAddr"),
    VNIC_ID("vnicId"),
    PRIVATE_IP("privateIp"),
    TIME_CREATED("timeCreated");

    private final String name;

    /**
     * @param name The name of the metadata key represented in Oracle Cloud Metadata.
     */
    OracleCloudMetadataKeys(String name) {
        this.name = name;
    }

    /**
     * @return The name of the metadata key represented in Oracle Cloud Metadata.
     */
    public String getName() {
        return name;
    }
}
