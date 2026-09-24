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

import ch.qos.logback.classic.ClassicConstants;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.classic.util.DefaultJoranConfigurator;
import ch.qos.logback.core.LogbackException;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.status.InfoStatus;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.logging.LoggingSystemException;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

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
     * Configures a Logger Context, typically after a {@link LoggerContext#reset()}.
     * <p>
     * A location that only Micronaut can see wins: {@code logback.configurationFile} set in
     * Micronaut configuration but not as a JVM system property, otherwise {@code logger.config}.
     * The context is configured from that location with {@link DefaultJoranConfigurator}, whatever
     * {@link Configurator} services exist. The location is looked up on the classpath first and
     * then on the file system, and a missing location fails.
     * </p>
     * <p>
     * Otherwise the context is configured the way Logback's own startup configures it, with
     * {@link ContextInitializer#autoConfig(ClassLoader)}: the {@link Configurator} services by rank,
     * until one returns {@link Configurator.ExecutionStatus#DO_NOT_INVOKE_NEXT_IF_ANY}, then the
     * {@code logback.configurationFile} JVM system property, {@code logback-test.xml} or
     * {@code logback.xml}, then the basic console configuration.
     * </p>
     *
     * @param classLoader       The class loader to look up the configuration and the {@link Configurator} services with
     * @param context           The Logger Context
     * @param configurationFile The {@code logback.configurationFile} property of the Micronaut configuration, if any
     * @param loggerConfig      The {@code logger.config} property of the Micronaut configuration, if any
     * @since 5.3.0
     */
    public static void configure(ClassLoader classLoader,
                                 LoggerContext context,
                                 @Nullable String configurationFile,
                                 @Nullable String loggerConfig) {
        String location = micronautOnlyLocation(configurationFile, loggerConfig);
        if (location != null) {
            configureByResource(context, location, findResource(classLoader, location));
        } else {
            try {
                new ContextInitializer(context).autoConfig(classLoader);
            } catch (JoranException | LogbackException e) {
                throw new LoggingSystemException("Error while refreshing Logback", e);
            }
        }
    }

    /**
     * Configures a Logger Context.
     *
     * @param classLoader        Class Loader
     * @param context            Logger Context
     * @param logbackXmlLocation the location of the xml logback config file
     * @deprecated This method cannot tell a location set in configuration from a default one, and
     * it uses the first {@link Configurator} service whatever its rank and status. Use
     * {@link #configure(ClassLoader, LoggerContext, String, String)} instead.
     */
    @Deprecated(since = "5.3", forRemoval = true)
    public static void configure(ClassLoader classLoader,
                                 LoggerContext context,
                                 String logbackXmlLocation) {
        List<Configurator> configuratorList = loadFromServiceLoader(Configurator.class, classLoader);
        Configurator configurator = CollectionUtils.isNotEmpty(configuratorList) ? configuratorList.get(0) : null;
        if (configurator != null && !(configurator instanceof DefaultJoranConfigurator)) {
            context.getStatusManager().add(new InfoStatus("Using " + configurator.getClass().getName(), context));
            programmaticConfiguration(context, configurator);
        } else {
            configureByResource(context, logbackXmlLocation, findResource(classLoader, logbackXmlLocation));
        }
    }

    /**
     * @param configurationFile The {@code logback.configurationFile} property of the Micronaut configuration
     * @param loggerConfig      The {@code logger.config} property of the Micronaut configuration
     * @return The location that Logback's own startup cannot see, if any
     */
    private static @Nullable String micronautOnlyLocation(@Nullable String configurationFile,
                                                          @Nullable String loggerConfig) {
        if (configurationFile != null) {
            // A JVM system property is Logback's own, and it still takes precedence over logger.config
            return configurationFile.equals(System.getProperty(ClassicConstants.CONFIG_FILE_PROPERTY)) ? null : configurationFile;
        }
        return loggerConfig;
    }

    /**
     * @param classLoader The class loader
     * @param location    The location on the classpath or on the file system
     * @return The resource, if it exists
     */
    private static @Nullable URL findResource(ClassLoader classLoader, String location) {
        // Check classpath first
        URL resource = classLoader.getResource(location);
        if (resource != null) {
            return resource;
        }
        // Check file system
        File file = new File(location);
        if (file.exists()) {
            try {
                return file.toURI().toURL();
            } catch (MalformedURLException e) {
                throw new LoggingSystemException("Error creating URL for off-classpath resource", e);
            }
        }
        return null;
    }

    /**
     * Configures a Logger Context from a Logback XML configuration file.
     *
     * @param context  Logger Context
     * @param location The location of the xml logback config file
     * @param resource The resource at that location, if it exists
     */
    private static void configureByResource(LoggerContext context, String location, @Nullable URL resource) {
        if (resource == null) {
            System.err.println("ERROR: Logback configuration file " + location + " not found");
            throw new LoggingSystemException("Resource " + location + " not found");
        }
        try {
            DefaultJoranConfigurator defaultConfigurator = new DefaultJoranConfigurator();
            defaultConfigurator.setContext(context);
            defaultConfigurator.configureByResource(resource);
        } catch (JoranException e) {
            throw new LoggingSystemException("Error while refreshing Logback", e);
        }
    }

    /**
     * Taken from {@link ContextInitializer#autoConfig}.
     */
    private static void programmaticConfiguration(LoggerContext context,
                                                  Configurator configurator) {
        try {
            configurator.setContext(context);
            configurator.configure(context);
        } catch (Exception e) {
            throw new LoggingSystemException("Failed to initialize Configurator: %s using ServiceLoader".formatted(configurator.getClass().getCanonicalName()), e);
        }
    }
}
