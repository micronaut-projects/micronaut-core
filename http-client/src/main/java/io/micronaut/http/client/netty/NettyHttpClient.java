/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.client.netty;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.buffer.netty.NettyReadBufferFactory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.beans.BeanMap;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ObjectUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.functional.ThrowingFunction;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableByteBodyHttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.bind.DefaultRequestBinderRegistry;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.body.AvailableByteBody;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CharSequenceBodyWriter;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.ContextlessMessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.stream.BodyElementsPublisher;
import io.micronaut.http.body.stream.BodyPublishers;
import io.micronaut.http.body.WritableBodyWriter;
import io.micronaut.http.client.AbstractHttpClient;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.ProxyHttpClient;
import io.micronaut.http.client.ProxyRequestOptions;
import io.micronaut.http.client.AsyncRawHttpClient;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawHttpClientSupport;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.client.exceptions.HttpClientErrorDecoder;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientExceptionUtils;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.exceptions.NoHostException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.client.exceptions.ResponseClosedException;
import io.micronaut.http.client.exceptions.StreamResetException;
import io.micronaut.http.client.exceptions.UnprocessedRequestException;
import io.micronaut.http.client.filter.ClientFilterResolutionContext;
import io.micronaut.http.client.loadbalance.LoadBalancerSelection;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.client.multipart.MultipartDataFactory;
import io.micronaut.http.client.netty.websocket.NettyWebSocketClientHandler;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.filter.HttpClientFilterResolver;
import io.micronaut.http.multipart.MultipartException;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.micronaut.http.netty.NettyHttpRequestBuilder;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.netty.body.NettyByteBufMessageBodyHandler;
import io.micronaut.http.netty.body.NettyJsonHandler;
import io.micronaut.http.netty.body.NettyJsonStreamHandler;
import io.micronaut.http.netty.stream.JsonSubscriber;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.http.uri.UriTemplate;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.codec.JsonMediaTypeCodec;
import io.micronaut.json.codec.JsonStreamMediaTypeCodec;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.websocket.WebSocketClient;
import io.micronaut.websocket.annotation.ClientWebSocket;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.context.WebSocketBean;
import io.micronaut.websocket.context.WebSocketBeanRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.util.AsciiString;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.FastThreadLocalThread;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default implementation of the {@link HttpClient} interface based on Netty.
 *
 * @since 5.0
 */
