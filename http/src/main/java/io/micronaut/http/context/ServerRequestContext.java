/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.http.context;

import io.micronaut.http.HttpRequest;

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

    private static final ThreadLocal<HttpRequest> REQUEST = new ThreadLocal<>();

    private ServerRequestContext() {
    }

    /**
     * Wrap the execution of the given runnable in request context processing.
     *
     * @param request  The request
     * @param runnable The runnable
     */
    public static void with(HttpRequest request, Runnable runnable) {
        HttpRequest existing = REQUEST.get();
        boolean isSet = false;
        try {
            if (existing == null) {
                isSet = true;
                REQUEST.set(request);
            }
            runnable.run();
        } finally {
            if (isSet) {
                REQUEST.remove();
            }
        }
    }

    /**
     * Return a new runnable by instrumenting the given runnable with request context handling.
     *
     * @param request  The request
     * @param runnable The runnable
     * @return The newly instrumented runnable
     */
    public static Runnable instrument(HttpRequest request, Runnable runnable) {
        return () -> with(request, runnable);
    }

    /**
     * Wrap the execution of the given callable in request context processing.
     *
     * @param request  The request
     * @param callable The callable
     * @param <T>      The return type of the callable
     * @return The return value of the callable
     */
    public static <T> T with(HttpRequest request, Supplier<T> callable) {
        try {
            REQUEST.set(request);
            return callable.get();
        } finally {
            REQUEST.remove();
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
    public static <T> T with(HttpRequest request, Callable<T> callable) throws Exception {
        try {
            REQUEST.set(request);
            return callable.call();
        } finally {
            REQUEST.remove();
        }
    }

    /**
     * Retrieve the current server request context.
     *
     * @param <T> The body type
     * @return The request context if it is present
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<HttpRequest<T>> currentRequest() {
        return Optional.ofNullable(REQUEST.get());
    }
}

