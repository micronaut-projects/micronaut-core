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

import io.micronaut.core.annotation.Experimental;
import org.graalvm.polyglot.Value;

/**
 * Interop helpers for Python code that calls Java APIs.
 * <p>
 * Python code imports the class like any other Java type:
 * <pre>{@code
 * from java.util.function import BiPredicate
 * from micronaut.context.python import PythonInterop
 *
 * builder.exitCondition(PythonInterop.fn(BiPredicate, lambda scope, index: index == 3))
 * }</pre>
 *
 * @since 5.2.3
 */
@Experimental
public final class PythonInterop {

    private PythonInterop() {
    }

    /**
     * Adapts a Python callable to a functional interface.
     * <p>
     * A Python callable passed to a Java method whose parameter is a standard functional interface
     * is converted automatically, and when the method is overloaded on functional interfaces the
     * overload whose interface method has the number of parameters of the callable is selected.
     * This method adapts a callable to any functional interface explicitly, for overloads on custom
     * functional interfaces or on interfaces of the same arity. The result is a host object that
     * implements the interface: it keeps its Java class when it is returned to Python, whereas an
     * automatically converted callable returns to Python as the original callable.
     *
     * @param type The functional interface
     * @param callable The Python callable
     * @param <T> The interface type
     * @return An implementation of the interface that invokes the callable
     * @throws IllegalArgumentException if the type is not a functional interface or the value is not callable
     */
    public static <T> T fn(Class<T> type, Value callable) {
        return PythonCallables.proxy(callable, type);
    }
}
