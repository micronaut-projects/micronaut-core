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

/**
 * Represents the configuration of a {@link LoggingSystem} logger
 *
 * @author Matthew Moss
 * @since 1.0
 */
public class LoggerConfiguration {

    private final String name;
    private final LogLevel configuredLevel;
    private final LogLevel effectiveLevel;

    /**
     * @param name the logger name
     * @param configuredLevel the configured {@link LogLevel}
     * @param effectiveLevel the effective {@link LogLevel}
     */
    public LoggerConfiguration(String name, LogLevel configuredLevel,
                               LogLevel effectiveLevel) {
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
     */
    public LogLevel getConfiguredLevel() {
        return configuredLevel;
    }

    /**
     * @return the effective {@link LogLevel}
     */
    public LogLevel getEffectiveLevel() {
        return effectiveLevel;
    }

}
