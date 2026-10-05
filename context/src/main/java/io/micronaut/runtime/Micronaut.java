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
import io.micronaut.context.env.CachedEnvironment;
import io.micronaut.context.env.DevelopmentMode;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.EnvironmentPropertySource;
import io.micronaut.context.env.SystemPropertiesPropertySource;
import io.micronaut.core.io.ResourceLoadStrategy;
import io.micronaut.context.env.PropertySource;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.naming.Described;
import io.micronaut.core.util.StringUtils;
import io.micronaut.runtime.exceptions.ApplicationStartupException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.List;
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
    static final String TRAINING_ENABLED_ENVIRONMENT_VARIABLE = "MICRONAUT_APPLICATION_TRAINING_ENABLED";
    private static final String BANNER_NAME = "micronaut-banner.txt";
    private static final Logger LOG = LoggerFactory.getLogger(Micronaut.class);
    private static final String SHUTDOWN_MONITOR_THREAD = "micronaut-shutdown-monitor-thread";
    private static final long SHUTDOWN_MONITOR_INTERVAL_MS = 250;

    private final Map<Class<? extends Throwable>, Function<Throwable, Integer>> exitHandlers = new LinkedHashMap<>();
    /**
     * Whether this launcher may exit the JVM, decided once the environment started: a server that fails to start, or
     * an application that stops, stops the context and with it the environment, whose properties are then gone.
     */
    private @Nullable Boolean exitAllowed;

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
        // a launcher started again decides again: until its environment starts, a failure is judged by that environment
        exitAllowed = null;
        printBanner();
        ApplicationContext applicationContext = super.build();

        try {

            // The training mode decides whether the context starts at all, so the switch is read before
            // applicationContext.start(), from the environment that start() would otherwise start first
            Environment environment = applicationContext.getEnvironment();
            // Test Resources reads the configuration together with the environment, so a training run that is
            // already known disables it first
            boolean trainingRunBeforeStart = isTrainingRunBeforeStart(environment);
            startEnvironment(environment, trainingRunBeforeStart);
            exitAllowed = isExitAllowed(environment);
            boolean trainingRun = isTrainingRun(environment);
            if (trainingRun) {
                TrainingTestResources.checkDisabled(environment, trainingRunBeforeStart);
                if (TrainingLoad.isSelected(environment)) {
                    TrainingLoad.run(applicationContext);
                    return applicationContext;
                }
            } else if (trainingRunBeforeStart) {
                // A source that the environment only reads when it starts turned the switch off again
                TrainingTestResources.warnNotATrainingRun(environment);
            }

            applicationContext.start();

            EmbeddedApplication<?> embeddedApplication = applicationContext.findBean(EmbeddedApplication.class).orElse(null);
            if (trainingRun) {
                announceTrainingRun(environment, embeddedApplication != null);
            }

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

                    if (trainingRun) {
                        finishTrainingRun(applicationContext, embeddedApplication);
                        return applicationContext;
                    }

                    Thread mainThread = Thread.currentThread();
                    boolean finalKeepAlive = keepAlive;
                    CountDownLatch countDownLatch = new CountDownLatch(1);
                    Thread shutdownHook = null;
                    if (embeddedApplication.isShutdownHookNeeded()) {
                        try {
                            shutdownHook = new Thread(() -> {
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
                            Runtime.getRuntime().addShutdownHook(shutdownHook);
                        } catch (IllegalStateException e) {
                            try (applicationContext) {
                                embeddedApplication.stop();
                            } catch (Throwable stopError) {
                                LOG.error("Embedded Application shutting down", stopError);
                            }
                            LOG.warn("Failed to register shutdown hook", e);
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

                    if (embeddedApplication.isForceExit() && mayExit(applicationContext.getEnvironment())) {
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

    /**
     * Reads the training run switch the way the beans of a training run do with
     * {@code @Requires(property = TRAINING_ENABLED, pattern = "(?i)true")}: only {@code true}, in
     * any case, turns it on. A {@code Boolean} conversion would also accept the strings
     * {@code yes} and {@code on}, which those beans do not match.
     *
     * <p>Every application runs this on startup, so it is a plain call: no lambda to link. With
     * {@link #isTrainingRunBeforeStart(Environment)}, it is the only thing an application that is
     * not training pays for: the mode ({@link ApplicationConfiguration#TRAINING_MODE}) is only read
     * when the switch is on.</p>
     *
     * <p>This read decides whether this run is a training run. The one before the start only
     * decides whether Micronaut Test Resources is disabled while the environment starts.</p>
     *
     * @param environment The started environment
     * @return Whether this run is a training run
     */
    private static boolean isTrainingRun(Environment environment) {
        return StringUtils.TRUE.equalsIgnoreCase(environment.getProperty(ApplicationConfiguration.TRAINING_ENABLED, String.class).orElse(null));
    }

    /**
     * Reads the training run switch before the environment starts, from the sources that the
     * environment does not have to read and that take precedence over the configuration files: the
     * system properties, the environment variables (with the includes and the excludes of this
     * builder, as the environment reads them), the properties and the arguments given to this
     * builder, and the property sources given to this builder whose order is at least that of the
     * environment variables. A switch that is only set in the configuration of the application, or
     * in a property source of this builder with a lower order, which a configuration file can
     * override, is not seen here.
     *
     * <p>A source that the environment only reads when it starts and that takes precedence over
     * these, such as distributed configuration, can still turn the switch off. {@link #start()} then
     * logs a warning, because Micronaut Test Resources was disabled for a run that is not a training
     * run (see {@link TrainingTestResources}).</p>
     *
     * <p>Every application runs this on startup: two lookups and a look at the property sources that
     * this builder has added, usually none or one.</p>
     *
     * @param environment The environment, not started
     * @return Whether this run is a training run
     */
    private boolean isTrainingRunBeforeStart(Environment environment) {
        @Nullable String value = null;
        int order = Integer.MIN_VALUE;
        if (isEnableDefaultPropertySources()) {
            value = CachedEnvironment.getProperty(ApplicationConfiguration.TRAINING_ENABLED);
            order = SystemPropertiesPropertySource.POSITION;
            if (value == null && readsTrainingVariable()) {
                value = CachedEnvironment.getenv(TRAINING_ENABLED_ENVIRONMENT_VARIABLE);
                order = EnvironmentPropertySource.POSITION;
            }
        }
        // The source with the highest order wins, and the sources of the builder win a tie, as in the started environment
        for (PropertySource propertySource : environment.getPropertySources()) {
            int sourceOrder = propertySource.getOrder();
            // Below the environment variables, a configuration file, which is not read yet, could override it
            if (sourceOrder < EnvironmentPropertySource.POSITION) {
                continue;
            }
            Object sourceValue = propertySource.get(ApplicationConfiguration.TRAINING_ENABLED);
            if (sourceValue != null && (value == null || sourceOrder >= order)) {
                value = sourceValue.toString();
                order = sourceOrder;
            }
        }
        return StringUtils.TRUE.equalsIgnoreCase(value);
    }

    /**
     * @return Whether the started environment reads {@value #TRAINING_ENABLED_ENVIRONMENT_VARIABLE}:
     * the environment variables are a property source, and the includes and the excludes of this
     * builder let that variable through, as {@link EnvironmentPropertySource} applies them
     */
    private boolean readsTrainingVariable() {
        if (!isEnvironmentPropertySource()) {
            return false;
        }
        @Nullable List<String> includes = getEnvironmentVariableIncludes();
        @Nullable List<String> excludes = getEnvironmentVariableExcludes();
        return (includes == null || includes.contains(TRAINING_ENABLED_ENVIRONMENT_VARIABLE))
            && (excludes == null || !excludes.contains(TRAINING_ENABLED_ENVIRONMENT_VARIABLE));
    }

    /**
     * Starts the environment. In a training run that is known before the start, the Micronaut Test
     * Resources client is disabled while the environment reads the configuration (see
     * {@link TrainingTestResources}).
     *
     * @param environment The environment
     * @param trainingRun Whether the switch is on before the environment starts
     */
    private static void startEnvironment(Environment environment, boolean trainingRun) {
        if (!trainingRun) {
            environment.start();
            return;
        }
        @Nullable String testResourcesClient = TrainingTestResources.disableClient();
        try {
            environment.start();
        } finally {
            TrainingTestResources.restoreClient(testResourcesClient);
        }
    }

    /**
     * Says as soon as the switch is read that this JVM is a training run, so that a switch set by
     * mistake on a deployment target is visible before the application stops itself. Without an
     * {@link EmbeddedApplication} it says that nothing stops the application.
     *
     * @param environment The environment
     * @param hasEmbeddedApplication Whether the context has an {@link EmbeddedApplication} to stop
     */
    private static void announceTrainingRun(Environment environment, boolean hasEmbeddedApplication) {
        if (!LOG.isWarnEnabled()) {
            return;
        }
        if (hasEmbeddedApplication) {
            LOG.warn("Training run ({}=true): this JVM is a training run and does not serve traffic. It stops the application as soon as startup has completed{}. "
                    + "Never set this property or {} on a deployment target",
                ApplicationConfiguration.TRAINING_ENABLED, exitsAfterTrainingRun(environment) ? " and exits with status 0" : "", TRAINING_ENABLED_ENVIRONMENT_VARIABLE);
        } else {
            // The beans that require the switch are still created: only the stop and the exit are missing
            LOG.warn("Training run ({}=true): the application is not stopped, because it has no EmbeddedApplication. The JVM exits when the application's own threads end",
                ApplicationConfiguration.TRAINING_ENABLED);
        }
    }

    private static boolean exitsAfterTrainingRun(Environment environment) {
        return !environment.getActiveNames().contains(Environment.TEST);
    }

    /**
     * Ends a training run ({@link ApplicationConfiguration#TRAINING_ENABLED}): the application has
     * started and every startup listener, warm-up included, has run. Stops the application, closes
     * the context and exits with status 0, except in the {@code test} environment.
     *
     * @param applicationContext The application context
     * @param embeddedApplication The started application
     */
    @SuppressWarnings("java:S1147") // Exiting the JVM is the point of a training run
    private static void finishTrainingRun(ApplicationContext applicationContext, EmbeddedApplication<?> embeddedApplication) {
        boolean exit = exitsAfterTrainingRun(applicationContext.getEnvironment());
        if (LOG.isWarnEnabled()) {
            LOG.warn("Training run ({}=true): startup completed, stopping the application{}. This JVM was a training run and served no traffic",
                ApplicationConfiguration.TRAINING_ENABLED, exit ? " and exiting with status 0" : "");
        }
        try (applicationContext) {
            embeddedApplication.stop();
        }
        if (exit) {
            System.exit(0);
        }
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
        if (code > 0 && mayExit(environment)) {
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

    private boolean mayExit(Environment environment) {
        Boolean decided = exitAllowed;
        return decided != null ? decided : isExitAllowed(environment);
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
