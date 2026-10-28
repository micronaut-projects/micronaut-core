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

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.PathVariables;
import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletionStage;

/**
 * The handler a located route adapts from the handler it was given, which reads the located
 * target: when the given handler is a {@link RouteMethodProvider}, the adapted handler gives its
 * method too, so that the route has it like a route given that handler directly.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
final class ProvidedHandlers {

    private ProvidedHandlers() {
    }

    /**
     * @param adapted The adapted handler
     * @param given   The handler given to the route
     * @return The adapted handler, which gives the method of the given handler, if any
     */
    static RequestHandler of(RequestHandler adapted, Object given) {
        return given instanceof RouteMethodProvider provider ? new Request(adapted, provider) : adapted;
    }

    /**
     * @param adapted The adapted handler
     * @param given   The handler given to the route
     * @return The adapted handler, which gives the method of the given handler, if any
     */
    static AsyncRequestHandler of(AsyncRequestHandler adapted, Object given) {
        return given instanceof RouteMethodProvider provider ? new AsyncRequest(adapted, provider) : adapted;
    }

    /**
     * @param adapted The adapted handler
     * @param given   The handler given to the route
     * @param <B>     The body type
     * @return The adapted handler, which gives the method of the given handler, if any
     */
    static <B extends @Nullable Object> BodyRequestHandler<B> of(BodyRequestHandler<B> adapted, Object given) {
        return given instanceof RouteMethodProvider provider ? new BodyRequest<>(adapted, provider) : adapted;
    }

    /**
     * @param adapted The adapted handler
     * @param given   The handler given to the route
     * @param <B>     The body type
     * @return The adapted handler, which gives the method of the given handler, if any
     */
    static <B extends @Nullable Object> AsyncBodyRequestHandler<B> of(AsyncBodyRequestHandler<B> adapted, Object given) {
        return given instanceof RouteMethodProvider provider ? new AsyncBodyRequest<>(adapted, provider) : adapted;
    }

    private record Request(RequestHandler handler, RouteMethodProvider provider) implements RequestHandler, RouteMethodProvider {
        @Override
        public @Nullable HttpResponse<?> handle(HttpRequest<?> request, PathVariables pathVariables) throws Exception {
            return handler.handle(request, pathVariables);
        }

        @Override
        public ExecutableMethod<?, ?> routeMethod() {
            return provider.routeMethod();
        }
    }

    private record AsyncRequest(AsyncRequestHandler handler, RouteMethodProvider provider) implements AsyncRequestHandler, RouteMethodProvider {
        @Override
        public CompletionStage<? extends @Nullable HttpResponse<?>> handle(HttpRequest<?> request, PathVariables pathVariables) throws Exception {
            return handler.handle(request, pathVariables);
        }

        @Override
        public ExecutableMethod<?, ?> routeMethod() {
            return provider.routeMethod();
        }
    }

    private record BodyRequest<B extends @Nullable Object>(BodyRequestHandler<B> handler,
                                                           RouteMethodProvider provider) implements BodyRequestHandler<B>, RouteMethodProvider {
        @Override
        public @Nullable HttpResponse<?> handle(HttpRequest<?> request, PathVariables pathVariables, B body) throws Exception {
            return handler.handle(request, pathVariables, body);
        }

        @Override
        public ExecutableMethod<?, ?> routeMethod() {
            return provider.routeMethod();
        }
    }

    private record AsyncBodyRequest<B extends @Nullable Object>(AsyncBodyRequestHandler<B> handler,
                                                                RouteMethodProvider provider) implements AsyncBodyRequestHandler<B>, RouteMethodProvider {
        @Override
        public CompletionStage<? extends @Nullable HttpResponse<?>> handle(HttpRequest<?> request, PathVariables pathVariables, B body) throws Exception {
            return handler.handle(request, pathVariables, body);
        }

        @Override
        public ExecutableMethod<?, ?> routeMethod() {
            return provider.routeMethod();
        }
    }
}
