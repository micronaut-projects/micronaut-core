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
package io.micronaut.http.filter;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletionStage;

/**
 * The functions of the filters declared on one route, see
 * {@link GenericHttpFilter#createRouteRequestFilter}. Like a filter method with a
 * {@link MutablePropagatedContext} parameter, each receives the propagated context of the filter
 * chain to change for what runs after it.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class RouteFilterFunctions {

    private RouteFilterFunctions() {
    }

    /**
     * A synchronous request filter.
     */
    @FunctionalInterface
    public interface Request {
        /**
         * @param request           The request
         * @param propagatedContext The propagated context, to change for what runs after the filter
         * @return A response to answer the request with, or {@code null} to proceed
         * @throws Exception An error
         */
        @Nullable HttpResponse<?> filter(HttpRequest<?> request, MutablePropagatedContext propagatedContext) throws Exception;
    }

    /**
     * An asynchronous request filter.
     */
    @FunctionalInterface
    public interface AsyncRequest {
        /**
         * @param request           The request
         * @param propagatedContext The propagated context, to change until the stage completes
         * @return Completes with a response to answer the request with, or with {@code null} to proceed
         * @throws Exception An error
         */
        CompletionStage<? extends @Nullable HttpResponse<?>> filter(HttpRequest<?> request, MutablePropagatedContext propagatedContext) throws Exception;
    }

    /**
     * A synchronous response filter.
     */
    @FunctionalInterface
    public interface Response {
        /**
         * @param request           The request
         * @param response          The response
         * @param propagatedContext The propagated context, to change for the response filters after it
         * @throws Exception An error
         */
        void filter(HttpRequest<?> request, MutableHttpResponse<?> response, MutablePropagatedContext propagatedContext) throws Exception;
    }

    /**
     * An asynchronous response filter.
     */
    @FunctionalInterface
    public interface AsyncResponse {
        /**
         * @param request           The request
         * @param response          The response
         * @param propagatedContext The propagated context, to change until the stage completes
         * @return Completes when the response is filtered
         * @throws Exception An error
         */
        CompletionStage<?> filter(HttpRequest<?> request, MutableHttpResponse<?> response, MutablePropagatedContext propagatedContext) throws Exception;
    }
}
