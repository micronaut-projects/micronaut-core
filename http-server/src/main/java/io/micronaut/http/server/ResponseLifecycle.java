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
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.async.subscriber.LazySendingSubscriber;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.ByteBodyHttpResponseWrapper;
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
import io.micronaut.json.JsonSyntaxException;
import io.micronaut.web.router.DefaultUrlRouteInfo;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteInfo;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

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
    protected abstract Executor ioExecutor();

    /**
     * Transform the given writer into a {@link ResponseBodyWriter}.
     *
     * @param messageBodyWriter The writer
     * @return The response writer
     * @param <T> The writer type
     */
    protected <T> ResponseBodyWriter<T> wrap(MessageBodyWriter<T> messageBodyWriter) {
        return ResponseBodyWriter.wrap(messageBodyWriter);
    }

    /**
     * Encode the response.
     *
     * @param httpRequest The request that triggered this response
     * @param response The unencoded response
     * @return The encoded response
     */
    public final ExecutionFlow<? extends ByteBodyHttpResponse<?>> encodeHttpResponseSafe(HttpRequest<?> httpRequest, HttpResponse<?> response) {
        try {
            return encodeHttpResponse(
                httpRequest,
                response
            );
        } catch (Throwable e) {
            try {
                response = routeExecutor.createDefaultErrorResponse(httpRequest, e);
                return encodeHttpResponse(
                    httpRequest,
                    response
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
        HttpResponse<?> httpResponse) {
        Object body = httpResponse.body();
        MutableHttpResponse<?> response = httpResponse.toMutableResponse();
        if (nettyRequest.getMethod() != HttpMethod.HEAD && body != null) {
            Object routeInfoO = RouteAttributes.getRouteInfo(response).orElse(null);
            // usually this is a UriRouteInfo, avoid scalability issues here
            @SuppressWarnings("unchecked") final RouteInfo<Object> routeInfo = (RouteInfo<Object>) (routeInfoO instanceof DefaultUrlRouteInfo<?, ?> uri ? uri : (RouteInfo<?>) routeInfoO);

            if (isImplicitlyEmptyBody(body)) {
                response.body(null);
                return encodeNoBody(response);
            }

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
                                                                              @Nullable RouteInfo<Object> routeInfo) {
        MediaType mediaType = response.getContentType().orElse(null);
        Flux<Object> bodyPublisher = Flux.from(Publishers.convertToPublisher(conversionService, body));
        Flux<ByteBody> httpContentPublisher;
        BooleanSupplier isJson;
        if (routeInfo != null) {
            if (mediaType == null) {
                mediaType = routeExecutor.resolveDefaultResponseContentType(request, routeInfo);
            }
            boolean isJsonRoute = mediaType.getExtension().equals(MediaType.EXTENSION_JSON) && routeInfo.isResponseBodyJsonFormattable();
            isJson = () -> isJsonRoute;
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
            MediaType finalMediaType = mediaType;
            // A single-value publisher (Mono, Single, Maybe, ...) is one document, not a stream of
            // elements, so it is never framed as an array. This is what the route path does too:
            // RouteExecutor unwraps single publishers before they get here.
            boolean single = Publishers.isSingle(body.getClass());
            boolean isJsonMediaType = !single && finalMediaType != null && MediaType.EXTENSION_JSON.equals(finalMediaType.getExtension());
            // There is no declared response body type here, so whether the items can be formatted
            // as a JSON array is derived from the type of the first item that is actually written,
            // and only the first: the flow below completes once that item has gone through the
            // writer, and the framing has to be settled by then. Later items of another type do not
            // change it, so a mixed stream comes out the same way regardless of timing.
            AtomicBoolean jsonFormattable = new AtomicBoolean(true);
            AtomicBoolean first = new AtomicBoolean(true);
            isJson = () -> isJsonMediaType && jsonFormattable.get();
            httpContentPublisher = bodyPublisher
                .concatMap(message -> {
                    Argument<Object> type = Argument.ofInstance(message);
                    if (isJsonMediaType && first.compareAndSet(true, false) && !isJsonFormattable(type)) {
                        jsonFormattable.set(false);
                    }
                    MessageBodyWriter<Object> messageBodyWriter = messageBodyHandlerRegistry.getWriter(type, finalMediaType == null ? List.of() : List.of(finalMediaType));
                    ExecutionFlow<CloseableByteBody> flow = writePieceAsync(messageBodyWriter, request, response, type, finalMediaType == null ? MediaType.ALL_TYPE : finalMediaType, message);
                    return ReactiveExecutionFlow.toPublisher(() -> flow);
                });
        }

        httpContentPublisher = httpContentPublisher.doOnDiscard(CloseableByteBody.class, CloseableByteBody::close);

        return LazySendingSubscriber.create(httpContentPublisher).map(items -> {
            CloseableByteBody byteBody = isJson.getAsBoolean() ? concatenateJson(items) : concatenate(items);
            return ByteBodyHttpResponseWrapper.wrap(response, byteBody);
        }).onErrorResume(t -> (ExecutionFlow) handleStreamingError(request, t));
    }

    /**
     * Whether items of the given type can be formatted as the elements of a JSON array. Mirrors
     * {@link RouteInfo#isResponseBodyJsonFormattable()} for responses that have no route.
     *
     * @param type The item type
     * @return {@code true} if the items may be joined into a JSON array
     */
    private static boolean isJsonFormattable(Argument<?> type) {
        // it would be nice to support netty ByteBuf here, but it's not clear how.
        // A ByteBody is raw bytes too: the writer passes it through unchanged, so framing it
        // would splice brackets and commas into a byte stream.
        return !(type.getType() == byte[].class
            || ByteBuffer.class.isAssignableFrom(type.getType())
            || ByteBody.class.isAssignableFrom(type.getType()));
    }

    /**
     * @see ConcatenatingSubscriber#concatenate
     * @param items The items
     * @return The concatenated body
     */
    protected CloseableByteBody concatenate(Publisher<ByteBody> items) {
        return ConcatenatingSubscriber.concatenate(byteBodyFactory, items, ConcatenatingSubscriber.Separators.NONE);
    }

    /**
     * @see ConcatenatingSubscriber#concatenate
     * @param items The items
     * @return The concatenated body
     */
    protected CloseableByteBody concatenateJson(Publisher<ByteBody> items) {
        return ConcatenatingSubscriber.concatenate(byteBodyFactory, items, ConcatenatingSubscriber.Separators.JDK_JSON);
    }

    /**
     * Handle an error that happened before the first item of a streaming response.
     *
     * @param request The request
     * @param t The error
     * @return The encoded error response
     */
    protected final ExecutionFlow<? extends ByteBodyHttpResponse<?>> handleStreamingError(HttpRequest<?> request, Throwable t) {
        // limited error handling
        MutableHttpResponse<?> errorResponse;
        if (t instanceof ConversionErrorException cee && cee.getCause() instanceof JsonSyntaxException jse) {
            // with delayed parsing, json syntax errors show up as conversion errors
            t = jse;
        }
        if (t instanceof JsonSyntaxException) {
            // a syntax error in a streamed request body is the client's fault, not the server's,
            // the same way RequestLifecycle answers it for a fully buffered body
            errorResponse = HttpResponse.badRequest().body(t.getMessage());
        } else if (t instanceof HttpStatusException hse) {
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
            errorResponse
        );
    }

    private <T> ExecutionFlow<CloseableByteBody> writePieceAsync(MessageBodyWriter<T> messageBodyWriter,
                                                                 HttpRequest<?> request,
                                                                 HttpResponse<?> response,
                                                                 Argument<T> type,
                                                                 MediaType mediaType,
                                                                 T object) {
        if (messageBodyWriter.isBlocking()) {
            return ExecutionFlow.async(ioExecutor(), () -> ExecutionFlow.just(writePieceSync(messageBodyWriter, request, response, type, mediaType, object)));
        } else {
            return ExecutionFlow.just(writePieceSync(messageBodyWriter, request, response, type, mediaType, object));
        }
    }

    private <T> CloseableByteBody writePieceSync(MessageBodyWriter<T> messageBodyWriter,
                                                 HttpRequest<?> request,
                                                 HttpResponse<?> response,
                                                 Argument<T> type,
                                                 MediaType mediaType,
                                                 T object) {
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
            Object errorBody = Objects.requireNonNull(errorResponse.getBody().orElse(null));
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

    private static boolean isImplicitlyEmptyBody(Object body) {
        return body instanceof byte[] bytes && bytes.length == 0;
    }

}
