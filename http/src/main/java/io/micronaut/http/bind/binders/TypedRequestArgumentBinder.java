/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.bind.binders;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.bind.TypeArgumentBinder;
import io.micronaut.http.HttpRequest;

import java.util.Collections;
import java.util.List;

/**
 * A {@link TypeArgumentBinder} that binds from an {@link HttpRequest}.
 *
 * @param <T> A type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface TypedRequestArgumentBinder<T> extends RequestArgumentBinder<T>, TypeArgumentBinder<T, HttpRequest<?>> {

    /**
     * Returns additional super types.
     *
     * @return Additional supers types
     */
    default @NonNull List<Class<?>> superTypes() {
        return Collections.emptyList();
    }

    /**
     * Check if this typed argument binder matches the provided class.
     * @param aClass The class to match
     * @return true if matches
     */
    default boolean matches(Class<?> aClass) {
        if (aClass.equals(argumentType().getType())) {
            return true;
        }
        for (Class<?> superType : superTypes()) {
            if (aClass.equals(superType)) {
                return true;
            }
        }
        return false;
    }
}
