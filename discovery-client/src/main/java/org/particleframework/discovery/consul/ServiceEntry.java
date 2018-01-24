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
package org.particleframework.discovery.consul;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A service entry in Consul. See https://www.consul.io/api/catalog.html#service
 *
 * @author graemerocher
 * @since 1.0
 */
@JsonNaming(PropertyNamingStrategy.UpperCamelCaseStrategy.class)
public class ServiceEntry {

    private final String service;

    @JsonCreator
    public ServiceEntry(@JsonProperty("Service") String service) {
        this.service = service;
    }

    private String address;
    private Integer port;
    private List<String> tags;

    /**
     * @return The name of the service
     */
    public String getService() {
        return service;
    }

    public Optional<String> getAddress() {
        return Optional.ofNullable(address);
    }

    public OptionalInt getPort() {
        if(port != null)
            return OptionalInt.of(port);
        else
            return OptionalInt.empty();
    }

    public List<String> getTags() {
        if(tags == null) {
            return Collections.emptyList();
        }
        return tags;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public ServiceEntry address(String address) {
        this.address = address;
        return this;
    }

    public ServiceEntry port(Integer port) {
        this.port = port;
        return this;
    }

    public ServiceEntry tags(List<String> tags) {
        this.tags = tags;
        return this;
    }
}
