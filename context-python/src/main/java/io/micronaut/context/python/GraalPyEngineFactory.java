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

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.event.BeanDestroyedEvent;
import io.micronaut.context.event.BeanDestroyedEventListener;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for producing a GraalVM Polyglot {@link Engine} for Python contexts.
 * Contexts created inside a single Micronaut application context share this bean, and the
 * engine is closed when that application context is destroyed.
 */
@Factory
final class GraalPyEngineFactory implements BeanDestroyedEventListener<Engine> {
    private static final Logger LOG = LoggerFactory.getLogger(GraalPyEngineFactory.class);

    @Singleton
    @Named(GraalPyRuntimeUtil.PYTHON)
    Engine pythonEngine() {
        // Keep defaults; options and instruments are configured on contexts.
        return buildPythonEngine();
    }

    static Engine buildPythonEngine() {
        return Engine.newBuilder()
            .exceptionHandler(GraalPyExceptionHandler.RETHROW_HOST_RUNTIME_EXCEPTION)
            .logHandler(new GraalPySlf4jLogHandler())
            .build();
    }

    @Override
    public void onDestroyed(@NonNull BeanDestroyedEvent<Engine> event) {
        Engine engine = event.getBean();
        PythonContextRuntime.onNoContexts(engine, () ->
            PythonContextRuntime.onNoActiveExecutions(engine, () -> closeEngine(engine))
        );
    }

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
}
