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
package io.micronaut.http.bind;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.ArgumentBinderRegistry;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.RequestArgumentBinder;

/**
 * A {@link ArgumentBinderRegistry} where the source of binding is a {@link HttpRequest}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface RequestBinderRegistry extends ArgumentBinderRegistry<HttpRequest<?>> {

    /**
     * Adds a request argument binder that will be used to match the argument that wasn't matched by a type or an annotation.
     * @param binder The binder
     * @since 4.0.0
     */
    default void addUnmatchedRequestArgumentBinder(RequestArgumentBinder<Object> binder) {
        throw new UnsupportedOperationException("Binder registry is not mutable");
    }

    /**
     * Whether this registry binds the given argument, of the type {@link HttpRequest}, to the
     * request as is: with its default binder, without a body type to convert and without a
     * binder that replaced it. A route whose arguments are all bound this way may be fulfilled
     * without its binders. A registry that does not know answers {@code false}.
     *
     * @param argument The argument
     * @return Whether the argument is the request as is
     * @since 5.3.0
     */
    @Internal
    default boolean bindsRequestAsIs(Argument<?> argument) {
        return false;
    }

}
