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
package io.micronaut.runtime;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.DefaultApplicationContextBuilder;
import io.micronaut.context.RuntimeBeanDefinition;
import io.micronaut.context.banner.Banner;
import io.micronaut.context.banner.MicronautBanner;
import io.micronaut.context.banner.ResourceBanner;
import io.micronaut.context.env.DevelopmentMode;
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.core.type.Argument;
import io.micronaut.core.io.ResourceLoadStrategy;
import io.micronaut.context.env.PropertySource;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.naming.Described;
import io.micronaut.runtime.exceptions.ApplicationStartupException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static io.micronaut.core.reflect.ReflectionUtils.EMPTY_CLASS_ARRAY;

/**
 * <p>Main entry point for running a Micronaut application.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@NullMarked
public class Micronaut extends DefaultApplicationContextBuilder implements ApplicationContextBuilder  {
    private static final String BANNER_NAME = "micronaut-banner.txt";
    private static final Logger LOG = LoggerFactory.getLogger(Micronaut.class);
    private static final String SHUTDOWN_MONITOR_THREAD = "micronaut-shutdown-monitor-thread";
    private static final long SHUTDOWN_MONITOR_INTERVAL_MS = 250;

    private final Map<Class<? extends Throwable>, Function<Throwable, Integer>> exitHandlers = new LinkedHashMap<>();

    /**
     * The default constructor.
     */
    protected Micronaut() {
    }

    /**
     * @return Run this {@link Micronaut}
     */
    @Override
    @SuppressWarnings({"java:S1181", "java:S3776", "java:S1141"})
    public ApplicationContext start() {
        long start = System.nanoTime();
        printBanner();
        ApplicationContext applicationContext = super.build();

        try {

            applicationContext.start();

            EmbeddedApplication<?> embeddedApplication = applicationContext.findBean(EmbeddedApplication.class).orElse(null);

            if (embeddedApplication != null) {
                try {
                    embeddedApplication.start();

                    boolean keepAlive;
                    if (embeddedApplication instanceof Described described) {
                        if (LOG.isInfoEnabled()) {
                            long took = elapsedMillis(start);
                            String desc = described.getDescription();
                            LOG.info("Startup completed in {}ms. Server Running: {}", took, desc);
                        }
                        keepAlive = embeddedApplication.isServer();
                    } else {
                        if (embeddedApplication instanceof EmbeddedServer embeddedServer) {
                            if (LOG.isInfoEnabled()) {
                                long took = elapsedMillis(start);
                                Object uri;
                                try {
                                    uri = embeddedServer.getContextURI();
                                } catch (UnsupportedOperationException e) {
                                    uri = "<URI display not available: " + e.getMessage() + ">";
                                }
                                LOG.info("Startup completed in {}ms. Server Running: {}", took, uri);
                            }
                            keepAlive = embeddedServer.isKeepAlive();
                        } else {
                            if (LOG.isInfoEnabled()) {
                                long took = elapsedMillis(start);
                                LOG.info("Startup completed in {}ms.", took);
                            }
                            keepAlive = embeddedApplication.isServer();
                        }
                    }

                    Thread mainThread = Thread.currentThread();
                    boolean finalKeepAlive = keepAlive;
                    CountDownLatch countDownLatch = new CountDownLatch(1);
                    Thread shutdownHook = null;
                    if (embeddedApplication.isShutdownHookNeeded()) {
                        try {
                            Thread hook = new Thread(() -> {
                                if (LOG.isInfoEnabled()) {
                                    LOG.info("Embedded Application shutting down");
                                }
                                try (applicationContext) {
                                /* stop the application without checking if it is running,
                                   as it may have already opened some resources before starting */
                                    embeddedApplication.stop();
                                    countDownLatch.countDown();
                                    if (finalKeepAlive) {
                                        mainThread.interrupt();
                                    }
                                }
                            });
                            Runtime.getRuntime().addShutdownHook(hook);
                            shutdownHook = hook;
                        } catch (IllegalStateException e) {
                            try (applicationContext) {
                                embeddedApplication.stop();
                            } catch (Throwable stopError) {
                                LOG.error("Embedded Application shutting down", stopError);
                            }
                            LOG.warn("Failed to register shutdown hook", e);
                        }
                        if (shutdownHook != null) {
                            removeShutdownHookOnStop(applicationContext, shutdownHook);
                        }
                    }

                    if (keepAlive) {
                        // release the calling thread once the application stops, however it was stopped: through
                        // the shutdown hook, or by another component such as a development launcher restarting it
                        Thread monitor = new Thread(() -> {
                            try {
                                while (embeddedApplication.isRunning()) {
                                    Thread.sleep(SHUTDOWN_MONITOR_INTERVAL_MS);
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } finally {
                                countDownLatch.countDown();
                            }
                        }, SHUTDOWN_MONITOR_THREAD);
                        monitor.setDaemon(true);
                        monitor.start();

                        boolean interrupted = false;
                        while (true) {
                            try {
                                countDownLatch.await();
                                break;
                            } catch (InterruptedException e) {
                                interrupted = true;
                                Thread.currentThread().interrupt();
                            }
                        }
                        monitor.interrupt();
                        if (interrupted) {
                            Thread.currentThread().interrupt();
                        }
                        if (LOG.isInfoEnabled()) {
                            LOG.info("Embedded Application shutting down");
                        }
                        removeShutdownHook(shutdownHook);
                    }

                    if (embeddedApplication.isForceExit() && isExitAllowed(applicationContext.getEnvironment())) {
                        System.exit(0);
                    }

                } catch (Throwable e) {
                    handleStartupException(applicationContext.getEnvironment(), e);
                    Thread.currentThread().interrupt();
                }
            }

            if (LOG.isInfoEnabled() && embeddedApplication == null) {
                LOG.info("No embedded container found. Running as CLI application");
            }
            return applicationContext;
        } catch (ApplicationStartupException e) {
            // already reported by handleStartupException for the embedded application
            throw e;
        } catch (Throwable e) {
            handleStartupException(applicationContext.getEnvironment(), e);
            Thread.currentThread().interrupt();
            return applicationContext;
        }
    }

    private static long elapsedMillis(long startNanos) {
        return TimeUnit.MILLISECONDS.convert(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public Micronaut include(String @Nullable ... configurations) {
        return (Micronaut) super.include(configurations);
    }

    @Override
    public Micronaut exclude(String @Nullable ... configurations) {
        return (Micronaut) super.exclude(configurations);
    }

    @Override
    public Micronaut banner(boolean isEnabled) {
        return (Micronaut) super.banner(isEnabled);
    }

    /**
     * Add classes to be included in the initialization of the application.
     *
     * @param classes The application
     * @return The classes
     */
    public Micronaut classes(@Nullable Class<?>... classes) {
        if (classes != null) {
            for (Class<?> aClass : classes) {
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
    public Micronaut singletons(Object @Nullable ... beans) {
        return (Micronaut) super.singletons(beans);
    }

    @Override
    public Micronaut beanDefinitions(RuntimeBeanDefinition<?>... definitions) {
        return (Micronaut) super.beanDefinitions(definitions);
    }

    @Override
    public Micronaut propertySources(PropertySource @Nullable ... propertySources) {
        return (Micronaut) super.propertySources(propertySources);
    }

    @Override
    public Micronaut environmentPropertySource(boolean environmentPropertySource) {
        return (Micronaut) super.environmentPropertySource(environmentPropertySource);
    }

    @Override
    public Micronaut environmentVariableIncludes(String @Nullable ... environmentVariables) {
        return (Micronaut) super.environmentVariableIncludes(environmentVariables);
    }

    @Override
    public Micronaut environmentVariableExcludes(String @Nullable... environmentVariables) {
        return (Micronaut) super.environmentVariableExcludes(environmentVariables);
    }

    @Override
    public Micronaut mainClass(@Nullable Class<?> mainClass) {
        return (Micronaut) super.mainClass(mainClass);
    }

    @Override
    public Micronaut classLoader(@Nullable ClassLoader classLoader) {
        return (Micronaut) super.classLoader(classLoader);
    }

    @Override
    public Micronaut args(String @Nullable ... args) {
        return (Micronaut) super.args(args);
    }

    @Override
    public Micronaut environments(String @Nullable ... environments) {
        return (Micronaut) super.environments(environments);
    }

    @Override
    public Micronaut configurationLoadingStrategy(ResourceLoadStrategy.Builder builder) {
        return (Micronaut) super.configurationLoadingStrategy(builder);
    }

    @Override
    public Micronaut defaultEnvironments(String @Nullable ... environments) {
        return (Micronaut) super.defaultEnvironments(environments);
    }

    @Override
    public Micronaut packages(String @Nullable ... packages) {
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
        return run(EMPTY_CLASS_ARRAY, args);
    }

    /**
     * Run the application for the given arguments.
     *
     * @param cls  The application class
     * @param args The arguments
     * @return The {@link ApplicationContext}
     */
    public static ApplicationContext run(Class<?> cls, String... args) {
        return run(new Class<?>[]{cls}, args);
    }

    /**
     * Run the application for the given arguments.
     *
     * @param classes The application classes
     * @param args    The arguments
     * @return The {@link ApplicationContext}
     */
    public static ApplicationContext run(Class<?>[] classes, String... args) {
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
        int code = exitCodeMapper.apply(exception);
        if (code > 0 && isExitAllowed(environment)) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Error starting Micronaut server: {}", exception.getMessage(), exception);
            }
            System.exit(code);
        }
        throw new ApplicationStartupException("Error starting Micronaut server: " + exception.getMessage(), exception);
    }

    /**
     * Whether this launcher may terminate the JVM, on a startup failure or when the embedded application
     * asks for a forced exit. It may not while the {@link Environment#TEST test} environment is active,
     * because the test owns the JVM, and not in {@link DevelopmentMode development mode}, because the
     * development launcher owns it and keeps serving the previous version of the application instead.
     *
     * @param environment The environment
     * @return True if the launcher may call {@code System.exit}
     * @since 5.3.0
     */
    protected boolean isExitAllowed(Environment environment) {
        return !environment.getActiveNames().contains(Environment.TEST) && !DevelopmentMode.isEnabled(environment);
    }

    /**
     * Removes the shutdown hook when the context stops, however it is stopped. An application that is not kept
     * alive, such as the Netty server, returns from {@link #start()} with the hook registered, and a context
     * stopped by its embedder (a test, or a development launcher that starts the next generation in the same
     * JVM) would otherwise stay reachable from the hook, with the hook's context class loader, until the JVM
     * exits. When the hook itself stops the context, the JVM is already shutting down and the hook stays.
     *
     * @param applicationContext The context
     * @param shutdownHook The hook registered for it
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void removeShutdownHookOnStop(ApplicationContext applicationContext, Thread shutdownHook) {
        ApplicationEventListener<ShutdownEvent> listener = event -> removeShutdownHook(shutdownHook);
        applicationContext.registerBeanDefinition(
            RuntimeBeanDefinition.builder((Class) ApplicationEventListener.class, () -> listener)
                .singleton(true)
                .typeArguments(Argument.of(ShutdownEvent.class))
                .build()
        );
    }

    private static void removeShutdownHook(@Nullable Thread shutdownHook) {
        if (shutdownHook == null) {
            return;
        }
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException e) {
            // the JVM is already shutting down and the hook is running or about to: leave it be
        }
    }

    @SuppressWarnings("java:S106")
    private void printBanner() {
        if (!isBannerEnabled()) {
            return;
        }
        PrintStream out = System.out;
        resolveBanner(out).print();
    }

    private Banner resolveBanner(PrintStream out) {
        return getResourceLoader().getResource(BANNER_NAME)
            .map(resource -> (Banner) new ResourceBanner(resource, out))
            .orElseGet(() -> new MicronautBanner(out));
    }

}
