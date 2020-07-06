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

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.ReflectiveAccess;

import java.util.Collections;
import java.util.List;

/**
 * Models a Consul Health Entry. See https://www.consul.io/api/health.html.
 *
 * @author graemerocher
 * @since 1.0
 */
@JsonNaming(PropertyNamingStrategy.UpperCamelCaseStrategy.class)
@Introspected
public class HealthEntry {

    private NodeEntry node;
    private ServiceEntry service;
    private List<Check> checks = Collections.emptyList();

    /**
     * @return The node for this health entry
     */
    public NodeEntry getNode() {
        return node;
    }

    /**
     * @return The service for the health entry
     */
    public ServiceEntry getService() {
        return service;
    }

    /**
     * @return The checks
     */
    public List<Check> getChecks() {
        return checks;
    }

    /**
     * @param checks The list of checks
     */
    @JsonDeserialize(contentAs = CheckEntry.class)
    @ReflectiveAccess
    void setChecks(List<Check> checks) {
        this.checks = checks;
    }

    /**
     * @param node The node
     */
    @ReflectiveAccess
    protected void setNode(NodeEntry node) {
        this.node = node;
    }

    /**
     * @param service The service
     */
    @ReflectiveAccess
    protected void setService(ServiceEntry service) {
        this.service = service;
    }
}
