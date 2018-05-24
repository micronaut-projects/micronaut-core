/*
 * Copyright 2017-2018 original authors
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

import io.micronaut.context.env.ComputePlatform;
import io.micronaut.discovery.cloud.ComputeInstanceMetadata;
import io.micronaut.discovery.cloud.NetworkInterface;

import java.util.List;
import java.util.Map;

/**
 * Represents {@link ComputeInstanceMetadata} for Amazon's EC2.
 *
 * @author rvanderwerf
 * @author Graeme Rocher
 * @since 1.0
 */
public class AmazonEC2InstanceMetadata implements ComputeInstanceMetadata {

    final ComputePlatform computePlatform = ComputePlatform.AMAZON_EC2;

    // anything non-standard goes in here
    Map<String, String> metadata;

    //network interfaces to get ip addresses
    List<NetworkInterface> interfaces;

    String availabilityZone;
    String localHostname;
    String publicHostname;
    String description;
    String machineType;
    String instanceId;
    String region;
    String account;
    String imageId;

    // should we keep these broken out or require people to look in the interfaces?
    String publicIpV4;
    String publicIpV6;
    String privateIpV4;
    String privateIpV6;

    boolean cached = false;

    // quick way to lookup tags
    private Map<String, String> tags;

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
    public ComputePlatform getComputePlatform() {
        return computePlatform;
    }

    @Override
    public Map<String, String> getTags() {
        return tags;
    }

    @Override
    public String getName() {
        return getInstanceId();
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
    public String getRegion() {
        return region;
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
    public boolean isCached() {
        return cached;
    }
}
