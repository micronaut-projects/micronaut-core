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

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.StringUtils;
import io.micronaut.logging.LogLevel;
import io.micronaut.management.endpoint.loggers.LoggerConfiguration;
import io.micronaut.management.endpoint.loggers.LoggersEndpoint;
import io.micronaut.management.endpoint.loggers.ManagedLoggingSystem;
import jakarta.inject.Singleton;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.AbstractConfiguration;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.util.NameUtil;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.logging.log4j.LogManager.ROOT_LOGGER_NAME;

/**
 * An implementation of {@link ManagedLoggingSystem} that works with log4j.
 *
 * @author Matteo Vaccari, Matthew Moss
 * @since 2.2.0
 */
@Singleton
@Requires(beans = LoggersEndpoint.class)
@Requires(classes = LoggerContext.class)
@Replaces(io.micronaut.logging.impl.Log4jLoggingSystem.class)
public class Log4jLoggingSystem implements ManagedLoggingSystem, io.micronaut.logging.LoggingSystem {

    public static final String ROOT = "ROOT";

    @Override
    @NonNull
    public Collection<LoggerConfiguration> getLoggers() {
        return getAllLoggers().entrySet().stream()
                .map(entry -> toLoggerConfiguration(entry.getKey(), entry.getValue()))
                .sorted(new LoggerConfigurationComparator(ROOT))
                .collect(Collectors.toList());
    }

    @Override
    @NonNull
    public LoggerConfiguration getLogger(String name) {
        boolean isRootLogger = StringUtils.isEmpty(name);
        final LoggerConfig loggerConfig = findLogger(isRootLogger ? ROOT_LOGGER_NAME : name);
        return toLoggerConfiguration(name, loggerConfig);
    }

    @Override
    public void setLogLevel(String loggerName, LogLevel logLevel) {
        setLogLevel(loggerName, toLog4jLevel(logLevel));
    }

    private void setLogLevel(String loggerName, Level level) {
        LoggerConfig logger = getLoggerConfig(loggerName);
        if (level == null) {
            clearLogLevel(loggerName, logger);
        } else {
            setLogLevel(loggerName, logger, level);
        }
        getLog4jLoggerContext().updateLoggers();
    }

    private void clearLogLevel(String loggerName, LoggerConfig logger) {
        if (logger instanceof LevelSetLoggerConfig) {
            getLog4jLoggerContext().getConfiguration().removeLogger(loggerName);
        } else {
            logger.setLevel(null);
        }
    }

    private void setLogLevel(String loggerName, LoggerConfig logger, Level level) {
        if (logger == null) {
            getLog4jLoggerContext().getConfiguration().addLogger(loggerName,
                    new LevelSetLoggerConfig(loggerName, level, true));
        } else {
            logger.setLevel(level);
        }
    }

    private LoggerConfig getLoggerConfig(String name) {
        boolean isRootLogger = ROOT.equals(name);
        return findLogger(isRootLogger ? "" : name);
    }

    private Map<String, LoggerConfig> getAllLoggers() {
        Map<String, LoggerConfig> loggers = new LinkedHashMap<>();
        for (org.apache.logging.log4j.core.Logger logger : getLog4jLoggerContext().getLoggers()) {
            addLogger(loggers, logger.getName());
        }
        getLog4jLoggerContext().getConfiguration().getLoggers().keySet().forEach((name) -> addLogger(loggers, name));
        return loggers;
    }

    private void addLogger(Map<String, LoggerConfig> loggers, String name) {
        Configuration configuration = getLog4jLoggerContext().getConfiguration();
        while (name != null) {
            loggers.computeIfAbsent(name, configuration::getLoggerConfig);
            name = getSubName(name);
        }
    }

    private String getSubName(String name) {
        if (!StringUtils.isNotEmpty(name)) {
            return null;
        }
        int nested = name.lastIndexOf('$');
        return (nested != -1) ? name.substring(0, nested) : NameUtil.getSubName(name);
    }

    /**
     * @param name The loggers name
     * @return loggerConfig The log4j {@link LoggerConfig}
     */
    private LoggerConfig findLogger(String name) {
        Configuration configuration = getLog4jLoggerContext().getConfiguration();
        if (configuration instanceof AbstractConfiguration) {
            return ((AbstractConfiguration) configuration).getLogger(name);
        }
        return configuration.getLoggers().get(name);
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
     * @param name The loggers name
     * @param loggerConfig The log4j {@link LoggerConfig} to convert
     * @return The converted micronaut {@link LoggerConfiguration}
     */
    private static LoggerConfiguration toLoggerConfiguration(String name, LoggerConfig loggerConfig) {
        if (loggerConfig == null) {
            return null;
        }
        LogLevel level = toMicronautLogLevel(loggerConfig.getLevel());
        boolean isLoggerConfigured = loggerConfig.getName().equals(name);
        if (ROOT_LOGGER_NAME.equals(name)) {
            name = ROOT;
        }
        LogLevel configuredLevel = (isLoggerConfigured) ? level : LogLevel.NOT_SPECIFIED;
        return new LoggerConfiguration(name, configuredLevel, level);
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

    /**
     * {@link LoggerConfig} used when the user has set a specific {@link Level}.
     */
    private static class LevelSetLoggerConfig extends LoggerConfig {

        LevelSetLoggerConfig(String name, Level level, boolean additive) {
            super(name, level, additive);
        }

    }
}
