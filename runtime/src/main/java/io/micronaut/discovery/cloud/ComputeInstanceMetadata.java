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
package io.micronaut.discovery.cloud;

import io.micronaut.context.env.ComputePlatform;

import java.util.List;
import java.util.Map;

/**
 * An interface modelling common Cloud platform compute instance metadata.
 *
 * @author rvanderwerf
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ComputeInstanceMetadata {

    /**
     * The metadata as a map.
     *
     * @return A map of metadata
     */
    Map<String, String> getMetadata();

    /**
     * The network interfaces attached to the compute instance.
     *
     * @return The {@link NetworkInterface} instances
     */
    List<NetworkInterface> getInterfaces();

    /**
     * @return The {@link ComputePlatform}
     */
    ComputePlatform getComputePlatform();

    /**
     * The tags attached to the instance.
     *
     * @return A map of tags
     */
    Map<String, String> getTags();

    /**
     * The name of the instance. Usually the same as {@link #getInstanceId()}. Some cloud platforms assign unique IDs
     *
     * @return The name of the instance
     */
    String getName();

    /**
     * @return The instance id
     */
    String getInstanceId();

    /**
     * @return The machine type
     */
    String getMachineType();

    /**
     * @return The availability zone of the instance
     */
    String getAvailabilityZone();

    /**
     * @return The region of the instance
     */
    String getRegion();

    /**
     * @return The local host name of the instance
     */
    String getLocalHostname();

    /**
     * @return The private host name of the instance
     */
    String getPrivateHostname();

    /**
     * @return The public host name of the instance
     */
    String getPublicHostname();

    /**
     * @return The public IP of the instance
     */
    String getPublicIpV4();

    /**
     * @return The public IP v6 of the instance
     */
    String getPublicIpV6();

    /**
     * @return The private IP of the instance
     */
    String getPrivateIpV4();

    /**
     * @return The private IP v6 of the instance
     */
    String getPrivateIpV6();

    /**
     * @return A description of the instance
     */
    String getDescription();

    /**
     * @return The account the instance is associated with
     */
    String getAccount();

    /**
     * @return The ID of the image used for the instance
     */
    String getImageId();

    /**
     * @return Is this cached instance metadata
     */
    boolean isCached();
}
