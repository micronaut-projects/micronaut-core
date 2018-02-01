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
package org.particleframework.discovery.eureka.client.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import org.particleframework.core.util.StringUtils;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Represents an application instance in Eureka. See https://github.com/Netflix/eureka/wiki/Eureka-REST-operations
 *
 * Based on https://github.com/Netflix/eureka/blob/master/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java
 *
 * @author graemerocher
 * @since 1.0
 */
@JsonRootName("instance")
public class InstanceInfo {
    public static final int DEFAULT_PORT = 7001;
    public static final int DEFAULT_SECURE_PORT = 7002;
    public static final int DEFAULT_COUNTRY_ID = 1; // US

    private String asgName;
    private String hostName;
    private String app;
    private String instanceId;
    private String ipAddr;
    private String appGroupName;
    private String vipAddress;
    private String secureVipAddress;
    private Status status = Status.UP;
    private int port = DEFAULT_PORT;
    private int securePort = DEFAULT_SECURE_PORT;
    private int countryId = DEFAULT_COUNTRY_ID; // Defaults to US
    private String homePageUrl;
    private String statusPageUrl;
    private String healthCheckUrl;
    private String secureHealthCheckUrl;
    private DataCenterInfo dataCenterInfo = () -> DataCenterInfo.Name.MyOwn;
    private LeaseInfo leaseInfo;
    private Map<String, String> metadata = new ConcurrentHashMap<>();

    /**
     * Based on https://github.com/Netflix/eureka/blob/master/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java
     *
     */
    @JsonCreator
    InstanceInfo(
            @JsonProperty("instanceId") String instanceId,
            @JsonProperty("app") String appName,
            @JsonProperty("appGroupName") String appGroupName,
            @JsonProperty("ipAddr") String ipAddr,
            @JsonProperty("port") PortWrapper port,
            @JsonProperty("securePort") PortWrapper securePort,
            @JsonProperty("homePageUrl") String homePageUrl,
            @JsonProperty("statusPageUrl") String statusPageUrl,
            @JsonProperty("healthCheckUrl") String healthCheckUrl,
            @JsonProperty("secureHealthCheckUrl") String secureHealthCheckUrl,
            @JsonProperty("vipAddress") String vipAddress,
            @JsonProperty("secureVipAddress") String secureVipAddress,
            @JsonProperty("countryId") int countryId,
            @JsonProperty("dataCenterInfo") DataCenterInfo dataCenterInfo,
            @JsonProperty("hostName") String hostName,
            @JsonProperty("status") Status status,
            @JsonProperty("overriddenstatus") Status overriddenstatus,
            @JsonProperty("leaseInfo") LeaseInfo leaseInfo,
            @JsonProperty("metadata") HashMap<String, String> metadata,
            @JsonProperty("asgName") String asgName) {
        this.instanceId = instanceId;
        this.app = appName;
        this.appGroupName = appGroupName;
        this.ipAddr = ipAddr;
        this.port = port == null ? 0 : port.getPort();
        this.securePort = securePort == null ? 0 : securePort.getPort();
        this.homePageUrl = homePageUrl;
        this.statusPageUrl = statusPageUrl;
        this.healthCheckUrl = healthCheckUrl;
        this.secureHealthCheckUrl = secureHealthCheckUrl;
        this.vipAddress = vipAddress;
        this.secureVipAddress = secureVipAddress;
        this.countryId = countryId;
        this.dataCenterInfo = dataCenterInfo;
        this.hostName = hostName;
        this.status = status;
        this.leaseInfo = leaseInfo;
        this.asgName = asgName;

        // ---------------------------------------------------------------
        // for compatibility

        if (metadata == null) {
            this.metadata = Collections.emptyMap();
        } else {
            this.metadata = metadata;
        }
    }

    /**
     * Creates an {@link InstanceInfo}
     *
     * @param host The host name
     * @param appName The application name
     * @param instanceId The instance identifier
     */
    public InstanceInfo(String host, @NotBlank String appName, @NotBlank String instanceId) {
        this(host, DEFAULT_PORT, appName, instanceId);
    }
    /**
     * Creates an {@link InstanceInfo}. The {@link #getInstanceId()} will default to the value of the host
     *
     * @param host The host name
     * @param appName The application name
     */
    public InstanceInfo(String host, @NotBlank String appName) {
        this(host, DEFAULT_PORT, appName, host);
    }
    /**
     * Creates an {@link InstanceInfo}. This constructor will perform an IP Address lookup based on the host name
     *
     * @param host The host name
     * @param port The port
     * @param appName The application name
     * @param instanceId The instance identifier
     */
    public InstanceInfo(String host, int port, @NotBlank String appName, @NotBlank String instanceId) {
        this(host, port, lookupIp(host), appName, instanceId);
    }

