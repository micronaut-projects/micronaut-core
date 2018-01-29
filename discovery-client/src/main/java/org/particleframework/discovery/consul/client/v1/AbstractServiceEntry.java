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
package org.particleframework.discovery.consul.client.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.particleframework.http.client.exceptions.HttpClientException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

/**
 * @author graemerocher
 * @since 1.0
 */
@JsonNaming(PropertyNamingStrategy.UpperCamelCaseStrategy.class)
public abstract class AbstractServiceEntry {

    protected final String name;
    private InetAddress address;
    private Integer port;
    private List<String> tags;
    private String ID;

    public AbstractServiceEntry(String name) {
        this.name = name;
    }

    /**
     * See https://www.consul.io/api/agent/service.html#id
     *
     * @return The ID of the service
     */
    @JsonProperty("ID")
    public Optional<String> getID() {
        return Optional.ofNullable(ID);
    }

    /**
     * See https://www.consul.io/api/agent/service.html#address
     *
     * @return The address of the service
     */
    public Optional<InetAddress> getAddress() {
        return Optional.ofNullable(address);
    }

    /**
     * See https://www.consul.io/api/agent/service.html#address
     * @return The port of the service
     */
    public OptionalInt getPort() {
        if(port != null)
            return OptionalInt.of(port);
        else
            return OptionalInt.empty();
    }

    /**
     * See https://www.consul.io/api/agent/service.html#tags
     * @return The service tags
     */
    public List<String> getTags() {
        if(tags == null) {
            return Collections.emptyList();
        }
        return tags;
    }

    public void setAddress(InetAddress address) {
        this.address = address;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    @JsonProperty("ID")
    public void setID(String id) {
        this.ID = id;
    }

    /**
     * See https://www.consul.io/api/agent/service.html#name
     *
     * @return The name of the service
     */
    public String getName() {
        return name;
    }

    public AbstractServiceEntry id(String id) {
        this.ID = id;
        return this;
    }

    public AbstractServiceEntry address(InetAddress address) {
        this.address = address;
        return this;
    }

    public AbstractServiceEntry address(String address) {
        try {
            this.address = InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            throw new HttpClientException(e.getMessage(), e);
        }
        return this;
    }

    public AbstractServiceEntry port(Integer port) {
        this.port = port;
        return this;
    }

    public AbstractServiceEntry tags(List<String> tags) {
        this.tags = tags;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractServiceEntry that = (AbstractServiceEntry) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(address, that.address) &&
                Objects.equals(port, that.port) &&
                Objects.equals(tags, that.tags) &&
                Objects.equals(ID, that.ID);
    }

    @Override
    public int hashCode() {

        return Objects.hash(name, address, port, tags, ID);
    }
}
