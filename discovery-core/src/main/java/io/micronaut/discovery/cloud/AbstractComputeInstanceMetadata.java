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
package io.micronaut.discovery.cloud;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Abstract class representing a cloud computing instance metadata.
 *
 * @author Alvaro Sanchez-Mariscal
 * @since 1.1
 */
public abstract class AbstractComputeInstanceMetadata implements ComputeInstanceMetadata {

    protected String region;
    protected String availabilityZone;

    //network interfaces to get ip addresses
    private List<NetworkInterface> interfaces = Collections.emptyList();

    // anything non-standard goes in here
    private Map<String, String> metadata;

    private String name;
    private String localHostname;
    private String publicHostname;
    private String description;
    private String machineType;
    private String instanceId;
    private String account;
    private String imageId;

    // should we keep these broken out or require people to look in the interfaces?
    private String publicIpV4;
    private String publicIpV6;
    private String privateIpV4;
    private String privateIpV6;

    private boolean cached = false;

    // quick way to lookup tags
    private Map<String, String> tags = Collections.emptyMap();

    @Override
    public String getImageId() {
        return imageId;
    }

    @Override
    public String getAccount() {
        return account;
    }

    @Override
    public Map<String, String> getMetadata() {
        return metadata;
    }

    @Override
    public List<NetworkInterface> getInterfaces() {
        return interfaces;
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
    public String getRegion() {
        return region;
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
    public String getLocalHostname() {
        return localHostname;
    }

    @Override
    public String getPrivateHostname() {
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
    public String getPublicHostname() {
        return publicHostname;
    }

    @Override
    @JsonIgnore
    public boolean isCached() {
        return cached;
    }

    /**
     * @param interfaces the list of interfaces
     */
    public void setInterfaces(List<NetworkInterface> interfaces) {
        this.interfaces = interfaces;
    }

    /**
     * @param metadata key/value metadata
     */
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    /**
     * @param name instance name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @param availabilityZone the availability zone
     */
    public void setAvailabilityZone(String availabilityZone) {
        this.availabilityZone = availabilityZone;
    }

    /**
     * @param localHostname the local host name
     */
    public void setLocalHostname(String localHostname) {
        this.localHostname = localHostname;
    }

    /**
     * @param publicHostname the public host name
     */
    public void setPublicHostname(String publicHostname) {
        this.publicHostname = publicHostname;
    }

    /**
     * @param description the description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @param machineType the machine type
     */
    public void setMachineType(String machineType) {
        this.machineType = machineType;
    }

    /**
     * @param instanceId the instance ID
     */
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    /**
     * @param region the region
     */
    public void setRegion(String region) {
        this.region = region;
    }

    /**
     * @param account the account
     */
    public void setAccount(String account) {
        this.account = account;
    }

    /**
     * @param imageId the image ID
     */
    public void setImageId(String imageId) {
        this.imageId = imageId;
    }

    /**
     * @param publicIpV4 the public IPV4
     */
    public void setPublicIpV4(String publicIpV4) {
        this.publicIpV4 = publicIpV4;
    }

    /**
     * @param publicIpV6 the public IPV6
     */
    public void setPublicIpV6(String publicIpV6) {
        this.publicIpV6 = publicIpV6;
    }

    /**
     * @param privateIpV4 the private IPV4
     */
    public void setPrivateIpV4(String privateIpV4) {
        this.privateIpV4 = privateIpV4;
    }

    /**
     * @param privateIpV6 the private IPV4
     */
    public void setPrivateIpV6(String privateIpV6) {
        this.privateIpV6 = privateIpV6;
    }

    /**
     * @param cached whether this instance is cached
     */
    @JsonIgnore
    public void setCached(boolean cached) {
        this.cached = cached;
    }

    /**
     * @param tags the instance tags
     */
    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

}
