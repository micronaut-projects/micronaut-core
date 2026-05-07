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

    private static @Nullable RuntimeException toGeneratedRuntimeException(PolyglotException exception) {
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
        RuntimeException mappedException = mappedRuntimeException(guestObject);
        if (mappedException != null) {
            return mappedException;
        }
        for (String className : generatedWrapperCandidates(guestObject)) {
            RuntimeException generatedException = instantiateGeneratedException(className, guestObject);
            if (generatedException != null) {
                return generatedException;
            }
        }
        return null;
    }

    private static @Nullable RuntimeException mappedRuntimeException(Value guestObject) {
        try {
            Object mappedObject = guestObject.as(Object.class);
            if (mappedObject instanceof RuntimeException runtimeException && mappedObject instanceof ValueCoercible) {
                return runtimeException;
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
            addCandidate(candidates, GraalPyRuntimeUtil.PYTHON + "." + simpleName);
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

    private static @Nullable RuntimeException instantiateGeneratedException(String className, Value guestObject) {
        try {
            Class<?> exceptionClass = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            if (!RuntimeException.class.isAssignableFrom(exceptionClass) || !ValueCoercible.class.isAssignableFrom(exceptionClass)) {
                return null;
            }
            Constructor<?> constructor = exceptionClass.getConstructor(Value.class);
            return (RuntimeException) constructor.newInstance(guestObject);
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
