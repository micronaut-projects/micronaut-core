/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.runtime;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.cli.CommandLine;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.runtime.context.env.CommandLinePropertySource;
import io.micronaut.runtime.exceptions.ApplicationStartupException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * <p>Main entry point for running a Micronaut application.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class Micronaut {

    private static final Logger LOG = LoggerFactory.getLogger(Micronaut.class);

    private Collection<Class> classes = new ArrayList<>();
    private Collection<Package> packages = new ArrayList<>();
    private Collection<String> configurationIncludes = new HashSet<>();
    private Collection<String> configurationExcludes = new HashSet<>();
    private String[] args = new String[0];
    private Set<String> environments = new HashSet<>();
    private Map<Class<? extends Throwable>, Function<Throwable, Integer>> exitHandlers = new LinkedHashMap<>();
    private Collection<Map<String, Object>> propertyMaps = new ArrayList<>();

    /**
     * The default constructor.
     */
    protected Micronaut() {
    }

    /**
     * @param applicationClass The application class
     * @return Run this {@link Micronaut}
     */
    public ApplicationContext start(Class applicationClass) {
        CommandLine commandLine = CommandLine.parse(args);

        String[] envArray = this.environments.toArray(new String[this.environments.size()]);
        ApplicationContext applicationContext;
        if (applicationClass != null) {
            applicationContext = ApplicationContext.build(applicationClass, envArray);
        } else {
            applicationContext = ApplicationContext.build(ApplicationContext.class.getClassLoader(), envArray);
        }
        applicationContext.registerSingleton(commandLine);

        // Add packages to scan
        Environment environment = applicationContext.getEnvironment();
        for (Class cls : classes) {
            environment.addPackage(cls.getPackage());
        }

        for (Package aPackage : packages) {
            environment.addPackage(aPackage);
        }
        // Add the system properties passed via the command line
        environment.addPropertySource(new CommandLinePropertySource(commandLine));

        for (Map<String, Object> propertyMap : propertyMaps) {
            environment.addPropertySource(PropertySource.of(Environment.DEFAULT_NAME, propertyMap));
        }

        try {
            long start = System.currentTimeMillis();
            applicationContext.start();

            Optional<EmbeddedServer> embeddedContainerBean = applicationContext.findBean(EmbeddedServer.class);

            embeddedContainerBean.ifPresent((embeddedServer -> {
                try {
                    embeddedServer.start();
                    if (LOG.isInfoEnabled()) {
                        long end = System.currentTimeMillis();
                        long took = end - start;
                        LOG.info("Startup completed in {}ms. Server Running: {}", took, embeddedServer.getURL());
                    }
                    Runtime.getRuntime().addShutdownHook(new Thread(embeddedServer::stop));

                } catch (Throwable e) {
                    handleStartupException(applicationContext.getEnvironment(), e);
                }
            }));

            if (LOG.isInfoEnabled() && !embeddedContainerBean.isPresent()) {
                LOG.info("No embedded container found. Running as CLI application");
            }
            return applicationContext;
        } catch (Throwable e) {
            handleStartupException(applicationContext.getEnvironment(), e);
            return null;
        }
    }

    /**
     * Add classes to be included in the initialization of the application.
     *
     * @param classes The application
     * @return The classes
     */
    public Micronaut classes(@Nullable Class... classes) {
        if (classes != null) {
            this.classes.addAll(Arrays.asList(classes));
        }
        return this;
    }

    /**
     * Add additional properties to the {@link PropertySource} list.
     *
     * @param properties The properties
     * @return The properties
     */
    public Micronaut properties(@Nullable Map<String, Object> properties) {
        if (properties != null) {
            this.propertyMaps.add(properties);
        }
        return this;
    }

    /**
     * Set the command line arguments.
     *
     * @param args The arguments
     * @return This application
     */
    public Micronaut args(@Nullable String... args) {
        if (args != null) {
            this.args = args;
        }
        return this;
    }

    /**
     * Set the environment.
     *
     * @param environments The environment
     * @return This application
     */
    public Micronaut env(@Nullable String... environments) {
        if (ArrayUtils.isNotEmpty(environments)) {
            this.environments = CollectionUtils.setOf(environments);
        }
        return this;
    }

    /**
     * Add packages to scan.
     *
     * @param packages The packages
     * @return This application
     */
    public Micronaut packages(@Nullable Package... packages) {
        if (packages != null) {
            this.packages.addAll(Arrays.asList(packages));
        }
        return this;
    }

    /**
     * Add packages to scan.
     *
     * @param packages The packages
     * @return This application
     */
    public Micronaut packages(String... packages) {
        if (packages != null) {
            for (String aPackage : packages) {
                Package thePackage = Package.getPackage(aPackage);
                if (thePackage != null) {
                    this.packages.add(thePackage);
                }
            }
        }
        return this;
    }

    /**
     * Allow customizing the configurations that will be loaded.
     *
     * @param configurations The configurations to include
     * @return This application
     */
    public Micronaut include(@Nullable String... configurations) {
        if (configurations != null) {
            this.configurationIncludes.addAll(Arrays.asList(configurations));
        }
        return this;
    }

    /**
     * Allow customizing the configurations that will be loaded.
     *
     * @param configurations The configurations to exclude
     * @return This application
     */
    public Micronaut exclude(@Nullable String... configurations) {
        if (configurations != null) {
            this.configurationExcludes.addAll(Arrays.asList(configurations));
        }
        return this;
    }

    /**
     * Maps an exception to the given error code.
     *
     * @param exception The exception
     * @param mapper    The mapper
     * @param <T>       The exception type
     * @return This application
     */
    public <T extends Throwable> Micronaut mapError(Class<T> exception, Function<T, Integer> mapper) {
        this.exitHandlers.put(exception, (Function<Throwable, Integer>) mapper);
        return this;
    }

    /**
     * Run the application for the given arguments. Classes for the application will be discovered automatically
     *
     * @param args The arguments
     * @return The {@link ApplicationContext}
     */
    public static Micronaut build(String... args) {
        return new Micronaut().args(args);
    }

    /**
     * Run the application for the given arguments. Classes for the application will be discovered automatically
     *
     * @param args The arguments
     * @return The {@link ApplicationContext}
     */
    public static ApplicationContext run(String... args) {
        return run(new Class[0], args);
    }

    /**
     * Run the application for the given arguments.
     *
     * @param cls  The application class
     * @param args The arguments
     * @return The {@link ApplicationContext}
     */
    public static ApplicationContext run(Class cls, String... args) {
        return run(new Class[]{cls}, args);
    }

    /**
     * Run the application for the given arguments.
     *
     * @param classes The application classes
     * @param args    The arguments
     * @return The {@link ApplicationContext}
     */
    public static ApplicationContext run(Class[] classes, String... args) {
        return new Micronaut()
            .classes(classes)
            .args(args)
            .start(ArrayUtils.isNotEmpty(classes) ? classes[0] : Micronaut.class);
    }

    /**
     * Default handling of startup exceptions.
     *
     * @param environment The environment
     * @param exception   The exception
     * @throws ApplicationStartupException If the server cannot be shutdown with an appropriate exist code
     */
    protected void handleStartupException(Environment environment, Throwable exception) {
        Function<Throwable, Integer> exitCodeMapper = exitHandlers.computeIfAbsent(exception.getClass(), exceptionType -> (throwable -> 1));
        Integer code = exitCodeMapper.apply(exception);
        if (code > 0) {
            if (!environment.getActiveNames().contains(Environment.TEST)) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Error starting Micronaut server: " + exception.getMessage(), exception);
                }
                System.exit(code);
            }
        }
        throw new ApplicationStartupException("Error starting Micronaut server: " + exception.getMessage(), exception);
    }
}
