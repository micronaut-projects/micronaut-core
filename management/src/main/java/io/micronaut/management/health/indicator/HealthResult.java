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
package io.micronaut.management.health.indicator;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.micronaut.health.HealthStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>Used to represent the output of a {@link HealthIndicator}.</p>
 *
 * @author James Kleeh
 * @since 1.0
 */
@JsonDeserialize(as = DefaultHealthResult.class)
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
     * Creates a builder to build a {@link HealthResult}.
     *
     * @param name   The name of the result
     * @param status The status
     * @return The builder
     */
    static Builder builder(String name, HealthStatus status) {
        return new Builder(name, status);
    }

    /**
     * Creates a builder to build a {@link HealthResult}.
     *
     * @param name The name of the result
     * @return The builder
     */
    static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Helper class to build instances.
     */
    class Builder {
        private static final Logger LOG = LoggerFactory.getLogger(HealthResult.class);
        private final String name;
        private HealthStatus status;
        private Object details;

        /**
         * @param name   The name of the health result
         * @param status The status
         */
        Builder(String name, HealthStatus status) {
            this.name = name;
            this.status = status;
        }

        /**
         * @param name   The name of the health result
         */
        Builder(String name) {
            this.name = name;
        }

        /**
         * Assigns the status to the builder.
         *
         * @param status The status, null allowed
         * @return The builder
         */
        public Builder status(HealthStatus status) {
            this.status = status;
            return this;
        }

        /**
         * Builds the details based off an exception.
         *
         * @param ex The exception that occurred
         * @return The builder
         */
        public Builder exception(@NotNull Throwable ex) {
            Map<String, String> error = new HashMap<>(1);
            final String message = ex.getClass().getName() + ": " + ex.getMessage();
            error.put("error", message);
            if (LOG.isErrorEnabled()) {
                LOG.error("Health indicator [" + name + "] reported exception: " + message, ex);
            }
            return details(error);
        }

        /**
         * Sets the details of the result.
         *
         * @param details The details, null allowed
         * @return The builder
         */
        public Builder details(Object details) {
            this.details = details;
            return this;
        }

        /**
         * Builds the result.
         *
         * @return The {@link HealthResult}
         */
        public HealthResult build() {
            return new DefaultHealthResult(
                    name,
                    status != null ? status : HealthStatus.UNKNOWN,
                    details
            );
        }
    }
}
