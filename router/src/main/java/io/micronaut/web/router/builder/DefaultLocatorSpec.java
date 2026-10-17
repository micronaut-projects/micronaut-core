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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.PathVariables;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.body.ReleasableRequestBody;
import io.micronaut.http.form.FormData;
import io.micronaut.web.router.RouteConditionContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * The body stage of a locator route, see {@link LocatorSpec}: the locator is an asynchronous
 * locator whose stage reads a copy of the body of the request first.
 *
 * @param scope    The scope the locator route is declared in
 * @param prefixUri The prefix of the locator route
 * @param context  Provides the body of a request
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record DefaultLocatorSpec(HttpRouteScope scope, String prefixUri, RouteConditionContext context) implements LocatorSpec {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultLocatorSpec.class);

    DefaultLocatorSpec {
        Objects.requireNonNull(prefixUri, "prefixUri");
    }

    @Override
    public <B extends @Nullable Object> LocatorBodySpec<B> body(Argument<B> bodyType) {
        Objects.requireNonNull(bodyType, "bodyType");
        if (bodyType.getType() == AsyncRequestBody.class) {
            // the body the locator reads itself, like body()
            @SuppressWarnings("unchecked")
            LocatorBodySpec<B> body = (LocatorBodySpec<B>) body();
            return body;
        }
        return new Body<>(this, copy -> copy.body(bodyType));
    }

    @Override
    public LocatorBodySpec<AsyncRequestBody> body() {
        return new Body<>(this, CompletableFuture::completedFuture);
    }

    @Override
    public LocatorBodySpec<FormData> form() {
        // the form of a copy is the one form of the request
        return new Body<>(this, AsyncRequestBody::form);
    }

    /**
     * The locator of a body read from a copy of the body of the request.
     *
     * @param spec   The locator route
     * @param reader Reads the body from the copy
     * @param <B>    The type of the body
     */
    record Body<B extends @Nullable Object>(DefaultLocatorSpec spec,
                                            Function<AsyncRequestBody, CompletionStage<? extends B>> reader) implements LocatorBodySpec<B> {

        @Override
        public <T> void locate(BodyLocatorHandler<B, ? extends T> locator, Function<? super T, ? extends LocatedRoutes<?>> routesOf) {
            Objects.requireNonNull(locator, "locator");
            locateAsync((request, pathVariables, body) -> CompletableFuture.completedFuture(locator.locate(request, pathVariables, body)), routesOf);
        }

        @Override
        public <T> void locateAsync(AsyncBodyLocatorHandler<B, ? extends T> locator, Function<? super T, ? extends LocatedRoutes<?>> routesOf) {
            Objects.requireNonNull(locator, "locator");
            RouteConditionContext context = spec.context();
            spec.scope().<T>locateAsync(spec.prefixUri(), (request, pathVariables) -> locate(context, request, pathVariables, locator), routesOf);
        }

        private <T> CompletionStage<? extends @Nullable T> locate(RouteConditionContext context,
                                                                 HttpRequest<?> request,
                                                                 PathVariables pathVariables,
                                                                 AsyncBodyLocatorHandler<B, ? extends T> locator) {
            AsyncRequestBody bound = context.locatorBody(request);
            if (bound == null) {
                throw new IllegalStateException("No HTTP server provides the body of the request " + request.getMethodName()
                    + " " + request.getPath() + " to its route locator");
            }
            CompletionStage<? extends @Nullable T> stage;
            try {
                // the located route reads the body of the request again
                stage = reader.apply(bound.copy()).thenCompose(body -> {
                    try {
                        return Objects.requireNonNull(locator.locate(request, pathVariables, body), () -> "The locator returned no stage: " + locator);
                    } catch (Exception e) {
                        throw e instanceof CompletionException completion ? completion : new CompletionException(e);
                    }
                });
            } catch (RuntimeException | Error e) {
                release(bound);
                throw e;
            }
            return stage.whenComplete((target, error) -> release(bound));
        }

        /**
         * Release what the read of the copy left open, e.g. the elements the locator did not read.
         */
        private static void release(AsyncRequestBody body) {
            if (body instanceof ReleasableRequestBody releasable) {
                releasable.releaseBody().whenComplete((ignored, error) -> {
                    if (error != null) {
                        LOG.warn("Failed to release the body read by a route locator", error);
                    }
                });
            }
        }
    }
}
