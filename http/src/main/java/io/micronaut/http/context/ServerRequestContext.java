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
package io.micronaut.http.context;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpRequest;
import reactor.util.context.ContextView;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * The holder for the current {@link HttpRequest} that is bound to instrumented threads.
 * Allowing lookup of the current request if it is present.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public final class ServerRequestContext {

    /**
     * @deprecated Please use {@link #currentRequest(ContextView)} to look up the request in a
     * reactor context.
     */
    @Deprecated
    public static final String KEY = "micronaut.http.server.request";

    private ServerRequestContext() {
    }

    /**
     * Wrap the execution of the given runnable in request context processing.
     *
     * @param request  The request
     * @param runnable The runnable
     */
    public static void with(@Nullable HttpRequest<?> request, @NonNull Runnable runnable) {
        try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty().plus(new ServerHttpRequestContext(request)).propagate()) {
            runnable.run();
        }
    }

    /**
     * Return a new runnable by instrumenting the given runnable with request context handling.
     *
     * @param request  The request
     * @param runnable The runnable
     * @return The newly instrumented runnable
     */
    public static Runnable instrument(@Nullable HttpRequest<?> request, @NonNull Runnable runnable) {
        return () -> with(request, runnable);
    }

    /**
     * Wrap the execution of the given callable in request context processing.
     *
     * @param request  The request
     * @param supplier The callable
     * @param <T>      The return type of the callable
     * @return The return value of the callable
     */
    public static <T> T with(@Nullable HttpRequest<?> request, @NonNull Supplier<T> supplier) {
        try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty().plus(new ServerHttpRequestContext(request)).propagate()) {
            return supplier.get();
        }
    }

    /**
     * Wrap the execution of the given callable in request context processing.
     *
     * @param request  The request
     * @param callable The callable
     * @param <T>      The return type of the callable
     * @return The return value of the callable
     * @throws Exception If the callable throws an exception
     */
    public static <T> T with(@Nullable HttpRequest<?> request, @NonNull Callable<T> callable) throws Exception {
        try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty().plus(new ServerHttpRequestContext(request)).propagate()) {
            return callable.call();
        }
    }

    /**
     * Retrieve the current server request context.
     *
     * @param <T> The body type
     * @return The request context if it is present
     */
    public static <T> Optional<HttpRequest<T>> currentRequest() {
        return ServerHttpRequestContext.find();
    }

    /**
     * Retrieve the current server request context from the given reactor context view.
     *
     * @param context The reactor context view
     * @param <T> The body type
     * @return The request context if it is present
     * @since 4.8.0
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public static <T> Optional<HttpRequest<T>> currentRequest(@NonNull ContextView context) {
        return ReactorPropagation.findPropagatedContext(context)
            .flatMap(ctx -> ctx.find(ServerHttpRequestContext.class))
            .map(e -> (HttpRequest<T>) e.httpRequest())
            .or(() -> context.getOrEmpty(KEY));
    }
}

