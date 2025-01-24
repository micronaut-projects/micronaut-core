/*
 * Copyright 2017-2024 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.async.subscriber.LazySendingSubscriber;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.type.Argument;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.ByteBodyHttpResponseWrapper;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseWrapper;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.ConcatenatingSubscriber;
import io.micronaut.http.body.MediaTypeProvider;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.body.ResponseBodyWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import io.micronaut.web.router.DefaultUrlRouteInfo;
import io.micronaut.web.router.RouteInfo;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * This class handles encoding of the HTTP response in a server-agnostic way. Note that while this
 * class is internal, it is used from servlet and must not be broken.
 *
 * @since 4.8.0
 * @author Jonas Konrad
 */
@Internal
public abstract class ResponseLifecycle {
    private final RouteExecutor routeExecutor;
    private final MessageBodyHandlerRegistry messageBodyHandlerRegistry;
    private final ConversionService conversionService;
    private final ByteBodyFactory byteBodyFactory;

    public ResponseLifecycle(RouteExecutor routeExecutor,
                             MessageBodyHandlerRegistry messageBodyHandlerRegistry,
                             ConversionService conversionService,
                             ByteBodyFactory byteBodyFactory) {
        this.routeExecutor = routeExecutor;
        this.messageBodyHandlerRegistry = messageBodyHandlerRegistry;
        this.conversionService = conversionService;
        this.byteBodyFactory = byteBodyFactory;
    }

    /**
     * The IO executor for blocking writers.
     *
     * @return The blocking executor
     */
    @NonNull
    protected abstract Executor ioExecutor();

    /**
     * Transform the given writer into a {@link ResponseBodyWriter}.
     *
     * @param messageBodyWriter The writer
     * @return The response writer
     * @param <T> The writer type
     */
    @NonNull
    protected <T> ResponseBodyWriter<T> wrap(@NonNull MessageBodyWriter<T> messageBodyWriter) {
        return ResponseBodyWriter.wrap(messageBodyWriter);
    }

