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
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.core.util.StringUtils;
import io.micronaut.runtime.context.scope.refresh.RefreshEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.List;

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
@Requires(property = PropertiesLoggingLevelsConfigurer.LOGGER_LEVELS_PROPERTY_PREFIX)
@Internal
final class PropertiesLoggingLevelsConfigurer implements ApplicationEventListener<RefreshEvent> {

    static final String LOGGER_LEVELS_PROPERTY_PREFIX = "logger.levels";
    private static final Logger LOGGER = LoggerFactory.getLogger(PropertiesLoggingLevelsConfigurer.class);

    private final Environment environment;
    private final List<LoggingSystem> loggingSystems;

    /**
     * Sets log level according to properties.
     *
     * @param environment   The environment
     * @param loggingSystems The logging systems
     */
    public PropertiesLoggingLevelsConfigurer(Environment environment, List<LoggingSystem> loggingSystems) {
        this.environment = environment;
        this.loggingSystems = loggingSystems;
        configureLogLevels();
    }

    /**
     * Sets log level according to properties on refresh.
     *
     * @param event refresh event
     */
    @Override
    public void onApplicationEvent(RefreshEvent event) {
        if (event.getSource().keySet().stream().anyMatch(key -> key.startsWith(LOGGER_LEVELS_PROPERTY_PREFIX))) {
            configureLogLevels();
        }
    }

    private void configureLogLevels() {
        environment.getProperties(LOGGER_LEVELS_PROPERTY_PREFIX, StringConvention.RAW).forEach((loggerPrefix, levelString) -> {
            LogLevel newLevel = toLogLevel(levelString.toString());
            if (newLevel == null) {
                throw new ConfigurationException("Invalid log level: '" + levelString + "' for logger: '" + loggerPrefix + "'");
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Setting log level '{}' for logger: '{}'", newLevel, loggerPrefix);
            }
            for (LoggingSystem loggingSystem : loggingSystems) {
                loggingSystem.setLogLevel(loggerPrefix, newLevel);
            }
        });
    }

    private static LogLevel toLogLevel(String logLevel) {
        if (StringUtils.isEmpty(logLevel)) {
            return LogLevel.NOT_SPECIFIED;
        } else {
            try {
                return Enum.valueOf(LogLevel.class, logLevel);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }

}
