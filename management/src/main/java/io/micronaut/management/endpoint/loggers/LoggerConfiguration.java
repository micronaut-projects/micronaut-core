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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents the configuration of a {@link LoggingSystem} logger.
 *
 * @author Matthew Moss
 * @author graemerocher
 * @since 1.0
 */
public class LoggerConfiguration {

    private static final String CONFIGURED_LEVEL = "configuredLevel";
    private static final String EFFECTIVE_LEVEL = "effectiveLevel";
    private final String name;
    private final io.micronaut.logging.LogLevel configuredLevel;
    private final io.micronaut.logging.LogLevel effectiveLevel;

    /**
     * @param name the logger name
     * @param configuredLevel the configured {@link LogLevel}
     * @param effectiveLevel the effective {@link LogLevel}
     * @deprecated Use {@link #LoggerConfiguration(String, io.micronaut.logging.LogLevel, io.micronaut.logging.LogLevel)} instead
     */
    @Deprecated
    public LoggerConfiguration(String name, LogLevel configuredLevel,
                               LogLevel effectiveLevel) {
        this(name,
            io.micronaut.logging.LogLevel.valueOf(configuredLevel.name()),
            io.micronaut.logging.LogLevel.valueOf(effectiveLevel.name()));
    }

    /**
     * @param name the logger name
     * @param configuredLevel the configured {@link io.micronaut.logging.LogLevel}
     * @param effectiveLevel the effective {@link io.micronaut.logging.LogLevel}
     */
    public LoggerConfiguration(String name,
                               io.micronaut.logging.LogLevel configuredLevel,
                               io.micronaut.logging.LogLevel effectiveLevel) {
        this.name = name;
        this.configuredLevel = configuredLevel;
        this.effectiveLevel = effectiveLevel;
    }

    /**
     * @return the logger name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the configured {@link LogLevel}
     * @deprecated Use {@link #configuredLevel()} instead
     */
    @Deprecated
    public LogLevel getConfiguredLevel() {
        return LogLevel.valueOf(configuredLevel.name());
    }

    /**
     * @return the effective {@link LogLevel}
     * @deprecated Use {@link #effectiveLevel()} instead
     */
    @Deprecated
    public LogLevel getEffectiveLevel() {
        return LogLevel.valueOf(effectiveLevel.name());
    }

    /**
     * @return the configured {@link io.micronaut.logging.LogLevel}
     */
    public io.micronaut.logging.LogLevel configuredLevel() {
        return configuredLevel;
    }

    /**
     * @return the effective {@link io.micronaut.logging.LogLevel}
     */
    public io.micronaut.logging.LogLevel effectiveLevel() {
        return effectiveLevel;
    }

    /**
     * @return a Map of data to emit (less the name)
     */
    public Map<String, Object> getData() {
        Map<String, Object> data = new LinkedHashMap<>(2);
        data.put(CONFIGURED_LEVEL, configuredLevel());
        data.put(EFFECTIVE_LEVEL, effectiveLevel());
        return data;
    }

}
