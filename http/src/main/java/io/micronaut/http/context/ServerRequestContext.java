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
package io.micronaut.http.context;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import io.micronaut.http.HttpRequest;

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
     * Set {@link HttpRequest}.
     *
     * @param request new {@link HttpRequest}
     */
    public static void set(@Nullable HttpRequest request) {
        if (request == null) {
            REQUEST.remove();
        } else {
            REQUEST.set(request);
        }
    }

    /**
     * Wrap the execution of the given runnable in request context processing.
     *
     * @param request  The request
     * @param runnable The runnable
     */
    public static void with(@Nullable HttpRequest request, @NonNull Runnable runnable) {
        HttpRequest existing = REQUEST.get();
        boolean isSet = false;
        try {
            if (request != existing) {
                isSet = true;
                set(request);
            }
            runnable.run();
        } finally {
            if (isSet) {
                set(existing);
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
    public static Runnable instrument(@Nullable HttpRequest request, @NonNull Runnable runnable) {
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
    public static <T> T with(@Nullable HttpRequest request, @NonNull Supplier<T> callable) {
        HttpRequest existing = REQUEST.get();
        boolean isSet = false;
        try {
            if (request != existing) {
                isSet = true;
                set(request);
            }
            return callable.get();
        } finally {
            if (isSet) {
                set(existing);
            }
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
    public static <T> T with(@Nullable HttpRequest request, @NonNull Callable<T> callable) throws Exception {
        HttpRequest existing = REQUEST.get();
        boolean isSet = false;
        try {
            if (request != existing) {
                isSet = true;
                set(request);
            }
            return callable.call();
        } finally {
            if (isSet) {
                set(existing);
            }
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

