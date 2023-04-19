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
package io.micronaut.http.expression;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.context.ServerRequestContext;
import jakarta.inject.Singleton;

/**
 * An expression evaluation context for use with HTTP annotations and the {@code condition} member.
 *
 * <p>This context allows access to the current request via a {@code request} object which is an instance of {@link HttpRequest}.</p>
 *
 * @see HttpRequest
 * @author graemerocher
 * @since 4.0.0
 */
@Singleton
@Experimental
public final class RequestConditionContext {

    /**
     * Default constructor.
     */
    @Internal
    RequestConditionContext() {
    }

    /**
     * @return The request object.
     */
    @SuppressWarnings("java:S1452")
    public @NonNull HttpRequest<?> getRequest() {
        return currentRequest();
    }

    private static HttpRequest<Object> currentRequest() {
        return ServerRequestContext.currentRequest()
            .orElseThrow(() -> new IllegalStateException("No request present in evaluation context"));
    }
}
