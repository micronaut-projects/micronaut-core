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

import java.io.Serializable;

/**
 * Represents information about a network interface in the Cloud.
 *
 * @author rvanderwerf
 * @author Graeme Rocher
 * @since 1.0
 */
public class NetworkInterface implements Serializable {
    private String ipv4;
    private String ipv6;
    private String name;
    private String mac;
    private String id;
    private String gateway;
    private String network;
    private String netmask;

    /**
     * @return The IPv4 address
     */
    public String getIpv4() {
        return ipv4;
    }

    /**
     * @return The IPv6 address
     */
    public String getIpv6() {
        return ipv6;
    }

    /**
     * @return Name of the Network interface
     */
    public String getName() {
        return name;
    }

    /**
     * @return The MAC address
     */
    public String getMac() {
        return mac;
    }

    /**
     * @return The ID of network interface
     */
    public String getId() {
        return id;
    }

    /**
     * @return The gateway
     */
    public String getGateway() {
        return gateway;
    }

    /**
     * @return The network
     */
    public String getNetwork() {
        return network;
    }

    /**
     * @return The network mask
     */
    public String getNetmask() {
        return netmask;
    }

    /**
     * @param ipv4 The IPv4 address
     */
    protected void setIpv4(String ipv4) {
        this.ipv4 = ipv4;
    }

    /**
     * @param ipv6 The IPv6 address
     */
    protected void setIpv6(String ipv6) {
        this.ipv6 = ipv6;
    }

    /**
     * @param name The name
     */
    protected void setName(String name) {
        this.name = name;
    }

    /**
     * @param mac The MAC address
     */
    protected void setMac(String mac) {
        this.mac = mac;
    }

    /**
     * @param id The Id of network interface
     */
    protected void setId(String id) {
        this.id = id;
    }

    /**
     * @param gateway The network gateway
     */
    protected void setGateway(String gateway) {
        this.gateway = gateway;
    }

    /**
     * @param network The network
     */
    protected void setNetwork(String network) {
        this.network = network;
    }

    /**
     * @param netmask The network mask
     */
    protected void setNetmask(String netmask) {
        this.netmask = netmask;
    }
}
