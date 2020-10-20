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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.logging.LogLevel;
import io.micronaut.logging.LoggingSystem;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

import javax.inject.Singleton;

/**
 * An implementation of {@link LoggingSystem} that works with Log4j.
 *
 * @author Matteo Vaccari
 * @since 2.2.0
 */
@Singleton
@Requires(classes = Configurator.class)
@Internal
public class Log4jLoggingSystem implements LoggingSystem {

    @Override
    public void setLogLevel(String name, LogLevel level) {
        Configurator.setLevel(name, toLevel(level));
    }

    /**
     * @param logLevel The micronaut {@link LogLevel} to convert
     * @return The converted log4j {@link Level}
     */
    private static Level toLevel(LogLevel logLevel) {
        if (logLevel == LogLevel.NOT_SPECIFIED) {
            return null;
        } else {
            return Level.valueOf(logLevel.name());
        }
    }
}
