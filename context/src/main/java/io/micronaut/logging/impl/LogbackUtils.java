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

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.classic.util.DefaultJoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.status.InfoStatus;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.logging.LoggingSystemException;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.function.Supplier;

import static ch.qos.logback.classic.util.ClassicEnvUtil.loadFromServiceLoader;

/**
 * Utility methods to configure {@link LoggerContext}.
 *
 * @author Sergio del Amo
 * @since 3.8.4
 */
public final class LogbackUtils {

    private LogbackUtils() {
    }

    /**
     * Configures a Logger Context.
     *
     * @param classLoader        Class Loader
     * @param context            Logger Context
     * @param logbackXmlLocation the location of the xml logback config file
     */
    public static void configure(@NonNull ClassLoader classLoader,
                                 @NonNull LoggerContext context,
                                 @NonNull String logbackXmlLocation) {
        configure(context, logbackXmlLocation, () -> {
            // Check classpath first
            URL resource = classLoader.getResource(logbackXmlLocation);
            if (resource != null) {
                return resource;
            }
            // Check file system
            File file = new File(logbackXmlLocation);
            if (file.exists()) {
                try {
                    resource = file.toURI().toURL();
                } catch (MalformedURLException e) {
                    throw new LoggingSystemException("Error creating URL for off-classpath resource", e);
                }
            }
            return resource;
        }, classLoader);
    }

    /**
     * Configures a Logger Context.
     * <p>
     * Searches for a custom {@link Configurator} via a service loader.
     * If not present it configures the context with the resource.
     * </p>
     *
     * @param context            Logger Context
     * @param logbackXmlLocation the location of the xml logback config file
     * @param resourceSupplier   A resource for example logback.xml
     */
    private static void configure(
        @NonNull LoggerContext context,
        @NonNull String logbackXmlLocation,
        Supplier<URL> resourceSupplier,
        ClassLoader classLoader) {

        List<Configurator> configuratorList = loadFromServiceLoader(Configurator.class, classLoader);
        Configurator configurator = CollectionUtils.isNotEmpty(configuratorList) ? configuratorList.get(0) : null;
        if (configurator != null && !(configurator instanceof DefaultJoranConfigurator)) {
            context.getStatusManager().add(new InfoStatus("Using " + configurator.getClass().getName(), context));
            programmaticConfiguration(context, configurator);
        } else {
            URL resource = resourceSupplier.get();
            if (resource != null) {
                try {
                    DefaultJoranConfigurator defaultConfigurator = new DefaultJoranConfigurator();
                    defaultConfigurator.setContext(context);
                    defaultConfigurator.configureByResource(resource);
                } catch (JoranException e) {
                    throw new LoggingSystemException("Error while refreshing Logback", e);
                }
            } else {
                System.err.println("ERROR: Logback configuration file " + logbackXmlLocation + " not found");
                throw new LoggingSystemException("Resource " + logbackXmlLocation + " not found");
            }
        }
    }

    /**
     * Taken from {@link ch.qos.logback.classic.util.ContextInitializer#autoConfig}.
     */
    private static void programmaticConfiguration(@NonNull LoggerContext context,
                                                  @NonNull Configurator configurator) {
        try {
            configurator.setContext(context);
            configurator.configure(context);
        } catch (Exception e) {
            throw new LoggingSystemException("Failed to initialize Configurator: %s using ServiceLoader".formatted(configurator.getClass().getCanonicalName()), e);
        }
    }
}
