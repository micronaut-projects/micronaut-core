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
package io.micronaut.management.endpoint.loggers.impl;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.logging.LogLevel;
import io.micronaut.management.endpoint.loggers.LoggerConfiguration;
import io.micronaut.management.endpoint.loggers.LoggersEndpoint;
import io.micronaut.management.endpoint.loggers.ManagedLoggingSystem;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;

import javax.inject.Singleton;
import java.util.Collection;

import static java.util.stream.Collectors.toList;

/**
 * An implementation of {@link ManagedLoggingSystem} that works with logback.
 *
 * @author Matteo Vaccari, Matthew Moss
 */
@Singleton
@Requires(beans = LoggersEndpoint.class)
@Requires(classes = LoggerContext.class)
@Replaces(io.micronaut.logging.impl.Log4jLoggingSystem.class)
public class Log4jLoggingSystem implements ManagedLoggingSystem, io.micronaut.logging.LoggingSystem {

    @Override
    @NonNull
    public Collection<LoggerConfiguration> getLoggers() {
        return getLog4jLoggerContext()
                .getLoggers()
                .stream()
                .map(Log4jLoggingSystem::toLoggerConfiguration)
                .collect(toList());
    }

    @Override
    @NonNull
    public LoggerConfiguration getLogger(String name) {
        return toLoggerConfiguration(LogManager.getLogger(name));
    }

    @Override
    public void setLogLevel(String name, LogLevel level) {
        Configurator.setLevel(name, toLog4jLevel(level));
    }

    /**
     * @return The log4j {@link org.apache.logging.log4j.core.LoggerContext}
     */
    private LoggerContext getLog4jLoggerContext() {
        return (LoggerContext) LogManager.getContext(false);
    }

    /**
     * @param logLevel The micronaut {@link LogLevel} to convert
     * @return The converted log4j {@link Level}
     */
    private static Level toLog4jLevel(LogLevel logLevel) {
        if (logLevel == LogLevel.NOT_SPECIFIED) {
            return null;
        } else {
            return Level.valueOf(logLevel.name());
        }
    }

    /**
     * @param logger The log4j {@link Logger} to convert
     * @return The converted micronaut {@link LoggerConfiguration}
     */
    private static LoggerConfiguration toLoggerConfiguration(Logger logger) {
        return new LoggerConfiguration(
                logger.getName(),
                toMicronautLogLevel(logger.getLevel()),
                toMicronautLogLevel(logger.getLevel())
        );
    }

    /**
     * @param level The log4j {@link Level} to convert
     * @return The converted micronaut {@link LogLevel}
     */
    private static LogLevel toMicronautLogLevel(Level level) {
        if (level == null) {
            return LogLevel.NOT_SPECIFIED;
        } else {
            return LogLevel.valueOf(level.toString());
        }
    }
}
