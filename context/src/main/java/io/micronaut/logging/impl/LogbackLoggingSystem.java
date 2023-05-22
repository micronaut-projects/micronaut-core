/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.logging.impl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.logging.LogLevel;
import io.micronaut.logging.LoggingSystem;
import jakarta.inject.Singleton;
import org.slf4j.LoggerFactory;

/**
 * An implementation of {@link LoggingSystem} that works with logback.
 *
 * @author Matthew Moss
 * @since 1.3.0
 */
@Singleton
@Requires(classes = LoggerContext.class)
@Internal
public final class LogbackLoggingSystem implements LoggingSystem {

    private static final String DEFAULT_LOGBACK_LOCATION = "logback.xml";

    private final String logbackXmlLocation;

    /**
     * @param logbackExternalConfigLocation The location of the logback configuration file set via logback properties
     * @param logbackXmlLocation The location of the logback configuration file set via micronaut properties
     * @since 3.8.8
     */
    public LogbackLoggingSystem(
        @Nullable @Property(name = "logback.configurationFile") String logbackExternalConfigLocation,
        @Nullable @Property(name = "logger.config") String logbackXmlLocation
    ) {
        if (logbackExternalConfigLocation != null) {
            this.logbackXmlLocation = logbackExternalConfigLocation;
        } else if (logbackXmlLocation != null) {
            this.logbackXmlLocation = logbackXmlLocation;
        } else {
            this.logbackXmlLocation = DEFAULT_LOGBACK_LOCATION;
        }
    }

    @Override
    public void setLogLevel(String name, LogLevel level) {
        getLoggerContext().getLogger(name).setLevel(toLevel(level));
    }

    @Override
    public void refresh() {
        LoggerContext context = getLoggerContext();
        context.reset();
        LogbackUtils.configure(getClass().getClassLoader(), context, logbackXmlLocation);
    }

    /**
     * @return The logback {@link LoggerContext}
     */
    private static LoggerContext getLoggerContext() {
        return (LoggerContext) LoggerFactory.getILoggerFactory();
    }

    /**
     * @param logLevel The micronaut {@link LogLevel} to convert
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