    /**
     * Creates an {@link InstanceInfo}.
     *
     * @param host The host name
     * @param port The port
     * @param appName The application name
     * @param instanceId The instance identifier
     */
    public InstanceInfo(String host, int port, String ipAddress, String appName, String instanceId) {
        if(StringUtils.isEmpty(host)) {
            throw new IllegalArgumentException("Argument [hostName] cannot be null or blank");
        }
        if(StringUtils.isEmpty(appName)) {
            throw new IllegalArgumentException("Argument [appName] cannot be null or blank");
        }
        if(StringUtils.isEmpty(instanceId)) {
            throw new IllegalArgumentException("Argument [instanceId] cannot be null or blank");
        }
        if(StringUtils.isEmpty(ipAddress)) {
            throw new IllegalArgumentException("Argument [ipAddress] cannot be null or blank");
        }
        this.hostName = host;
        this.port = port;
        this.ipAddr = ipAddress;
        this.app = appName;
        this.instanceId = instanceId;
    }

    /**
     * The host name of the application instance
     */
    @NotBlank
    public String getHostName() {
        return hostName;
    }

    /**
     * Returns the unique id of the instance.
     * (Note) now that id is set at creation time within the instanceProvider, why do the other checks?
     * This is still necessary for backwards compatibility when upgrading in a deployment with multiple
     * client versions (some with the change, some without).
     *
     * @return the unique id.
     */
    @JsonIgnore
    public String getId() {
        if (instanceId != null && !instanceId.isEmpty()) {
            return instanceId;
        } else if (dataCenterInfo instanceof AmazonInfo) {
            String uniqueId = ((AmazonInfo) dataCenterInfo).getId();
            if (uniqueId != null && !uniqueId.isEmpty()) {
                return uniqueId;
            }
        }
        return hostName;
    }
    /**
     * The port of the application instance
     */
    @JsonIgnore
    public int getPort() {
        return port;
    }
    /**
     * The secure port of the application instance
     */
    @JsonIgnore
    public int getSecurePort() {
        return securePort;
    }

    @JsonProperty("port")
    PortWrapper getPortWrapper() {
        if(port < 1) {
            return new PortWrapper(false, 0);
        }
        return new PortWrapper(true, port);
    }

    @JsonProperty("securePort")
    PortWrapper getSecurePortWrapper() {
        if(securePort < 1) {
            return new PortWrapper(false, 0);
        }
        return new PortWrapper(true, securePort);
    }

    public void setSecurePort(int securePort) {
        this.securePort = securePort;
    }

    public void setPort(int port) {
        if(port >= 0) {
            this.port = port;
        }
    }

    /**
     * The application name
     */
    @NotBlank
    public String getApp() {
        return app;
    }

    /**
     * The application group name
     */
    public String getAppGroupName() {
        return appGroupName;
    }

    /**
     * The instance id
     */
    @NotBlank
    public String getInstanceId() {
        return instanceId;
    }

    /**
     * The country id
     */
    @Min(1L)
    public int getCountryId() {
        return countryId;
    }

    /**
     * The IP address of the instance
     */
    @NotBlank
    public String getIpAddr() {
        return ipAddr;
    }

    @NotNull
    public Status getStatus() {
        return status;
    }

    /**
     * The {@link DataCenterInfo} instance
     */
    @NotNull
    public DataCenterInfo getDataCenterInfo() {
        return dataCenterInfo;
    }

    /**
     * The {@link LeaseInfo} instance
     */
    public LeaseInfo getLeaseInfo() {
        return leaseInfo;
    }

    /**
     * @return The instance metadata
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * @return The status page URL
     */
    public String getStatusPageUrl() {
        if(this.statusPageUrl == null) {
            return getHealthCheckUrl();
        }
        return statusPageUrl;
    }

    /**
     * @return The home page URL
     */
    public String getHomePageUrl() {
        if(this.homePageUrl == null) {
            return "http://" + this.hostName + portString();
        }
        return homePageUrl;
    }

