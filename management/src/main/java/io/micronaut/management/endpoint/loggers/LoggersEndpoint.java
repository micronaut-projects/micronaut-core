/*
 * Copyright 2017-2018 original authors
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

import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.management.endpoint.Endpoint;
import io.micronaut.management.endpoint.EndpointConfiguration;
import io.micronaut.management.endpoint.Read;
import io.micronaut.management.endpoint.Write;
import io.reactivex.Single;

import javax.annotation.Nullable;
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
    public static final boolean DEFAULT_ENABLED = true;

    /**
     * Endpoint default sensitivity.
     */
    public static final boolean DEFAULT_SENSITIVE = false;

    private final LoggingSystem loggingSystem;
    private final LoggersManager loggersManager;

    /**
     * @param loggingSystem the {@link LoggingSystem}
     * @param loggersManager the {@link LoggersManager}
     */
    public LoggersEndpoint(LoggingSystem loggingSystem,
                           LoggersManager loggersManager) {
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
     * @return the {@link LogLevel} (both configured and effective) of the named logger
     */
    @Read
    public Single<Map<String, Object>> logger(@QueryValue @NotBlank String name) {
        return Single.fromPublisher(loggersManager.getLogger(loggingSystem, name));
    }

    /**
     * @param name The name of the logger to configure
     * @param configuredLevel The {@link LogLevel} to set on the named logger
     */
    @Write
    public void setLogLevel(@QueryValue @NotBlank String name,
                            @Nullable LogLevel configuredLevel) {
        try {
            loggersManager.setLogLevel(loggingSystem, name,
                    configuredLevel != null ? configuredLevel : LogLevel.NOT_SPECIFIED);
        } catch (IllegalArgumentException ex) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

}
