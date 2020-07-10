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
package io.micronaut.management.endpoint.loggers;

import org.reactivestreams.Publisher;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * Used to retrieve and update logger information for the {@link LoggersEndpoint}.
 *
 * @param <T> The type
 * @author Matthew Moss
 * @since 1.0
 */
public interface LoggersManager<T> {

    /**
     * Collect all existing loggers in the system.
     *
     * @param loggingSystem The {@link ManagedLoggingSystem} in use
     * @return A {@link Publisher} of <code>T</code>
     */
    Publisher<T> getLoggers(ManagedLoggingSystem loggingSystem);

    /**
     * Find (or create if not found) the named logger in the system.
     *
     * @param loggingSystem The {@link ManagedLoggingSystem} in use
     * @param name The name of the logger to find or create
     * @return A {@link Publisher} of <code>T</code>
     */
    Publisher<T> getLogger(ManagedLoggingSystem loggingSystem, @NotBlank String name);


    /**
     * Set the log level for the named logger in the system.
     *
     * @param loggingSystem The {@link ManagedLoggingSystem} in use
     * @param name The name of the logger to find or create
     * @param level The log level to configure
     */
    void setLogLevel(ManagedLoggingSystem loggingSystem, @NotBlank String name,
                             @NotNull io.micronaut.logging.LogLevel level);
}
