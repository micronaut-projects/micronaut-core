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
package io.micronaut.management.endpoint.loggers;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Collection;

/**
 * Abstraction for a logging system.
 *
 * @author Matthew Moss
 * @since 1.0
 */
public interface LoggingSystem extends io.micronaut.logging.LoggingSystem {

    /**
     * Returns all existing loggers.
     *
     * @return A {@link Collection} of {@link LoggerConfiguration} instances for all existing loggers
     */
    @NonNull Collection<LoggerConfiguration> getLoggers();

    /**
     * Returns a {@link LoggerConfiguration} for the logger found by name (or created if not found).
     *
     * @param name the logger name
     * @return the logger configuration
     */
    @NonNull LoggerConfiguration getLogger(@NotBlank String name);

    /**
     * Set the log level for the logger found by name (or created if not found).
     *
     * @param name the logger name
     * @param level the log level to set on the named logger
     * @deprecated Use {@link io.micronaut.logging.LoggingSystem#setLogLevel(String, io.micronaut.logging.LogLevel)} instead
     */
    @Deprecated
    void setLogLevel(@NotBlank String name, @NotNull LogLevel level);

    @Override
    default void setLogLevel(@NotBlank String name, io.micronaut.logging.@NotNull LogLevel level) {
        if (level != null) {
            setLogLevel(name, LogLevel.valueOf(level.name()));
        }
    }
}
