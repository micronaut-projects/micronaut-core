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
package io.micronaut.logging;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.StringUtils;
import io.micronaut.runtime.context.scope.refresh.RefreshEvent;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Properties logging levels configurer.
 *
 * @author Denis Stepanov
 * @author graemerocher
 * @since 1.3.0
 */
@BootstrapContextCompatible
@Singleton
@Context
@Requires(beans = LoggingSystem.class)
@Requires(beans = Environment.class)
@Requires(property = PropertiesLoggingLevelsConfigurer.LOGGER_PROPERTY_PREFIX)
@Internal
final class PropertiesLoggingLevelsConfigurer implements ApplicationEventListener<RefreshEvent>, Ordered {

    static final String LOGGER_PROPERTY_PREFIX = "logger";
    static final String LOGGER_LEVELS_PROPERTY_PREFIX = LOGGER_PROPERTY_PREFIX + ".levels";
    private static final Logger LOGGER = LoggerFactory.getLogger(PropertiesLoggingLevelsConfigurer.class);

    private final Environment environment;
    private final PropertiesLoggingLevelsConfiguration configuration;
    private final List<LoggingSystem> loggingSystems;

    /**
     * Sets log level according to properties.
     *
     * @param environment The environment
     * @param configuration The logging levels configuration
     * @param loggingSystems The logging systems
     */
    PropertiesLoggingLevelsConfigurer(Environment environment,
                                      PropertiesLoggingLevelsConfiguration configuration,
                                      List<LoggingSystem> loggingSystems) {
        this.environment = environment;
        this.configuration = configuration;
        this.loggingSystems = loggingSystems;
        initLogging();
        configureLogLevels();
    }

    /**
     * Sets log level according to properties on refresh.
     *
     * @param event refresh event
     */
    @Override
    public void onApplicationEvent(RefreshEvent event) {
        initLogging();
        configureLogLevels();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private void initLogging() {
        this.loggingSystems.forEach(LoggingSystem::refresh);
    }

    private void configureLogLevels() {
        // Adding the generated properties allows environment variables and system properties to override names in application.yaml
        final Map<String, Object> generatedProperties = environment.getProperties(LOGGER_LEVELS_PROPERTY_PREFIX);
        final Map<String, LogLevel> levels = configuration.getLevels();

        final Map<String, Object> properties = new HashMap<>(generatedProperties.size() + levels.size(), 1f);
        properties.putAll(levels);
        properties.putAll(generatedProperties);
        properties.forEach(this::configureLogLevelForPrefix);
    }

    private void configureLogLevelForPrefix(final String loggerPrefix, final Object levelValue) {
        final LogLevel newLevel;
        if (levelValue instanceof LogLevel logLevel) {
            newLevel = logLevel;
        } else if (levelValue instanceof Boolean booleanValue && !booleanValue) {
            newLevel = LogLevel.OFF; // SnakeYAML converts OFF (without quotations) to a boolean false value, hence we need to handle that here...
        } else {
            newLevel = toLogLevel(levelValue.toString());
        }
        if (newLevel == null) {
            throw new ConfigurationException("Invalid log level: '" + levelValue + "' for logger: '" + loggerPrefix + "'");
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Setting log level '{}' for logger: '{}'", newLevel, loggerPrefix);
        } else if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Setting log level '{}' for logger: '{}'", newLevel, loggerPrefix);
        }
        for (LoggingSystem loggingSystem : loggingSystems) {
            loggingSystem.setLogLevel(loggerPrefix, newLevel);
        }
    }

    @Nullable
    private static LogLevel toLogLevel(String logLevel) {
        if (StringUtils.isEmpty(logLevel)) {
            return LogLevel.NOT_SPECIFIED;
        }
        try {
            return Enum.valueOf(LogLevel.class, logLevel.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Configuration for setting log levels from {@code logger.levels}.
     */
    @BootstrapContextCompatible
    @ConfigurationProperties(LOGGER_PROPERTY_PREFIX)
    @Internal
    static final class PropertiesLoggingLevelsConfiguration {
        @MapFormat(keyFormat = StringConvention.RAW, transformation = MapFormat.MapTransformation.FLAT)
        private Map<String, LogLevel> levels = Map.of();

        /**
         * The configured log levels keyed by logger name.
         *
         * @return The log levels
         */
        public Map<String, LogLevel> getLevels() {
            return levels;
        }

        /**
         * Sets the configured log levels keyed by logger name.
         *
         * @param levels The log levels
         */
        public void setLevels(@MapFormat(keyFormat = StringConvention.RAW, transformation = MapFormat.MapTransformation.FLAT) Map<String, LogLevel> levels) {
            this.levels = levels;
        }
    }
}
