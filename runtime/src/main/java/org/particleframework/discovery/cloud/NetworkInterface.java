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
package org.particleframework.discovery.cloud;

import java.io.Serializable;

/**
 * Represents information about a network interface in the Cloud
 *
 * @author rvanderwerf
 * @author Graeme Rocher
 *
 * @since 1.0
 */
public class NetworkInterface implements Serializable
{
    private String ipv4;
    private String ipv6;
    private String name;
    private String mac;
    private String id;
    private String gateway;
    private String network;
    private String netmask;

    public String getIpv4() {
        return ipv4;
    }

    public String getIpv6() {
        return ipv6;
    }

    public String getName() {
        return name;
    }

    public String getMac() {
        return mac;
    }

    public String getId() {
        return id;
    }

    public String getGateway() {
        return gateway;
    }

    public String getNetwork() {
        return network;
    }

    public String getNetmask() {
        return netmask;
    }

    protected void setIpv4(String ipv4) {
        this.ipv4 = ipv4;
    }

    protected void setIpv6(String ipv6) {
        this.ipv6 = ipv6;
    }

    protected void setName(String name) {
        this.name = name;
    }

    protected void setMac(String mac) {
        this.mac = mac;
    }

    protected void setId(String id) {
        this.id = id;
    }

    protected void setGateway(String gateway) {
        this.gateway = gateway;
    }

    protected void setNetwork(String network) {
        this.network = network;
    }

    protected void setNetmask(String netmask) {
        this.netmask = netmask;
    }
}
