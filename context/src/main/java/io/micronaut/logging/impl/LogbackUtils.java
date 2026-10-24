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
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.status.ErrorStatus;
import ch.qos.logback.core.status.InfoStatus;
import ch.qos.logback.core.status.StatusUtil;
import ch.qos.logback.core.util.Loader;
import ch.qos.logback.core.util.StatusPrinter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.logging.LoggingSystemException;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import static ch.qos.logback.classic.util.ClassicEnvUtil.loadFromServiceLoader;

/**
 * Utility methods to configure {@link LoggerContext}.
 *
 * @author Sergio del Amo
 * @since 3.8.4
 */
public final class LogbackUtils {

    /**
     * What {@link #describeConfiguration(ClassLoader)} returns when {@link Configurator} services configure Logback.
     */
    @Internal
    public static final String CONFIGURATOR_SERVICES = "Configurator services";

    private LogbackUtils() {
    }

    /**
     * Configures a Logger Context, typically after a {@link LoggerContext#reset()}. Only
     * {@link LogbackLoggingSystem#refresh()} calls it, also for the logging system of the loggers
     * endpoint, which delegates its refresh to that class.
     * <p>
     * A location that only Micronaut can see wins: {@code logback.configurationFile} of the
     * Micronaut configuration, unless its value is exactly that of the JVM system property of the
     * same name, or {@code logger.config} when {@code logback.configurationFile} is not set.
     * The context is configured from that location with {@link DefaultJoranConfigurator}, whatever
     * {@link Configurator} services exist. The location is looked up on the classpath first and
     * then on the file system, and a missing location fails.
     * </p>
     * <p>
     * Otherwise the context is configured the way Logback's own startup configures it, with
     * {@link ContextInitializer#autoConfig(ClassLoader)} and the class loader that Logback's startup
     * uses, which is the one that loaded Logback: the {@link Configurator} services by rank, until
     * one returns {@link Configurator.ExecutionStatus#DO_NOT_INVOKE_NEXT_IF_ANY}, then the
     * {@code logback.configurationFile} JVM system property, {@code logback-test.xml} or
     * {@code logback.xml} on the classpath, then the basic console configuration.
     * </p>
     * <p>
     * There is one addition to Logback's own lookup, kept from the previous versions of this
     * class: a {@code logback.xml} file in the working directory is used when there is no
     * {@link Configurator} service, the {@code logback.configurationFile} JVM system property is
     * not set, and neither {@code logback-test.xml} nor {@code logback.xml} is on the classpath.
     * </p>
     * <p>
     * This method fails when a file that it reads itself cannot be parsed, be it the location set in
     * Micronaut configuration or the {@code logback.xml} in the working directory. It does not fail
     * when Logback finds the file: Logback reports the error the way it does at startup.
     * </p>
     *
     * @param classLoader       The class loader to look up a location set in Micronaut configuration with
     * @param context           The Logger Context
     * @param configurationFile The {@code logback.configurationFile} property of the Micronaut configuration, if any
     * @param loggerConfig      The {@code logger.config} property of the Micronaut configuration, if any
     * @throws LoggingSystemException if the location set in Micronaut configuration does not exist, if
     *                                that location or the {@code logback.xml} in the working directory
     *                                cannot be parsed, or if Logback fails to configure the context
     */
    static void configure(ClassLoader classLoader,
                          LoggerContext context,
                          @Nullable String configurationFile,
                          @Nullable String loggerConfig) {
        // ContextInitializer.autoConfig(), which Logback's startup calls, uses this class loader
        configure(classLoader, Loader.getClassLoaderOfClass(Configurator.class), new File(ClassicConstants.AUTOCONFIG_FILE),
            context, configurationFile, loggerConfig);
    }

