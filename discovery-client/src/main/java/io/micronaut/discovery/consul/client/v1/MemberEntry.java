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
package io.micronaut.discovery.consul.client.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.micronaut.core.annotation.Introspected;

import java.net.InetAddress;
import java.util.Map;

/**
 * A member entry of a Consul cluster. See https://www.consul.io/api/agent.html
 * @author Álvaro Sánchez-Mariscal
 */
@JsonNaming(PropertyNamingStrategy.UpperCamelCaseStrategy.class)
@Introspected
public class MemberEntry {

    private String name;
    private InetAddress address;
    private Integer port;
    private Map<String, String> tags;
    private Integer status;

    /**
     * @return The name of this memeber
     */
    public String getName() {
        return name;
    }

    /**
     * @param name Name of this member
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return The {@link InetAddress} of this member
     */
    public InetAddress getAddress() {
        return address;
    }

    /**
     * @param address The {@link InetAddress} of this member
     */
    @JsonProperty("Addr")
    public void setAddress(InetAddress address) {
        this.address = address;
    }

    /**
     * @return The port this member is listening on
     */
    public Integer getPort() {
        return port;
    }

    /**
     * @param port Listening port
     */
    public void setPort(Integer port) {
        this.port = port;
    }

    /**
     * @return Tags associated with this member
     */
    public Map<String, String> getTags() {
        return tags;
    }

    /**
     * @param tags Tags associated with this member
     */
    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    /**
     * @return Status of this member
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * @param status Status of this member
     */
    public void setStatus(Integer status) {
        this.status = status;
    }
}
