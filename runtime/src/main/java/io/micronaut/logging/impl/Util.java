/*
 * Copyright 2017-2022 original authors
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

import io.micronaut.context.env.Environment;
import io.micronaut.core.naming.conventions.StringConvention;

import java.util.HashMap;
import java.util.Map;

/**
 * Logging utils.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
final class Util {

    static final String LOGGER_LEVELS_PROPERTY_PREFIX = "logger.levels";

    static void configureLogLevels(Environment environment, LoggerValueApplier loggerValueApplier) {
        // Using raw keys here allows configuring log levels for camelCase package names in application.yml
        final Map<String, Object> rawProperties = environment.getProperties(LOGGER_LEVELS_PROPERTY_PREFIX, StringConvention.RAW);
        // Adding the generated properties allows environment variables and system properties to override names in application.yaml
        final Map<String, Object> generatedProperties = environment.getProperties(LOGGER_LEVELS_PROPERTY_PREFIX);

        final Map<String, Object> properties = new HashMap<>(generatedProperties.size() + rawProperties.size(), 1f);
        properties.putAll(rawProperties);
        properties.putAll(generatedProperties);
        properties.forEach((loggerPrefix, levelValue) -> configureLogLevelForPrefix(loggerPrefix, levelValue, loggerValueApplier));
    }

    private static void configureLogLevelForPrefix(String loggerPrefix, Object levelValue, LoggerValueApplier loggerValueApplier) {
        if (levelValue instanceof Boolean && !((boolean) levelValue)) {
            levelValue = "OFF"; // SnakeYAML converts OFF (without quotations) to a boolean false value, hence we need to handle that here...
        }
        loggerValueApplier.apply(loggerPrefix, levelValue);
    }

    interface LoggerValueApplier {

        void apply(String loggerPrefix, Object levelValue);

    }

}
