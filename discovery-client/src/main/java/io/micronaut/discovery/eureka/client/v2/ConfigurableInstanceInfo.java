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
package io.micronaut.discovery.eureka.client.v2;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * Interface implemented by {@link InstanceInfo} modelling the data returned by the Eureka REST API.
 * <p>
 * See https://github.com/Netflix/eureka/wiki/Eureka-REST-operations
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ConfigurableInstanceInfo {

    /**
     * The host name of the application instance.
     *
     * @return The hostname
     */
    @NotBlank String getHostName();

    /**
     * Returns the unique id of the instance.
     * (Note) now that id is set at creation time within the instanceProvider, why do the other checks?
     * This is still necessary for backwards compatibility when upgrading in a deployment with multiple
     * client versions (some with the change, some without).
     *
     * @return the unique id.
     */
    String getId();

    /**
     * The port of the application instance.
     *
     * @return The port
     */
    int getPort();

    /**
     * Sets the port of the application instance.
     *
     * @param port The port of the application instance
     */
    void setPort(int port);

    /**
     * The secure port of the application instance.
     *
     * @return The secure port
     */
    int getSecurePort();

    /**
     * Sets the secure port of the application instance.
     *
     * @param securePort The secure port of the application instance
     */
    void setSecurePort(int securePort);

    /**
     * The application name.
     *
     * @return The application name
     */
    @NotBlank String getApp();

    /**
     * The application group name.
     *
     * @return The application group name
     */
    String getAppGroupName();

    /**
     * Sets the application group name.
     *
     * @param appGroupName The application group name
     */
    void setAppGroupName(String appGroupName);

    /**
     * The instance id.
     *
     * @return The instance id
     */
    @NotBlank String getInstanceId();

    /**
     * Sets the instance ID.
     *
     * @param instanceId The instance ID
     */
    void setInstanceId(String instanceId);

    /**
     * The country id.
     *
     * @return The country id
     */
    @Min(1L) int getCountryId();

    /**
     * Sets the country id.
     *
     * @param countryId The country id
     */
    void setCountryId(int countryId);

    /**
     * The IP address of the instance.
     *
     * @return The IP address
     */
    @NotBlank String getIpAddr();

    /**
     * Sets the IP address of the instance.
     *
     * @param ipAddr The IP address of the instance
     */
    void setIpAddr(String ipAddr);

    /**
     * @return The application status
     */
    @NotNull InstanceInfo.Status getStatus();

    /**
     * Sets the application status.
     *
     * @param status The application status
     */
    void setStatus(InstanceInfo.Status status);

    /**
     * The {@link DataCenterInfo} instance.
     *
     * @return The data center info
     */
    @NotNull DataCenterInfo getDataCenterInfo();

    /**
     * Sets the {@link DataCenterInfo}.
     *
     * @param dataCenterInfo The {@link DataCenterInfo}
     */
    void setDataCenterInfo(DataCenterInfo dataCenterInfo);

    /**
     * The {@link LeaseInfo} instance.
     *
     * @return The lease info
     */
    LeaseInfo getLeaseInfo();

    /**
     * Sets the {@link LeaseInfo}.
     *
     * @param leaseInfo The {@link LeaseInfo}
     */
    void setLeaseInfo(LeaseInfo leaseInfo);

    /**
     * @return The instance metadata
     */
    Map<String, String> getMetadata();

    /**
     * @return The status page URL
     */
    String getStatusPageUrl();

    /**
     * Sets the status page URL.
     *
     * @param statusPageUrl The status page URL
     */
    void setStatusPageUrl(String statusPageUrl);

    /**
     * @return The home page URL
     */
    String getHomePageUrl();

    /**
     * Sets the home page URL.
     *
     * @param homePageUrl The home page URL
     */
    void setHomePageUrl(String homePageUrl);

    /**
     * @return The health check URL
     */
    String getHealthCheckUrl();

    /**
     * Sets the health check URL.
     *
     * @param healthCheckUrl The health check URL
     */
    void setHealthCheckUrl(String healthCheckUrl);

    /**
     * @return The Virtual Host Address for this instance (defaults to the app name).
     */
    String getVipAddress();

    /**
     * Sets the Virtual Host Address.
     *
     * @param vipAddress The Virtual Host Address
     */
    void setVipAddress(String vipAddress);

    /**
     * @return The Secure Virtual Host Address for this instance (defaults to the app name)
     */
    String getSecureVipAddress();

    /**
     * Sets the Secure Virtual Host Address.
     *
     * @param secureVipAddress The Secure Virtual Host Address
     */
    void setSecureVipAddress(String secureVipAddress);

    /**
     * @return The secure health check URL
     */
    String getSecureHealthCheckUrl();

    /**
     * Sets the secure health check URL.
     *
     * @param secureHealthCheckUrl The secure health check URL
     */
    void setSecureHealthCheckUrl(String secureHealthCheckUrl);

    /**
     * @return The amazon auto scaling group name
     */
    String getAsgName();

    /**
     * Sets the Amazon auto scaling group name to use.
     *
     * @param asgName The Amazon auto scaling group name to use
     */
    void setAsgName(String asgName);
}