    /**
     * Configures a Logger Context as {@link #configure(ClassLoader, LoggerContext, String, String)} does, except in
     * development mode, where the application's resources are on a class loader of their own: the given one, whose
     * parent loaded Logback. Logback's own lookup, which searches the loader that loaded Logback, does not see them,
     * so {@link #configureFromResources(LoggerContext, ClassLoader, File)} looks them up in the same order.
     *
     * @param classLoader       The class loader to look up a location set in Micronaut configuration with
     * @param resources         The loader of the application's resources in development mode, otherwise null
     * @param context           The Logger Context
     * @param configurationFile The {@code logback.configurationFile} property of the Micronaut configuration, if any
     * @param loggerConfig      The {@code logger.config} property of the Micronaut configuration, if any
     */
    static void configure(ClassLoader classLoader,
                          @Nullable ClassLoader resources,
                          LoggerContext context,
                          @Nullable String configurationFile,
                          @Nullable String loggerConfig) {
        if (resources == null) {
            configure(classLoader, context, configurationFile, loggerConfig);
            return;
        }
        String location = micronautOnlyLocation(configurationFile, loggerConfig);
        if (location != null) {
            configureByResource(context, location, findResource(resources, location));
            return;
        }
        try {
            configureFromResources(context, resources, new File(ClassicConstants.AUTOCONFIG_FILE));
        } catch (Exception | ServiceConfigurationError e) {
            throw new LoggingSystemException("Error while refreshing Logback", e);
        }
    }

    /**
     * Resets the Logger Context and configures it from the application's resources, for development mode, whose
     * launcher keeps Logback in a parent tier that does not see them: the {@link Configurator} services of the
     * resources' loader, the {@code logback.configurationFile} JVM system property, {@code logback-test.xml} or
     * {@code logback.xml} among the resources, the {@code logback.xml} file of the working directory, then the basic
     * console configuration. Errors are reported as Logback's startup reports them: printed, and not thrown.
     *
     * @param context   The Logger Context
     * @param resources The loader of the application's resources
     * @since 5.3.0
     */
    @Internal
    public static void reconfigure(LoggerContext context, ClassLoader resources) {
        long start = System.currentTimeMillis();
        context.reset();
        try {
            configureFromResources(context, resources, new File(ClassicConstants.AUTOCONFIG_FILE));
        } catch (Exception | ServiceConfigurationError e) {
            context.getStatusManager().add(new ErrorStatus("Error while configuring Logback from " + resources, context, e));
        }
        if (!StatusUtil.contextHasStatusListener(context)) {
            StatusPrinter.printInCaseOfErrorsOrWarnings(context, start);
        }
    }

    /**
     * What {@link #reconfigure(LoggerContext, ClassLoader)} would configure a Logger Context with, for development
     * mode to tell whether it changed: the {@link Configurator} services, or the configuration file.
     *
     * @param resources The loader of the application's resources, or the one that loaded Logback
     * @return {@value #CONFIGURATOR_SERVICES} when a {@link Configurator} service configures Logback, the URL of the
     * configuration file otherwise, or null when there is neither and Logback falls back to its basic configuration
     * @since 5.3.0
     */
    @Internal
    public static @Nullable String describeConfiguration(ClassLoader resources) {
        try {
            if (hasConfiguratorService(resources)) {
                return CONFIGURATOR_SERVICES;
            }
            URL url = findConfigurationFile(resources, new File(ClassicConstants.AUTOCONFIG_FILE));
            return url == null ? null : url.toExternalForm();
        } catch (MalformedURLException | ServiceConfigurationError e) {
            return null;
        }
    }

    /**
     * Logback's lookup at startup, with the default files and a classpath location of the
     * {@code logback.configurationFile} JVM system property looked up among the given resources, and the
     * {@code logback.xml} file of the working directory after them, as {@link #findWorkingDirectoryFile} has it.
     *
     * @param context              The Logger Context
     * @param resources            The loader of the application's resources
     * @param workingDirectoryFile The {@code logback.xml} file of the working directory
     * @throws JoranException if Logback's lookup fails
     * @throws MalformedURLException if the working directory file cannot be converted to a URL
     */
    static void configureFromResources(LoggerContext context, ClassLoader resources, File workingDirectoryFile)
        throws JoranException, MalformedURLException {
        if (hasConfiguratorService(resources)) {
            new ContextInitializer(context).autoConfig(resources);
            return;
        }
        URL url = findConfigurationFile(resources, workingDirectoryFile);
        if (url == null) {
            // the basic console configuration, which Logback's startup reports as it does
            new ContextInitializer(context).autoConfig(resources);
            return;
        }
        context.getStatusManager().add(new InfoStatus("Found Logback configuration [" + url + "] among the application's resources", context));
        try {
            configureByUrl(context, url);
        } catch (JoranException e) {
            // as at Logback's startup, the error is a status of the context, printed rather than thrown
            context.getStatusManager().add(new ErrorStatus("Failed to configure Logback from [" + url + "]", context, e));
        }
    }

