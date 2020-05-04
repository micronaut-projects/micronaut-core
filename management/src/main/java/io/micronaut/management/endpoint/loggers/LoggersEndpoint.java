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
package io.micronaut.management.endpoint.loggers;

import io.micronaut.core.bind.exceptions.UnsatisfiedArgumentException;
import io.micronaut.core.type.Argument;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.EndpointConfiguration;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.management.endpoint.annotation.Selector;
import io.micronaut.management.endpoint.annotation.Write;
import io.reactivex.Single;

import edu.umd.cs.findbugs.annotations.Nullable;
import javax.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Exposes an {@link Endpoint} to manage loggers.
 *
 * @author Matthew Moss
 * @since 1.0
 */
@Endpoint(id = LoggersEndpoint.NAME,
        defaultEnabled = LoggersEndpoint.DEFAULT_ENABLED,
        defaultSensitive = LoggersEndpoint.DEFAULT_SENSITIVE)
public class LoggersEndpoint {

    /**
     * Endpoint name.
     */
    public static final String NAME = "loggers";

    /**
     * Endpoint configuration prefix.
     */
    public static final String PREFIX = EndpointConfiguration.PREFIX + "." + NAME;

    /**
     * Endpoint default enabled.
     */
    public static final boolean DEFAULT_ENABLED = false;

    /**
     * Endpoint default sensitivity.
     */
    public static final boolean DEFAULT_SENSITIVE = false;

    private final LoggingSystem loggingSystem;
    private final LoggersManager<Map<String, Object>> loggersManager;

    /**
     * @param loggingSystem the {@link LoggingSystem}
     * @param loggersManager the {@link LoggersManager}
     */
    public LoggersEndpoint(LoggingSystem loggingSystem,
                           LoggersManager<Map<String, Object>> loggersManager) {
        this.loggingSystem = loggingSystem;
        this.loggersManager = loggersManager;
    }

    /**
     * @return the loggers as a {@link Single}
     */
    @Read
    public Single<Map<String, Object>> loggers() {
        return Single.fromPublisher(loggersManager.getLoggers(loggingSystem));
    }

    /**
     * @param name The name of the logger to find
     * @return the {@link io.micronaut.logging.LogLevel} (both configured and effective) of the named logger
     */
    @Read
    public Single<Map<String, Object>> logger(@NotBlank @Selector String name) {
        return Single.fromPublisher(loggersManager.getLogger(loggingSystem, name));
    }

    /**
     * @param name The name of the logger to configure
     * @param configuredLevel The {@link io.micronaut.logging.LogLevel} to set on the named logger
     */
    @Write
    public void setLogLevel(@NotBlank @Selector String name,
                            @Nullable io.micronaut.logging.LogLevel configuredLevel) {
        try {
            loggersManager.setLogLevel(loggingSystem, name,
                    configuredLevel != null ? configuredLevel : io.micronaut.logging.LogLevel.NOT_SPECIFIED);
        } catch (IllegalArgumentException ex) {
            throw new UnsatisfiedArgumentException(
                    Argument.of(io.micronaut.logging.LogLevel.class, "configuredLevel"),
                    "Invalid log level specified: " + configuredLevel
            );
        }
    }

}
