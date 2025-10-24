/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.processing;

import io.micronaut.python.processing.visitor.ClassDef;
import io.micronaut.python.processing.visitor.DecoratorDef;
import org.graalvm.polyglot.Context;

import java.util.Map;

/**
 * Represents a Python environment containing parsed Python classes, decorators, and the GraalVM Polyglot context.
 * This record holds the state of a Python module after parsing, providing access to class definitions and decorators.
 *
 * @param classes A map of Python class names to their definitions.
 * @param decorators A map of Python decorator names to their definitions.
 * @param context The GraalVM Polyglot context used for executing Python code.
 * @since 4.8.0
 * @author Micronaut
 */
public record PythonEnvironment(
    Map<String, ClassDef> classes,
    Map<String, DecoratorDef> decorators,
    Context context
) implements AutoCloseable {

    @Override
    public void close() {
        if (context != null) {
            context.close();
        }
    }
}