    /**
     * @param resources            The loader of the application's resources
     * @param workingDirectoryFile The {@code logback.xml} file of the working directory
     * @return The configuration file Logback's startup would find, were the resources on its loader, if any
     * @throws MalformedURLException if the working directory file cannot be converted to a URL
     */
    private static @Nullable URL findConfigurationFile(ClassLoader resources, File workingDirectoryFile) throws MalformedURLException {
        String property = System.getProperty(ClassicConstants.CONFIG_FILE_PROPERTY);
        if (property != null) {
            // as Logback resolves it: a URL, then a classpath resource, then a file; otherwise the default files
            try {
                return URI.create(property).toURL();
            } catch (IllegalArgumentException | MalformedURLException e) {
                URL resource = resources.getResource(property);
                if (resource != null) {
                    return resource;
                }
                File file = new File(property);
                if (file.isFile()) {
                    return file.toURI().toURL();
                }
            }
        }
        URL url = resources.getResource(ClassicConstants.TEST_AUTOCONFIG_FILE);
        if (url == null) {
            url = resources.getResource(ClassicConstants.AUTOCONFIG_FILE);
        }
        if (url == null && workingDirectoryFile.isFile()) {
            url = workingDirectoryFile.toURI().toURL();
        }
        return url;
    }

    /**
     * Visible for testing: production code goes through the overload above, which passes the class
     * loader that loaded Logback and the {@code logback.xml} file of the working directory. Only tests
     * call this overload directly, to make Logback's lookup see {@link Configurator} services and
     * default files that are not on their classpath, and to put the working-directory file elsewhere.
     *
     * @param classLoader          The class loader to look up a location set in Micronaut configuration with
     * @param logbackClassLoader   The class loader that Logback's own lookup uses
     * @param workingDirectoryFile The {@code logback.xml} file of the working directory
     * @param context              The Logger Context
     * @param configurationFile    The {@code logback.configurationFile} property of the Micronaut configuration, if any
     * @param loggerConfig         The {@code logger.config} property of the Micronaut configuration, if any
     */
    static void configure(ClassLoader classLoader,
                          ClassLoader logbackClassLoader,
                          File workingDirectoryFile,
                          LoggerContext context,
                          @Nullable String configurationFile,
                          @Nullable String loggerConfig) {
        String location = micronautOnlyLocation(configurationFile, loggerConfig);
        if (location != null) {
            configureByResource(context, location, findResource(classLoader, location));
            return;
        }
        try {
            URL workingDirectoryUrl = findWorkingDirectoryFile(workingDirectoryFile, logbackClassLoader);
            if (workingDirectoryUrl != null) {
                context.getStatusManager().add(new InfoStatus("Found resource [" + ClassicConstants.AUTOCONFIG_FILE + "] in the working directory at [" + workingDirectoryUrl + "]", context));
                configureByUrl(context, workingDirectoryUrl);
            } else {
                new ContextInitializer(context).autoConfig(logbackClassLoader);
            }
        } catch (Exception | ServiceConfigurationError e) {
            // Logback wraps what a Configurator throws in a LogbackException, and a Configurator service
            // that cannot be loaded is a ServiceConfigurationError. Nothing else is expected.
            throw new LoggingSystemException("Error while refreshing Logback", e);
        }
    }

