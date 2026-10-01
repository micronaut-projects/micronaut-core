/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.context.python;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.event.BeanDestroyedEvent;
import io.micronaut.context.event.BeanDestroyedEventListener;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for producing a GraalVM Polyglot {@link Engine} for Python contexts.
 * Contexts created inside a single Micronaut application context share this bean, and the
 * engine is closed when that application context is destroyed.
 */
@Factory
final class GraalPyEngineFactory implements BeanDestroyedEventListener<Engine> {
    private static final Logger LOG = LoggerFactory.getLogger(GraalPyEngineFactory.class);
    private static final Map<String, Boolean> SUPPORTED_ENGINE_OPTIONS = new ConcurrentHashMap<>();

    /**
     * Create the application-scoped Python engine bean.
     * <p>
     * Context-level configuration is intentionally kept out of the shared engine
     * so package collaborators can create isolated Python contexts while still
     * sharing compiled code, instruments, and engine-level resources.
     *
     * @return The shared Python polyglot engine.
     */
    @Singleton
    @Named(PythonContextRuntime.PYTHON)
    Engine pythonEngine(GraalPyEngineConfiguration engineConfiguration) {
        if (PythonContextRuntime.isInitialized() && PythonContextRuntime.isReuseContext()) {
            // A reusable bootstrap context brings its own engine. Sharing it keeps compiled code
            // and one compiler queue for the primary and the pooled contexts; the reusable context
            // stays registered, so the destruction listener never closes this engine.
            Engine engine = PythonContextRuntime.getContext().getEngine();
            LOG.info("Sharing the GraalPy engine of the reusable context using the {} runtime", engine.getImplementationName());
            return engine;
        }
        // Keep defaults; options and instruments are configured on contexts.
        LOG.debug("Creating GraalPy Engine");
        long now = System.currentTimeMillis();
        Engine engine = buildPythonEngine(engineConfiguration);
        // "Interpreted" means the fallback Truffle runtime: Python runs 30-60x slower than with a
        // GraalVM JDK whose Truffle version matches the GraalPy artifacts. Truffle logs its own
        // warning for that case; this line makes the active runtime visible in every startup log.
        LOG.info("GraalPy engine created in {}ms using the {} runtime", System.currentTimeMillis() - now, engine.getImplementationName());
        return engine;
    }

    /**
     * Build a Python polyglot engine with Micronaut's shared host exception and logging policy.
     * <p>
     * This method is separate from the bean factory method so tests and package
     * internals can create equivalent engines without starting an application
     * context or duplicating the handler configuration.
     *
     * @return A new Python polyglot engine.
     */
    static Engine buildPythonEngine(GraalPyEngineConfiguration engineConfiguration) {
        Engine.Builder builder = engineConfiguration.builder
            .exceptionHandler(GraalPyExceptionHandler.RETHROW_HOST_RUNTIME_EXCEPTION)
            .logHandler(new GraalPySlf4jLogHandler());
        // Engine.Builder#options accumulates, so an unsupported option cannot be removed once set.
        // The support check has to happen before it is applied, not as a retry.
        Map<String, String> supported = optionalOptionsToApply(engineConfiguration);
        if (!supported.isEmpty()) {
            builder.options(supported);
        }
        return builder.build();
    }

    /**
     * The optional (best effort) engine options that should be applied on top of the configured ones.
     *
     * <p>An option the user configured explicitly through {@code graalpy.engine.options} always wins over
     * the built-in default, and an option the active runtime does not know is left unset.</p>
     *
     * @param engineConfiguration The engine configuration
     * @return The options to apply, in configuration order
     */
    static Map<String, String> optionalOptionsToApply(GraalPyEngineConfiguration engineConfiguration) {
        Map<String, String> supported = new LinkedHashMap<>();
        for (Map.Entry<String, String> option : engineConfiguration.optionalOptions.entrySet()) {
            if (engineConfiguration.configuredOptions.contains(option.getKey())) {
                LOG.debug("Engine option {} is configured explicitly, not applying the default {}", option.getKey(), option.getValue());
            } else if (isEngineOptionSupported(option.getKey())) {
                supported.put(option.getKey(), option.getValue());
            } else {
                LOG.debug("GraalPy runtime does not support engine option {}, leaving it unset", option.getKey());
            }
        }
        return supported;
    }

