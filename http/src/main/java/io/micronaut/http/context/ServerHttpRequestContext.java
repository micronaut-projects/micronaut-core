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
import io.micronaut.core.annotation.Nullable;
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
        return PropagatedContext.find()
            .flatMap(ctx -> ctx.find(ServerHttpRequestContext.class))
            .map(e -> (HttpRequest<T>) e.httpRequest);
    }

}
