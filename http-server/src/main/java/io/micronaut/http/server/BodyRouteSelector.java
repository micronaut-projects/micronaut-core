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
import java.util.concurrent.CompletionStage;

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
     * @param <T>               The target type
     * @param <R>               The result type
     * @return A flow of the chosen match, empty if the body matches no route
     */
    <T, R> ExecutionFlow<Optional<UriRouteMatch<T, R>>> select(HttpRequest<?> request,
                                                               PendingRouteMatch<T, R> pending,
                                                               PropagatedContext propagatedContext) {
        int serverLimit = serverLimit();
        return read(request, pending.getMaxBytes(serverLimit))
            .flatMap(body -> propagatedContext.propagate(() -> ExecutionFlow.just(choose(request, pending, body, serverLimit))));
    }

    private int serverLimit() {
        return (int) Math.min(configuration.getMaxRequestBufferSize(), MAX_ARRAY);
    }

    private <T, R> Optional<UriRouteMatch<T, R>> choose(HttpRequest<?> request,
                                                        PendingRouteMatch<T, R> pending,
                                                        RequestBody body,
                                                        int serverLimit) {
        for (List<UriRouteMatch<T, R>> rank : pending.getRanks()) {
            List<UriRouteMatch<T, R>> matched = new ArrayList<>(rank.size());
            for (UriRouteMatch<T, R> match : rank) {
                if (meets(match.getRouteInfo().getBodyConditions(), body, serverLimit)) {
                    matched.add(match);
                }
            }
            if (!matched.isEmpty()) {
                return Optional.ofNullable(pending.choose(request, matched));
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("The body of the request {} {} meets the body conditions of no route", request.getMethodName(), request.getPath());
        }
        return Optional.empty();
    }

    private static boolean meets(List<RouteCondition.Body<?>> conditions, RequestBody body, int serverLimit) {
        for (RouteCondition.Body<?> condition : conditions) {
            if (!meets(condition, body, serverLimit)) {
                return false;
            }
        }
        return true;
    }

    private static <V> boolean meets(RouteCondition.Body<V> condition, RequestBody body, int serverLimit) {
        if (body.length() > condition.effectiveMaxBytes(serverLimit)) {
            return false;
        }
        Optional<V> value = body.decode(condition.type());
        return value.isPresent() && condition.predicate().test(value.get());
    }

    /**
     * Read the body of the request, as the pre-matching filters left it, up to a limit, from a
     * copy of its {@link AsyncRequestBody}.
     *
     * @param request  The request
     * @param maxBytes The limit
     * @return The body
     */
    private ExecutionFlow<RequestBody> read(HttpRequest<?> request, int maxBytes) {
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
                    if (cause instanceof ContentLengthExceededException) {
                        return oversized(request, maxBytes + 1L, maxBytes);
                    }
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
            this.length = 0;
        }

        long length() {
            return length;
        }

        @SuppressWarnings("unchecked")
        <V> Optional<V> decode(Argument<V> type) {
            for (Decoded d : decoded) {
                if (d.type().equalsType(type)) {
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
     * The body decoded to a type.
     *
     * @param type  The type
     * @param value The value, empty if the body does not decode to the type
     */
    private record Decoded(Argument<?> type, Optional<?> value) {
    }
}
