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
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.graalvm.polyglot.Engine;

/**
 * Factory for producing a shared GraalVM Polyglot {@link Engine} for Python contexts.
 * Contexts created by the pool will share this engine for better performance and code sharing.
 */
@Factory
final class GraalPyEngineFactory {

    @Singleton
    @Named(GraalPyRuntimeUtil.PYTHON)
    Engine pythonEngine() {
        // Keep defaults; options and instruments are configured on contexts.
        // Sharing a single Engine allows sharing compiled code/ASTs across contexts.
        return Engine.create();
    }
}
