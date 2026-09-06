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
package io.micronaut.python.processing.util;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;

import java.lang.reflect.Modifier;
import java.util.Set;

/**
 * Facts about Java types that the Python transformer needs when it rewrites a class whose base is
 * a Java type. Answered here so the transformer asks once per type instead of probing the element
 * from Python.
 *
 * @since 5.2.0
 */
@Internal
public final class PythonJavaTypes {

    /** The bases a reflection-backed element cannot answer {@code isAssignable(String)} for. */
    private static final Set<String> THROWABLE_ROOTS = Set.of(
        "java.lang.Throwable", "java.lang.Exception", "java.lang.RuntimeException", "java.lang.Error"
    );

    private PythonJavaTypes() {
    }

    /**
     * Whether the type is a {@link Throwable}. GraalPy cannot subclass a concrete Java throwable at
     * run time, so the transformer replaces such a base with a Python exception in runtime code.
     *
     * @param classElement The type
     * @return Whether it is a throwable
     */
    public static boolean isThrowable(@Nullable ClassElement classElement) {
        if (classElement == null) {
            return false;
        }
        if (classElement.isAssignable(Throwable.class.getName())) {
            return true;
        }
        // Reflection-backed elements (ClassElement.of(Class)) do not answer isAssignable(String).
        return THROWABLE_ROOTS.contains(classElement.getName());
    }

    /**
     * Whether the type can be extended at run time as a concrete Java class: not an interface and
     * not abstract.
     *
     * @param classElement The type
     * @return Whether it is a concrete class
     */
    public static boolean isConcreteClass(@Nullable ClassElement classElement) {
        if (classElement == null || classElement.isInterface() || classElement.isAbstract()) {
            return false;
        }
        // reflection-backed elements do not report abstract classes
        return !(classElement.getNativeType() instanceof Class<?> type && Modifier.isAbstract(type.getModifiers()));
    }
}
