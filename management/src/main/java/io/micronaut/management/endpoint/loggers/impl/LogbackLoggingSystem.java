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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.logging.LogLevel;
import io.micronaut.management.endpoint.loggers.LoggerConfiguration;
import io.micronaut.management.endpoint.loggers.LoggersEndpoint;
import io.micronaut.management.endpoint.loggers.LoggingSystem;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
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
    @NonNull
    public Collection<LoggerConfiguration> getLoggers() {
        return getLoggerContext()
                .getLoggerList()
                .stream()
                .map(LogbackLoggingSystem::toLoggerConfiguration)
                .collect(Collectors.toList());
    }

    @Override
    @NonNull
    public LoggerConfiguration getLogger(String name) {
        return toLoggerConfiguration(getLoggerContext().getLogger(name));
    }

    @Override
    public void setLogLevel(String name, LogLevel level) {
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
    private static LogLevel toLogLevel(Level level) {
        if (level == null) {
            return LogLevel.NOT_SPECIFIED;
        } else {
            return LogLevel.valueOf(level.toString());
        }
    }

    /**
     * @param logLevel The micronaut {@link io.micronaut.logging.LogLevel} to convert
     * @return The converted logback {@link Level}
     */
    private static Level toLevel(LogLevel logLevel) {
        if (logLevel == LogLevel.NOT_SPECIFIED) {
            return null;
        } else {
            return Level.valueOf(logLevel.name());
        }
    }
}
