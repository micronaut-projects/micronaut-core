/*
 * Copyright 2017-2019 original authors
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * @author graemerocher
 * @since 1.0
 */
@JsonNaming(PropertyNamingStrategy.UpperCamelCaseStrategy.class)
public class ServiceEntry extends AbstractServiceEntry {

    /**
     * @param name The name
     * @param id   The id
     */
    @JsonCreator
    public ServiceEntry(@Nullable @JsonProperty("Service") String name, @Nullable @JsonProperty("ID") String id) {
        super(name);
        setID(id);
    }

    /**
     * Creates a copy from another entry.
     *
     * @param entry The entry
     */
    public ServiceEntry(AbstractServiceEntry entry) {
        super(entry.getName());
        entry.getID().ifPresent(this::id);
        entry.getAddress().ifPresent(this::address);
        entry.getPort().ifPresent(this::port);
        tags(entry.getTags());
    }

    /**
     * See https://www.consul.io/api/agent/service.html#name.
     *
     * @return The name of the service
     */
    public String getName() {
        return name;
    }
}
