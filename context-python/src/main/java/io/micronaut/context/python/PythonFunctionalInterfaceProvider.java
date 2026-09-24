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

import io.micronaut.core.annotation.Internal;

import java.util.List;

/**
 * The functional interfaces the Python compiler found in the Java types the Python sources
 * reference: the host access converts a Python callable to each of them by the arity of the
 * callable, so that a Java method overloaded on functional interfaces, or on a functional
 * interface and {@code Iterable}, is called with a plain lambda (see {@link PythonCallables}).
 * <p>
 * The compiler generates an implementation per compilation, a singleton bean that is also
 * registered as a service, naming the functional interfaces the referenced Java types (imported
 * types, Java bases of Python classes, type hints) accept as method parameters, the interfaces
 * accepted by the methods of the types those methods return, and the referenced types that are
 * functional interfaces themselves. A functional interface is an interface annotated with
 * {@link FunctionalInterface} or, outside the JDK, an interface with a single abstract method.
 *
 * @since 5.2.4
 */
@Internal
public interface PythonFunctionalInterfaceProvider {

    /**
     * @return The functional interfaces
     */
    List<Entry> entries();

    /**
     * A functional interface, named rather than referenced: an interface of a compile-time only
     * dependency is absent at run time, and the host access skips an entry it cannot load instead
     * of failing with the Python context.
     *
     * @param typeName The binary name of the interface
     * @param arity The number of parameters of its single abstract method
     * @param returnsValue Whether the single abstract method returns a value
     */
    record Entry(String typeName, int arity, boolean returnsValue) {
    }
}
