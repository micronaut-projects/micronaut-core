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
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.MediaType;
import io.micronaut.http.PathVariables;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.body.ReleasableRequestBody;
import io.micronaut.http.form.FormData;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.web.router.RouteConditionContext;
import io.micronaut.web.router.RouteLocator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.function.Function;

/**
 * The body stage of a locator route, see {@link LocatorSpec}: the locator is an asynchronous
 * locator whose stage reads a copy of the body of the request first.
 *
 * @param scope        The scope the locator route is declared in
 * @param prefixUri    The prefix of the locator route
 * @param declaration  The declaration of the locator route, which its terminal ends
 * @param context      Provides the body of a request
 * @param consumes     The media types a handler route consumes by default
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record DefaultLocatorSpec(HttpRouteScope scope, String prefixUri, PendingRoute declaration, RouteConditionContext context,
                          List<MediaType> consumes) implements LocatorSpec {

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
        // a body without a content type is bound like the body of a handler route, which
        // decodes it with the media type it consumes by default: no route is matched yet to select it
        MediaType defaultType = consumes.getFirst();
        return new Body<>(this, null, request -> request.getContentType().isPresent() ? request : new DefaultContentType<>(request, defaultType),
            bound -> bound.copy().body(bodyType));
    }

    @Override
    public LocatorBodySpec<AsyncRequestBody> body() {
        return new Body<>(this, null, UnaryOperator.identity(), bound -> CompletableFuture.completedFuture(bound.copy()));
    }

    @Override
    public LocatorBodySpec<FormData> form() {
        // the form of a copy is the one form of the request; a request whose body is not a form,
        // e.g. a GET or a JSON body for a located route, is located with the empty form, and its
        // body is left unread to the located route
        return new Body<>(this, request -> isForm(request) ? null : FormData.empty(), UnaryOperator.identity(), bound -> bound.copy().form());
    }

    /**
     * @param request A request
     * @return Whether its content type is a form, {@code application/x-www-form-urlencoded} or {@code multipart/form-data}
     */
    private static boolean isForm(HttpRequest<?> request) {
        MediaType contentType = request.getContentType().orElse(null);
        return contentType != null
            && (contentType.matches(MediaType.APPLICATION_FORM_URLENCODED_TYPE) || contentType.matches(MediaType.MULTIPART_FORM_DATA_TYPE));
    }

    /**
     * The locator of a body read from a copy of the body of the request.
     *
     * @param spec     The locator route
     * @param unread   The body to locate a request with without reading its body, {@code null} when it is read; {@code null} to read every body
     * @param binding  The request the body is bound from, e.g. with a default content type
     * @param reader   Reads the body from a copy of the body of the request
     * @param <B>      The type of the body
     */
    record Body<B extends @Nullable Object>(DefaultLocatorSpec spec,
                                            @Nullable Function<HttpRequest<?>, @Nullable B> unread,
                                            UnaryOperator<HttpRequest<?>> binding,
                                            Function<AsyncRequestBody, CompletionStage<? extends B>> reader) implements LocatorBodySpec<B> {

        @Override
        public <T> void locate(BodyLocatorHandler<B, ? extends T> locator, Function<? super T, ? extends LocatedRoutes<?>> routesOf) {
            Objects.requireNonNull(locator, "locator");
            locateAsync((request, pathVariables, body) -> CompletableFuture.completedFuture(locator.locate(request, pathVariables, body)), routesOf);
        }

        @Override
        public <T> void locateAsync(AsyncBodyLocatorHandler<B, ? extends T> locator, Function<? super T, ? extends LocatedRoutes<?>> routesOf) {
            Objects.requireNonNull(locator, "locator");
            // one terminal per declaration
            spec.declaration().endLocator();
            RouteConditionContext context = spec.context();
            spec.scope().<T>locateAsync(spec.prefixUri(), (request, pathVariables) -> locate(context, request, pathVariables, locator), routesOf);
        }

        private <T> CompletionStage<? extends @Nullable T> locate(RouteConditionContext context,
                                                                 HttpRequest<?> request,
                                                                 PathVariables pathVariables,
                                                                 AsyncBodyLocatorHandler<B, ? extends T> locator) throws Exception {
            B without = unread == null ? null : unread.apply(request);
            if (without != null) {
                // nothing is bound, copied or buffered
                return Objects.requireNonNull(locator.locate(request, pathVariables, without), () -> "The locator returned no stage: " + locator);
            }
            AsyncRequestBody bound = context.locatorBody(binding.apply(request));
            if (bound == null) {
                throw new IllegalStateException("No HTTP server provides the body of the request " + request.getMethodName()
                    + " " + request.getPath() + " to its route locator");
            }
            // the locator runs when the body was read, maybe on another thread: in the context of the matching
            PropagatedContext propagated = PropagatedContext.getOrEmpty();
            BodyLocation<T> location = new BodyLocation<>(bound);
            CompletionStage<? extends @Nullable T> stage = null;
            try {
                // the located route reads the body of the request again
                stage = reader.apply(bound).thenCompose(body -> propagated.propagate(() -> {
                    try {
                        return Objects.requireNonNull(locator.locate(request, pathVariables, body), () -> "The locator returned no stage: " + locator);
                    } catch (Exception e) {
                        throw e instanceof CompletionException completion ? completion : new CompletionException(e);
                    }
                }));
            } finally {
                if (stage == null) {
                    // failed before the read started
                    location.abandon();
                }
            }
            // the stage of the locator may be shared and outlive the request: it references the
            // location only, which stops holding the body when the request is abandoned
            stage.whenComplete(location::located);
            return location;
        }

    }

    /**
     * The location of a target by a locator that reads the body: completes with the outcome of
     * the locator once what its read of the body left open was released, like the result of a
     * handler of the body, see {@link ReleasableRequestBody#releaseAfter}. It is the request's
     * own stage: abandoned with the request, it releases the body at once, and stops holding it
     * for a stage of the locator that may be shared and outlive the request.
     *
     * @param <T> The type of the target
     */
    static final class BodyLocation<T> extends CompletableFuture<@Nullable T> implements RouteLocator.AbandonableStage {
        private final AtomicReference<@Nullable AsyncRequestBody> body;

        BodyLocation(AsyncRequestBody body) {
            this.body = new AtomicReference<>(body);
        }

        /**
         * The locator completed: release the body, then complete with its outcome.
         */
        void located(@Nullable T target, @Nullable Throwable error) {
            AsyncRequestBody held = body.getAndSet(null);
            if (held == null) {
                // abandoned: nobody waits
                return;
            }
            if (!(held instanceof ReleasableRequestBody releasable)) {
                complete(target, error, null);
                return;
            }
            CompletionStage<Void> released;
            try {
                released = releasable.releaseBody();
            } catch (RuntimeException e) {
                complete(target, error, e);
                return;
            }
            released.whenComplete((ignored, releaseError) -> complete(target, error, releaseError));
        }

        private void complete(@Nullable T target, @Nullable Throwable error, @Nullable Throwable releaseError) {
            Throwable releaseFailure = unwrap(releaseError);
            Throwable failure = unwrap(error);
            if (failure != null) {
                if (releaseFailure != null && releaseFailure != failure) {
                    failure.addSuppressed(releaseFailure);
                }
                completeExceptionally(failure);
            } else if (releaseFailure != null) {
                // like a handler of the body: a failure to release fails a successful outcome
                completeExceptionally(releaseFailure);
            } else {
                complete(target);
            }
        }

        private static @Nullable Throwable unwrap(@Nullable Throwable error) {
            return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        }

        @Override
        public void abandon() {
            AsyncRequestBody held = body.getAndSet(null);
            if (!(held instanceof ReleasableRequestBody releasable)) {
                return;
            }
            CompletionStage<Void> released;
            try {
                released = releasable.releaseBody();
            } catch (RuntimeException e) {
                // the request is abandoned anyway: nobody receives the failure
                LOG.warn("Failed to release the body read by the route locator of an abandoned request", e);
                return;
            }
            released.whenComplete((ignored, error) -> {
                if (error != null) {
                    LOG.warn("Failed to release the body read by the route locator of an abandoned request", error);
                }
            });
        }
    }

    /**
     * A request without a content type, bound as if it had the one a handler route consumes by
     * default, e.g. {@code application/json}: the body is decoded by the binders of the server,
     * with their checks, e.g. of a missing required body, and a body a filter replaced is that body.
     *
     * @param <B> The body type
     */
    private static final class DefaultContentType<B> extends HttpRequestWrapper<B> {
        private final Optional<MediaType> contentType;

        @SuppressWarnings("unchecked")
        DefaultContentType(HttpRequest<?> request, MediaType contentType) {
            super((HttpRequest<B>) request);
            this.contentType = Optional.of(contentType);
        }

        @Override
        public Optional<MediaType> getContentType() {
            return contentType;
        }
    }
}
