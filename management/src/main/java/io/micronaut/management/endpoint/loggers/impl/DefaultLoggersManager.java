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
package io.micronaut.management.endpoint.loggers.impl;

import io.micronaut.context.annotation.Requires;
import io.micronaut.management.endpoint.loggers.*;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link LoggersManager}.
 *
 * @author Matthew Moss
 * @since 1.0
 */
@Singleton
@Requires(beans = LoggersEndpoint.class)
public class DefaultLoggersManager implements LoggersManager<Map<String, Object>> {

    private static final String LEVELS = "levels";
    private static final String LOGGERS = "loggers";

    @Override
    public Publisher<Map<String, Object>> getLoggers(LoggingSystem loggingSystem) {
        Map<String, Object> data = new LinkedHashMap<>(2);

        data.put(LEVELS, getLogLevels());
        data.put(LOGGERS, getLoggerData(loggingSystem.getLoggers()));

        return Flowable.just(data);
    }

    @Override
    public Publisher<Map<String, Object>> getLogger(LoggingSystem loggingSystem,
                                                    String name) {
        return Flowable.just(getLoggerData(loggingSystem.getLogger(name)));
    }

    @Override
    public void setLogLevel(LoggingSystem loggingSystem, @NotBlank String name, io.micronaut.logging.@NotNull LogLevel level) {
        loggingSystem.setLogLevel(name, level);
    }

    /**
     * @param configurations The logger configurations
     * @return A Map from logger name to logger configuration data
     */
    private static Map<String, Object> getLoggerData(
            Collection<LoggerConfiguration> configurations) {
        return configurations
                .stream()
                .collect(Collectors.toMap(
                        LoggerConfiguration::getName,
                        LoggerConfiguration::getData,
                        (l1, l2) -> l1,
                        LinkedHashMap::new));
    }

    /**
     * @param configuration The logger configuration
     * @return The logger configuration data
     */
    private static Map<String, Object> getLoggerData(
            LoggerConfiguration configuration) {
        return configuration.getData();
    }

    /**
     * @return A list with all {@link io.micronaut.logging.LogLevel} values
     */
    private static List<io.micronaut.logging.LogLevel> getLogLevels() {
        return Arrays.asList(io.micronaut.logging.LogLevel.values());
    }

}