@SuppressWarnings("FileLength")
@Internal
final class NettyHttpClient extends AbstractHttpClient<NettyClientByteBodyResponse> implements
    WebSocketClient,
    HttpClient,
    StreamingHttpClient,
    SseClient,
    ProxyHttpClient,
    RawHttpClient,
    Closeable,
    AutoCloseable {

    /**
     * Request attribute of a request that may switch the connection to another protocol, see
     * {@link RawRequestOptions#isAllowUpgrade()}.
     */
    static final String ALLOW_UPGRADE = "micronaut.http.client.raw.allow-upgrade";

    /**
     * Request attribute with the {@link RawRequestOptions#getActivityTimeout() activity timeout}
     * of an upgraded connection.
     */
    static final String ACTIVITY_TIMEOUT = "micronaut.http.client.raw.activity-timeout";

    /**
     * Request attribute with the {@link UploadListener} of the request body.
     */
    static final String UPLOAD_LISTENER = "micronaut.http.client.raw.upload-listener";

    /**
     * Default logger, use {@link #log} where possible.
     */
    private static final Logger DEFAULT_LOG = LoggerFactory.getLogger(NettyHttpClient.class);
    /**
     * Set on a connection once a request was sent on it, to tell reused connections from new ones.
     */
    private static final AttributeKey<Boolean> REQUEST_SENT = AttributeKey.valueOf(NettyHttpClient.class, "requestSent");
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;

    private final ByteBufferFactory<ByteBufAllocator, ByteBuf> byteBufferFactory = new NettyByteBufferFactory();

    private ConnectionManager connectionManager;

    private final WebSocketBeanRegistry webSocketRegistry;
    private final RequestBinderRegistry requestBinderRegistry;
    @Nullable
    private final ExecutorService blockingExecutor;
    @Nullable
    private final LifecycleListener lifecycleListener;

    NettyHttpClient(NettyHttpClientBuilder builder) {
        super(
            configuration(builder),
            DEFAULT_LOG,
            builder.contextPath,
            builder.loadBalancer,
            builder.codecRegistry == null ? createDefaultMediaTypeRegistry() : builder.codecRegistry,
            builder.handlerRegistry == null ? createDefaultMessageBodyHandlerRegistry() : builder.handlerRegistry,
            filterResolver(builder),
            builder.clientFilterEntries,
            builder.conversionService,
            builder.informationalServiceId
        );
        this.webSocketRegistry = builder.webSocketBeanRegistry;
        this.requestBinderRegistry = builder.requestBinderRegistry == null ? new DefaultRequestBinderRegistry(conversionService) : builder.requestBinderRegistry;
        this.blockingExecutor = builder.blockingExecutor;
        this.lifecycleListener = builder.lifecycleListener;

        this.connectionManager = new ConnectionManager(log, configuration, builder);
    }

    private static HttpClientConfiguration configuration(NettyHttpClientBuilder builder) {
        return builder.configuration == null ? new DefaultHttpClientConfiguration() : builder.configuration;
    }

    private static HttpClientFilterResolver<ClientFilterResolutionContext> filterResolver(NettyHttpClientBuilder builder) {
        if (builder.filterResolver == null) {
            builder.filters();
        }
        return Objects.requireNonNull(builder.filterResolver);
    }

    @Override
    protected ScheduledExecutorService scheduler() {
        return connectionManager.getGroup();
    }

    @Override
    protected ByteBufferFactory<?, ?> byteBufferFactory() {
        return byteBufferFactory;
    }

    /**
     * The pieces of the body of {@code dataStream} and {@code exchangeStream}, read as the Netty
     * client always read them: Netty buffers, as they arrived, with the bytes that wait for the
     * subscriber limited by {@code max-content-length}.
     *
     * @param body  The body, which the pieces take over
     * @param lines Whether the body is split into the lines of an event stream
     * @return The pieces of the body
     */
    @Override
    protected BodyElements<ByteBuffer<?>> streamPieces(CloseableByteBody body, boolean lines) {
        return new StreamedBodyPieces(body, lines, sizeLimits().maxBufferSize());
    }

    /**
     * The pieces of the body of {@code dataStream} and {@code exchangeStream}: Netty buffers,
     * released after {@code onNext} unless the subscriber retained them.
     *
     * @param pieces The pieces
     * @return The publisher of the pieces
     */
    @Override
    protected Publisher<ByteBuffer<?>> streamPiecesPublisher(BodyElements<ByteBuffer<?>> pieces) {
        if (pieces instanceof StreamedBodyPieces streamed) {
            return streamed.publisher();
        }
        // e.g. the empty body of a response without one
        return new BodyElementsPublisher<>(pieces);
    }

    @Override
    protected boolean isSameOrigin(URI first, URI second) {
        return new RequestKey(this, first).equals(new RequestKey(this, second));
    }

    @Override
    protected @Nullable HttpClientException mapReadFailure(Throwable cause) {
        if (cause instanceof io.netty.handler.timeout.ReadTimeoutException) {
            // a Netty read timeout fires before the response headers arrive
            return decorate(new ReadTimeoutException(false));
        }
        return null;
    }

    static NettyHttpClientBuilder newBuilder() {
        return new NettyHttpClientBuilder();
    }

    @Override
    public BlockingHttpClient toBlocking() {
        return new BlockingHttpClient() {

            @Override
            public void close() {
                NettyHttpClient.this.close();
            }

            @Override
            public <I, O, E> HttpResponse<O> exchange(io.micronaut.http.HttpRequest<I> request, @Nullable Argument<O> bodyType, Argument<E> errorType) {
                if (!configuration.isAllowBlockEventLoop() && Thread.currentThread() instanceof FastThreadLocalThread) {
                    throw new HttpClientException("""
                        You are trying to run a BlockingHttpClient operation on a netty event \
                        loop thread. This is a common cause for bugs: Event loops should \
                        never be blocked. You can either mark your controller as \
                        @ExecuteOn(TaskExecutors.BLOCKING), or use the reactive HTTP client \
                        to resolve this bug. There is also a configuration option to \
                        disable this check if you are certain a blocking operation is fine \
                        here.""");
                }
                if (Schedulers.isInNonBlockingThread()) {
                    // same check (and message) as reactor's block(), which this client used before
                    throw new IllegalStateException("block()/blockFirst()/blockLast() are blocking, which is not supported in thread " + Thread.currentThread().getName());
                }
                try {
                    return Objects.requireNonNull(awaitFlow(exchangeFlow(request, bodyType, errorType, Thread.currentThread())),
                        "The blocking HTTP client returned a null response");
                    // We don't have to release client response buffer
                } catch (HttpClientException e) {
                    if (configuration.isBlockingCallerStackTrace()) {
                        throw customizeBlockingException(e);
                    }
                    throw e;
                }
            }

            @Override
            public <I, O, E> O retrieve(io.micronaut.http.HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType) {
                // mostly copied from super method, but with customizeException

                HttpResponse<O> response = exchange(request, bodyType, errorType);
                if (HttpStatus.class.isAssignableFrom(bodyType.getType())) {
                    return (O) response.getStatus();
                } else {
                    Optional<O> body = response.getBody();
                    if (body.isEmpty() && response.getBody(Argument.of(byte[].class)).isPresent()) {
                        throw decorate(new HttpClientResponseException(
                            "Failed to decode the body for the given content type [%s]".formatted(response.getContentType().orElse(null)),
                            response
                        ));
                    } else {
                        return body.orElseThrow(() -> decorate(new HttpClientResponseException(
                            "Empty body",
                            response
                        )));
                    }
                }
            }

            @Override
            public boolean isRunning() {
                return NettyHttpClient.this.isRunning();
            }

            @Override
            public BlockingHttpClient start() {
                return NettyHttpClient.this.start().toBlocking();
            }

            @Override
            public BlockingHttpClient stop() {
                return NettyHttpClient.this.stop().toBlocking();
            }
        };
    }

    /**
     * Rewrite the stack trace of an exception thrown from a blocking client call so that it points
     * to the code that made the call rather than to the Netty event loop (or the request-timeout
     * scheduler) where the exception was actually constructed. Without this, exceptions such as
     * {@link ReadTimeoutException} carry a stack trace that does not mention the caller at all.
     *
     * <p>This method is only ever called on the thread that performed the blocking call, right
     * after the blocking await (see {@link #awaitFlow(ExecutionFlow)}) has unwound, so the current
     * thread's stack trace is exactly the caller chain. The original execution stack trace is
     * preserved as a suppressed exception for debugging.
     *
     * @param exception the exception thrown from the blocking call
     * @param <E>        the exception type
     * @return the same exception, with its stack trace pointing at the caller
     * @see <a href="https://github.com/micronaut-projects/micronaut-core/issues/12655">gh-12655</a>
     */
    private static <E extends HttpClientException> E customizeBlockingException(E exception) {
        StackTraceElement[] origin = exception.getStackTrace();
        if (origin.length > 0) {
            BlockingClientExecutionTrace originTrace = new BlockingClientExecutionTrace();
            originTrace.setStackTrace(origin);
            exception.addSuppressed(originTrace);
        }
        // We are back on the thread that made the blocking call; its stack points to the caller.
        // Drop Thread.getStackTrace() (index 0) and this method's frame (index 1).
        StackTraceElement[] caller = Thread.currentThread().getStackTrace();
        if (caller.length > 2) {
            exception.setStackTrace(Arrays.copyOfRange(caller, 2, caller.length));
        }
        return exception;
    }

    /**
     * Access to the connection manager, for micronaut-oracle-cloud.
     *
     * @return The connection manager of this client
     */
    public ConnectionManager connectionManager() {
        return connectionManager;
    }

    /**
     * @return The connection manager in use.
     */
    public ConnectionManager getConnectionManager() {
        return connectionManager();
    }

    void setConnectionManager(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public HttpClient start() {
        if (!isRunning()) {
            connectionManager.start();
        }
        if (lifecycleListener != null) {
            lifecycleListener.onStart(this);
        }
        return this;
    }

    @Override
    public boolean isRunning() {
        return connectionManager.isRunning();
    }

    @Override
    public HttpClient stop() {
        if (isRunning()) {
            connectionManager.shutdown();
        }
        if (lifecycleListener != null) {
            lifecycleListener.onStop(this);
        }
        return this;
    }

    @Override
    public HttpClient refresh() {
        if (isRunning()) {
            connectionManager.refresh();
        } else {
            start();
            connectionManager.refresh();
        }
        return this;
    }

    @Override
    protected <O, E> ExecutionFlow<FullNettyClientHttpResponse<O>> fullResponse(@Nullable Argument<O> bodyType, Argument<E> errorType, NettyClientByteBodyResponse resp, CloseableAvailableByteBody av) {
        ByteBuf buf = NettyByteBodyFactory.toByteBuf(av);
        FullHttpResponse fullHttpResponse;
        try {
            if (log.isTraceEnabled()) {
                traceBody("Response", buf);
            }
            // copy the pooled body exactly once; every response object created below (including
            // the error paths) shares this copy, and the pooled buffer can be released right away
            fullHttpResponse = FullNettyClientHttpResponse.detach(resp.nettyResponse, buf);
        } finally {
            buf.release();
        }

        try {
            boolean convertBodyWithBodyType = shouldConvertWithBodyType(fullHttpResponse, this.configuration, bodyType, errorType);
            FullNettyClientHttpResponse<O> response = new FullNettyClientHttpResponse<>(fullHttpResponse, handlerRegistry, bodyType, convertBodyWithBodyType, conversionService);

            if (convertBodyWithBodyType) {
                return ExecutionFlow.just(response);
            } else { // error flow
                try {
                    return ExecutionFlow.error(makeErrorFromRequestBody(errorType, fullHttpResponse.status(), response));
                } catch (HttpClientResponseException t) {
                    return ExecutionFlow.error(t);
                } catch (Exception t) {
                    return ExecutionFlow.error(makeErrorBodyParseError(fullHttpResponse, t));
                }
            }
        } catch (HttpClientResponseException t) {
            return ExecutionFlow.error(t);
        } catch (Exception t) {
            FullNettyClientHttpResponse<Object> response = new FullNettyClientHttpResponse<>(
                fullHttpResponse,
                handlerRegistry,
                null,
                false,
                conversionService
            );
            HttpClientResponseException clientResponseError = decorate(new HttpClientResponseException(
                "Error decoding HTTP response body: " + t.getMessage(),
                t,
                response,
                new HttpClientErrorDecoder() {
                    @Override
                    public Argument<?> getErrorType(MediaType mediaType) {
                        return errorType;
                    }
                }
            ));
            return ExecutionFlow.error(clientResponseError);
        }
    }

    @Override
    public <T extends AutoCloseable> Publisher<T> connect(Class<T> clientEndpointType, MutableHttpRequest<?> request) {
        setupConversionService(request);
        return toMono(resolveRequestURI(request), PropagatedContext.getOrEmpty()).flux()
            .switchMap(target -> connectWebSocket(target.uri(), request, clientEndpointType, null));
    }

    @Override
    public <T extends AutoCloseable> Publisher<T> connect(Class<T> clientEndpointType, Map<String, Object> parameters) {
        WebSocketBean<T> webSocketBean = webSocketRegistry.getWebSocket(clientEndpointType);
        String uri = webSocketBean.getBeanDefinition().stringValue(ClientWebSocket.class).orElse("/ws");
        uri = UriTemplate.of(uri).expand(parameters);
        MutableHttpRequest<Object> request = io.micronaut.http.HttpRequest.GET(uri);
        return toMono(resolveRequestURI(request), PropagatedContext.getOrEmpty()).flux()
            .switchMap(target -> connectWebSocket(target.uri(), request, clientEndpointType, webSocketBean));

    }

    @Override
    public void close() {
        stop();
    }

    private <T> Publisher<T> connectWebSocket(URI uri, MutableHttpRequest<?> request, Class<T> clientEndpointType, @Nullable WebSocketBean<T> webSocketBean) {
        RequestKey requestKey;
        try {
            requestKey = new RequestKey(this, uri);
        } catch (HttpClientException e) {
            return Flux.error(e);
        }

        if (webSocketBean == null) {
            webSocketBean = webSocketRegistry.getWebSocket(clientEndpointType);
        }

        WebSocketVersion protocolVersion = webSocketBean.getBeanDefinition().enumValue(ClientWebSocket.class, "version", WebSocketVersion.class).orElse(WebSocketVersion.V13);
        int maxFramePayloadLength = webSocketBean.messageMethod()
            .map(m -> m.intValue(OnMessage.class, "maxPayloadLength")
                .orElse(65536)).orElse(65536);
        String subprotocol = webSocketBean.getBeanDefinition().stringValue(ClientWebSocket.class, "subprotocol").orElse(StringUtils.EMPTY_STRING);
        URI webSocketURL = UriBuilder.of(uri)
            .scheme(!requestKey.isSecure() ? "ws" : "wss")
            .host(requestKey.getHost())
            .port(requestKey.getPort())
            .build();

        MutableHttpHeaders headers = request.getHeaders();
        HttpHeaders customHeaders = EmptyHttpHeaders.INSTANCE;
        if (headers instanceof NettyHttpHeaders httpHeaders) {
            customHeaders = httpHeaders.getNettyHeaders();
        }
        if (StringUtils.isNotEmpty(subprotocol)) {
            NettyHttpHeaders.validateHeader("Sec-WebSocket-Protocol", subprotocol);
            customHeaders.add("Sec-WebSocket-Protocol", subprotocol);
        }

        NettyWebSocketClientHandler<T> handler = new NettyWebSocketClientHandler<>(
            request,
            webSocketBean,
            WebSocketClientHandshakerFactory.newHandshaker(
                webSocketURL, protocolVersion, subprotocol, true, customHeaders, maxFramePayloadLength),
            requestBinderRegistry,
            mediaTypeCodecRegistry,
            handlerRegistry,
            conversionService);

        if (!isRunning()) {
            return Mono.error(decorate(new HttpClientException("The client is closed, unable to connect for websocket.")));
        }

        return connectionManager.connectForWebsocket(requestKey, handler)
            .then(handler.getHandshakeCompletedMono());
    }

    @Override
    public Publisher<MutableHttpResponse<?>> proxy(io.micronaut.http.HttpRequest<?> request) {
        return proxy(request, ProxyRequestOptions.getDefault());
    }

    @Override
    public Publisher<MutableHttpResponse<?>> proxy(io.micronaut.http.HttpRequest<?> request, ProxyRequestOptions options) {
        Objects.requireNonNull(options, "options");
        setupConversionService(request);
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        return Mono.defer(() -> {
            // the body bytes of a server request are claimed when the exchange starts, and
            // released when it ends, unless they were sent: e.g. when the upstream refuses the
            // connection, a streaming server request can then discard the rest of its body
            MutableHttpRequest<?> httpRequest = toProxyRequest(request);
            Mono<MutableHttpResponse<?>> response = proxy(propagatedContext, request, httpRequest, options);
            return httpRequest instanceof RawHttpRequestWrapper<?> claimed ? response.doFinally(signal -> claimed.close()) : response;
        });
    }

    private Mono<MutableHttpResponse<?>> proxy(PropagatedContext propagatedContext, io.micronaut.http.HttpRequest<?> request, MutableHttpRequest<?> httpRequest, ProxyRequestOptions options) {
        return toMono(resolveRequestURI(request)
            .flatMap(target -> {
                if (!options.isRetainHostHeader()) {
                    httpRequest.headers(headers -> headers.remove(HttpHeaderNames.HOST));
                }

                return this.sendRequestWithRedirects(
                    propagatedContext,
                    null,
                    httpRequest.uri(target.uri()),
                    target.selection(),
                    (req, resp) -> {
                        if (!hasBody(resp)) {
                            resp.close();
                            return ExecutionFlow.just(MutableByteBodyHttpResponse.of(resp, NettyByteBodyFactory.empty()));
                        }
                        // relayed without a length, like the content publisher proxied responses used to be
                        return ExecutionFlow.just(MutableByteBodyHttpResponse.of(resp, new UnknownLengthByteBody(resp.byteBody().move())));
                    }
                );
            })
            .map(HttpResponse::toMutableResponse), propagatedContext);
    }

    /**
     * The request to send for {@link #proxy}. The body bytes of a server request are relayed as
     * they are, whichever server received it.
     *
     * @param request The request to proxy
     * @return The request to send
     */
    private MutableHttpRequest<?> toProxyRequest(io.micronaut.http.HttpRequest<?> request) {
        MutableHttpRequest<?> mutableRequest = toMutableRequest(request);
        CloseableByteBody serverBody = RawHttpClientSupport.claimServerRequestBody(request);
        if (serverBody != null) {
            return new RawHttpRequestWrapper<>(conversionService, mutableRequest, serverBody);
        }
        return mutableRequest;
    }

    /**
     * @param request             The request
     * @param nettyRequestBuilder The netty builder of the request, from
     *                            {@link NettyHttpRequestBuilder#asBuilder}
     * @param requestKey          The key (host, port) of the request
     * @param requestContentType  The request content type
     * @param permitsBody         Whether permits body
     * @param channel             The channel
     * @param outgoingHeaders     The headers of the outgoing netty request, a copy of the headers
     *                            of the caller's request that the client adds its generated
     *                            headers to, so that the caller's request is not modified
     * @return The body
     * @throws HttpPostRequestEncoder.ErrorDataEncoderException if there is an encoder exception
     */
    private CloseableByteBody buildNettyRequest(
        MutableHttpRequest<?> request,
        NettyHttpRequestBuilder nettyRequestBuilder,
        RequestKey requestKey,
        MediaType requestContentType,
        boolean permitsBody,
        Channel channel,
        HttpHeaders outgoingHeaders) throws HttpPostRequestEncoder.ErrorDataEncoderException {

        NettyByteBodyFactory byteBodyFactory = new NettyByteBodyFactory(channel);
        if (!outgoingHeaders.contains(HttpHeaderNames.HOST)) {
            outgoingHeaders.set(HttpHeaderNames.HOST, getHostHeader(requestKey));
        }

        if (permitsBody) {
            Optional<?> body = request.getBody();
            if (body.isPresent()) {
                if (!outgoingHeaders.contains(HttpHeaderNames.CONTENT_TYPE)) {
                    MediaType mediaType = request.getContentType().orElse(MediaType.APPLICATION_JSON_TYPE);
                    outgoingHeaders.set(HttpHeaderNames.CONTENT_TYPE, mediaType);
                }
            }
        }

        ByteBody direct = nettyRequestBuilder.byteBodyDirect();
        if (direct != null) {
            return direct.move();
        }

        if (permitsBody) {
            Optional<?> body = request.getBody();
            boolean hasBody = body.isPresent();
            if (requestContentType.equals(MediaType.APPLICATION_FORM_URLENCODED_TYPE) && hasBody && !isEncodedFormBody(body.get())) {
                Object bodyValue = body.get();
                return buildFormRequest(outgoingHeaders, byteBodyFactory, r -> buildFormDataRequest(r, bodyValue));
            } else if (requestContentType.equals(MediaType.MULTIPART_FORM_DATA_TYPE) && hasBody && !isEncodedFormBody(body.get())) {
                return buildFormRequest(outgoingHeaders, byteBodyFactory, r -> buildMultipartRequest(r, body.get()));
            } else {
                ReadBuffer bodyContent;
                if (hasBody) {
                    Object bodyValue = body.get();
                    if (Publishers.isConvertibleToPublisher(bodyValue)) {
                        boolean isSingle = Publishers.isSingle(bodyValue.getClass());

                        Publisher<?> publisher = conversionService.convert(bodyValue, Publisher.class).orElseThrow(() ->
                            new IllegalArgumentException("Unconvertible reactive type: " + bodyValue)
                        );

                        Publisher<HttpContent> requestBodyPublisher = BodyPublishers.map(publisher, value -> {
                            Argument<Object> type = Argument.ofInstance(value);
                            ByteBuffer<?> buffer = handlerRegistry.getWriter(type, List.of(requestContentType))
                                .writeTo(type, requestContentType, value, request.getHeaders(), byteBufferFactory);
                            return new DefaultHttpContent(((ByteBuf) buffer.asNativeBuffer()));
                        });

                        if (!isSingle && MediaType.APPLICATION_JSON_TYPE.equals(requestContentType)) {
                            requestBodyPublisher = JsonSubscriber.lift(requestBodyPublisher);
                        }

                        return byteBodyFactory.adapt(BodyPublishers.map(requestBodyPublisher, ByteBufHolder::content), outgoingHeaders, null);
                    } else if (bodyValue instanceof CharSequence sequence) {
                        bodyContent = charSequenceToByteBuf(sequence, requestContentType);
                    } else {
                        Argument<Object> type = Argument.ofInstance(bodyValue);
                        ByteBuffer<?> buffer = handlerRegistry.getWriter(type, List.of(requestContentType))
                            .writeTo(type, requestContentType, bodyValue, new NettyHttpHeaders(outgoingHeaders, conversionService), byteBufferFactory);
                        bodyContent = byteBodyFactory.readBufferFactory().adapt(buffer);
                    }
                } else {
                    bodyContent = byteBodyFactory.readBufferFactory().createEmpty();
                }
                return byteBodyFactory.adapt(bodyContent);
            }
        } else {
            return NettyByteBodyFactory.empty();
        }
    }

    @Override
    public Publisher<? extends HttpResponse<?>> exchange(io.micronaut.http.HttpRequest<?> request, @Nullable CloseableByteBody requestBody, @Nullable Thread blockedThread) {
        return rawExchange(request, requestBody, blockedThread, null);
    }

    @Override
    public Publisher<? extends HttpResponse<?>> exchange(io.micronaut.http.HttpRequest<?> request, @Nullable CloseableByteBody requestBody, @Nullable Thread blockedThread, RawRequestOptions options) {
        Objects.requireNonNull(options, "options");
        return rawExchange(request, requestBody, blockedThread, options);
    }

    @Override
    public AsyncRawHttpClient toAsyncRaw() {
        return new NettyAsyncRawHttpClient(this);
    }

    private Mono<HttpResponse<?>> rawExchange(io.micronaut.http.HttpRequest<?> request, @Nullable CloseableByteBody requestBody, @Nullable Thread blockedThread, @Nullable RawRequestOptions options) {
        CloseableByteBody body = requestBody == null ? NettyByteBodyFactory.empty() : requestBody;
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        ExecutionFlow<HttpResponse<?>> flow = rawExchangeFlow(propagatedContext, request, body, blockedThread, options);
        // doFinally: a cancelled exchange closes the body too, e.g. one that waits for a connection
        return toMono(flow, propagatedContext).doFinally(signal -> body.close());
    }

    /**
     * The flow of a raw exchange. Cancelling it before the response arrives aborts the request.
     * The caller closes the request body when the flow completes or is cancelled.
     *
     * @param propagatedContext The propagated context
     * @param request           The request metadata
     * @param requestBody       The request body
     * @param blockedThread     The thread that blocks on the response, if any
     * @param options           The per-exchange options, or {@code null} for none
     * @return The response flow
     */
    ExecutionFlow<HttpResponse<?>> rawExchangeFlow(PropagatedContext propagatedContext, io.micronaut.http.HttpRequest<?> request, CloseableByteBody requestBody, @Nullable Thread blockedThread, @Nullable RawRequestOptions options) {
        try {
            if (options == null) {
                RawHttpRequestWrapper<?> rawRequest = new RawHttpRequestWrapper<>(conversionService, request.toMutableRequest(), requestBody);
                return rawRequest.keepReplacedBody(sendRawExchange(propagatedContext, blockedThread, rawRequest));
            }
            RawHttpRequestWrapper<Object> rawRequest = new RawHttpRequestWrapper<>(conversionService, RawHttpClientSupport.copyRequest(request, options), requestBody);
            applyOptions(rawRequest, options);
            // the response timeout does not count the upload of the body: a slow upload does not time out
            CompletableFuture<@Nullable Void> uploadStarted = new CompletableFuture<>();
            CompletableFuture<@Nullable Void> uploaded = new CompletableFuture<>();
            if (options.getResponseTimeout() != null) {
                rawRequest.setAttribute(UPLOAD_LISTENER, new UploadListener(() -> uploadStarted.complete(null), () -> uploaded.complete(null)));
            }
            return RawHttpClientSupport.withResponseTimeout(rawRequest.keepReplacedBody(sendRawExchange(
                propagatedContext,
                blockedThread,
                rawRequest
            )), options.getResponseTimeout(), uploadStarted, uploaded, connectionManager.getGroup()).map(RawHttpClientSupport::toMutableResponse);
        } catch (RuntimeException | Error e) {
            requestBody.close();
            throw e;
        }
    }

    /**
     * Send a raw request. A relative request URI is resolved against the URL of this client
     * first, like the URI of any other request.
     *
     * @param propagatedContext The propagated context
     * @param blockedThread     The thread that blocks on the response, if any
     * @param rawRequest        The raw request
     * @return The response flow
     */
    private ExecutionFlow<HttpResponse<?>> sendRawExchange(PropagatedContext propagatedContext, @Nullable Thread blockedThread, MutableHttpRequest<?> rawRequest) {
        if (rawRequest.getUri().getScheme() != null) {
            return sendRequestWithRedirects(propagatedContext, blockedThread, rawRequest, null, (req, resp) -> ExecutionFlow.just(resp));
        }
        return resolveRequestURI(rawRequest).flatMap(target -> sendRequestWithRedirects(
            propagatedContext,
            blockedThread,
            rawRequest.uri(target.uri()),
            target.selection(),
            (req, resp) -> ExecutionFlow.just(resp)
        ));
    }

    private static void applyOptions(MutableHttpRequest<?> request, RawRequestOptions options) {
        if (options.isAllowUpgrade()) {
            request.setAttribute(ALLOW_UPGRADE, Boolean.TRUE);
        }
        if (options.getActivityTimeout() != null) {
            request.setAttribute(ACTIVITY_TIMEOUT, options.getActivityTimeout());
        }
        if (!options.isFollowRedirects()) {
            request.setAttribute(NO_FOLLOW_REDIRECTS, Boolean.TRUE);
        }
        if (!options.isDecompress()) {
            request.setAttribute(NO_DECOMPRESSION, Boolean.TRUE);
        }
        if (options.getReadIdleTimeout() != null) {
            request.setAttribute(READ_IDLE_TIMEOUT, options.getReadIdleTimeout());
        }
    }

    @Override
    protected ExecutionFlow<NettyClientByteBodyResponse> send(
        PropagatedContext propagatedContext,
        AtomicReference<ScheduledExecutorService> preferredScheduler,
        @Nullable Thread blockedThread,
        MutableHttpRequest<?> request,
        @Nullable LoadBalancerSelection selection
    ) {
        BlockHint blockHint = blockedThread == null ? null : new BlockHint(blockedThread, null);
        RequestKey requestKey;
        try {
            requestKey = new RequestKey(this, request.getUri());
        } catch (Exception e) {
            return ExecutionFlow.error(e);
        }

        if (!isRunning()) {
            return ExecutionFlow.error(decorate(new HttpClientException("The client is closed, unable to send request.")));
        }

        // first: connect
        return connectionManager.connect(requestKey, blockHint, preferredScheduler)
            .onErrorResume(e -> ExecutionFlow.error(failedBeforeSending(connectFailure(e), request, selection)))
            .flatMap(poolHandle -> {
                poolHandle.touch();
                preferredScheduler.set(poolHandle.channel.eventLoop());

                // build the raw request
                request.setAttribute(NettyClientHttpRequest.CHANNEL, poolHandle.channel);

                boolean permitsBody = io.micronaut.http.HttpMethod.permitsRequestBody(request.getMethod());
                NettyHttpRequestBuilder nettyRequestBuilder = NettyHttpRequestBuilder.asBuilder(request);
                CloseableByteBody byteBody;
                HttpRequest nettyRequest;
                try {
                    // the client adds its generated headers (Host, Content-Length...) to the
                    // headers of this request only, never to the caller's request
                    nettyRequest = toOutgoingNettyRequest(request, nettyRequestBuilder);
                    byteBody = buildNettyRequest(
                        request,
                        nettyRequestBuilder,
                        requestKey,
                        request
                            .getContentType()
                            .orElse(MediaType.APPLICATION_JSON_TYPE),
                        permitsBody,
                        poolHandle.channel,
                        nettyRequest.headers()
                    );
                } catch (Exception e) {
                    // nothing was written yet, so the connection is still usable: return it to
                    // the pool instead of leaving it marked as busy forever. Like a release after
                    // a response, this must happen on the event loop of the connection.
                    if (poolHandle.channel.eventLoop().inEventLoop()) {
                        poolHandle.release();
                    } else {
                        poolHandle.channel.eventLoop().execute(poolHandle::release);
                    }
                    return ExecutionFlow.error(e);
                }

                // send the raw request
                return sendRawRequestAllowingRetry(poolHandle, request, selection, byteBody, nettyRequest, blockHint, preferredScheduler);
            });
    }

    /**
     * Create the netty request to send for the given request. Its headers are a copy of the
     * headers of the given request, so that the client can add the headers it generates (Host,
     * Content-Type, Content-Length, Transfer-Encoding, Connection) without modifying the caller's
     * request, which may be sent again, e.g. to another host.
     *
     * @param request             The request to send
     * @param nettyRequestBuilder The netty builder of the request, from
     *                            {@link NettyHttpRequestBuilder#asBuilder}
     * @return The netty request, without body
     */
    private static HttpRequest toOutgoingNettyRequest(io.micronaut.http.HttpRequest<?> request, NettyHttpRequestBuilder nettyRequestBuilder) {
        URI uri = request.getUri();
        String uriWithoutHost = uri.getRawPath();
        if (uri.getRawQuery() != null) {
            uriWithoutHost += "?" + uri.getRawQuery();
        }
        io.micronaut.http.HttpRequest<?> source = request instanceof RawHttpRequestWrapper<?> raw ? raw.getDelegate() : request;
        if (source instanceof NettyClientHttpRequest<?> clientRequest) {
            // common case: copy the headers directly, without an intermediate netty request
            return clientRequest.toOutgoingHttpRequest(uriWithoutHost);
        }
        HttpRequest requestWithoutBody = nettyRequestBuilder.toHttpRequestWithoutBody(uriWithoutHost);
        // the netty request may share its headers with the caller's request
        return new DefaultHttpRequest(
            requestWithoutBody.protocolVersion(),
            requestWithoutBody.method(),
            requestWithoutBody.uri(),
            requestWithoutBody.headers().copy()
        );
    }

    /**
     * Send the request on the given connection. If the connection was reused from the pool and
     * turns out to be closed already, an idempotent request with an available body is sent again
     * once on another connection, with the same outgoing request head. Nothing is set up for that
     * unless the request could actually be sent again.
     *
     * @param poolHandle         The connection
     * @param request            The request to send
     * @param selection          The selection of the load balancer, or {@code null}
     * @param byteBody           The request body
     * @param nettyRequest       The netty request to send, from {@link #toOutgoingNettyRequest}
     * @param blockHint          The optional block hint
     * @param preferredScheduler The preferred scheduler reference
     * @return The response flow
     */
    private ExecutionFlow<NettyClientByteBodyResponse> sendRawRequestAllowingRetry(
        ConnectionManager.PoolHandle poolHandle,
        MutableHttpRequest<?> request,
        @Nullable LoadBalancerSelection selection,
        CloseableByteBody byteBody,
        HttpRequest nettyRequest,
        @Nullable BlockHint blockHint,
        AtomicReference<ScheduledExecutorService> preferredScheduler
    ) {
        boolean reusedConnection = markRequestSent(poolHandle);
        if (!reusedConnection || poolHandle.http2 || !(byteBody instanceof AvailableByteBody) || !request.getMethod().isIdempotent()) {
            return sendRawRequest(poolHandle, request, selection, byteBody, nettyRequest, false);
        }
        return sendRawRequestWithRetry(poolHandle, request, selection, byteBody, nettyRequest, blockHint, preferredScheduler);
    }

    private ExecutionFlow<NettyClientByteBodyResponse> sendRawRequestWithRetry(
        ConnectionManager.PoolHandle poolHandle,
        MutableHttpRequest<?> request,
        @Nullable LoadBalancerSelection selection,
        CloseableByteBody byteBody,
        HttpRequest nettyRequest,
        @Nullable BlockHint blockHint,
        AtomicReference<ScheduledExecutorService> preferredScheduler
    ) {
        return sendRawRequest(poolHandle, request, selection, byteBody, nettyRequest, true)
            .onErrorResume(e -> {
                if (e instanceof StaleConnectionException stale) {
                    return resendOnNewConnection(blockHint, preferredScheduler, request, selection, nettyRequest, stale.replayBody);
                }
                return ExecutionFlow.error(e);
            });
    }

    /**
     * Send a request a second time, after its first attempt failed because the reused connection
     * it was written to had already been closed by the server (see
     * {@link StaleConnectionException}). The connection is acquired from the pool as usual, and
     * this attempt is not retried again.
     *
     * @param blockHint          The optional block hint
     * @param preferredScheduler The preferred scheduler reference
     * @param request            The request to send
     * @param selection          The selection of the load balancer, or {@code null}. The first
     *                           attempt handed it on unreported: it is reported by this attempt, or
     *                           released if this attempt is not sent
     * @param firstAttemptRequest The netty request of the first attempt, with the headers the
     *                           client generated for it. It is not sent again, see
     *                           {@link #outgoingRequestForRetry}
     * @param firstAttemptBody   The request body of the first attempt, or {@code null} if it was
     *                           empty
     * @return The response flow
     */
    private ExecutionFlow<NettyClientByteBodyResponse> resendOnNewConnection(
        @Nullable BlockHint blockHint,
        AtomicReference<ScheduledExecutorService> preferredScheduler,
        MutableHttpRequest<?> request,
        @Nullable LoadBalancerSelection selection,
        HttpRequest firstAttemptRequest,
        @Nullable CloseableAvailableByteBody firstAttemptBody
    ) {
        if (log.isDebugEnabled()) {
            log.debug("Reused connection was closed before a response to {} {} was received, retrying once", request.getMethodName(), request.getUri());
        }
        CloseableAvailableByteBody replayBody = firstAttemptBody == null ? NettyByteBodyFactory.empty() : firstAttemptBody;
        RequestKey requestKey;
        try {
            // the first attempt was sent to this URI already, so this does not fail in practice
            requestKey = new RequestKey(this, request.getUri());
        } catch (Exception e) {
            replayBody.close();
            releaseSelection(selection);
            return ExecutionFlow.error(e);
        }
        // The replay body is owned by this method until it is either handed to sendRawRequest
        // or closed. Whoever sets this flag first (connection acquired, acquisition failed, or
        // exchange cancelled while the acquisition is pending) is responsible for the body.
        AtomicBoolean replayBodyClaimed = new AtomicBoolean();
        ExecutionFlow<NettyClientByteBodyResponse> response = connectionManager.connect(requestKey, blockHint, preferredScheduler)
            .onErrorResume(e -> {
                Throwable failure = failedBeforeSending(connectFailure(e), request, selection);
                if (replayBodyClaimed.compareAndSet(false, true)) {
                    replayBody.close();
                    releaseSelection(selection);
                }
                return ExecutionFlow.error(failure);
            })
            .flatMap(poolHandle -> {
                if (!replayBodyClaimed.compareAndSet(false, true)) {
                    // the exchange was cancelled while the connection was acquired, and the
                    // body is already closed. This may run on any thread, but like any other
                    // release, this must happen on the event loop of the connection.
                    if (poolHandle.channel.eventLoop().inEventLoop()) {
                        poolHandle.release();
                    } else {
                        poolHandle.channel.eventLoop().execute(poolHandle::release);
                    }
                    // the selection was released with the body
                    return ExecutionFlow.error(new HttpClientException("Request cancelled"));
                }
                poolHandle.touch();
                preferredScheduler.set(poolHandle.channel.eventLoop());
                request.setAttribute(NettyClientHttpRequest.CHANNEL, poolHandle.channel);
                markRequestSent(poolHandle);
                return sendRawRequest(poolHandle, request, selection, replayBody, outgoingRequestForRetry(firstAttemptRequest), false);
            });
        if (replayBodyClaimed.get()) {
            // the connection was available immediately, the body has been handed off already
            return response;
        }
        // cancelling the exchange cancels the pending acquisition, which then completes neither
        // way, so the body must be released on cancellation
        DelayedExecutionFlow<NettyClientByteBodyResponse> result = DelayedExecutionFlow.create();
        response.onComplete((r, e) -> {
            if (e != null) {
                result.completeExceptionally(e);
            } else if (result.isCancelled()) {
                if (r != null) {
                    r.close();
                }
            } else {
                result.complete(r);
            }
        });
        result.onCancel(() -> {
            if (replayBodyClaimed.compareAndSet(false, true)) {
                replayBody.close();
                releaseSelection(selection);
            }
            response.cancel();
        });
        return result;
    }

    /**
     * Record that a request is sent on the given connection.
     *
     * @param poolHandle The connection
     * @return {@code true} iff an earlier request was sent on this connection, i.e. it was reused
     * from the pool
     */
    private static boolean markRequestSent(ConnectionManager.PoolHandle poolHandle) {
        return poolHandle.channel.attr(REQUEST_SENT).getAndSet(Boolean.TRUE) != null;
    }

    /**
     * The netty request to send a request again with, after its first attempt failed on a stale
     * connection. The request of the first attempt was handed to the pipeline of that connection,
     * so it is not reused: the new request has a copy of its head, with the headers the client
     * generated for the first attempt (Host, Content-Type...). The framing headers
     * (Content-Length, Transfer-Encoding) and Connection are set again for the new connection
     * when its pipeline is prepared; Connection is removed here so that it is only sent where
     * the new connection uses it.
     *
     * @param firstAttemptRequest The netty request of the first attempt
     * @return A new netty request with the same head
     */
    private static HttpRequest outgoingRequestForRetry(HttpRequest firstAttemptRequest) {
        HttpHeaders headers = firstAttemptRequest.headers().copy();
        headers.remove(HttpHeaderNames.CONNECTION);
        return new DefaultHttpRequest(
            firstAttemptRequest.protocolVersion(),
            firstAttemptRequest.method(),
            firstAttemptRequest.uri(),
            headers
        );
    }

    /**
     * This is the low-level request method, without redirect handling and with raw body bytes.
     *
     * @param poolHandle The pool handle to send the request on
     * @param request    The request to send
     * @param selection  The selection of the load balancer, or {@code null}
     * @param byteBody   The request body
     * @param nettyRequest The netty request to send, from {@link #toOutgoingNettyRequest}
     * @param allowRetry Whether the request may fail with a {@link StaleConnectionException} so
     *                   that it is sent again on another connection. Only for idempotent requests
     *                   with an available body on a reused HTTP/1 connection
     * @return A mono containing the response
     */
    private ExecutionFlow<NettyClientByteBodyResponse> sendRawRequest(
        ConnectionManager.PoolHandle poolHandle,
        io.micronaut.http.HttpRequest<?> request,
        @Nullable LoadBalancerSelection selection,
        CloseableByteBody byteBody,
        HttpRequest nettyRequest,
        boolean allowRetry
    ) {
        poolHandle.touch();

        DelayedExecutionFlow<NettyClientByteBodyResponse> flow = DelayedExecutionFlow.create();
        // need to run the create() on the event loop so that pipeline modification happens synchronously
        if (poolHandle.channel.eventLoop().inEventLoop()) {
            sendRawRequest0(poolHandle, request, selection, byteBody, flow, nettyRequest, allowRetry);
        } else {
            poolHandle.channel.eventLoop().execute(() -> sendRawRequest0(poolHandle, request, selection, byteBody, flow, nettyRequest, allowRetry));
        }
        return flow;
    }

    private void sendRawRequest0(ConnectionManager.PoolHandle poolHandle, io.micronaut.http.HttpRequest<?> request, @Nullable LoadBalancerSelection selection, CloseableByteBody byteBody, DelayedExecutionFlow<NettyClientByteBodyResponse> sink, HttpRequest nettyRequest, boolean allowRetry) {
        ExchangePlan plan = ExchangePlan.of(request, nettyRequest, selection, byteBody, allowRetry);
        new ClientExchange(this, poolHandle, plan, sink).start(byteBody);
    }

    /**
     * The failure of a connection that could not be opened: the request was never sent, so the
     * caller may send it again on another connection.
     *
     * @param error The failure of the connect, or {@code null} if the channel closed without one
     * @return The failure of the request
     */
    static UnprocessedRequestException connectError(@Nullable Throwable error) {
        UnprocessedRequestException.Reason reason = error instanceof io.netty.channel.ConnectTimeoutException
            ? UnprocessedRequestException.Reason.CONNECT_TIMEOUT
            : UnprocessedRequestException.Reason.CONNECT;
        return new UnprocessedRequestException(reason, error == null ? "Unknown connect error" : "Connect Error: " + error.getMessage(), error);
    }

    private ReadBuffer charSequenceToByteBuf(CharSequence bodyValue, MediaType requestContentType) {
        return NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT).copyOf(bodyValue.toString(), requestContentType.getCharset().orElse(defaultCharset));
    }

    private String getHostHeader(RequestKey requestKey) {
        StringBuilder host = new StringBuilder(requestKey.getHost());
        int port = requestKey.getPort();
        if (port > -1 && port != 80 && port != 443) {
            host.append(":").append(port);
        }
        return host.toString();
    }

    private CloseableByteBody buildFormRequest(
        HttpHeaders outgoingHeaders,
        NettyByteBodyFactory bodyFactory,
        ThrowingFunction<HttpRequest, HttpPostRequestEncoder, HttpPostRequestEncoder.ErrorDataEncoderException> buildMethod
    ) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        // this function acts like a wrapper around HttpPostRequestEncoder. HttpPostRequestEncoder
        // takes a request + form data and transforms it to a request + bytes. Because we only want
        // the bytes, we need to copy the data from the netty request into the
        // outgoing headers. This is just the Content-Type header, which sometimes gets an extra
        // boundary specifier that we need.

        // build the mock netty request (only the content-type matters)
        HttpRequest nettyRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        List<AsciiString> relevantHeaders = List.of(HttpHeaderNames.CONTENT_TYPE);
        for (AsciiString header : relevantHeaders) {
            nettyRequest.headers().add(header, outgoingHeaders.getAll(header));
        }

        HttpPostRequestEncoder encoder = buildMethod.apply(nettyRequest);
        HttpRequest finalized = encoder.finalizeRequest();
        // copy back the content-type
        for (AsciiString header : relevantHeaders) {
            outgoingHeaders.remove(header);
            for (String value : finalized.headers().getAll(header)) {
                outgoingHeaders.add(header, value);
            }
        }
        // return the body bytes
        if (encoder.isChunked()) {
            Flux<ByteBuf> bytes = Flux.create(em -> {
                em.onRequest(n -> {
                    try {
                        while (n-- > 0) {
                            HttpContent chunk = encoder.readChunk(ByteBufAllocator.DEFAULT);
                            if (chunk == null) {
                                assert encoder.isEndOfInput();
                                em.complete();
                                break;
                            }
                            em.next(chunk.content());
                        }
                    } catch (Exception e) {
                        em.error(e);
                    }
                });
                em.onDispose(encoder::cleanFiles);
            });
            if (blockingExecutor != null &&
                encoder.getBodyListAttributes().stream().anyMatch(d -> !(d instanceof HttpData hd) || !hd.isInMemory())) {
                // readChunk in the above code can block.
                bytes = bytes.subscribeOn(Schedulers.fromExecutor(blockingExecutor));
            }
            return bodyFactory.adapt(bytes, null, null);
        } else {
            return bodyFactory.adapt(((FullHttpRequest) finalized).content());
        }
    }

    private HttpPostRequestEncoder buildFormDataRequest(HttpRequest baseRequest, Object bodyValue) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        HttpPostRequestEncoder postRequestEncoder = new HttpPostRequestEncoder(baseRequest, false);

        Map<String, Object> formData;
        if (bodyValue instanceof Map) {
            //noinspection unchecked
            formData = (Map<String, Object>) bodyValue;
        } else {
            formData = BeanMap.of(bodyValue);
        }
        for (Map.Entry<String, Object> entry : formData.entrySet()) {
            Object value = entry.getValue();
            if (value != null) {
                if (value instanceof Collection<?> collection) {
                    for (Object val : collection) {
                        addBodyAttribute(postRequestEncoder, entry.getKey(), val);
                    }
                } else {
                    addBodyAttribute(postRequestEncoder, entry.getKey(), value);
                }
            }
        }
        return postRequestEncoder;
    }

    private void addBodyAttribute(HttpPostRequestEncoder postRequestEncoder, String key, Object value) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        Optional<String> converted = conversionService.convert(value, String.class);
        if (converted.isPresent()) {
            postRequestEncoder.addBodyAttribute(key, converted.get());
        }
    }

    private HttpPostRequestEncoder buildMultipartRequest(HttpRequest baseRequest, Object bodyValue) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        HttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);
        HttpPostRequestEncoder postRequestEncoder = new HttpPostRequestEncoder(factory, baseRequest, true, CharsetUtil.UTF_8, HttpPostRequestEncoder.EncoderMode.HTML5);
        if (bodyValue instanceof MultipartBody.Builder builder) {
            bodyValue = builder.build();
        }
        if (bodyValue instanceof MultipartBody multipartBody) {
            postRequestEncoder.setBodyHttpDatas(multipartBody.getData(new MultipartDataFactory<>() {
                @Override
                public InterfaceHttpData createFileUpload(String name, String filename, MediaType contentType, @Nullable String encoding, @Nullable Charset charset, long length) {
                    return factory.createFileUpload(
                        baseRequest,
                        name,
                        filename,
                        contentType.toString(),
                        encoding,
                        charset,
                        length
                    );
                }

                @Override
                public InterfaceHttpData createAttribute(String name, String value) {
                    return factory.createAttribute(
                        baseRequest,
                        name,
                        value
                    );
                }

                @Override
                public void setContent(InterfaceHttpData fileUploadObject, Object content) throws IOException {
                    if (fileUploadObject instanceof FileUpload fu) {
                        if (content instanceof InputStream stream) {
                            fu.setContent(stream);
                        } else if (content instanceof File file) {
                            fu.setContent(file);
                        } else if (content instanceof byte[] bytes) {
                            final ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
                            fu.setContent(buffer);
                        }
                    }
                }
            }));
        } else {
            throw new MultipartException("The type %s is not a supported type for a multipart request body".formatted(bodyValue.getClass().getName()));
        }

        return postRequestEncoder;
    }

    void traceBody(String type, ByteBuf content) {
        log.trace("{} Body", type);
        log.trace("----");
        log.trace(content.toString(defaultCharset));
        log.trace("----");
    }

    private void traceChunk(ByteBuf content) {
        log.trace("Sending Chunk");
        log.trace("----");
        log.trace(content.toString(defaultCharset));
        log.trace("----");
    }

    private static MediaTypeCodecRegistry createDefaultMediaTypeRegistry() {
        JsonMapper mapper = JsonMapper.createDefault();
        ApplicationConfiguration configuration = new ApplicationConfiguration();
        return MediaTypeCodecRegistry.of(
            new JsonMediaTypeCodec(mapper, configuration, null),
            new JsonStreamMediaTypeCodec(mapper, configuration, null)
        );
    }

    private static MessageBodyHandlerRegistry createDefaultMessageBodyHandlerRegistry() {
        ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration();
        ContextlessMessageBodyHandlerRegistry registry = new ContextlessMessageBodyHandlerRegistry(
            applicationConfiguration,
            NettyByteBufferFactory.DEFAULT,
            new NettyByteBufMessageBodyHandler(),
            new WritableBodyWriter(applicationConfiguration)
        );
        JsonMapper mapper = JsonMapper.createDefault();
        registry.add(MediaType.APPLICATION_JSON_TYPE, new NettyJsonHandler<>(mapper));
        registry.add(MediaType.APPLICATION_JSON_TYPE, new CharSequenceBodyWriter(StandardCharsets.UTF_8));
        registry.add(MediaType.APPLICATION_JSON_STREAM_TYPE, new NettyJsonStreamHandler<>(mapper));
        return registry;
    }

    static boolean isSecureScheme(String scheme) {
        // fast path
        if (scheme.equals("http")) {
            return false;
        }
        if (scheme.equals("https")) {
            return true;
        }
        // actual case-insensitive check
        return io.micronaut.http.HttpRequest.SCHEME_HTTPS.equalsIgnoreCase(scheme) || SCHEME_WSS.equalsIgnoreCase(scheme);
    }

    /**
     * Classify a failure to get a connection for a request: a connection pool acquire timeout
     * fails the flow with a plain {@link TimeoutException}, but no byte of the request was sent.
     *
     * @param failure The failure
     * @return The failure, an {@link UnprocessedRequestException} for an acquire timeout
     */
    private Throwable connectFailure(Throwable failure) {
        if (failure instanceof TimeoutException) {
            return new UnprocessedRequestException(UnprocessedRequestException.Reason.POOL_ACQUIRE,
                "Cannot acquire connection: the acquire timeout of " + configuration.getConnectionPoolConfiguration().getAcquireTimeout().orElse(null) + " elapsed", failure);
        }
        return failure;
    }

    /**
     * Whether the protocols a server switched to were offered: a client may offer several, e.g.
     * {@code Upgrade: websocket, example/1}, and the server selects among them (RFC 9110, section
     * 7.8). Tokens are compared ignoring case.
     *
     * @param selected The {@code Upgrade} list of the {@code 101} response
     * @param offered  The {@code Upgrade} list of the request
     * @return Whether every selected protocol was offered
     */
    static boolean isOffered(String selected, String offered) {
        boolean any = false;
        for (String token : selected.split(",")) {
            String protocol = token.trim();
            if (protocol.isEmpty()) {
                continue;
            }
            any = true;
            boolean found = false;
            for (String candidate : offered.split(",")) {
                if (candidate.trim().equalsIgnoreCase(protocol)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return any;
    }

    /**
     * The outcome to report to the load balancer for a failed exchange.
     *
     * @param failure The failure, before the response or of its body, as raised or as mapped
     * @return The outcome, or {@code null} if the failure says nothing about the instance
     */
    static LoadBalancer.@Nullable Outcome failureOutcome(Throwable failure) {
        if (failure instanceof UnprocessedRequestException unprocessed) {
            return switch (unprocessed.getReason()) {
                case CONNECT, CONNECT_TIMEOUT -> LoadBalancer.Outcome.CONNECT_FAILURE;
                case STREAM_REFUSED -> LoadBalancer.Outcome.RESET;
                // a pool that is full, or a keep-alive connection the server closed, say nothing about the instance
                default -> null;
            };
        } else if (failure instanceof ReadTimeoutException || failure instanceof io.netty.handler.timeout.ReadTimeoutException) {
            return LoadBalancer.Outcome.TIMEOUT;
        } else if (failure instanceof ResponseClosedException || failure instanceof StreamResetException) {
            return LoadBalancer.Outcome.RESET;
        }
        return null;
    }

    /**
     * Record the target of a request that was not sent on its exception, and the service id if
     * it has none yet.
     *
     * @param failure  The failure
     * @param request  The request that failed
     * @param selection The selection of the load balancer, or {@code null}
     * @return The failure
     */
    private Throwable failedBeforeSending(Throwable failure, io.micronaut.http.HttpRequest<?> request, @Nullable LoadBalancerSelection selection) {
        if (failure instanceof UnprocessedRequestException unprocessed) {
            unprocessed.setTarget(request.getUri(), selection == null ? null : selection.instance());
            if (unprocessed.getServiceId() == null) {
                decorate(unprocessed);
            }
            LoadBalancer.Outcome outcome = failureOutcome(unprocessed);
            if (outcome != null) {
                report(selection, outcome);
            }
        }
        return failure;
    }

    private static <O, E> boolean shouldConvertWithBodyType(io.netty.handler.codec.http.HttpResponse msg,
                                                            HttpClientConfiguration configuration,
                                                            @Nullable Argument<O> bodyType,
                                                            Argument<E> errorType) {
        if (msg.status().code() < 400) {
            return true;
        }
        return !configuration.isExceptionOnErrorStatus() && bodyType != null && bodyType.equalsType(errorType);
    }

    /**
     * Create a {@link HttpClientResponseException} if parsing of the HTTP error body failed.
     */
    private HttpClientResponseException makeErrorBodyParseError(FullHttpResponse fullResponse, Throwable t) {
        FullNettyClientHttpResponse<Object> errorResponse = new FullNettyClientHttpResponse<>(
            fullResponse,
            handlerRegistry,
            null,
            false,
            conversionService
        );
        return decorate(new HttpClientResponseException(
            "Error decoding HTTP error response body: " + t.getMessage(),
            t,
            errorResponse,
            null
        ));
    }

    /**
     * Create a {@link HttpClientResponseException} from a response with a failed HTTP status.
     */
    private HttpClientResponseException makeErrorFromRequestBody(@Nullable Argument<?> errorType, HttpResponseStatus status, FullNettyClientHttpResponse<?> response) {
        if (errorType != null && errorType != HttpClient.DEFAULT_ERROR_TYPE) {
            return decorate(new HttpClientResponseException(
                status.reasonPhrase(),
                null,
                response,
                new HttpClientErrorDecoder() {
                    @Override
                    public Argument<?> getErrorType(MediaType mediaType) {
                        return errorType;
                    }
                }
            ));
        } else {
            return decorate(new HttpClientResponseException(status.reasonPhrase(), response));
        }
    }

    /**
     * Key used for connection pooling and determining host/port.
     */
    public static class RequestKey {
        private final String host;
        private final int port;
        private final boolean secure;

        /**
         * @param ctx        The HTTP client that created this request key. Only used for exception
         *                   context, not stored
         * @param requestURI The request URI
         */
        public RequestKey(NettyHttpClient ctx, URI requestURI) {
            this.secure = isSecureScheme(requestURI.getScheme());
            String host = requestURI.getHost();
            int port;
            if (host == null) {
                host = requestURI.getAuthority();
                if (host == null) {
                    throw decorate(ctx, new NoHostException("URI specifies no host to connect to"));
                }

                final int i = host.indexOf(':');
                if (i > -1) {
                    final String portStr = host.substring(i + 1);
                    host = host.substring(0, i);
                    try {
                        port = Integer.parseInt(portStr);
                    } catch (NumberFormatException e) {
                        throw decorate(ctx, new HttpClientException("URI specifies an invalid port: " + portStr));
                    }
                } else {
                    port = requestURI.getPort() > -1 ? requestURI.getPort() : secure ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT;
                }
            } else {
                port = requestURI.getPort() > -1 ? requestURI.getPort() : secure ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT;
            }

            this.host = host;
            this.port = port;
        }

        public InetSocketAddress getRemoteAddress() {
            return InetSocketAddress.createUnresolved(host, port);
        }

        public boolean isSecure() {
            return secure;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof RequestKey that)) {
                return false;
            }
            return port == that.port &&
                secure == that.secure &&
                Objects.equals(host, that.host);
        }

        @Override
        public int hashCode() {
            return ObjectUtils.hash(host, port, secure);
        }

        private <E extends HttpClientException> E decorate(NettyHttpClient ctx, E exc) {
            return HttpClientExceptionUtils.populateServiceId(exc, ctx.informationalServiceId, ctx.configuration);
        }
    }

    /**
     * Notified of the upload of the body of a request.
     *
     * @param started  Run when the upload of the body starts
     * @param uploaded Run once the whole request, the body included, is written to the connection
     */
    record UploadListener(Runnable started, Runnable uploaded) {
    }

    /**
     * Notified whenever a client is started or stopped, so that the owner of the client can
     * track the clients that are running.
     */
    interface LifecycleListener {
        /**
         * Called after {@link #start()}.
         *
         * @param client The client
         */
        void onStart(NettyHttpClient client);

        /**
         * Called after {@link #stop()}, including when the client is closed.
         *
         * @param client The client
         */
        void onStop(NettyHttpClient client);
    }

    /**
     * Internal signal that a request failed because the reused connection it was written to had
     * already been closed, before any part of the response was received. The request is sent
     * again on another connection, and this exception never reaches the caller.
     */
    static final class StaleConnectionException extends RuntimeException {
        @Nullable
        final transient CloseableAvailableByteBody replayBody;

        StaleConnectionException(@Nullable CloseableAvailableByteBody replayBody) {
            super("Reused connection was closed before the response was received", null, false, false);
            this.replayBody = replayBody;
        }
    }

    /**
     * Marker carrying the original execution stack trace of a blocking client failure (the point
     * on the event loop where the exception was constructed), attached as a suppressed exception
     * by {@link #customizeBlockingException(HttpClientException)}.
     */
    private static final class BlockingClientExecutionTrace extends Throwable {
        BlockingClientExecutionTrace() {
            super("Client request execution failed on a background thread; stack trace of the failure follows", null, false, true);
        }
    }
}
