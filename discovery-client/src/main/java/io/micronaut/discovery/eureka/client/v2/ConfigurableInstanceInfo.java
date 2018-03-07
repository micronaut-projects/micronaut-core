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
package io.micronaut.discovery.eureka.client.v2;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * Interface implemented by {@link InstanceInfo} modelling the data returned by the Eureka REST API
 *
 * See https://github.com/Netflix/eureka/wiki/Eureka-REST-operations
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ConfigurableInstanceInfo {
    /**
     * The host name of the application instance
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
     * The port of the application instance
     */

    int getPort();

    /**
     * The secure port of the application instance
     */
    int getSecurePort();

    /**
     * The application name
     */
    @NotBlank String getApp();

    /**
     * The application group name
     */

    String getAppGroupName();

    /**
     * The instance id
     */
    @NotBlank String getInstanceId();

    /**
     * The country id
     */
    @Min(1L) int getCountryId();

    /**
     * The IP address of the instance
     */
    @NotBlank String getIpAddr();

    /**
     * @return The application status
     */
    @NotNull InstanceInfo.Status getStatus();

    /**
     * The {@link DataCenterInfo} instance
     */
    @NotNull DataCenterInfo getDataCenterInfo();

    /**
     * The {@link LeaseInfo} instance
     */
    LeaseInfo getLeaseInfo();

    /**
     * @return The instance metadata
     */
    Map<String, String> getMetadata();

    /**
     * @return The status page URL
     */
    String getStatusPageUrl();

    /**
     * @return The home page URL
     */
    String getHomePageUrl();

    /**
     * @return The health check URL
     */
    String getHealthCheckUrl();

    /**
     * @return The Virtual Host Address for this instance (defaults to the app name)
     */
    String getVipAddress();


    /**
     * @return The Secure Virtual Host Address for this instance (defaults to the app name)
     */
    String getSecureVipAddress();

    /**
     * @return The secure health check URL
     */
    String getSecureHealthCheckUrl();

    /**
     * @return The amazon auto scaling group name
     */
    String getAsgName();

    /**
     * Sets the instance ID
     * @param instanceId The instance ID
     */
    void setInstanceId(String instanceId);

    /**
     * Sets the Amazon auto scaling group name to use
     *
     * @param asgName The Amazon auto scaling group name to use
     */
    void setAsgName(String asgName);

    void setSecurePort(int securePort);

    void setPort(int port);

    void setHomePageUrl(String homePageUrl);

    void setLeaseInfo(LeaseInfo leaseInfo);

    void setCountryId(int countryId);

    void setStatusPageUrl(String statusPageUrl);

    void setHealthCheckUrl(String healthCheckUrl);

    void setSecureHealthCheckUrl(String secureHealthCheckUrl);

    void setDataCenterInfo(DataCenterInfo dataCenterInfo);

    void setStatus(InstanceInfo.Status status);

    void setAppGroupName(String appGroupName);

    void setIpAddr(String ipAddr);

    void setVipAddress(String vipAddress);

    void setSecureVipAddress(String secureVipAddress);
}
