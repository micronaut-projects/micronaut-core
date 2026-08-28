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

import java.util.Map;

/**
 * Factory for producing a GraalVM Polyglot {@link Engine} for Python contexts.
 * Contexts created inside a single Micronaut application context share this bean, and the
 * engine is closed when that application context is destroyed.
 */
@Factory
final class GraalPyEngineFactory implements BeanDestroyedEventListener<Engine> {
    private static final Logger LOG = LoggerFactory.getLogger(GraalPyEngineFactory.class);

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
    @Named(GraalPyRuntimeUtil.PYTHON)
    Engine pythonEngine(GraalPyEngineConfiguration engineConfiguration) {
        // Keep defaults; options and instruments are configured on contexts.
        LOG.debug("Creating GraalPy Engine");
        long now = System.currentTimeMillis();
        try {
            return buildPythonEngine(engineConfiguration);
        } finally {
            LOG.debug("Created GraalPy Engine in {}ms", System.currentTimeMillis() - now);
        }
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
        return engineConfiguration.builder
            .exceptionHandler(GraalPyExceptionHandler.RETHROW_HOST_RUNTIME_EXCEPTION)
            .logHandler(new GraalPySlf4jLogHandler())
            .build();
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
        PythonContextRuntime.onNoContexts(engine, () -> closeEngine(engine));
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

        @ConfigurationBuilder(prefixes = "", excludes = {"out", "in", "err", "exceptionHandler", "messageTransport"})
        Engine.Builder builder = Engine.newBuilder(
            GraalPyContextCustomizers.languages(GraalPyContextCustomizers.currentClassLoader())
        );

        GraalPyEngineConfiguration() {
            // currently GraalPy spawns too many compiler threads by default. limit to 1 for now.
            builder.options(Map.of("engine.CompilerThreads", "1"));
        }

        /**
         * Sets the engine options.
         * @param options The options.
         */
        void setOptions(@MapFormat(keyFormat = StringConvention.RAW, transformation = MapFormat.MapTransformation.FLAT) @Nullable Map<String, String> options) {
            if (options != null) {
                GraalPyEngineFactory.LOG.debug("Using GraalPy engine options {}", options);
                builder.options(options);
            }
        }
    }
}
