/*
 * Copyright 2017 original authors
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
package org.particleframework.management.endpoint.health;

import org.particleframework.health.HealthStatus;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * <p>Used to represent the output of a {@link org.particleframework.management.endpoint.health.indicator.HealthIndicator}.</p>
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface HealthResult {

    /**
     * @return The name associated with the details
     */
    String getName();

    /**
     * @return The status of the result
     */
    HealthStatus getStatus();

    /**
     * @return Any data to be returned
     */
    Object getDetails();

    /**
     * Creates a builder to build a {@link HealthResult}
     * @param name The name of the result
     * @param status The status
     * @return The builder
     */
    static Builder builder(String name, HealthStatus status) {
        return new Builder(name, status);
    }

    /**
     * Creates a builder to build a {@link HealthResult}
     * @param name The name of the result
     * @return The builder
     */
    static Builder builder(String name) {
        return new Builder(name);
    }
    
    class Builder {

        private final String name;
        private Optional<HealthStatus> status;
        private Optional<Object> details;
        
        Builder(String name, HealthStatus status) {
            this.name = name;
            this.status = Optional.ofNullable(status);
            this.details = Optional.empty();
        }

        Builder(String name) {
            this.name = name;
            this.status = Optional.empty();
            this.details = Optional.empty();
        }

        /**
         * Assigns the status to the builder
         *
         * @param status The status, null allowed
         * @return The builder
         */
        public Builder status(HealthStatus status) {
            this.status = Optional.ofNullable(status);
            return this;
        }

        /**
         * Builds the details based off an exception
         *
         * @param ex The exception that occurred
         * @return The builder
         */
        public Builder exception(@NotNull Throwable ex) {
            Map<String, String> error = new HashMap<>(1);
            error.put("error", ex.getClass().getName() + ": " + ex.getMessage());
            return details(error);
        }


        /**
         * Sets the details of the result
         *
         * @param details The details, null allowed
         * @return The builder
         */
        public Builder details(Object details) {
            this.details = Optional.ofNullable(details);
            return this;
        }

        /**
         * Builds the result
         * @return The {@link HealthResult}
         */
        public HealthResult build() {
            return new HealthResult() {
                @Override
                public String getName() {
                    return name;
                }

                @Override
                public HealthStatus getStatus() {
                    return status.orElse(HealthStatus.UNKNOWN);
                }

                @Override
                public Object getDetails() {
                    return details.orElse(null);
                }
            };
        }
    }
}
