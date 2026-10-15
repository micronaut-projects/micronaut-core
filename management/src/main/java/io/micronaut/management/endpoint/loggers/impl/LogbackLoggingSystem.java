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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import org.jspecify.annotations.Nullable;
import io.micronaut.logging.LogLevel;
import io.micronaut.management.endpoint.loggers.LoggerConfiguration;
import io.micronaut.management.endpoint.loggers.LoggersEndpoint;
import io.micronaut.management.endpoint.loggers.ManagedLoggingSystem;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * An implementation of {@link ManagedLoggingSystem} that works with logback.
 *
 * @author Matthew Moss
 * @since 1.0
 */
@Singleton
@Requires(beans = LoggersEndpoint.class)
@Requires(classes = ch.qos.logback.classic.LoggerContext.class)
@Replaces(io.micronaut.logging.impl.LogbackLoggingSystem.class)
public class LogbackLoggingSystem implements ManagedLoggingSystem, io.micronaut.logging.LoggingSystem {
    // The refresh is the one of the logging system that this bean replaces
    private final io.micronaut.logging.impl.LogbackLoggingSystem refreshDelegate;

    /**
     * @param logbackExternalConfigLocation The location of the logback configuration file set via logback properties
     * @param logbackXmlLocation The location of the logback configuration file set via micronaut properties
     * @since 5.3.0
     */
    @Inject
    LogbackLoggingSystem(
        @Nullable @Property(name = "logback.configurationFile") String logbackExternalConfigLocation,
        @Nullable @Property(name = "logger.config") String logbackXmlLocation
    ) {
        this.refreshDelegate = new io.micronaut.logging.impl.LogbackLoggingSystem(logbackExternalConfigLocation, logbackXmlLocation);
    }

    /**
     * @param logbackXmlLocation The location of the logback configuration file set via micronaut properties
     * @deprecated Do not construct this class. The Micronaut framework creates this bean when the loggers
     * endpoint is enabled, with a constructor that also honours {@code logback.configurationFile}. Obtain
     * it from the application context, for example by injecting {@link io.micronaut.logging.LoggingSystem}
     * or {@link ManagedLoggingSystem}. To change it, replace it with a bean of your own that implements both
     * and is annotated with {@code @Replaces(LogbackLoggingSystem.class)}, rather than extending it. This
     * constructor ignores {@code logback.configurationFile} and is kept for binary compatibility only.
     * Passing {@code null} no longer defaults to {@code logback.xml}: without a location, the refresh
     * configures Logback the way Logback's own startup does.
     */
    @Deprecated(since = "5.3", forRemoval = true)
    public LogbackLoggingSystem(@Nullable String logbackXmlLocation) {
        this(null, logbackXmlLocation);
    }

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
            String name = level.toString();
            for (LogLevel logLevel : LogLevel.values()) {
                if (logLevel.name().equals(name)) {
                    return logLevel;
                }
            }
            throw new IllegalArgumentException("No enum constant " + LogLevel.class.getName() + "." + name);
        }
    }

    /**
     * @param logLevel The micronaut {@link io.micronaut.logging.LogLevel} to convert
     * @return The converted logback {@link Level}
     */
    @Nullable
    private static Level toLevel(LogLevel logLevel) {
        if (logLevel == LogLevel.NOT_SPECIFIED) {
            return null;
        } else {
            return Level.valueOf(logLevel.name());
        }
    }

    @Override
    public void refresh() {
        refreshDelegate.refresh();
    }
}
