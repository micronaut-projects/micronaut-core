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
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.DefaultApplicationContextBuilder;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.cli.CommandLine;
import io.micronaut.runtime.context.env.CommandLinePropertySource;
import io.micronaut.runtime.exceptions.ApplicationStartupException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;

/**
 * <p>Main entry point for running a Micronaut application.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class Micronaut extends DefaultApplicationContextBuilder implements ApplicationContextBuilder  {

    private static final Logger LOG = LoggerFactory.getLogger(Micronaut.class);

    private String[] args = new String[0];
    private Map<Class<? extends Throwable>, Function<Throwable, Integer>> exitHandlers = new LinkedHashMap<>();

    /**
     * The default constructor.
     */
    protected Micronaut() {
    }

    /**
     * @return Run this {@link Micronaut}
     */
    @Override
    public ApplicationContext start() {
        CommandLine commandLine = CommandLine.parse(args);
        propertySources(new CommandLinePropertySource(commandLine));
        ApplicationContext applicationContext = super.build();

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

    @Override
    public Micronaut include(@Nullable String... configurations) {
        return (Micronaut) super.include(configurations);
    }

    @Override
    public Micronaut exclude(@Nullable String... configurations) {
        return (Micronaut) super.exclude(configurations);
    }

    /**
     * Add classes to be included in the initialization of the application.
     *
     * @param classes The application
     * @return The classes
     */
    public Micronaut classes(@Nullable Class... classes) {
        if (classes != null) {
            for (Class aClass : classes) {
                packages(aClass.getPackage().getName());
            }
        }
        return this;
    }

    @Override
    public Micronaut properties(@Nullable Map<String, Object> properties) {
        return (Micronaut) super.properties(properties);
    }

    @Override
    public Micronaut singletons(Object... beans) {
        return (Micronaut) super.singletons(beans);
    }

    @Override
    public Micronaut propertySources(@Nullable PropertySource... propertySources) {
        return (Micronaut) super.propertySources(propertySources);
    }

    @Override
    public Micronaut mainClass(Class mainClass) {
        return (Micronaut) super.mainClass(mainClass);
    }

    @Override
    public Micronaut classLoader(ClassLoader classLoader) {
        return (Micronaut) super.classLoader(classLoader);
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

    @Override
    public Micronaut environments(@Nullable String... environments) {
        return (Micronaut) super.environments(environments);
    }

    @Override
    public Micronaut packages(@Nullable String... packages) {
        return (Micronaut) super.packages(packages);
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
            .start();
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
