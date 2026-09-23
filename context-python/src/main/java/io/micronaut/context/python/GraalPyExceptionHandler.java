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
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Shared exception handling for GraalPy contexts.
 */
@Internal
final class GraalPyExceptionHandler {
    static final Consumer<PolyglotException> RETHROW_HOST_RUNTIME_EXCEPTION = GraalPyExceptionHandler::rethrowHostRuntimeException;

    private GraalPyExceptionHandler() {
    }

    static void rethrowHostRuntimeException(PolyglotException exception) {
        RuntimeException generatedException = toGeneratedRuntimeException(exception);
        if (generatedException != null) {
            throw generatedException;
        }
        if (exception.isHostException()) {
            Throwable hostException = exception.asHostException();
            if (hostException instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (hostException instanceof Error error) {
                throw error;
            }
        }
    }

    /**
     * Resolve the host throwable represented by a guest exception value.
     * <p>
     * Host exceptions that crossed into Python keep their identity, generated Python exception wrappers
     * are instantiated, and any other Python exception is reported as the {@link PolyglotException}
     * that {@link Value#throwException()} produces for it.
     *
     * @param exception The exception value
     * @return The throwable to report to Java
     */
    static Throwable toHostThrowable(Value exception) {
        if (exception.isHostObject() && exception.asHostObject() instanceof Throwable throwable) {
            return throwable;
        }
        if (exception.hasMembers() && exception.hasMember("java_exception")) {
            Value javaException = exception.getMember("java_exception");
            if (javaException != null && javaException.isHostObject() && javaException.asHostObject() instanceof Throwable throwable) {
                return throwable;
            }
        }
        if (exception.isException()) {
            try {
                exception.throwException();
            } catch (PolyglotException polyglotException) {
                if (polyglotException.isHostException()) {
                    return polyglotException.asHostException();
                }
                RuntimeException generated = toGeneratedRuntimeException(polyglotException);
                return generated != null ? generated : polyglotException;
            }
        }
        return new RuntimeException(exception.toString());
    }

    private static @Nullable RuntimeException toGeneratedRuntimeException(PolyglotException exception) {
        return toGeneratedException(exception, RuntimeException.class, Thread.currentThread().getContextClassLoader());
    }

    /**
     * The exception a bridge method declaring checked exceptions rethrows for a Python exception:
     * a host exception of one of the declared types, raised in Python as is or thrown by a Java
     * call the Python code did not catch, or the generated Java exception of a Python exception
     * class extending one of the declared types.
     *
     * @param exception The exception of the Python call
     * @param classLoader The class loader of the generated classes (the context class loader of the thread
     * is the loader of the polyglot context only while Python code runs)
     * @param declaredTypes The checked exception types the bridge method declares
     * @return The exception to rethrow, or {@code null} when the Python exception is none of them
     */
    static @Nullable Throwable toDeclaredException(PolyglotException exception, ClassLoader classLoader, Class<?>[] declaredTypes) {
        if (exception.isHostException()) {
            Throwable hostException = exception.asHostException();
            for (Class<?> declaredType : declaredTypes) {
                if (declaredType.isInstance(hostException)) {
                    return hostException;
                }
            }
        }
        for (Class<?> declaredType : declaredTypes) {
            if (Throwable.class.isAssignableFrom(declaredType)) {
                Throwable generated = toGeneratedException(exception, declaredType.asSubclass(Throwable.class), classLoader);
                if (generated != null) {
                    return generated;
                }
            }
        }
        return null;
    }

    private static <T extends Throwable> @Nullable T toGeneratedException(PolyglotException exception, Class<T> exceptionType, ClassLoader classLoader) {
        Value guestObject = null;
        try {
            guestObject = exception.getGuestObject();
        } catch (IllegalStateException | UnsupportedOperationException e) {
            // Fall back to the host adapter delegate below.
        }
        if ((guestObject == null || guestObject.isNull()) && exception.isHostException()) {
            guestObject = adapterDelegate(exception.asHostException());
        }
        if (guestObject == null || guestObject.isNull()) {
            return null;
        }
        T mappedException = mappedException(guestObject, exceptionType);
        if (mappedException != null) {
            return mappedException;
        }
        for (String className : generatedWrapperCandidates(guestObject)) {
            T generatedException = instantiateGeneratedException(className, guestObject, exceptionType, classLoader);
            if (generatedException != null) {
                return generatedException;
            }
        }
        return null;
    }

    private static <T extends Throwable> @Nullable T mappedException(Value guestObject, Class<T> exceptionType) {
        try {
            Object mappedObject = guestObject.as(Object.class);
            if (exceptionType.isInstance(mappedObject) && mappedObject instanceof ValueCoercible) {
                return exceptionType.cast(mappedObject);
            }
        } catch (ClassCastException | IllegalArgumentException | IllegalStateException | UnsupportedOperationException e) {
            return null;
        }
        return null;
    }

    private static Set<String> generatedWrapperCandidates(Value guestObject) {
        Set<String> candidates = new LinkedHashSet<>();
        Value metaObject = guestObject.getMetaObject();
        String simpleName = null;
        if (metaObject != null && metaObject.isMetaObject()) {
            simpleName = normalizeClassName(metaObject.getMetaSimpleName());
            addCandidate(candidates, metaObject.getMetaQualifiedName());
        }
        Value pythonClass = guestObject.hasMember("__class__") ? guestObject.getMember("__class__") : null;
        String moduleName = stringMember(pythonClass, "__module__");
        if (simpleName != null && !simpleName.isBlank()) {
            addCandidate(candidates, PythonContextRuntime.PYTHON + "." + simpleName);
            if (moduleName != null && !moduleName.isBlank()) {
                addCandidate(candidates, moduleName);
                addCandidate(candidates, moduleName + "." + simpleName);
                int lastDot = moduleName.lastIndexOf('.');
                if (lastDot > -1) {
                    addCandidate(candidates, moduleName.substring(0, lastDot) + "." + simpleName);
                }
            }
        }
        return candidates;
    }

    private static <T extends Throwable> @Nullable T instantiateGeneratedException(String className, Value guestObject, Class<T> exceptionType, ClassLoader classLoader) {
        try {
            Class<?> exceptionClass = Class.forName(className, false, classLoader);
            if (!exceptionType.isAssignableFrom(exceptionClass) || !ValueCoercible.class.isAssignableFrom(exceptionClass)) {
                return null;
            }
            Constructor<?> constructor = exceptionClass.getConstructor(Value.class);
            return exceptionType.cast(constructor.newInstance(guestObject));
        } catch (InvocationTargetException e) {
            // the generated constructor failed to call the Java super constructor: a programming
            // error of the Python class, not a Python exception to report as is
            throw new IllegalStateException("Cannot create the Java exception [" + className + "] for the Python exception ["
                + guestObject + "]: " + e.getCause(), e.getCause());
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static void addCandidate(Set<String> candidates, @Nullable String candidate) {
        String normalized = normalizeClassName(candidate);
        if (normalized != null
            && !normalized.isBlank()
            && !normalized.equals("builtins")
            && !normalized.startsWith("builtins.")) {
            candidates.add(normalized);
        }
    }

    private static @Nullable String normalizeClassName(@Nullable String className) {
        if (className == null) {
            return null;
        }
        String normalized = className.trim();
        if (normalized.startsWith("<class '") && normalized.endsWith("'>")) {
            normalized = normalized.substring(8, normalized.length() - 2);
        }
        return normalized;
    }

    private static @Nullable String stringMember(@Nullable Value value, String member) {
        if (value == null || value.isNull() || !value.hasMember(member)) {
            return null;
        }
        Value memberValue = value.getMember(member);
        if (memberValue == null || memberValue.isNull()) {
            return null;
        }
        return memberValue.isString() ? memberValue.asString() : memberValue.toString();
    }

    private static @Nullable Value adapterDelegate(Throwable hostException) {
        try {
            Field delegate = hostException.getClass().getField("this");
            if (Value.class.isAssignableFrom(delegate.getType())) {
                return (Value) delegate.get(hostException);
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
        return null;
    }
}
