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

import ch.qos.logback.classic.BasicConfigurator;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.classic.util.EnvUtil;
import ch.qos.logback.core.LogbackException;
import ch.qos.logback.core.joran.spi.JoranException;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.logging.LoggingSystemException;

import java.net.URL;
import java.util.Objects;

/**
 * Utility methods to configure {@link LoggerContext}.
 * @author Sergio del Amo
 * @since 3.8.3
 */
public class LogbackUtils {

    /**
     * Configures a Logger Context.
     * @param classLoader Class Loader
     * @param context Logger Context
     * @param logbackXmlLocation Optional logbackXMLLocation
     */
    public static void configure(@NonNull ClassLoader classLoader,
                                 @NonNull LoggerContext context,
                                 @NonNull String logbackXmlLocation) {
        configure(context, classLoader.getResource(logbackXmlLocation));
    }

    /**
     * Configures a Logger Context.
     * If resource is not present searches for a custom {@link Configurator} via a service loader.
     * If no custom configuration, it uses a {@link BasicConfigurator}.
     * if resource is present it configures the context with the resource.
     *
     * @param context Logger Context
     * @param resource A resource for example logback.xml
     */
    private static void configure(@NonNull LoggerContext context,
                                 @Nullable URL resource) {
        try {
            if (Objects.isNull(resource)) {
                Configurator configurator = EnvUtil.loadFromServiceLoader(Configurator.class);
                programmaticConfiguration(context, configurator);
            } else {
                new ContextInitializer(context).configureByResource(resource);
            }
        } catch (JoranException e) {
            throw new LoggingSystemException("Error while refreshing Logback", e);
        }
    }

    // Taken from ch.qos.logback.classic.util.ContextInitializer#autoConfig
    private static void programmaticConfiguration(@NonNull LoggerContext context,
                                                  @Nullable Configurator configurator) {
        if (configurator != null) {
            try {
                configurator.setContext(context);
                configurator.configure(context);
            } catch (Exception e) {
                throw new LogbackException(String.format("Failed to initialize Configurator: %s using ServiceLoader", configurator.getClass().getCanonicalName()), e);
            }
        } else {
            BasicConfigurator basicConfigurator = new BasicConfigurator();
            basicConfigurator.setContext(context);
            basicConfigurator.configure(context);
        }
    }
}