    /**
     * Configures a Logger Context.
     *
     * @param classLoader        Class Loader
     * @param context            Logger Context
     * @param logbackXmlLocation the location of the xml logback config file
     * @deprecated This method cannot tell a location set in configuration from a default one, and
     * it uses the first {@link Configurator} service whatever its rank and status. Call
     * {@link io.micronaut.logging.LoggingSystem#refresh()} on the logging system bean instead.
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
            // The location is Logback's own when it is the -Dlogback.configurationFile JVM system property.
            // System properties are a property source of the Micronaut environment, so a value that is
            // only set there arrives here as the very same string, and exact equality tells the cases apart:
            // - Equal: Logback's startup read the same string, so its own lookup is used. A value repeated
            //   verbatim in another property source cannot be told apart from the system property, and
            //   does not need to be.
            // - Not equal, be it only by a trailing slash or by a relative instead of an absolute path:
            //   Micronaut configuration holds a value that Logback's startup did not read, because there
            //   is no system property or because a property source that takes precedence over the system
            //   properties overrides it. That value is used here, and it fails when it does not exist.
            //   Normalizing both values before comparing them would be guessing that two different
            //   settings mean the same.
            // Either way logback.configurationFile keeps its precedence over logger.config.
            return configurationFile.equals(System.getProperty(ClassicConstants.CONFIG_FILE_PROPERTY)) ? null : configurationFile;
        }
        return loggerConfig;
    }

    /**
     * Logback's own lookup searches only the classpath for the default file names, whereas this class
     * has always fallen back to a {@code logback.xml} file in the working directory. That fallback is
     * kept for the case that nothing else selects a configuration. The previous versions of this class
     * never looked for {@code logback-test.xml}, so they also used the file in the working directory
     * when only {@code logback-test.xml} was on the classpath. Logback's lookup finds it, so it now
     * takes precedence.
     *
     * @param file               The {@code logback.xml} file of the working directory
     * @param logbackClassLoader The class loader that Logback's own lookup uses
     * @return The URL of that file, if it exists and no {@link Configurator} service,
     * {@code logback.configurationFile} JVM system property or default file on the classpath takes
     * precedence over it
     * @throws MalformedURLException if the file cannot be converted to a URL
     */
    private static @Nullable URL findWorkingDirectoryFile(File file, ClassLoader logbackClassLoader) throws MalformedURLException {
        // The file is checked first because it rarely exists, and then nothing else is looked up
        if (!file.exists()
            || System.getProperty(ClassicConstants.CONFIG_FILE_PROPERTY) != null
            || logbackClassLoader.getResource(ClassicConstants.TEST_AUTOCONFIG_FILE) != null
            || logbackClassLoader.getResource(ClassicConstants.AUTOCONFIG_FILE) != null
            || hasConfiguratorService(logbackClassLoader)) {
            return null;
        }
        return file.toURI().toURL();
    }

    /**
     * @param logbackClassLoader The class loader that Logback's own lookup uses
     * @return Whether that lookup finds a {@link Configurator} service
     */
    private static boolean hasConfiguratorService(ClassLoader logbackClassLoader) {
        // The same lookup as Logback's ClassicEnvUtil.loadFromServiceLoader, which runs next when there is a
        // service, except that the stream does not instantiate the services. With loadFromServiceLoader here
        // as well, a refresh would instantiate each of them twice.
        @SuppressWarnings("NoReflection")
        boolean found = ServiceLoader.load(Configurator.class, logbackClassLoader).stream().findAny().isPresent();
        return found;
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
    @SuppressWarnings("java:S106") // the context being configured has no appender to log this error with
    private static void configureByResource(LoggerContext context, String location, @Nullable URL resource) {
        if (resource == null) {
            System.err.println("ERROR: Logback configuration file " + location + " not found");
            throw new LoggingSystemException("Resource " + location + " not found");
        }
        try {
            configureByUrl(context, resource);
        } catch (JoranException e) {
            throw new LoggingSystemException("Error while refreshing Logback", e);
        }
    }

    /**
     * @param context Logger Context
     * @param url     The URL of the xml logback config file
     * @throws JoranException if the file cannot be read or parsed
     */
    private static void configureByUrl(LoggerContext context, URL url) throws JoranException {
        DefaultJoranConfigurator defaultConfigurator = new DefaultJoranConfigurator();
        defaultConfigurator.setContext(context);
        defaultConfigurator.configureByResource(url);
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
