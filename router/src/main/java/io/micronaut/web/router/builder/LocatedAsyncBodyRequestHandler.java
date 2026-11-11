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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.PathVariables;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletionStage;

/**
 * A route handler of the routes of a located target that receives the target and the body of the
 * request, and completes the response later, see {@link LocatedHttpBodyRouteSpec}: otherwise the
 * same as an {@link AsyncBodyRequestHandler}. The body is the one the body stage of the route
 * declares: decoded, the form, or the {@link io.micronaut.http.body.AsyncRequestBody} the handler
 * reads.
 *
 * @param <T> The type of the located target
 * @param <B> The type of the body the handler receives
 * @author Denis Stepanov
 * @since 5.3.0
 * @see LocatedHttpBodyRouteSpec#handleAsync(LocatedAsyncBodyRequestHandler)
 */
@Experimental
@FunctionalInterface
public interface LocatedAsyncBodyRequestHandler<T, B extends @Nullable Object> {

    /**
     * Handle the request.
     *
     * @param request       The request
     * @param pathVariables The path variables of the prefixes of the locators and of the route
     * @param target        The located target
     * @param body          The body of the request
     * @return The response, completed later
     * @throws Exception An error, handled by the error routes like a controller error
     */
    CompletionStage<? extends @Nullable HttpResponse<?>> handle(HttpRequest<?> request, PathVariables pathVariables, T target, B body) throws Exception;
}
