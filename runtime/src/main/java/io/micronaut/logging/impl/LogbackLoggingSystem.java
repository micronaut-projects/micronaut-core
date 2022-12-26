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
package io.micronaut.logging.impl;

import java.net.URL;
import java.util.Objects;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.joran.spi.JoranException;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.logging.LogLevel;
import io.micronaut.logging.LoggingSystem;
import io.micronaut.logging.LoggingSystemException;
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

    public LogbackLoggingSystem(@Nullable @Property(name = "logger.config") String logbackXmlLocation) {
        this.logbackXmlLocation = logbackXmlLocation != null ? logbackXmlLocation : DEFAULT_LOGBACK_LOCATION;
    }

    @Override
    public void setLogLevel(String name, LogLevel level) {
        getLoggerContext().getLogger(name).setLevel(toLevel(level));
    }

    @Override
    public void refresh() {
        LoggerContext context = getLoggerContext();
        context.reset();
        URL resource = getClass().getClassLoader().getResource(logbackXmlLocation);
        if (Objects.isNull(resource)) {
            throw new LoggingSystemException("Resource " + logbackXmlLocation + " not found");
        }

        try {
            new ContextInitializer(context).configureByResource(resource);
        } catch (JoranException e) {
            throw new LoggingSystemException("Error while refreshing Logback", e);
        }
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
