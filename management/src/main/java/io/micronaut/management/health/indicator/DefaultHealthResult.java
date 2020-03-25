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
package io.micronaut.management.health.indicator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.health.HealthStatus;

import java.util.Map;

/**
 * Default implementation of the {@link HealthResult} interface.
 *
 * @author graemerocher
 * @since 1.0
 */
@Introspected
class DefaultHealthResult implements HealthResult {
    private final String name;
    private final HealthStatus healthStatus;
    private final Object details;

    /**
     * Default constructor.
     *
     * @param name The name
     * @param healthStatus The health status
     * @param details The details
     */
    DefaultHealthResult(String name, HealthStatus healthStatus, Object details) {
        this.name = name;
        this.healthStatus = healthStatus;
        this.details = details;
    }

    /**
     * JSON specific constructor.
     *
     * @param name The name
     * @param status The status
     * @param details The details
     */
    @JsonCreator
    DefaultHealthResult(
            @JsonProperty("name") String name,
            @JsonProperty("status") String status,
            @JsonProperty("details") Map<String, Object> details) {
        this.name = name;
        switch (status) {
            case HealthStatus.NAME_DOWN:
                this.healthStatus = HealthStatus.DOWN;
            break;
            case HealthStatus.NAME_UP:
                this.healthStatus = HealthStatus.UP;
            break;
            default:
                this.healthStatus = new HealthStatus(status);
        }
        this.details = details;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public HealthStatus getStatus() {
        return this.healthStatus;
    }

    @Override
    public Object getDetails() {
        return this.details;
    }
}
