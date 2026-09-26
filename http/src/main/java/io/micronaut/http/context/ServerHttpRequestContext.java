/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.context;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.http.HttpRequest;

import java.util.Optional;

/**
 * Http request propagation.
 *
 * @param httpRequest The HTTP request
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Experimental
public record ServerHttpRequestContext(HttpRequest<?> httpRequest) implements PropagatedContextElement {

    /**
     * @param <T> The request body type
     * @return {@link HttpRequest} or null
     */
    @Nullable
    public static <T> HttpRequest<T> get() {
        return ServerHttpRequestContext.<T>find().orElse(null);
    }

    /**
     * @param <T> The request body type
     * @return an optional {@link HttpRequest}
     */
    public static <T> Optional<HttpRequest<T>> find() {
        return PropagatedContext.find().flatMap(ServerHttpRequestContext::find);
    }

    /**
     * Returns a context in which the given request is the current request. If the last
     * {@link ServerHttpRequestContext} of the given context already holds this exact request
     * instance, the given context is returned as-is instead of a copy with a duplicate element,
     * which also lets propagating it skip the context switch when it is already in scope.
     *
     * @param context The context
     * @param request The request
     * @return the given context, or a new context with the request added
     * @since 5.3
     */
    @Internal
    public static PropagatedContext withRequest(PropagatedContext context, HttpRequest<?> request) {
        ServerHttpRequestContext current = context.findOrNull(ServerHttpRequestContext.class);
        if (current != null && current.httpRequest == request) {
            return context;
        }
        return context.plus(new ServerHttpRequestContext(request));
    }

    /**
     * Finds an {@link HttpRequest} within the provided {@link PropagatedContext}.
     *
     * @param <T> The type of the request body.
     * @param context The propagated context to search within.
     * @return An {@link Optional} containing the {@link HttpRequest} if found; otherwise, an empty {@link Optional}.
     */
    public static <T> Optional<HttpRequest<T>> find(PropagatedContext context) {
        return context.find(ServerHttpRequestContext.class)
            .map(e -> (HttpRequest<T>) e.httpRequest);
    }

}
