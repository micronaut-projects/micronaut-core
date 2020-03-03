/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.bind.binders;

import io.micronaut.core.bind.TypeArgumentBinder;
import io.micronaut.http.HttpRequest;

/**
 * A {@link TypeArgumentBinder} that binds from an {@link HttpRequest}.
 *
 * @param <T> A type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface TypedRequestArgumentBinder<T> extends RequestArgumentBinder<T>, TypeArgumentBinder<T, HttpRequest<?>> {

    /**
     * Determines whether arguments that are an interface that {@link T}
     * implements are candidates for binding.
     *
     * @return True if super interfaces are binding candidates
     */
    default boolean supportsSuperTypes() {
        return true;
    }
}
