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
package io.micronaut.management.endpoint.loggers.impl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.management.endpoint.loggers.LogLevel;
import io.micronaut.management.endpoint.loggers.LoggerConfiguration;
import io.micronaut.management.endpoint.loggers.LoggersEndpoint;
import io.micronaut.management.endpoint.loggers.LoggingSystem;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * An implementation of {@link LoggingSystem} that works with logback.
 *
 * @author Matthew Moss
 * @since 1.0
 */
@Singleton
@Requires(beans = LoggersEndpoint.class)
@Requires(classes = ch.qos.logback.classic.LoggerContext.class)
@Replaces(io.micronaut.logging.impl.LogbackLoggingSystem.class)
public class LogbackLoggingSystem implements LoggingSystem {

    @Override
    public Collection<LoggerConfiguration> getLoggers() {
        return getLoggerContext()
                .getLoggerList()
                .stream()
                .map(LogbackLoggingSystem::toLoggerConfiguration)
                .collect(Collectors.toList());
    }

    @Override
    public LoggerConfiguration getLogger(String name) {
        return toLoggerConfiguration(getLoggerContext().getLogger(name));
    }

    @Override
    @Deprecated
    public void setLogLevel(@NotBlank String name, @NotNull LogLevel level) {
        getLoggerContext().getLogger(name).setLevel(toLevel(
                io.micronaut.logging.LogLevel.valueOf(level.name())
        ));
    }

    @Override
    public void setLogLevel(String name, io.micronaut.logging.LogLevel level) {
        getLoggerContext().getLogger(name).setLevel(toLevel(level));
    }

    /**
     * @return The logback {@link LoggerContext}
     */
    private static LoggerContext getLoggerContext() {
        return (LoggerContext) LoggerFactory.getILoggerFactory();
    }

    /**
     * @param logger The logback {@link Logger} to convert
     * @return The converted {@link LoggerConfiguration}
     */
    private static LoggerConfiguration toLoggerConfiguration(Logger logger) {
        return new LoggerConfiguration(
                logger.getName(),
                toLogLevel(logger.getLevel()),
                toLogLevel(logger.getEffectiveLevel())
        );
    }

    /**
     * @param level The logback {@link Level} to convert
     * @return The converted {@link io.micronaut.logging.LogLevel}
     */
    private static io.micronaut.logging.LogLevel toLogLevel(Level level) {
        if (level == null) {
            return io.micronaut.logging.LogLevel.NOT_SPECIFIED;
        } else {
            return io.micronaut.logging.LogLevel.valueOf(level.toString());
        }
    }

    /**
     * @param logLevel The micronaut {@link io.micronaut.logging.LogLevel} to convert
     * @return The converted logback {@link Level}
     */
    private static Level toLevel(io.micronaut.logging.LogLevel logLevel) {
        if (logLevel == io.micronaut.logging.LogLevel.NOT_SPECIFIED) {
            return null;
        } else {
            return Level.valueOf(logLevel.name());
        }
    }
}
