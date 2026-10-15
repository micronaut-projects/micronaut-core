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
package io.micronaut.http.server;

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.CompletableFutureExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.body.ReleasableRequestBody;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.micronaut.http.server.binding.ServerRequestBody;
import io.micronaut.web.router.PendingRouteMatch;
import io.micronaut.web.router.RouteLocator;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.web.router.builder.RouteCondition;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * The asynchronous step of the request lifecycle that chooses a route by the body of the request,
 * see {@link RouteCondition.Body}: it reads the body once, without blocking, up to the largest
 * limit of the body conditions of the routes the request matched, evaluates the conditions rank
 * by rank, see {@link PendingRouteMatch}, and chooses the route.
 *
 * <p>The body is read from a {@link AsyncRequestBody#copy() copy} of the {@link AsyncRequestBody}
 * of the request, so the chosen route reads the same body as without body conditions: its body
 * handler, {@code @Body} argument, form or {@code AsyncRequestBody}. The body is held to the buffer
 * limit of the server like the body a filter looks at. What the read left open is released when
 * the route is chosen, or, on a failure or a cancellation, when the request ends.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class BodyRouteSelector {

    private static final Logger LOG = LoggerFactory.getLogger(BodyRouteSelector.class);
    private static final byte[] EMPTY = new byte[0];
    private static final Argument<AsyncRequestBody> ASYNC_REQUEST_BODY = Argument.of(AsyncRequestBody.class);
    /**
     * The largest array the server reads a body into.
     */
    private static final int MAX_ARRAY = Integer.MAX_VALUE - 8;

    private final HttpServerConfiguration configuration;
    private final MessageBodyHandlerRegistry bodyHandlerRegistry;
    private final RequestBinderRegistry binderRegistry;
    private final ConversionService conversionService;

    /**
     * @param configuration       The configuration of the server
     * @param bodyHandlerRegistry The message body readers
     * @param binderRegistry      The binders of the arguments of the routes
     * @param conversionService   The conversion service
     */
    BodyRouteSelector(HttpServerConfiguration configuration,
                      MessageBodyHandlerRegistry bodyHandlerRegistry,
                      RequestBinderRegistry binderRegistry,
                      ConversionService conversionService) {
        this.configuration = configuration;
        this.bodyHandlerRegistry = bodyHandlerRegistry;
        this.binderRegistry = binderRegistry;
        this.conversionService = conversionService;
    }

    /**
     * Check that no body condition of a route has a limit, see
     * {@link RouteCondition.Body#maxBytes(int)}, above the number of bytes of a request the
     * server may buffer: a limit lowers the limit of the server, and cannot raise it.
     *
     * @param router               The router
     * @param maxRequestBufferSize {@code micronaut.server.max-request-buffer-size}
     * @throws ConfigurationException if a condition has a larger limit
     */
    static void checkMaxBytes(Router router, long maxRequestBufferSize) {
        router.uriRoutes()
            // an implicit HEAD route has the conditions of its GET route, which names them
            .filter(route -> !route.isImplicitHead())
            .forEach(route -> {
                for (RouteCondition.Body<?> condition : route.getBodyConditions()) {
                    int maxBytes = condition.maxBytes();
                    if (maxBytes != RouteCondition.Body.SERVER_LIMIT && maxBytes > maxRequestBufferSize) {
                        throw new ConfigurationException("A body condition of the route " + route + " reads up to "
                            + maxBytes + " bytes, maxBytes(" + maxBytes + "), more than the " + maxRequestBufferSize
                            + " bytes of micronaut.server.max-request-buffer-size that the server may buffer: lower the maxBytes of the condition, "
                            + "or raise micronaut.server.max-request-buffer-size");
                    }
                }
            });
    }

    /**
     * Read the body of the request and choose the route whose body conditions it meets.
     *
     * @param request           The request, as the pre-matching filters left it
     * @param pending           The matches that depend on the body
     * @param propagatedContext The context the conditions are evaluated in
     * @param waits             Tells the lifecycle of the request what the choice waits for, so that it stops waiting when the request is abandoned
     * @param <T>               The target type
     * @param <R>               The result type
     * @return A flow of the choice: the chosen match, and whether the body met the conditions of a route
     */
    <T, R> ExecutionFlow<Choice<T, R>> select(HttpRequest<?> request,
                                              PendingRouteMatch<T, R> pending,
                                              PropagatedContext propagatedContext,
                                              Waits waits) {
        int serverLimit = serverLimit();
        return read(request, pending.getMaxBytes(serverLimit), serverLimit)
            .flatMap(body -> propagatedContext.propagate(() -> choose(new Selection<>(request, pending, body, serverLimit, propagatedContext, waits), 0)));
    }

    private int serverLimit() {
        return (int) Math.min(configuration.getMaxRequestBufferSize(), MAX_ARRAY);
    }

    /**
     * Choose the route among the ranks from one on: the first rank with a route whose body
     * conditions the body meets. The conditions are evaluated one after the other, and an
     * asynchronous condition continues the choice when its stage completes.
     */
    private <T, R> ExecutionFlow<Choice<T, R>> choose(Selection<T, R> selection, int from) {
        List<List<UriRouteMatch<T, R>>> ranks = selection.pending.getRanks();
        for (int i = from; i < ranks.size(); i++) {
            List<UriRouteMatch<T, R>> rank = ranks.get(i);
            ExecutionFlow<List<UriRouteMatch<T, R>>> matchedFlow = matched(selection, rank, 0, new ArrayList<>(rank.size()));
            List<UriRouteMatch<T, R>> matched = matchedFlow.tryCompleteValue();
            if (matched == null) {
                int next = i + 1;
                return matchedFlow.flatMap(m -> selection.propagate(() -> m.isEmpty()
                    ? choose(selection, next)
                    : chosen(selection, m)));
            }
            if (!matched.isEmpty()) {
                return chosen(selection, matched);
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("The body of the request {} {} meets the body conditions of no route", selection.request.getMethodName(), selection.request.getPath());
        }
        return ExecutionFlow.just(Choice.unmet());
    }

    /**
     * The route chosen among the matches of a rank the body meets. A chosen route locator
     * locates its target: when the routes of the target depend on the body too, the body is
     * read again for them, and an asynchronous locator that has not located its target yet is
     * waited for, like the router does without body conditions.
     */
    private <T, R> ExecutionFlow<Choice<T, R>> chosen(Selection<T, R> selection, List<UriRouteMatch<T, R>> matched) {
        List<PendingRouteMatch<T, R>> next = new ArrayList<>(1);
        UriRouteMatch<T, R> match;
        try {
            match = selection.pending.choose(selection.request, matched, next::add);
        } catch (RuntimeException e) {
            CompletionStage<?> location = RouteLocator.pendingLocation(e);
            if (location == null) {
                throw e;
            }
            // a locator started after the body was read: the request stops waiting for it when abandoned
            selection.waits.onPendingLocation(selection.request, location);
            return CompletableFutureExecutionFlow.just(location)
                .flatMap(located -> selection.propagate(() -> chosen(selection, matched)));
        }
        if (!next.isEmpty()) {
            return select(selection.request, next.getFirst(), selection.context, selection.waits);
        }
        // the body met the conditions of a route, which may still resolve to no route, e.g. a
        // located target without a route for the method: answered as without body conditions
        return ExecutionFlow.just(new Choice<>(match, true));
    }

    /**
     * The matches of a rank, from one on, whose body conditions the body meets.
     */
    private <T, R> ExecutionFlow<List<UriRouteMatch<T, R>>> matched(Selection<T, R> selection,
                                                                   List<UriRouteMatch<T, R>> rank,
                                                                   int from,
                                                                   List<UriRouteMatch<T, R>> matched) {
        for (int i = from; i < rank.size(); i++) {
            UriRouteMatch<T, R> match = rank.get(i);
            ExecutionFlow<Boolean> flow = meets(selection, match.getRouteInfo().getBodyConditions(), 0);
            Boolean meets = flow.tryCompleteValue();
            if (meets == null) {
                int next = i + 1;
                return flow.flatMap(m -> selection.propagate(() -> {
                    if (m) {
                        matched.add(match);
                    }
                    return matched(selection, rank, next, matched);
                }));
            }
            if (meets) {
                matched.add(match);
            }
        }
        return ExecutionFlow.just(matched);
    }

    /**
     * Whether the body meets the conditions of a route, from one on: all of them, in order.
     */
    private ExecutionFlow<Boolean> meets(Selection<?, ?> selection, List<RouteCondition.Body<?>> conditions, int from) {
        for (int i = from; i < conditions.size(); i++) {
            ExecutionFlow<Boolean> flow = meets(selection, conditions.get(i));
            Boolean meets = flow.tryCompleteValue();
            if (meets == null) {
                int next = i + 1;
                return flow.flatMap(m -> selection.propagate(() -> m ? meets(selection, conditions, next) : ExecutionFlow.just(false)));
            }
            if (!meets) {
                return ExecutionFlow.just(false);
            }
        }
        return ExecutionFlow.just(true);
    }

    private <V> ExecutionFlow<Boolean> meets(Selection<?, ?> selection, RouteCondition.Body<V> condition) {
        RequestBody body = selection.body;
        if (body.length() > condition.effectiveMaxBytes(selection.serverLimit)) {
            return ExecutionFlow.just(false);
        }
        Optional<V> value = body.decode(condition.type());
        if (value.isEmpty()) {
            return ExecutionFlow.just(false);
        }
        CompletionStage<Boolean> stage;
        try {
            stage = condition.condition().apply(value.get());
        } catch (RuntimeException e) {
            return failed(selection.request, e);
        }
        if (stage == null) {
            return ExecutionFlow.just(false);
        }
        return CompletableFutureExecutionFlow.just(detached(selection, stage))
            .map(Boolean.TRUE::equals)
            .onErrorResume(error -> error instanceof ConditionAbandoned || error.getCause() instanceof ConditionAbandoned
                ? ExecutionFlow.error(error)
                : failed(selection.request, error));
    }

    /**
     * The outcome of the stage of a condition, which the request stops waiting for when it is
     * abandoned: the stage, which may be shared, e.g. by a cache, is not cancelled, and it no
     * longer references the choice, the request or its body then.
     */
    private static CompletionStage<Boolean> detached(Selection<?, ?> selection, CompletionStage<Boolean> stage) {
        CompletableFuture<Boolean> outcome = new CompletableFuture<>();
        AtomicReference<@Nullable CompletableFuture<Boolean>> waiting = new AtomicReference<>(outcome);
        stage.whenComplete((met, error) -> {
            CompletableFuture<Boolean> waiter = waiting.getAndSet(null);
            if (waiter != null) {
                if (error != null) {
                    waiter.completeExceptionally(error);
                } else {
                    waiter.complete(met);
                }
            }
        });
        if (!outcome.isDone()) {
            Runnable remove = selection.waits.whenAbandoned(selection.request, () -> {
                CompletableFuture<Boolean> waiter = waiting.getAndSet(null);
                if (waiter != null) {
                    waiter.completeExceptionally(new ConditionAbandoned());
                }
            });
            if (remove != null) {
                outcome.whenComplete((met, error) -> remove.run());
            }
        }
        return outcome;
    }

    /**
     * A condition that failed, or whose stage failed, is not met.
     */
    private static ExecutionFlow<Boolean> failed(HttpRequest<?> request, Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        LOG.warn("A body condition of the request {} {} failed, it is not met: {}", request.getMethodName(), request.getPath(), cause.getMessage(), cause);
        return ExecutionFlow.just(false);
    }

    /**
     * Read the body of the request, as the pre-matching filters left it, up to a limit, from a
     * copy of its {@link AsyncRequestBody}.
     *
     * @param request  The request
     * @param maxBytes The limit
     * @param serverLimit The limit of the server for buffered request content
     * @return The body
     */
    private ExecutionFlow<RequestBody> read(HttpRequest<?> request, int maxBytes, int serverLimit) {
        if (ServerRequestBody.isBodySet(request) || ServerRequestBody.of(request) == null) {
            // a body a filter set, as an object, or cleared
            Object object = request.getBody().orElse(null);
            return ExecutionFlow.just(object == null ? new RequestBody(request, EMPTY, 0) : new RequestBody(request, object));
        }
        AsyncRequestBody body = bind(request);
        if (body == null || !body.hasBody()) {
            return ExecutionFlow.just(new RequestBody(request, EMPTY, 0));
        }
        OptionalLong expected = body.expectedBodySize();
        if (expected.isPresent() && expected.getAsLong() > maxBytes) {
            // not read at all
            return ExecutionFlow.just(oversized(request, expected.getAsLong(), maxBytes));
        }
        CompletionStage<RequestBody> read;
        try {
            read = body.copy().bytes(maxBytes)
                .handle((bytes, error) -> {
                    release(body);
                    if (error == null) {
                        return new RequestBody(request, bytes, bytes.length);
                    }
                    Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                    if (cause instanceof ContentLengthExceededException && maxBytes < serverLimit) {
                        // larger than the limit of the conditions, within the buffer of the server, which
                        // keeps the body for the next route
                        return oversized(request, maxBytes + 1L, maxBytes);
                    }
                    // larger than the server may buffer: the body is not kept for another route, the
                    // request fails with 413, like a @Body argument of the route would
                    throw error instanceof CompletionException completion ? completion : new CompletionException(cause);
                });
        } catch (RuntimeException e) {
            release(body);
            return ExecutionFlow.error(e);
        }
        return CompletableFutureExecutionFlow.just(read)
            .onErrorResume(error -> ExecutionFlow.error(error instanceof CompletionException && error.getCause() != null ? error.getCause() : error));
    }

    /**
     * The {@link AsyncRequestBody} of the request, bound like the argument of a filter method:
     * it is not a body of the route, and is released here once it is read.
     */
    private @Nullable AsyncRequestBody bind(HttpRequest<?> request) {
        AsyncRequestBody[] bound = new AsyncRequestBody[1];
        BasicHttpAttributes.detachRouteWaitsFor(request, () -> binderRegistry.findArgumentBinder(ASYNC_REQUEST_BODY)
            .flatMap(binder -> binder.bind(ConversionContext.of(ASYNC_REQUEST_BODY), request).getValue())
            .ifPresent(body -> bound[0] = body));
        return bound[0];
    }

    private static void release(AsyncRequestBody body) {
        if (body instanceof ReleasableRequestBody releasable) {
            releasable.releaseBody().whenComplete((ignored, error) -> {
                if (error != null) {
                    LOG.warn("Failed to release the copy of the body read for the body conditions of the routes", error);
                }
            });
        }
    }

    private RequestBody oversized(HttpRequest<?> request, long length, int maxBytes) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("The body of the request {} {} is larger than {} bytes, the largest limit of the body conditions of its routes",
                request.getMethodName(), request.getPath(), maxBytes);
        }
        return new RequestBody(request, null, Math.max(length, maxBytes + 1L));
    }

    /**
     * The body of the request, as the conditions read it, each decoded form once.
     */
    private final class RequestBody {
        private final HttpRequest<?> request;
        private final byte @Nullable [] bytes;
        private final @Nullable Object object;
        private final long length;
        private final List<Decoded> decoded = new ArrayList<>(1);

        /**
         * @param request The request
         * @param bytes   The bytes, or {@code null} if the body is larger than the limit
         * @param length  The length
         */
        RequestBody(HttpRequest<?> request, byte @Nullable [] bytes, long length) {
            this.request = request;
            this.bytes = bytes;
            this.object = null;
            this.length = length;
        }

        /**
         * @param request The request
         * @param object  The body a filter set
         */
        RequestBody(HttpRequest<?> request, Object object) {
            this.request = request;
            this.bytes = null;
            this.object = object;
            // the limits of the conditions apply to the bytes of a body a filter set; another object
            // has no size of its own
            this.length = switch (object) {
                case byte[] array -> array.length;
                case CharSequence text -> text.toString().getBytes(charset()).length;
                default -> 0;
            };
        }

        long length() {
            return length;
        }

        @SuppressWarnings("unchecked")
        <V> Optional<V> decode(Argument<V> type) {
            for (Decoded d : decoded) {
                if (sameDecoding(d.type(), type)) {
                    return (Optional<V>) d.value();
                }
            }
            Optional<V> value;
            try {
                value = decodeNow(type);
            } catch (RuntimeException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("The body of the request {} {} does not decode to {}: {}", request.getMethodName(), request.getPath(), type, e.getMessage(), e);
                }
                value = Optional.empty();
            }
            decoded.add(new Decoded(type, value));
            return value;
        }

        @SuppressWarnings("unchecked")
        private <V> Optional<V> decodeNow(Argument<V> type) {
            Class<V> rawType = type.getType();
            Object value = object;
            if (value != null) {
                return rawType.isInstance(value) ? Optional.of((V) value) : conversionService.convert(value, type);
            }
            byte[] array = bytes;
            if (array == null) {
                return Optional.empty();
            }
            if (rawType == byte[].class) {
                return Optional.of((V) array);
            }
            if (rawType == String.class) {
                return Optional.of((V) new String(array, charset()));
            }
            MediaType contentType = request.getContentType().orElse(null);
            MessageBodyReader<V> reader = bodyHandlerRegistry.findReader(type, contentType == null ? List.of() : List.of(contentType)).orElse(null);
            if (reader != null && reader.isReadable(type, contentType)) {
                // the bytes are in memory: the read does not block
                return Optional.ofNullable(reader.read(type, contentType, request.getHeaders(), new ByteArrayInputStream(array)));
            }
            return conversionService.convert(new String(array, charset()), type);
        }

        private Charset charset() {
            return request.getContentType().flatMap(MediaType::getCharset).orElse(configuration.getDefaultCharset());
        }
    }

    /**
     * @return Whether the body decoded to one argument is the body decoded to another: the same
     * argument, or the same type without annotations, which may change the decoding, e.g. a
     * {@code @JsonView}
     */
    private static boolean sameDecoding(Argument<?> decoded, Argument<?> type) {
        return decoded == type || decoded.equalsType(type)
            && decoded.getAnnotationMetadata().isEmpty() && type.getAnnotationMetadata().isEmpty();
    }

    /**
     * The body decoded to a type.
     *
     * @param type  The type
     * @param value The value, empty if the body does not decode to the type
     */
    private record Decoded(Argument<?> type, Optional<?> value) {
    }

    /**
     * The state of the choice of a route by the body of a request.
     *
     * @param request     The request
     * @param pending     The matches that depend on the body
     * @param body        The body, as the conditions read it
     * @param serverLimit The limit of the server for buffered request content
     * @param context     The context the conditions are evaluated in
     * @param waits       What the choice waits for
     * @param <T>         The target type
     * @param <R>         The result type
     */
    private record Selection<T, R>(HttpRequest<?> request,
                                   PendingRouteMatch<T, R> pending,
                                   RequestBody body,
                                   int serverLimit,
                                   PropagatedContext context,
                                   Waits waits) {
        <U> ExecutionFlow<U> propagate(Supplier<ExecutionFlow<U>> next) {
            return context.propagate(next);
        }
    }

    /**
     * The route chosen by the body of a request.
     *
     * @param match   The chosen match, or {@code null} if there is none
     * @param bodyMet Whether the body met the conditions of a route: a request whose body met
     *                none is not found, without a {@code 405}, {@code 415} or {@code 406} of the routes
     *                whose conditions it did not meet
     * @param <T>     The target type
     * @param <R>     The result type
     */
    record Choice<T, R>(@Nullable UriRouteMatch<T, R> match, boolean bodyMet) {
        static <T, R> Choice<T, R> unmet() {
            return new Choice<>(null, false);
        }
    }

    /**
     * Tells the lifecycle of a request what choosing its route waits for, so that it stops waiting
     * when the client abandons the request.
     */
    interface Waits {
        /**
         * @param request The request
         * @param located Completes when the target of a route locator is located
         */
        void onPendingLocation(HttpRequest<?> request, CompletionStage<?> located);

        /**
         * @param request The request
         * @param action  Run when the client abandons the request
         * @return Removes the action, or {@code null} if the server cannot tell
         */
        @Nullable Runnable whenAbandoned(HttpRequest<?> request, Runnable action);
    }

    /**
     * The request was abandoned while a condition was decided: an expected outcome, without a stack trace.
     */
    private static final class ConditionAbandoned extends RuntimeException {
        ConditionAbandoned() {
            super("The request was abandoned before the body conditions of its routes were decided", null, false, false);
        }
    }
}