    /**
     * @return The health check URL
     */
    public String getHealthCheckUrl() {
        if(this.healthCheckUrl == null) {
            return "http://" + this.hostName + portString() + "/health";
        }
        return healthCheckUrl;
    }

    /**
     * @return The Virtual Host Address for this instance (defaults to the app name)
     */
    @NotBlank
    public String getVipAddress() {
        if(this.vipAddress == null) {
            return this.app;
        }
        return vipAddress;
    }

    /**
     * @return The Secure Virtual Host Address for this instance (defaults to the app name)
     */
    @NotBlank
    public String getSecureVipAddress() {
        if(this.secureVipAddress == null) {
            return this.app;
        }
        return secureVipAddress;
    }

    /**
     * @return The secure health check URL
     */
    public String getSecureHealthCheckUrl() {
        if(this.secureHealthCheckUrl == null) {
            return "https://" + this.hostName + securePortString() + "/health";
        }
        return secureHealthCheckUrl;
    }

    /**
     * @return The amazon auto scaling group name
     */
    public String getAsgName() {
        return asgName;
    }

    public void setAsgName(String asgName) {
        this.asgName = asgName;
    }

    public void setHomePageUrl(String homePageUrl) {
        if(!StringUtils.isEmpty(homePageUrl)) {
            this.homePageUrl = homePageUrl;
        }
    }

    public void setLeaseInfo(LeaseInfo leaseInfo) {
        this.leaseInfo = leaseInfo;
    }

    public void setCountryId(int countryId) {
        if(countryId > 0) {
            this.countryId = countryId;
        }
    }

    public void setStatusPageUrl(String statusPageUrl) {
        if(!StringUtils.isEmpty(statusPageUrl)) {
            this.statusPageUrl = statusPageUrl;
        }
    }

    public void setHealthCheckUrl(String healthCheckUrl) {
        if(!StringUtils.isEmpty(healthCheckUrl)) {
            this.healthCheckUrl = healthCheckUrl;
        }
    }

    public void setSecureHealthCheckUrl(String secureHealthCheckUrl) {
        if(!StringUtils.isEmpty(secureHealthCheckUrl)) {
            this.secureHealthCheckUrl = secureHealthCheckUrl;
        }
    }

    public void setDataCenterInfo(DataCenterInfo dataCenterInfo) {
        if(dataCenterInfo != null) {
            this.dataCenterInfo = dataCenterInfo;
        }
    }

    public void setStatus(Status status) {
        if(status != null) {
            this.status = status;
        }
    }

    public void setAppGroupName(String appGroupName) {
        if(StringUtils.isNotEmpty(appGroupName)) {
            this.appGroupName = appGroupName;
        }
    }

    public void setIpAddr(String ipAddr) {
        if(StringUtils.isNotEmpty(ipAddr)) {
            this.ipAddr = ipAddr;
        }
    }

    public void setVipAddress(String vipAddress) {
        if(StringUtils.isNotEmpty(vipAddress)) {
            this.vipAddress = vipAddress;
        }
    }

    public void setSecureVipAddress(String secureVipAddress) {
        if(StringUtils.isNotEmpty(secureVipAddress)) {
            this.secureVipAddress = secureVipAddress;
        }
    }



    /**
     * The instance status according to Eureka
     */
    public enum Status {
        UP, DOWN, STARTING, OUT_OF_SERVICE, UNKNOWN;

    }
    /**
     * {@link com.netflix.appinfo.InstanceInfo} JSON and XML format for port information does not follow the usual conventions, which
     * makes its mapping complicated. This class represents the wire format for port information.
     */
    static class PortWrapper {

        private final boolean enabled;
        private final int port;
        @JsonCreator
        public PortWrapper(@JsonProperty("@enabled") boolean enabled, @JsonProperty("$") int port) {
            this.enabled = enabled;
            this.port = port;
        }

        @JsonProperty("@enabled")
        public boolean isEnabled() {
            return enabled;
        }

        @JsonProperty("$")
        public int getPort() {
            return port;
        }

    }
    private String portString() {
        return port > 0  ? ":" + this.port: "";
    }

    private String securePortString() {
        return securePort > 0  ? ":" + this.securePort: "";
    }

    private static String lookupIp(String host)  {
        try {
            return InetAddress.getByName(host).getHostAddress();
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Unable to lookup host IP address: " + host, e);
        }
    }

}