    /**
     * Whether the active Truffle runtime knows the given engine option.
     *
     * <p>The tuning options Micronaut sets exist only on the optimizing runtime. On the fallback
     * runtime - any JVM without JVMCI, which includes stock OpenJDK and a GraalVM CE not started
     * with {@code -XX:+EnableJVMCI} - setting one makes {@code build()} throw and Python then fails
     * to start at all rather than merely running interpreted.</p>
     *
     * <p>The probe engine is not the cost it appears to be: it is polyglot initialization, which the
     * real engine pays regardless, so a second creation is effectively free.</p>
     *
     * @param option The option name
     * @return Whether it is supported
     */
    private static boolean isEngineOptionSupported(String option) {
        Boolean known = SUPPORTED_ENGINE_OPTIONS.get(option);
        if (known != null) {
            return known;
        }
        boolean supported;
        try (Engine probe = Engine.create()) {
            supported = probe.getOptions().get(option) != null;
        } catch (Exception e) {
            supported = false;
        }
        SUPPORTED_ENGINE_OPTIONS.put(option, supported);
        return supported;
    }

    /**
     * Build a Python polyglot engine with default engine configuration.
     *
     * @return A new Python polyglot engine.
     */
    static Engine buildPythonEngine() {
        return buildPythonEngine(new GraalPyEngineConfiguration());
    }

    /**
     * Close the shared Python engine after Micronaut destroys the engine bean.
     * <p>
     * Shutdown is deferred until all tracked Python contexts are gone. Context shutdown waits
     * for active executions before closing, so closing an engine too early cannot invalidate
     * in-flight bridge calls. This listener coordinates with {@link PythonContextRuntime}
     * instead of closing directly from the bean destruction callback.
     *
     * @param event The destroyed engine bean event.
     */
    @Override
    public void onDestroyed(@NonNull BeanDestroyedEvent<Engine> event) {
        Engine engine = event.getBean();
        PythonContextRegistry.onNoContexts(engine, () -> closeEngine(engine));
    }

    /**
     * Close an engine without cancelling active polyglot work.
     * <p>
     * Cancellation during shutdown is expected when GraalPy reports that close
     * raced with already-cancelled work. A remaining active-context state is kept
     * at debug level because {@link PythonContextRuntime} should normally prevent
     * it, but destruction must not fail the Micronaut context in that case.
     *
     * @param engine The engine to close.
     */
    private static void closeEngine(Engine engine) {
        try {
            engine.close(false);
        } catch (PolyglotException e) {
            if (!e.isCancelled()) {
                throw e;
            }
        } catch (IllegalStateException e) {
            LOG.debug("Python engine still has active contexts during destruction", e);
        }
    }

    /**
     * Configuration options for the GraalPy Engine.
     *
     */
    @ConfigurationProperties(GraalPyEngineConfiguration.PREFIX)
    static final class GraalPyEngineConfiguration {
        public static final String PREFIX = "graalpy.engine";

        /**
         * Options to apply only if the runtime recognises them and the user did not configure them.
         */
        final Map<String, String> optionalOptions = new LinkedHashMap<>();

        /**
         * Option names configured explicitly through {@code graalpy.engine.options}.
         */
        final Set<String> configuredOptions = new HashSet<>();

        @ConfigurationBuilder(prefixes = "", excludes = {"out", "in", "err", "exceptionHandler", "messageTransport"})
        Engine.Builder builder = Engine.newBuilder(
            GraalPyContextCustomizers.languages(GraalPyContextCustomizers.currentClassLoader())
        );

        GraalPyEngineConfiguration() {
            // currently GraalPy spawns too many compiler threads by default. limit to 1 for now.
            // Only the optimizing Truffle runtime knows this option. On the fallback runtime - any
            // JVM without JVMCI enabled, which includes stock OpenJDK and a GraalVM CE that was not
            // started with -XX:+EnableJVMCI - setting it makes Engine.build() throw
            // IllegalArgumentException, and Python then fails to start at all rather than merely
            // running interpreted. Applied on a best effort basis instead, in buildPythonEngine.
            optionalOptions.put("engine.CompilerThreads", "1");
        }

        /**
         * Sets the engine options.
         * @param options The options.
         */
        void setOptions(@MapFormat(keyFormat = StringConvention.RAW, transformation = MapFormat.MapTransformation.FLAT) @Nullable Map<String, String> options) {
            if (options != null) {
                GraalPyEngineFactory.LOG.debug("Using GraalPy engine options {}", options);
                configuredOptions.addAll(options.keySet());
                builder.options(options);
            }
        }
    }
}