    /**
     * Encode the response.
     *
     * @param httpRequest The request that triggered this response
     * @param response The unencoded response
     * @return The encoded response
     */
    @NonNull
    public final ExecutionFlow<? extends ByteBodyHttpResponse<?>> encodeHttpResponseSafe(@NonNull HttpRequest<?> httpRequest, @NonNull HttpResponse<?> response) {
        try {
            return encodeHttpResponse(
                httpRequest,
                response,
                response.body()
            );
        } catch (Throwable e) {
            try {
                response = routeExecutor.createDefaultErrorResponse(httpRequest, e);
                return encodeHttpResponse(
                    httpRequest,
                    response,
                    response.body()
                );
            } catch (Throwable f) {
                f.addSuppressed(e);
                return ExecutionFlow.error(f);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private ExecutionFlow<? extends ByteBodyHttpResponse<?>> encodeHttpResponse(
        HttpRequest<?> nettyRequest,
        HttpResponse<?> httpResponse,
        Object body) {
        MutableHttpResponse<?> response = httpResponse.toMutableResponse();
        if (nettyRequest.getMethod() != HttpMethod.HEAD && body != null) {
            Object routeInfoO = response.getAttribute(HttpAttributes.ROUTE_INFO).orElse(null);
            // usually this is a UriRouteInfo, avoid scalability issues here
            @SuppressWarnings("unchecked") final RouteInfo<Object> routeInfo = (RouteInfo<Object>) (routeInfoO instanceof DefaultUrlRouteInfo<?, ?> uri ? uri : (RouteInfo<?>) routeInfoO);

            if (Publishers.isConvertibleToPublisher(body)) {
                response.body(null);
                return mapToHttpContent(nettyRequest, response, body, routeInfo);
            }

            // avoid checkcast for MessageBodyWriter interface here
            Object o = response.getBodyWriter().orElse(null);
            MessageBodyWriter<Object> messageBodyWriter = o instanceof ResponseBodyWriter rbw ? rbw : (MessageBodyWriter<Object>) o;
            MediaType responseMediaType = response.getContentType().orElse(null);
            Argument<Object> responseBodyType;
            if (routeInfo != null) {
                responseBodyType = (Argument<Object>) routeInfo.getResponseBodyType();
            } else {
                responseBodyType = Argument.of((Class<Object>) body.getClass());
            }
            if (responseMediaType == null) {
                // perf: check for common body types
                //noinspection ConditionCoveredByFurtherCondition
                if (!(body instanceof String) && !(body instanceof byte[]) && body instanceof MediaTypeProvider mediaTypeProvider) {
                    responseMediaType = mediaTypeProvider.getMediaType();
                } else if (routeInfo != null) {
                    responseMediaType = routeExecutor.resolveDefaultResponseContentType(nettyRequest, routeInfo);
                } else {
                    responseMediaType = MediaType.APPLICATION_JSON_TYPE;
                }
            }

            if (messageBodyWriter == null) {
                // lookup write to use, any logic that hits this path should consider setting
                // a body writer on the response before writing
                messageBodyWriter = messageBodyHandlerRegistry
                    .findWriter(responseBodyType, Collections.singletonList(responseMediaType))
                    .orElse(null);
            }
            if (messageBodyWriter == null || !responseBodyType.isInstance(body) || !messageBodyWriter.isWriteable(responseBodyType, responseMediaType)) {
                responseBodyType = Argument.ofInstance(body);
                messageBodyWriter = messageBodyHandlerRegistry.getWriter(responseBodyType, List.of(responseMediaType));
            }
            return buildFinalResponse(nettyRequest, (MutableHttpResponse<Object>) response, responseBodyType, responseMediaType, body, messageBodyWriter, false);
        } else {
            response.body(null);

            return encodeNoBody(response);
        }
    }

    /**
     * Encode the given response without body, either because it has none or because this is a HEAD
     * response.
     *
     * @param response The response
     * @return The encoded response
     */
    protected ExecutionFlow<? extends ByteBodyHttpResponse<?>> encodeNoBody(HttpResponse<?> response) {
        if (response instanceof HttpResponseWrapper<?> wrapper) {
            return encodeNoBody(wrapper.getDelegate());
        }

        return ExecutionFlow.just(ByteBodyHttpResponseWrapper.wrap(response, byteBodyFactory.createEmpty()));
    }

    private ExecutionFlow<? extends ByteBodyHttpResponse<?>> mapToHttpContent(HttpRequest<?> request,
                                                                              MutableHttpResponse<?> response,
                                                                              Object body,
                                                                              RouteInfo<Object> routeInfo) {
        MediaType mediaType = response.getContentType().orElse(null);
        Flux<Object> bodyPublisher = Flux.from(Publishers.convertToPublisher(conversionService, body));
        Flux<ByteBody> httpContentPublisher;
        boolean isJson;
        if (routeInfo != null) {
            if (mediaType == null) {
                mediaType = routeExecutor.resolveDefaultResponseContentType(request, routeInfo);
            }
            isJson = mediaType != null &&
                mediaType.getExtension().equals(MediaType.EXTENSION_JSON) && routeInfo.isResponseBodyJsonFormattable();
            MediaType finalMediaType = mediaType;
            httpContentPublisher = bodyPublisher.concatMap(message -> {
                MessageBodyWriter<Object> messageBodyWriter = routeInfo.getMessageBodyWriter();
                @SuppressWarnings("unchecked")
                Argument<Object> responseBodyType = (Argument<Object>) routeInfo.getResponseBodyType();

                if (messageBodyWriter == null || !responseBodyType.isInstance(message) || !messageBodyWriter.isWriteable(responseBodyType, finalMediaType)) {
                    responseBodyType = Argument.ofInstance(message);
                    messageBodyWriter = wrap(messageBodyHandlerRegistry.getWriter(responseBodyType, List.of(finalMediaType)));
                }
                ExecutionFlow<CloseableByteBody> flow = writePieceAsync(
                    messageBodyWriter,
                    request,
                    response,
                    responseBodyType,
                    finalMediaType,
                    message);
                return ReactiveExecutionFlow.toPublisher(() -> flow);
            });
        } else {
            isJson = false;
            MediaType finalMediaType = mediaType;
            httpContentPublisher = bodyPublisher
                .concatMap(message -> {
                    Argument<Object> type = Argument.ofInstance(message);
                    MessageBodyWriter<Object> messageBodyWriter = messageBodyHandlerRegistry.getWriter(type, finalMediaType == null ? List.of() : List.of(finalMediaType));
                    ExecutionFlow<CloseableByteBody> flow = writePieceAsync(messageBodyWriter, request, response, type, finalMediaType, message);
                    return ReactiveExecutionFlow.toPublisher(() -> flow);
                });
        }

        httpContentPublisher = httpContentPublisher.doOnDiscard(CloseableByteBody.class, CloseableByteBody::close);

        return LazySendingSubscriber.create(httpContentPublisher).map(items -> {
            CloseableByteBody byteBody = isJson ? concatenateJson(items) : concatenate(items);
            return ByteBodyHttpResponseWrapper.wrap(response, byteBody);
        }).onErrorResume(t -> (ExecutionFlow) handleStreamingError(request, t));
    }

    /**
     * @see ConcatenatingSubscriber.ByteBufferConcatenatingSubscriber#concatenate
     * @param items The items
     * @return The concatenated body
     */
    protected @NonNull CloseableByteBody concatenate(@NonNull Publisher<ByteBody> items) {
        return ConcatenatingSubscriber.ByteBufferConcatenatingSubscriber.concatenate(items);
    }

    /**
     * @see ConcatenatingSubscriber.JsonByteBufferConcatenatingSubscriber#concatenateJson
     * @param items The items
     * @return The concatenated body
     */
    protected @NonNull CloseableByteBody concatenateJson(@NonNull Publisher<ByteBody> items) {
        return ConcatenatingSubscriber.JsonByteBufferConcatenatingSubscriber.concatenateJson(items);
    }

    /**
     * Handle an error that happened before the first item of a streaming response.
     *
     * @param request The request
     * @param t The error
     * @return The encoded error response
     */
    @NonNull
    protected final ExecutionFlow<? extends ByteBodyHttpResponse<?>> handleStreamingError(@NonNull HttpRequest<?> request, @NonNull Throwable t) {
        // limited error handling
        MutableHttpResponse<?> errorResponse;
        if (t instanceof HttpStatusException hse) {
            errorResponse = HttpResponse.status(hse.getStatus());
            if (hse.getBody().isPresent()) {
                errorResponse.body(hse.getBody().get());
            } else if (hse.getMessage() != null) {
                errorResponse.body(hse.getMessage());
            }
        } else {
            errorResponse = routeExecutor.createDefaultErrorResponse(request, t);
        }
        return encodeHttpResponse(
            request,
            errorResponse,
            errorResponse.body()
        );
    }

    private <T> ExecutionFlow<CloseableByteBody> writePieceAsync(
        @NonNull MessageBodyWriter<T> messageBodyWriter,
        @NonNull HttpRequest<?> request,
        @NonNull HttpResponse<?> response,
        @NonNull Argument<T> type,
        @NonNull MediaType mediaType,
        T object
    ) {
        if (messageBodyWriter.isBlocking()) {
            return ExecutionFlow.async(ioExecutor(), () -> ExecutionFlow.just(writePieceSync(messageBodyWriter, request, response, type, mediaType, object)));
        } else {
            return ExecutionFlow.just(writePieceSync(messageBodyWriter, request, response, type, mediaType, object));
        }
    }

    private <T> CloseableByteBody writePieceSync(@NonNull MessageBodyWriter<T> messageBodyWriter, @NonNull HttpRequest<?> request, @NonNull HttpResponse<?> response, @NonNull Argument<T> type, @NonNull MediaType mediaType, T object) {
        return wrap(messageBodyWriter).writePiece(byteBodyFactory, request, response, type, mediaType, object);
    }

    private <T> ExecutionFlow<ByteBodyHttpResponse<?>> buildFinalResponse(HttpRequest<?> nettyRequest,
                                                                          MutableHttpResponse<T> response,
                                                                          Argument<T> responseBodyType,
                                                                          MediaType mediaType,
                                                                          T body,
                                                                          MessageBodyWriter<T> messageBodyWriter,
                                                                          boolean onIoExecutor) {
        if (!onIoExecutor && messageBodyWriter.isBlocking()) {
            return ExecutionFlow.async(ioExecutor(), () -> buildFinalResponse(nettyRequest, response, responseBodyType, mediaType, body, messageBodyWriter, true));
        }

        try {
            return ExecutionFlow.just(wrap(messageBodyWriter)
                .write(byteBodyFactory, nettyRequest, response, responseBodyType, mediaType, body));
        } catch (CodecException e) {
            final MutableHttpResponse<Object> errorResponse = (MutableHttpResponse<Object>) routeExecutor.createDefaultErrorResponse(nettyRequest, e);
            Object errorBody = errorResponse.body();
            Argument<Object> type = Argument.ofInstance(errorBody);
            MediaType errorContentType = errorResponse.getContentType().orElse(MediaType.APPLICATION_JSON_TYPE);
            MessageBodyWriter<Object> errorBodyWriter = messageBodyHandlerRegistry.getWriter(type, List.of(errorContentType));
            if (!onIoExecutor && errorBodyWriter.isBlocking()) {
                return ExecutionFlow.async(ioExecutor(), () -> ExecutionFlow.just(wrap(errorBodyWriter)
                    .write(byteBodyFactory, nettyRequest, errorResponse, type, errorContentType, errorBody)));
            } else {
                return ExecutionFlow.just(wrap(errorBodyWriter)
                    .write(byteBodyFactory, nettyRequest, errorResponse, type, errorContentType, errorBody));
            }
        }
    }

}
