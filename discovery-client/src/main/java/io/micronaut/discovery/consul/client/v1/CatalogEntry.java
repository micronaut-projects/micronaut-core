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
package io.micronaut.discovery.consul.client.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.net.InetAddress;
import java.util.Map;
import java.util.Optional;

/**
 * A catalog entry in Consul. See https://www.consul.io/api/catalog.html.
 *
 * @author graemerocher
 * @since 1.0
 */
@JsonNaming(PropertyNamingStrategy.UpperCamelCaseStrategy.class)
public class CatalogEntry extends NodeEntry {
    private NewServiceEntry service;

    /**
     * Create a new catalog entry.
     *
     * @param nodeId  The node ID
     * @param address The node address
     */
    @JsonCreator
    public CatalogEntry(@JsonProperty("Node") String nodeId, @JsonProperty("Address") InetAddress address) {
        super(nodeId, address);
    }

    @Override
    public CatalogEntry datacenter(String datacenter) {
        return (CatalogEntry) super.datacenter(datacenter);
    }

    @Override
    public CatalogEntry taggedAddresses(Map<String, String> taggedAddresses) {
        return (CatalogEntry) super.taggedAddresses(taggedAddresses);
    }

    @Override
    public CatalogEntry nodeMetadata(Map<String, String> nodeMetadata) {
        return (CatalogEntry) super.nodeMetadata(nodeMetadata);
    }

    /**
     * See https://www.consul.io/api/catalog.html#service.
     *
     * @return The service
     */
    public Optional<NewServiceEntry> getService() {
        return Optional.ofNullable(service);
    }

    /**
     * See https://www.consul.io/api/catalog.html#service.
     *
     * @param service The service
     */
    public void setService(NewServiceEntry service) {
        this.service = service;
    }

    /**
     * @param service The service
     * @return The {@link CatalogEntry} instance
     */
    public CatalogEntry service(NewServiceEntry service) {
        this.service = service;
        return this;
    }
}
