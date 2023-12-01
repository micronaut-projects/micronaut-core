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
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataResolver;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.beans.BeanMap;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.ConversionServiceAware;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.ObjectUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseWrapper;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.bind.DefaultRequestBinderRegistry;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.body.ChunkedMessageBodyReader;
import io.micronaut.http.body.ContextlessMessageBodyHandlerRegistry;
import io.micronaut.http.body.DynamicMessageBodyWriter;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.ProxyHttpClient;
import io.micronaut.http.client.ProxyRequestOptions;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.client.exceptions.ContentLengthExceededException;
import io.micronaut.http.client.exceptions.HttpClientErrorDecoder;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientExceptionUtils;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.exceptions.NoHostException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.client.exceptions.ResponseClosedException;
import io.micronaut.http.client.filter.ClientFilterResolutionContext;
import io.micronaut.http.client.filter.DefaultHttpClientFilterResolver;
import io.micronaut.http.client.filters.ClientServerContextFilter;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.client.multipart.MultipartDataFactory;
import io.micronaut.http.client.netty.ssl.ClientSslBuilder;
import io.micronaut.http.client.netty.ssl.NettyClientSslBuilder;
import io.micronaut.http.client.netty.websocket.NettyWebSocketClientHandler;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.context.ContextPathUtils;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.filter.FilterOrder;
import io.micronaut.http.filter.FilterRunner;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.http.filter.HttpClientFilterResolver;
import io.micronaut.http.filter.HttpFilterResolver;
import io.micronaut.http.multipart.MultipartException;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.micronaut.http.netty.NettyHttpRequestBuilder;
import io.micronaut.http.netty.NettyHttpResponseBuilder;
import io.micronaut.http.netty.body.ByteBufRawMessageBodyHandler;
import io.micronaut.http.netty.body.NettyJsonHandler;
import io.micronaut.http.netty.body.NettyJsonStreamHandler;
import io.micronaut.http.netty.body.NettyWritableBodyWriter;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.netty.reactive.HotObservable;
import io.micronaut.http.netty.stream.DefaultStreamedHttpResponse;
import io.micronaut.http.netty.stream.JsonSubscriber;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.micronaut.http.netty.stream.StreamedHttpResponse;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import io.micronaut.http.sse.Event;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.http.uri.UriTemplate;
import io.micronaut.http.util.HttpHeadersUtil;
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
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.EmptyByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.flow.FlowControlHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Default implementation of the {@link HttpClient} interface based on Netty.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class DefaultHttpClient implements
        WebSocketClient,
        HttpClient,
        StreamingHttpClient,
        SseClient,
        ProxyHttpClient,
        Closeable,
        AutoCloseable {

    /**
     * Default logger, use {@link #log} where possible.
     */
    private static final Logger DEFAULT_LOG = LoggerFactory.getLogger(DefaultHttpClient.class);
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;

    /**
     * Which headers <i>not</i> to copy from the first request when redirecting to a second request. There doesn't
     * appear to be a spec for this. {@link java.net.HttpURLConnection} seems to drop all headers, but that would be a
     * breaking change.
     * <p>
     * Stored as a {@link HttpHeaders} with empty values because presumably someone thought about optimizing those
     * already.
     */
    private static final HttpHeaders REDIRECT_HEADER_BLOCKLIST;

    static {
        REDIRECT_HEADER_BLOCKLIST = new DefaultHttpHeaders();
        // The host should be recalculated based on the location
        REDIRECT_HEADER_BLOCKLIST.add(HttpHeaderNames.HOST, "");
        // post body headers
        REDIRECT_HEADER_BLOCKLIST.add(HttpHeaderNames.CONTENT_TYPE, "");
        REDIRECT_HEADER_BLOCKLIST.add(HttpHeaderNames.CONTENT_LENGTH, "");
        REDIRECT_HEADER_BLOCKLIST.add(HttpHeaderNames.TRANSFER_ENCODING, "");
        REDIRECT_HEADER_BLOCKLIST.add(HttpHeaderNames.CONNECTION, "");
    }

    protected MediaTypeCodecRegistry mediaTypeCodecRegistry;
    protected ByteBufferFactory<ByteBufAllocator, ByteBuf> byteBufferFactory = new NettyByteBufferFactory();

    ConnectionManager connectionManager;

    private MessageBodyHandlerRegistry handlerRegistry;
    private final List<HttpFilterResolver.FilterEntry> clientFilterEntries;
    private final LoadBalancer loadBalancer;
    private final HttpClientConfiguration configuration;
    private final String contextPath;
    private final Charset defaultCharset;
    private final Logger log;
    private final HttpClientFilterResolver<ClientFilterResolutionContext> filterResolver;
    private final WebSocketBeanRegistry webSocketRegistry;
    private final RequestBinderRegistry requestBinderRegistry;
    private final String informationalServiceId;
    private final ConversionService conversionService;

    /**
     * Construct a client for the given arguments.
     *
     * @param loadBalancer                    The {@link LoadBalancer} to use for selecting servers
     * @param configuration                   The {@link HttpClientConfiguration} object
     * @param contextPath                     The base URI to prepend to request uris
     * @param threadFactory                   The thread factory to use for client threads
     * @param nettyClientSslBuilder           The SSL builder
     * @param codecRegistry                   The {@link MediaTypeCodecRegistry} to use for encoding and decoding objects
     * @param handlerRegistry                 The handler registry for encoding and decoding
     * @param annotationMetadataResolver      The annotation metadata resolver
     * @param conversionService               The conversion service
     * @param filters                         The filters to use
     */
    public DefaultHttpClient(@Nullable LoadBalancer loadBalancer,
                             @NonNull HttpClientConfiguration configuration,
                             @Nullable String contextPath,
                             @Nullable ThreadFactory threadFactory,
                             ClientSslBuilder nettyClientSslBuilder,
                             @NonNull MediaTypeCodecRegistry codecRegistry,
                             @NonNull MessageBodyHandlerRegistry handlerRegistry,
                             @Nullable AnnotationMetadataResolver annotationMetadataResolver,
                             ConversionService conversionService,
                             HttpClientFilter... filters) {
        this(loadBalancer,
            null,
            configuration,
            contextPath,
            new DefaultHttpClientFilterResolver(null, annotationMetadataResolver, Arrays.asList(filters)),
            null,
            threadFactory,
            nettyClientSslBuilder,
            codecRegistry,
            handlerRegistry,
            WebSocketBeanRegistry.EMPTY,
            new DefaultRequestBinderRegistry(conversionService),
            null,
            NioSocketChannel::new,
            NioDatagramChannel::new,
            CompositeNettyClientCustomizer.EMPTY,
            null,
            conversionService);
    }

    /**
     * Construct a client for the given arguments.
     * @param loadBalancer                    The {@link LoadBalancer} to use for selecting servers
     * @param explicitHttpVersion                     The HTTP version to use. Can be null and defaults to {@link io.micronaut.http.HttpVersion#HTTP_1_1}
     * @param configuration                   The {@link HttpClientConfiguration} object
     * @param contextPath                     The base URI to prepend to request uris
     * @param filterResolver                  The http client filter resolver
     * @param clientFilterEntries             The client filter entries
     * @param threadFactory                   The thread factory to use for client threads
     * @param nettyClientSslBuilder           The SSL builder
     * @param codecRegistry                   The {@link MediaTypeCodecRegistry} to use for encoding and decoding objects
     * @param handlerRegistry                 The handler registry for encoding and decoding
     * @param webSocketBeanRegistry           The websocket bean registry
     * @param requestBinderRegistry           The request binder registry
     * @param eventLoopGroup                  The event loop group to use
     * @param socketChannelFactory            The socket channel factory
     * @param udpChannelFactory               The UDP channel factory
     * @param clientCustomizer                The pipeline customizer
     * @param informationalServiceId          Optional service ID that will be passed to exceptions created by this client
     * @param conversionService               The conversion service
     */
    public DefaultHttpClient(@Nullable LoadBalancer loadBalancer,
                             @Nullable HttpVersionSelection explicitHttpVersion,
                             @NonNull HttpClientConfiguration configuration,
                             @Nullable String contextPath,
                             @NonNull HttpClientFilterResolver<ClientFilterResolutionContext> filterResolver,
                             @NonNull List<HttpFilterResolver.FilterEntry> clientFilterEntries,
                             @Nullable ThreadFactory threadFactory,
                             @NonNull ClientSslBuilder nettyClientSslBuilder,
                             @NonNull MediaTypeCodecRegistry codecRegistry,
                             @NonNull MessageBodyHandlerRegistry handlerRegistry,
                             @NonNull WebSocketBeanRegistry webSocketBeanRegistry,
                             @NonNull RequestBinderRegistry requestBinderRegistry,
                             @Nullable EventLoopGroup eventLoopGroup,
                             @NonNull ChannelFactory<? extends SocketChannel> socketChannelFactory,
                             @NonNull ChannelFactory<? extends DatagramChannel> udpChannelFactory,
                             NettyClientCustomizer clientCustomizer,
                             @Nullable String informationalServiceId,
                             ConversionService conversionService
    ) {
        ArgumentUtils.requireNonNull("nettyClientSslBuilder", nettyClientSslBuilder);
        ArgumentUtils.requireNonNull("codecRegistry", codecRegistry);
        ArgumentUtils.requireNonNull("webSocketBeanRegistry", webSocketBeanRegistry);
        ArgumentUtils.requireNonNull("requestBinderRegistry", requestBinderRegistry);
        ArgumentUtils.requireNonNull("configuration", configuration);
        ArgumentUtils.requireNonNull("filterResolver", filterResolver);
        ArgumentUtils.requireNonNull("socketChannelFactory", socketChannelFactory);
        this.loadBalancer = loadBalancer;
        this.defaultCharset = configuration.getDefaultCharset();
        if (StringUtils.isNotEmpty(contextPath)) {
            if (contextPath.charAt(0) != '/') {
                contextPath = '/' + contextPath;
            }
            this.contextPath = contextPath;
        } else {
            this.contextPath = null;
        }
        this.configuration = configuration;

        this.mediaTypeCodecRegistry = codecRegistry;
        this.handlerRegistry = handlerRegistry;
        this.log = configuration.getLoggerName().map(LoggerFactory::getLogger).orElse(DEFAULT_LOG);
        this.filterResolver = filterResolver;
        if (clientFilterEntries != null) {
            this.clientFilterEntries = clientFilterEntries;
        } else {
            this.clientFilterEntries = filterResolver.resolveFilterEntries(
                    new ClientFilterResolutionContext(null, AnnotationMetadata.EMPTY_METADATA)
            );
        }
        this.webSocketRegistry = webSocketBeanRegistry != null ? webSocketBeanRegistry : WebSocketBeanRegistry.EMPTY;
        this.requestBinderRegistry = requestBinderRegistry;
        this.informationalServiceId = informationalServiceId;
        this.conversionService = conversionService;

        this.connectionManager = new ConnectionManager(
            log,
            eventLoopGroup,
            threadFactory,
            configuration,
            explicitHttpVersion,
            socketChannelFactory,
            udpChannelFactory,
            nettyClientSslBuilder,
            clientCustomizer,
            informationalServiceId);
    }

    /**
     * @param uri The URL
     */
    public DefaultHttpClient(@Nullable URI uri) {
        this(uri, new DefaultHttpClientConfiguration());
    }

    /**
     *
     */
    public DefaultHttpClient() {
        this((URI) null, new DefaultHttpClientConfiguration());
    }

    /**
     * @param uri           The URI
     * @param configuration The {@link HttpClientConfiguration} object
     */
    public DefaultHttpClient(@Nullable URI uri, @NonNull HttpClientConfiguration configuration) {
        this(uri, configuration, new NettyClientSslBuilder(new ResourceResolver()));
    }

    /**
     * Constructor used by micronaut-oracle-cloud.
     *
     * @param uri           The URI
     * @param configuration The {@link HttpClientConfiguration} object
     * @param clientSslBuilder The SSL builder
     */
    public DefaultHttpClient(@Nullable URI uri, @NonNull HttpClientConfiguration configuration, @NonNull ClientSslBuilder clientSslBuilder) {
        this(
            uri == null ? null : LoadBalancer.fixed(uri), configuration, null, new DefaultThreadFactory(MultithreadEventLoopGroup.class),
            clientSslBuilder,
            createDefaultMediaTypeRegistry(),
            createDefaultMessageBodyHandlerRegistry(),
            AnnotationMetadataResolver.DEFAULT,
            ConversionService.SHARED);
    }

    /**
     * @param loadBalancer  The {@link LoadBalancer} to use for selecting servers
     * @param configuration The {@link HttpClientConfiguration} object
     */
    public DefaultHttpClient(@Nullable LoadBalancer loadBalancer, HttpClientConfiguration configuration) {
        this(loadBalancer,
            configuration, null, new DefaultThreadFactory(MultithreadEventLoopGroup.class),
            new NettyClientSslBuilder(new ResourceResolver()),
            createDefaultMediaTypeRegistry(),
            createDefaultMessageBodyHandlerRegistry(),
            AnnotationMetadataResolver.DEFAULT,
            ConversionService.SHARED);
    }

    static boolean isAcceptEvents(io.micronaut.http.HttpRequest<?> request) {
        String acceptHeader = request.getHeaders().get(io.micronaut.http.HttpHeaders.ACCEPT);
        return acceptHeader != null && acceptHeader.equalsIgnoreCase(MediaType.TEXT_EVENT_STREAM);
    }

    /**
     * @return The configuration used by this client
     */
    public HttpClientConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * @return The client-specific logger name
     */
    public Logger getLog() {
        return log;
    }

    /**
     * Access to the connection manager, for micronaut-oracle-cloud.
     *
     * @return The connection manager of this client
     */
    public ConnectionManager connectionManager() {
        return connectionManager;
    }

    @Override
    public HttpClient start() {
        if (!isRunning()) {
            connectionManager.start();
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
        return this;
    }

    /**
     * @return The {@link MediaTypeCodecRegistry} used by this client
     */
    public MediaTypeCodecRegistry getMediaTypeCodecRegistry() {
        return mediaTypeCodecRegistry;
    }

    /**
     * Sets the {@link MediaTypeCodecRegistry} used by this client.
     *
     * @param mediaTypeCodecRegistry The registry to use. Should not be null
     */
    @Deprecated
    public void setMediaTypeCodecRegistry(MediaTypeCodecRegistry mediaTypeCodecRegistry) {
        if (mediaTypeCodecRegistry != null) {
            this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        }
    }

    /**
     * Get the handler registry for this client.
     *
     * @return The handler registry
     */
    @NonNull
    public final MessageBodyHandlerRegistry getHandlerRegistry() {
        return handlerRegistry;
    }

    /**
     * Set the handler registry for this client.
     *
     * @param handlerRegistry The handler registry
     */
    public final void setHandlerRegistry(@NonNull MessageBodyHandlerRegistry handlerRegistry) {
        this.handlerRegistry = handlerRegistry;
    }

    @Override
    public BlockingHttpClient toBlocking() {
        return new BlockingHttpClient() {

            @Override
            public void close() {
                DefaultHttpClient.this.close();
            }

            @Override
            public <I, O, E> io.micronaut.http.HttpResponse<O> exchange(io.micronaut.http.HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType) {
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
                BlockHint blockHint = BlockHint.willBlockThisThread();
                Flux<HttpResponse<O>> publisher = Flux.from(DefaultHttpClient.this.exchange(request, bodyType, errorType, blockHint));
                return publisher.doOnNext(res -> {
                    Optional<ByteBuf> byteBuf = res.getBody(ByteBuf.class);
                    byteBuf.ifPresent(bb -> {
                        if (bb.refCnt() > 0) {
                            ReferenceCountUtil.safeRelease(bb);
                        }
                    });
                    if (res instanceof FullNettyClientHttpResponse response) {
                        response.onComplete();
                    }
                }).blockFirst();
            }

            @Override
            public <I, O, E> O retrieve(io.micronaut.http.HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType) {
                // mostly copied from super method, but with customizeException

                HttpResponse<O> response = exchange(request, bodyType, errorType);
                if (HttpStatus.class.isAssignableFrom(bodyType.getType())) {
                    return (O) response.getStatus();
                } else {
                    Optional<O> body = response.getBody();
                    if (!body.isPresent() && response.getBody(Argument.of(byte[].class)).isPresent()) {
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
        };
    }

    @NonNull
    private <I> MutableHttpRequest<?> toMutableRequest(io.micronaut.http.HttpRequest<I> request) {
        return MutableHttpRequestWrapper.wrapIfNecessary(conversionService, request);
    }

    @SuppressWarnings("SubscriberImplementation")
    @Override
    public <I> Publisher<Event<ByteBuffer<?>>> eventStream(@NonNull io.micronaut.http.HttpRequest<I> request) {
        setupConversionService(request);
        return eventStreamOrError(request, null);
    }

    private <I> Publisher<Event<ByteBuffer<?>>> eventStreamOrError(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<?> errorType) {

        if (request instanceof MutableHttpRequest httpRequest) {
            httpRequest.accept(MediaType.TEXT_EVENT_STREAM_TYPE);
        }

        return Flux.create(emitter ->
                dataStream(request, errorType).subscribe(new Subscriber<ByteBuffer<?>>() {
                    private Subscription dataSubscription;
                    private CurrentEvent currentEvent;

                    @Override
                    public void onSubscribe(Subscription s) {
                        this.dataSubscription = s;
                        Disposable cancellable = () -> dataSubscription.cancel();
                        emitter.onCancel(cancellable);
                        if (!emitter.isCancelled() && emitter.requestedFromDownstream() > 0) {
                            // request the first chunk
                            dataSubscription.request(1);
                        }
                    }

                    @Override
                    public void onNext(ByteBuffer<?> buffer) {

                        try {
                            int len = buffer.readableBytes();

                            // a length of zero indicates the start of a new event
                            // emit the current event
                            if (len == 0) {
                                try {
                                    Event event = Event.of(byteBufferFactory.wrap(currentEvent.data))
                                            .name(currentEvent.name)
                                            .retry(currentEvent.retry)
                                            .id(currentEvent.id);
                                    emitter.next(
                                            event
                                    );
                                } finally {
                                    currentEvent = null;
                                }
                            } else {
                                if (currentEvent == null) {
                                    currentEvent = new CurrentEvent();
                                }
                                int colonIndex = buffer.indexOf((byte) ':');
                                // SSE comments start with colon, so skip
                                if (colonIndex > 0) {
                                    // obtain the type
                                    String type = buffer.slice(0, colonIndex).toString(StandardCharsets.UTF_8).trim();
                                    int fromIndex = colonIndex + 1;
                                    // skip the white space before the actual data
                                    if (buffer.getByte(fromIndex) == ((byte) ' ')) {
                                        fromIndex++;
                                    }
                                    if (fromIndex < len) {
                                        int toIndex = len - fromIndex;
                                        switch (type) {
                                            case "data":
                                                ByteBuffer content = buffer.slice(fromIndex, toIndex);
                                                byte[] d = currentEvent.data;
                                                if (d == null) {
                                                    currentEvent.data = content.toByteArray();
                                                } else {
                                                    currentEvent.data = ArrayUtils.concat(d, content.toByteArray());
                                                }


                                                break;
                                            case "id":
                                                ByteBuffer id = buffer.slice(fromIndex, toIndex);
                                                currentEvent.id = id.toString(StandardCharsets.UTF_8).trim();

                                                break;
                                            case "event":
                                                ByteBuffer event = buffer.slice(fromIndex, toIndex);
                                                currentEvent.name = event.toString(StandardCharsets.UTF_8).trim();

                                                break;
                                            case "retry":
                                                ByteBuffer retry = buffer.slice(fromIndex, toIndex);
                                                String text = retry.toString(StandardCharsets.UTF_8);
                                                if (!StringUtils.isEmpty(text)) {
                                                    Long millis = Long.valueOf(text);
                                                    currentEvent.retry = Duration.ofMillis(millis);
                                                }

                                                break;
                                            default:
                                                // ignore message
                                                break;
                                        }
                                    }
                                }
                            }

                            if (emitter.requestedFromDownstream() > 0 && !emitter.isCancelled()) {
                                dataSubscription.request(1);
                            }
                        } catch (Throwable e) {
                            onError(e);
                        } finally {
                            if (buffer instanceof ReferenceCounted counted) {
                                counted.release();
                            }
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        dataSubscription.cancel();
                        if (t instanceof HttpClientException) {
                            emitter.error(t);
                        } else {
                            emitter.error(decorate(new HttpClientException("Error consuming Server Sent Events: " + t.getMessage(), t)));
                        }
                    }

                    @Override
                    public void onComplete() {
                        emitter.complete();
                    }
                }), FluxSink.OverflowStrategy.BUFFER);
    }

    @Override
    public <I, B> Publisher<Event<B>> eventStream(@NonNull io.micronaut.http.HttpRequest<I> request,
                                                  @NonNull Argument<B> eventType) {
        setupConversionService(request);
        return eventStream(request, eventType, DEFAULT_ERROR_TYPE);
    }

    @Override
    public <I, B> Publisher<Event<B>> eventStream(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<B> eventType, @NonNull Argument<?> errorType) {
        setupConversionService(request);
        MessageBodyReader<B> reader = handlerRegistry.findReader(eventType, List.of(MediaType.APPLICATION_JSON_TYPE)).orElseThrow(() -> new CodecException("JSON codec not present"));
        return Flux.from(eventStreamOrError(request, errorType)).map(byteBufferEvent -> {
            ByteBuffer<?> data = byteBufferEvent.getData();

            B decoded = reader.read(eventType, MediaType.APPLICATION_JSON_TYPE, request.getHeaders(), data);
            return Event.of(byteBufferEvent, decoded);
        });
    }

    @Override
    public <I> Publisher<ByteBuffer<?>> dataStream(@NonNull io.micronaut.http.HttpRequest<I> request) {
        setupConversionService(request);
        return dataStream(request, DEFAULT_ERROR_TYPE);
    }

    @Override
    public <I> Publisher<ByteBuffer<?>> dataStream(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<?> errorType) {
        setupConversionService(request);
        final io.micronaut.http.HttpRequest<Object> parentRequest = ServerRequestContext.currentRequest().orElse(null);
        return new MicronautFlux<>(Flux.from(resolveRequestURI(request))
                .flatMap(requestURI -> dataStreamImpl(toMutableRequest(request), errorType, parentRequest, requestURI)))
                .doAfterNext(buffer -> {
                    Object o = buffer.asNativeBuffer();
                    if (o instanceof ByteBuf byteBuf) {
                        if (byteBuf.refCnt() > 0) {
                            ReferenceCountUtil.safeRelease(byteBuf);
                        }
                    }
                });
    }

    @Override
    public <I> Publisher<io.micronaut.http.HttpResponse<ByteBuffer<?>>> exchangeStream(@NonNull io.micronaut.http.HttpRequest<I> request) {
        return exchangeStream(request, DEFAULT_ERROR_TYPE);
    }

    @Override
    public <I> Publisher<io.micronaut.http.HttpResponse<ByteBuffer<?>>> exchangeStream(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<?> errorType) {
        setupConversionService(request);
        io.micronaut.http.HttpRequest<Object> parentRequest = ServerRequestContext.currentRequest().orElse(null);
        return new MicronautFlux<>(Flux.from(resolveRequestURI(request))
                .flatMap(uri -> exchangeStreamImpl(parentRequest, toMutableRequest(request), errorType, uri)))
                .doAfterNext(byteBufferHttpResponse -> {
                    ByteBuffer<?> buffer = byteBufferHttpResponse.body();
                    if (buffer instanceof ReferenceCounted counted) {
                        counted.release();
                    }
                });
    }

    @Override
    public <I, O> Publisher<O> jsonStream(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<O> type) {
        return jsonStream(request, type, DEFAULT_ERROR_TYPE);
    }

    @Override
    public <I, O> Publisher<O> jsonStream(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<O> type, @NonNull Argument<?> errorType) {
        setupConversionService(request);
        final io.micronaut.http.HttpRequest<Object> parentRequest = ServerRequestContext.currentRequest().orElse(null);
        setupConversionService(parentRequest);
        return Flux.from(resolveRequestURI(request))
                .flatMap(requestURI -> jsonStreamImpl(parentRequest, toMutableRequest(request), type, errorType, requestURI));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <I> Publisher<Map<String, Object>> jsonStream(@NonNull io.micronaut.http.HttpRequest<I> request) {
        return (Publisher) jsonStream(request, Map.class);
    }

    @Override
    public <I, O> Publisher<O> jsonStream(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Class<O> type) {
        setupConversionService(request);
        return jsonStream(request, io.micronaut.core.type.Argument.of(type));
    }

    @Override
    public <I, O, E> Publisher<io.micronaut.http.HttpResponse<O>> exchange(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<O> bodyType, @NonNull Argument<E> errorType) {
        return exchange(request, bodyType, errorType, null);
    }

    @NonNull
    private <I, O, E> Flux<HttpResponse<O>> exchange(io.micronaut.http.HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType, @Nullable BlockHint blockHint) {
        setupConversionService(request);
        final io.micronaut.http.HttpRequest<Object> parentRequest = ServerRequestContext.currentRequest().orElse(null);
        Publisher<URI> uriPublisher = resolveRequestURI(request);
        return Flux.from(uriPublisher)
            .switchMap(uri -> exchangeImpl(uri, parentRequest, toMutableRequest(request), bodyType, errorType, blockHint));
    }

    @Override
    public <I, O, E> Publisher<O> retrieve(io.micronaut.http.HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType) {
        setupConversionService(request);
        // mostly same as default impl, but with exception customization
        Flux<HttpResponse<O>> exchange = Flux.from(exchange(request, bodyType, errorType));
        if (bodyType.getType() == void.class) {
            // exchange() returns a HttpResponse<Void>, we can't map the Void body properly, so just drop it and complete
            return (Publisher<O>) exchange.ignoreElements();
        }
        return exchange.map(response -> {
            if (bodyType.getType() == HttpStatus.class) {
                return (O) response.getStatus();
            } else {
                Optional<O> body = response.getBody();
                if (!body.isPresent() && response.getBody(byte[].class).isPresent()) {
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
        });
    }

    @Override
    public <T extends AutoCloseable> Publisher<T> connect(Class<T> clientEndpointType, io.micronaut.http.MutableHttpRequest<?> request) {
        setupConversionService(request);
        Publisher<URI> uriPublisher = resolveRequestURI(request);
        return Flux.from(uriPublisher)
                .switchMap(resolvedURI -> connectWebSocket(resolvedURI, request, clientEndpointType, null));
    }

    @Override
    public <T extends AutoCloseable> Publisher<T> connect(Class<T> clientEndpointType, Map<String, Object> parameters) {
        WebSocketBean<T> webSocketBean = webSocketRegistry.getWebSocket(clientEndpointType);
        String uri = webSocketBean.getBeanDefinition().stringValue(ClientWebSocket.class).orElse("/ws");
        uri = UriTemplate.of(uri).expand(parameters);
        MutableHttpRequest<Object> request = io.micronaut.http.HttpRequest.GET(uri);
        Publisher<URI> uriPublisher = resolveRequestURI(request);

        return Flux.from(uriPublisher)
                .switchMap(resolvedURI -> connectWebSocket(resolvedURI, request, clientEndpointType, webSocketBean));

    }

    @Override
    public void close() {
        stop();
    }

    private <T> Publisher<T> connectWebSocket(URI uri, MutableHttpRequest<?> request, Class<T> clientEndpointType, WebSocketBean<T> webSocketBean) {
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
            conversionService);

        return connectionManager.connectForWebsocket(requestKey, handler)
            .then(handler.getHandshakeCompletedMono());
    }

    private <I> Flux<HttpResponse<ByteBuffer<?>>> exchangeStreamImpl(io.micronaut.http.HttpRequest<Object> parentRequest, MutableHttpRequest<I> request, Argument<?> errorType, URI requestURI) {
        Flux<HttpResponse<?>> streamResponsePublisher = Flux.from(buildStreamExchange(parentRequest, request, requestURI, errorType));
        return streamResponsePublisher.switchMap(response -> {
            StreamedHttpResponse streamedHttpResponse = NettyHttpResponseBuilder.toStreamResponse(response);
            Flux<HttpContent> httpContentReactiveSequence = Flux.from(streamedHttpResponse);
            return httpContentReactiveSequence
                    .filter(message -> !(message.content() instanceof EmptyByteBuf))
                    .map(message -> {
                        ByteBuf byteBuf = message.content();
                        if (log.isTraceEnabled()) {
                            log.trace("HTTP Client Streaming Response Received Chunk (length: {}) for Request: {} {}",
                                    byteBuf.readableBytes(), request.getMethodName(), request.getUri());
                            traceBody("Response", byteBuf);
                        }
                        ByteBuffer<?> byteBuffer = byteBufferFactory.wrap(byteBuf);
                        NettyStreamedHttpResponse<ByteBuffer<?>> thisResponse = new NettyStreamedHttpResponse<>(streamedHttpResponse, conversionService);
                        thisResponse.setBody(byteBuffer);
                        return (HttpResponse<ByteBuffer<?>>) new HttpResponseWrapper<>(thisResponse);
                    });
        });
    }

    private <I, O> Flux<O> jsonStreamImpl(io.micronaut.http.HttpRequest<?> parentRequest, MutableHttpRequest<I> request, Argument<O> type, Argument<?> errorType, URI requestURI) {
        Flux<HttpResponse<?>> streamResponsePublisher =
                Flux.from(buildStreamExchange(parentRequest, request, requestURI, errorType));
        return streamResponsePublisher.switchMap(response -> {
            if (!(response instanceof NettyStreamedHttpResponse)) {
                throw new IllegalStateException("Response has been wrapped in non streaming type. Do not wrap the response in client filters for stream requests");
            }

            StreamedHttpResponse streamResponse = NettyHttpResponseBuilder.toStreamResponse(response);

            // could also be application/json, in which case we will stream an array
            MediaType mediaType = response.getContentType().orElse(MediaType.APPLICATION_JSON_STREAM_TYPE);
            ChunkedMessageBodyReader<O> reader = (ChunkedMessageBodyReader<O>) handlerRegistry.findReader(type, List.of(mediaType)).orElseThrow(() -> new CodecException("Codec missing for media type " + mediaType));
            return reader.readChunked(type, mediaType, response.getHeaders(), Flux.from(streamResponse).map(c -> NettyByteBufferFactory.DEFAULT.wrap(c.content())));
        });
    }

    private <I> Flux<ByteBuffer<?>> dataStreamImpl(MutableHttpRequest<I> request, Argument<?> errorType, io.micronaut.http.HttpRequest<Object> parentRequest, URI requestURI) {
        Flux<HttpResponse<?>> streamResponsePublisher = Flux.from(buildStreamExchange(parentRequest, request, requestURI, errorType));
        Function<HttpContent, ByteBuffer<?>> contentMapper = message -> {
            ByteBuf byteBuf = message.content();
            return byteBufferFactory.wrap(byteBuf);
        };
        return streamResponsePublisher.switchMap(response -> {
                    if (!(response instanceof NettyStreamedHttpResponse)) {
                        throw new IllegalStateException("Response has been wrapped in non streaming type. Do not wrap the response in client filters for stream requests");
                    }
                    NettyStreamedHttpResponse nettyStreamedHttpResponse = (NettyStreamedHttpResponse) response;
                    Flux<HttpContent> httpContentReactiveSequence = Flux.from(nettyStreamedHttpResponse.getNettyResponse());
                    return httpContentReactiveSequence
                            .filter(message -> !(message.content() instanceof EmptyByteBuf))
                            .map(contentMapper);
                });
    }

    /**
     * Implementation of {@link #jsonStream}, {@link #dataStream}, {@link #exchangeStream}.
     */
    @SuppressWarnings("MagicNumber")
    private  <I> Publisher<MutableHttpResponse<?>> buildStreamExchange(
            @Nullable io.micronaut.http.HttpRequest<?> parentRequest,
            @NonNull MutableHttpRequest<I> request,
            @NonNull URI requestURI,
            @Nullable Argument<?> errorType) {

        AtomicReference<MutableHttpRequest<?>> requestWrapper = new AtomicReference<>(request);
        Flux<MutableHttpResponse<?>> streamResponsePublisher = connectAndStream(parentRequest, request, requestURI, requestWrapper, false, true);

        streamResponsePublisher = readBodyOnError(errorType, streamResponsePublisher);

        // apply filters
        streamResponsePublisher = Flux.from(
                applyFilterToResponsePublisher(parentRequest, request, requestURI, streamResponsePublisher)
        );

        return streamResponsePublisher;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> proxy(@NonNull io.micronaut.http.HttpRequest<?> request) {
        return proxy(request, ProxyRequestOptions.getDefault());
    }

    @Override
    public Publisher<MutableHttpResponse<?>> proxy(@NonNull io.micronaut.http.HttpRequest<?> request, @NonNull ProxyRequestOptions options) {
        Objects.requireNonNull(options, "options");
        setupConversionService(request);
        return Flux.from(resolveRequestURI(request))
                .flatMap(requestURI -> {
                    io.micronaut.http.MutableHttpRequest<?> httpRequest = toMutableRequest(request);
                    if (!options.isRetainHostHeader()) {
                        httpRequest.headers(headers -> headers.remove(HttpHeaderNames.HOST));
                    }

                    AtomicReference<MutableHttpRequest<?>> requestWrapper = new AtomicReference<>(httpRequest);
                    Flux<MutableHttpResponse<?>> proxyResponsePublisher = connectAndStream(request, request, requestURI, requestWrapper, true, false);
                    // apply filters
                    //noinspection unchecked
                    proxyResponsePublisher = Flux.from(
                            applyFilterToResponsePublisher(
                                    request,
                                    requestWrapper.get(),
                                    requestURI,
                                    (Publisher) proxyResponsePublisher
                            )
                    );
                    return proxyResponsePublisher;
                });
    }

    private void setupConversionService(io.micronaut.http.HttpRequest<?> httpRequest) {
        if (httpRequest instanceof ConversionServiceAware aware) {
            aware.setConversionService(conversionService);
        }
    }

    private <I> Flux<MutableHttpResponse<?>> connectAndStream(
            io.micronaut.http.HttpRequest<?> parentRequest,
            io.micronaut.http.HttpRequest<I> request,
            URI requestURI,
            AtomicReference<MutableHttpRequest<?>> requestWrapper,
            boolean isProxy,
            boolean failOnError
    ) {
        RequestKey requestKey;
        try {
            requestKey = new RequestKey(this, requestURI);
        } catch (Exception e) {
            return Flux.error(e);
        }
        return connectionManager.connect(requestKey, null).flatMapMany(poolHandle -> {
            request.setAttribute(NettyClientHttpRequest.CHANNEL, poolHandle.channel);

            boolean sse = !isProxy && isAcceptEvents(request);
            if (sse) {
                poolHandle.channel.pipeline().addLast(HttpLineBasedFrameDecoder.NAME, new HttpLineBasedFrameDecoder(configuration.getMaxContentLength(), true, true));
            }

            return this.streamRequestThroughChannel(
                parentRequest,
                requestWrapper.get(),
                poolHandle,
                failOnError,
                requestKey.isSecure()
            );
        });
    }

    /**
     * Implementation of {@link #exchange(io.micronaut.http.HttpRequest, Argument, Argument)} (after URI resolution).
     */
    private <I, O, E> Publisher<? extends io.micronaut.http.HttpResponse<O>> exchangeImpl(
        URI requestURI,
        io.micronaut.http.HttpRequest<?> parentRequest,
        MutableHttpRequest<I> request,
        @NonNull Argument<O> bodyType,
        @NonNull Argument<E> errorType,
        @Nullable BlockHint blockHint) {
        AtomicReference<MutableHttpRequest<?>> requestWrapper = new AtomicReference<>(request);

        RequestKey requestKey;
        try {
            requestKey = new RequestKey(this, requestURI);
        } catch (HttpClientException e) {
            return Flux.error(e);
        }

        Mono<ConnectionManager.PoolHandle> handlePublisher = connectionManager.connect(requestKey, blockHint);

        Flux<io.micronaut.http.HttpResponse<O>> responsePublisher = handlePublisher.flatMapMany(poolHandle -> {
            poolHandle.channel.pipeline()
                .addLast(ChannelPipelineCustomizer.HANDLER_HTTP_AGGREGATOR, new HttpObjectAggregator(configuration.getMaxContentLength()) {
                    @Override
                    protected void finishAggregation(FullHttpMessage aggregated) throws Exception {
                        // only set content-length if there's any content
                        if (!HttpUtil.isContentLengthSet(aggregated) &&
                            aggregated.content().readableBytes() > 0) {
                            super.finishAggregation(aggregated);
                        }
                    }
                });

            return Flux.create(emitter -> {
                try {
                    sendRequestThroughChannel(
                        requestWrapper.get(),
                        bodyType,
                        errorType,
                        emitter,
                        requestKey.isSecure(),
                        poolHandle
                    );
                } catch (Exception e) {
                    emitter.error(e);
                }
            });
        });

        Publisher<io.micronaut.http.HttpResponse<O>> finalPublisher = applyFilterToResponsePublisher(
            parentRequest,
            request,
            requestURI,
            responsePublisher
        );
        Flux<io.micronaut.http.HttpResponse<O>> finalReactiveSequence = Flux.from(finalPublisher);
        // apply timeout to flowable too in case a filter applied another policy
        Optional<Duration> readTimeout = configuration.getReadTimeout();
        if (readTimeout.isPresent()) {
            // add an additional second, because generally the timeout should occur
            // from the Netty request handling pipeline
            final Duration rt = readTimeout.get();
            if (!rt.isNegative()) {
                Duration duration = rt.plus(Duration.ofSeconds(1));
                finalReactiveSequence = finalReactiveSequence.timeout(duration) // todo: move to CM
                    .onErrorResume(throwable -> {
                        if (throwable instanceof TimeoutException) {
                            return Flux.error(ReadTimeoutException.TIMEOUT_EXCEPTION);
                        }
                        return Flux.error(throwable);
                    });
            }
        }
        return finalReactiveSequence;
    }

    /**
     * @param request The request
     * @param <I>     The input type
     * @return A {@link Publisher} with the resolved URI
     */
    protected <I> Publisher<URI> resolveRequestURI(io.micronaut.http.HttpRequest<I> request) {
        return resolveRequestURI(request, true);
    }

    /**
     * @param request            The request
     * @param includeContextPath Whether to prepend the client context path
     * @param <I>                The input type
     * @return A {@link Publisher} with the resolved URI
     */
    protected <I> Publisher<URI> resolveRequestURI(io.micronaut.http.HttpRequest<I> request, boolean includeContextPath) {
        URI requestURI = request.getUri();
        if (requestURI.getScheme() != null) {
            // if the request URI includes a scheme then it is fully qualified so use the direct server
            return Flux.just(requestURI);
        } else {
            return resolveURI(request, includeContextPath);
        }
    }

    /**
     * @param parentRequest      The parent request
     * @param request            The redirect location request
     * @param <I>                The input type
     * @return A {@link Publisher} with the resolved URI
     */
    protected <I> Publisher<URI> resolveRedirectURI(io.micronaut.http.HttpRequest<?> parentRequest, io.micronaut.http.HttpRequest<I> request) {
        URI requestURI = request.getUri();
        if (requestURI.getScheme() != null) {
            // if the request URI includes a scheme then it is fully qualified so use the direct server
            return Flux.just(requestURI);
        } else {
            if (parentRequest == null || parentRequest.getUri().getHost() == null) {
                return resolveURI(request, false);
            } else {
                URI parentURI = parentRequest.getUri();
                UriBuilder uriBuilder = UriBuilder.of(requestURI)
                        .scheme(parentURI.getScheme())
                        .userInfo(parentURI.getUserInfo())
                        .host(parentURI.getHost())
                        .port(parentURI.getPort());
                return Flux.just(uriBuilder.build());
            }
        }
    }

    /**
     * @return The discriminator to use when selecting a server for the purposes of load balancing (defaults to null)
     */
    protected Object getLoadBalancerDiscriminator() {
        return null;
    }

    private <I, R extends io.micronaut.http.HttpResponse<?>> Publisher<R> applyFilterToResponsePublisher(
            io.micronaut.http.HttpRequest<?> parentRequest,
            io.micronaut.http.HttpRequest<I> request,
            URI requestURI,
            Publisher<R> responsePublisher) {

        if (!(request instanceof MutableHttpRequest<?> mutRequest)) {
            return responsePublisher;
        }

        mutRequest.uri(requestURI);
        if (informationalServiceId != null && mutRequest.getAttribute(HttpAttributes.SERVICE_ID).isEmpty()) {

            mutRequest.setAttribute(HttpAttributes.SERVICE_ID, informationalServiceId);
        }

        List<GenericHttpFilter> filters =
                filterResolver.resolveFilters(request, clientFilterEntries);
        if (parentRequest != null) {
            // todo: migrate to new filter
            filters.add(
                GenericHttpFilter.createLegacyFilter(new ClientServerContextFilter(parentRequest), new FilterOrder.Fixed(Ordered.HIGHEST_PRECEDENCE))
            );
        }

        FilterRunner.sortReverse(filters);
        filters.add(GenericHttpFilter.terminalReactiveFilter(responsePublisher));

        FilterRunner runner = new FilterRunner(filters);
        Mono<R> responseMono = Mono.from(ReactiveExecutionFlow.fromFlow((ExecutionFlow<R>) runner.run(request)).toPublisher());
        if (parentRequest != null) {
            responseMono = responseMono.contextWrite(c -> {
                // existing entry takes precedence. The parentRequest is derived from a thread
                // local, and is more likely to be wrong than any reactive context we are fed.
                if (c.hasKey(ServerRequestContext.KEY)) {
                    return c;
                } else {
                    return c.put(ServerRequestContext.KEY, parentRequest);
                }
            });
        }
        return responseMono;
    }

    /**
     * @param request                The request
     * @param requestURI             The URI of the request
     * @param requestContentType     The request content type
     * @param permitsBody            Whether permits body
     * @param onError                Called when the body publisher encounters an error
     * @return A {@link NettyRequestWriter}
     * @throws HttpPostRequestEncoder.ErrorDataEncoderException if there is an encoder exception
     */
    protected NettyRequestWriter buildNettyRequest(
        MutableHttpRequest request,
        URI requestURI,
        MediaType requestContentType,
        boolean permitsBody,
        Consumer<? super Throwable> onError) throws HttpPostRequestEncoder.ErrorDataEncoderException {

        NettyHttpRequestBuilder nettyRequestBuilder = NettyHttpRequestBuilder.asBuilder(request);
        Optional<HttpRequest> direct = nettyRequestBuilder.toHttpRequestDirect();
        String newUri = requestURI.getRawPath();
        if (requestURI.getRawQuery() != null) {
            newUri += "?" + requestURI.getRawQuery();
        }
        if (direct.isPresent()) {
            if (direct.get() instanceof StreamedHttpRequest shr) {
                return new ReactiveRequestWriter(direct.get().setUri(newUri), shr);
            } else {
                return new FullRequestWriter((FullHttpRequest) direct.get().setUri(newUri));
            }
        }

        HttpPostRequestEncoder postRequestEncoder = null;
        if (permitsBody) {
            Optional body = request.getBody();
            boolean hasBody = body.isPresent();
            if (requestContentType.equals(MediaType.APPLICATION_FORM_URLENCODED_TYPE) && hasBody) {
                Object bodyValue = body.get();
                if (bodyValue instanceof CharSequence sequence) {
                    ByteBuf byteBuf = charSequenceToByteBuf(sequence, requestContentType);
                    FullHttpRequest nettyRequest = withBytes(nettyRequestBuilder.toHttpRequestWithoutBody(), byteBuf);
                    nettyRequest.setUri(newUri);
                    return new FullRequestWriter(nettyRequest);
                } else {
                    postRequestEncoder = buildFormDataRequest(request, bodyValue);
                    return postToWriter(newUri, postRequestEncoder);
                }
            } else if (requestContentType.equals(MediaType.MULTIPART_FORM_DATA_TYPE) && hasBody) {
                Object bodyValue = body.get();
                postRequestEncoder = buildMultipartRequest(request, bodyValue);
                return postToWriter(newUri, postRequestEncoder);
            } else {
                ByteBuf bodyContent = null;
                if (hasBody) {
                    Object bodyValue = body.get();
                    DynamicMessageBodyWriter dynamicWriter = new DynamicMessageBodyWriter(handlerRegistry, List.of(requestContentType));
                    if (Publishers.isConvertibleToPublisher(bodyValue)) {
                        boolean isSingle = Publishers.isSingle(bodyValue.getClass());

                        Publisher<?> publisher = conversionService.convert(bodyValue, Publisher.class).orElseThrow(() ->
                            new IllegalArgumentException("Unconvertible reactive type: " + bodyValue)
                        );

                        Flux<HttpContent> requestBodyPublisher = Flux.from(publisher).map(o -> {
                            ByteBuffer<?> buffer = dynamicWriter.writeTo(Argument.OBJECT_ARGUMENT, requestContentType, o, request.getHeaders(), byteBufferFactory);
                            return new DefaultHttpContent(((ByteBuf) buffer.asNativeBuffer()));
                        });

                        if (!isSingle && MediaType.APPLICATION_JSON_TYPE.equals(requestContentType)) {
                            requestBodyPublisher = JsonSubscriber.lift(requestBodyPublisher);
                        }

                        requestBodyPublisher = requestBodyPublisher.doOnError(onError);

                        HttpRequest nettyRequest = nettyRequestBuilder.toHttpRequestWithoutBody();
                        nettyRequest.setUri(newUri);
                        return new ReactiveRequestWriter(nettyRequest, requestBodyPublisher);
                    } else if (bodyValue instanceof CharSequence sequence) {
                        bodyContent = charSequenceToByteBuf(sequence, requestContentType);
                    } else {
                        ByteBuffer<?> buffer = dynamicWriter.writeTo(Argument.OBJECT_ARGUMENT, requestContentType, bodyValue, request.getHeaders(), byteBufferFactory);
                        bodyContent = (ByteBuf) buffer.asNativeBuffer();
                    }
                    if (bodyContent == null) {
                        bodyContent = conversionService.convert(bodyValue, ByteBuf.class).orElseThrow(() ->
                            decorate(new HttpClientException("Body [" + bodyValue + "] cannot be encoded to content type [" + requestContentType + "]. No possible codecs or converters found."))
                        );
                    }
                } else {
                    bodyContent = Unpooled.EMPTY_BUFFER;
                }
                FullHttpRequest nettyRequest = withBytes(nettyRequestBuilder.toHttpRequestWithoutBody(), bodyContent);
                nettyRequest.setUri(newUri);
                return new FullRequestWriter(nettyRequest);
            }
        } else {
            FullHttpRequest nettyRequest = withBytes(nettyRequestBuilder.toHttpRequestWithoutBody(), Unpooled.EMPTY_BUFFER);
            nettyRequest.setUri(newUri);
            return new FullRequestWriter(nettyRequest);
        }
    }

    private static NettyRequestWriter postToWriter(String newUri, HttpPostRequestEncoder postRequestEncoder) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        HttpRequest nettyRequest = postRequestEncoder.finalizeRequest();
        nettyRequest.setUri(newUri);
        if (postRequestEncoder.isChunked()) {
            return new ChunkedPostRequestWriter(nettyRequest, postRequestEncoder);
        } else {
            return new FullRequestWriter((FullHttpRequest) nettyRequest);
        }
    }

    private static FullHttpRequest withBytes(HttpRequest request, ByteBuf bytes) {
        HttpHeaders headers = request.headers();
        headers.remove(HttpHeaderNames.TRANSFER_ENCODING);
        headers.set(HttpHeaderNames.CONTENT_LENGTH, bytes.readableBytes());
        return new DefaultFullHttpRequest(
            request.protocolVersion(),
            request.method(),
            request.uri(),
            bytes,
            headers,
            LastHttpContent.EMPTY_LAST_CONTENT.trailingHeaders()
        );
    }

    private Flux<MutableHttpResponse<?>> readBodyOnError(@Nullable Argument<?> errorType, @NonNull Flux<MutableHttpResponse<?>> publisher) {
        if (errorType != null && errorType != HttpClient.DEFAULT_ERROR_TYPE) {
            return publisher.onErrorResume(clientException -> {
                if (clientException instanceof HttpClientResponseException exception) {
                    final HttpResponse<?> response = exception.getResponse();
                    if (response instanceof NettyStreamedHttpResponse streamedResponse) {
                        return Mono.create(emitter -> {
                            final StreamedHttpResponse nettyResponse = streamedResponse.getNettyResponse();
                            nettyResponse.subscribe(new Subscriber<HttpContent>() {
                                final CompositeByteBuf buffer = byteBufferFactory.getNativeAllocator().compositeBuffer();
                                Subscription s;
                                @Override
                                public void onSubscribe(Subscription s) {
                                    this.s = s;
                                    s.request(1);
                                }

                                @Override
                                public void onNext(HttpContent httpContent) {
                                    buffer.addComponent(true, httpContent.content());
                                    s.request(1);
                                }

                                @Override
                                public void onError(Throwable t) {
                                    buffer.release();
                                    emitter.error(t);
                                }

                                @Override
                                public void onComplete() {
                                    try {
                                        FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(nettyResponse.protocolVersion(), nettyResponse.status(), buffer, nettyResponse.headers(), new DefaultHttpHeaders(true));
                                        final FullNettyClientHttpResponse<Object> fullNettyClientHttpResponse = new FullNettyClientHttpResponse<>(fullHttpResponse, handlerRegistry, (Argument<Object>) errorType, true, conversionService);
                                        fullNettyClientHttpResponse.onComplete();
                                        emitter.error(decorate(new HttpClientResponseException(
                                            fullHttpResponse.status().reasonPhrase(),
                                            null,
                                            fullNettyClientHttpResponse,
                                            new HttpClientErrorDecoder() {
                                                @Override
                                                public Argument<?> getErrorType(MediaType mediaType) {
                                                    return errorType;
                                                }
                                            }
                                        )));
                                    } finally {
                                        buffer.release();
                                    }
                                }
                            });
                        });
                    }
                }
                return Mono.error(clientException);
            });
        }
        return publisher;
    }

    private <I> Publisher<URI> resolveURI(io.micronaut.http.HttpRequest<I> request, boolean includeContextPath) {
        URI requestURI = request.getUri();
        if (loadBalancer == null) {
            return Flux.error(decorate(new NoHostException("Request URI specifies no host to connect to")));
        }

        return Flux.from(loadBalancer.select(getLoadBalancerDiscriminator())).map(server -> {
                    Optional<String> authInfo = server.getMetadata().get(io.micronaut.http.HttpHeaders.AUTHORIZATION_INFO, String.class);
                    if (request instanceof MutableHttpRequest httpRequest && authInfo.isPresent()) {
                        httpRequest.getHeaders().auth(authInfo.get());
                    }

                    try {
                        return server.resolve(includeContextPath ? ContextPathUtils.prepend(requestURI, contextPath) : requestURI);
                    } catch (URISyntaxException e) {
                        throw decorate(new HttpClientException("Failed to construct the request URI", e));
                    }
                }
        );
    }

    private <I, O, E> void sendRequestThroughChannel(
            io.micronaut.http.HttpRequest<I> finalRequest,
            Argument<O> bodyType,
            Argument<E> errorType,
            FluxSink<? super HttpResponse<O>> emitter,
            boolean secure,
            ConnectionManager.PoolHandle poolHandle) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        URI requestURI = finalRequest.getUri();
        MediaType requestContentType = finalRequest
                .getContentType()
                .orElse(MediaType.APPLICATION_JSON_TYPE);

        boolean permitsBody = io.micronaut.http.HttpMethod.permitsRequestBody(finalRequest.getMethod());

        MutableHttpRequest clientHttpRequest = (MutableHttpRequest) finalRequest;
        NettyRequestWriter requestWriter = buildNettyRequest(
                clientHttpRequest,
            requestURI,
            requestContentType,
            permitsBody,
            throwable -> {
                if (!emitter.isCancelled()) {
                    emitter.error(throwable);
                }
            }
        );
        HttpRequest nettyRequest = requestWriter.nettyRequest();

        prepareHttpHeaders(
            poolHandle,
            requestURI,
            finalRequest,
            nettyRequest,
            permitsBody
        );

        if (log.isDebugEnabled()) {
            debugRequest(requestURI, nettyRequest);
        }

        if (log.isTraceEnabled()) {
            traceRequest(finalRequest, nettyRequest);
        }

        Promise<HttpResponse<O>> responsePromise = poolHandle.channel.eventLoop().newPromise();
        poolHandle.channel.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE,
            new FullHttpResponseHandler<>(responsePromise, poolHandle, finalRequest, bodyType, errorType));
        poolHandle.notifyRequestPipelineBuilt();
        Publisher<HttpResponse<O>> publisher = new NettyFuturePublisher<>(responsePromise, true);
        publisher.subscribe(new ForwardingSubscriber<>(emitter));

        requestWriter.write(poolHandle);
    }

    private Flux<MutableHttpResponse<?>> streamRequestThroughChannel(
            io.micronaut.http.HttpRequest<?> parentRequest,
            MutableHttpRequest<?> request,
            ConnectionManager.PoolHandle poolHandle,
            boolean failOnError,
            boolean secure) {
        return Flux.<MutableHttpResponse<?>>create(sink -> {
            try {
                streamRequestThroughChannel0(parentRequest, request, sink, poolHandle);
            } catch (HttpPostRequestEncoder.ErrorDataEncoderException e) {
                sink.error(e);
            }
        }).flatMap(resp -> handleStreamHttpError(resp, failOnError));
    }

    private <R extends HttpResponse<?>> Flux<R> handleStreamHttpError(
            R response,
            boolean failOnError
    ) {
        boolean errorStatus = response.code() >= 400;
        if (errorStatus && failOnError) {
            return Flux.error(decorate(new HttpClientResponseException(response.reason(), response)));
        } else {
            return Flux.just(response);
        }
    }

    private void streamRequestThroughChannel0(
        io.micronaut.http.HttpRequest<?> parentRequest,
        MutableHttpRequest<?> request,
        FluxSink<? super MutableHttpResponse<?>> emitter,
        ConnectionManager.PoolHandle poolHandle) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        URI requestURI = request.getUri();
        boolean permitsBody = io.micronaut.http.HttpMethod.permitsRequestBody(request.getMethod());
        NettyRequestWriter requestWriter = buildNettyRequest(
            request,
            requestURI,
            request
                .getContentType()
                .orElse(MediaType.APPLICATION_JSON_TYPE),
            permitsBody,
            throwable -> {
                if (!emitter.isCancelled()) {
                    emitter.error(throwable);
                }
            }
        );
        prepareHttpHeaders(poolHandle, requestURI, request, requestWriter.nettyRequest(), permitsBody);

        HttpRequest nettyRequest = requestWriter.nettyRequest();
        Promise<MutableHttpResponse<?>> responsePromise = poolHandle.channel.eventLoop().newPromise();
        ChannelPipeline pipeline = poolHandle.channel.pipeline();
        pipeline.addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE, new StreamHttpResponseHandler(responsePromise, parentRequest, request, poolHandle));
        poolHandle.notifyRequestPipelineBuilt();

        if (log.isDebugEnabled()) {
            debugRequest(request.getUri(), nettyRequest);
        }

        if (log.isTraceEnabled()) {
            traceRequest(request, nettyRequest);
        }

        requestWriter.write(poolHandle);
        responsePromise.addListener((Future<MutableHttpResponse<?>> future) -> {
            if (future.isSuccess()) {
                emitter.next(future.getNow());
                emitter.complete();
            } else {
                emitter.error(future.cause());
            }
        });
    }

    private ByteBuf charSequenceToByteBuf(CharSequence bodyValue, MediaType requestContentType) {
        CharSequence charSequence = bodyValue;
        return byteBufferFactory.copiedBuffer(
                charSequence.toString().getBytes(
                        requestContentType.getCharset().orElse(defaultCharset)
                )
        ).asNativeBuffer();
    }

    private String getHostHeader(URI requestURI) {
        RequestKey requestKey = new RequestKey(this, requestURI);
        StringBuilder host = new StringBuilder(requestKey.getHost());
        int port = requestKey.getPort();
        if (port > -1 && port != 80 && port != 443) {
            host.append(":").append(port);
        }
        return host.toString();
    }

    private <I> void prepareHttpHeaders(
        ConnectionManager.PoolHandle poolHandle,
        URI requestURI,
        io.micronaut.http.HttpRequest<I> request,
        HttpRequest nettyRequest,
        boolean permitsBody) {
        HttpHeaders headers = nettyRequest.headers();

        if (!headers.contains(HttpHeaderNames.HOST)) {
            headers.set(HttpHeaderNames.HOST, getHostHeader(requestURI));
        }

        if (!poolHandle.http2) {
            if (poolHandle.canReturn()) {
                headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            } else {
                headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            }
        }

        if (permitsBody) {
            Optional<I> body = request.getBody();
            if (body.isPresent()) {
                if (!headers.contains(HttpHeaderNames.CONTENT_TYPE)) {
                    MediaType mediaType = request.getContentType().orElse(MediaType.APPLICATION_JSON_TYPE);
                    headers.set(HttpHeaderNames.CONTENT_TYPE, mediaType);
                }
                if (nettyRequest instanceof FullHttpRequest fullHttpRequest) {
                    headers.set(HttpHeaderNames.CONTENT_LENGTH, fullHttpRequest.content().readableBytes());
                } else {
                    if (!headers.contains(HttpHeaderNames.CONTENT_LENGTH) && !headers.contains(HttpHeaderNames.TRANSFER_ENCODING)) {
                        headers.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                    }
                }
            } else if (nettyRequest instanceof FullHttpRequest full) {
                headers.set(HttpHeaderNames.CONTENT_LENGTH, full.content().readableBytes());
            }
        }
    }

    private HttpPostRequestEncoder buildFormDataRequest(MutableHttpRequest clientHttpRequest, Object bodyValue) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        HttpPostRequestEncoder postRequestEncoder = new HttpPostRequestEncoder(NettyHttpRequestBuilder.asBuilder(clientHttpRequest).toHttpRequestWithoutBody(), false);

        Map<String, Object> formData;
        if (bodyValue instanceof Map) {
            formData = (Map<String, Object>) bodyValue;
        } else {
            formData = BeanMap.of(bodyValue);
        }
        for (Map.Entry<String, Object> entry : formData.entrySet()) {
            Object value = entry.getValue();
            if (value != null) {
                if (value instanceof Collection collection) {
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

    private HttpPostRequestEncoder buildMultipartRequest(MutableHttpRequest clientHttpRequest, Object bodyValue) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        HttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);
        io.netty.handler.codec.http.HttpRequest request = NettyHttpRequestBuilder.asBuilder(clientHttpRequest).toHttpRequestWithoutBody();
        HttpPostRequestEncoder postRequestEncoder = new HttpPostRequestEncoder(factory, request, true, CharsetUtil.UTF_8, HttpPostRequestEncoder.EncoderMode.HTML5);
        if (bodyValue instanceof MultipartBody.Builder builder) {
            bodyValue = builder.build();
        }
        if (bodyValue instanceof MultipartBody multipartBody) {
            postRequestEncoder.setBodyHttpDatas(multipartBody.getData(new MultipartDataFactory<InterfaceHttpData>() {
                @NonNull
                @Override
                public InterfaceHttpData createFileUpload(@NonNull String name, @NonNull String filename, @NonNull MediaType contentType, @Nullable String encoding, @Nullable Charset charset, long length) {
                    return factory.createFileUpload(
                            request,
                            name,
                            filename,
                            contentType.toString(),
                            encoding,
                            charset,
                            length
                    );
                }

                @NonNull
                @Override
                public InterfaceHttpData createAttribute(@NonNull String name, @NonNull String value) {
                    return factory.createAttribute(
                            request,
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

    private void debugRequest(URI requestURI, io.netty.handler.codec.http.HttpRequest nettyRequest) {
        log.debug("Sending HTTP {} to {}",
                nettyRequest.method(),
                requestURI.toString());
    }

    private void traceRequest(io.micronaut.http.HttpRequest<?> request, io.netty.handler.codec.http.HttpRequest nettyRequest) {
        HttpHeaders headers = nettyRequest.headers();
        HttpHeadersUtil.trace(log, headers.names(), headers::getAll);
        if (io.micronaut.http.HttpMethod.permitsRequestBody(request.getMethod()) && request.getBody().isPresent() && nettyRequest instanceof FullHttpRequest fullHttpRequest) {
            ByteBuf content = fullHttpRequest.content();
            if (log.isTraceEnabled()) {
                traceBody("Request", content);
            }
        }
    }

    private void traceBody(String type, ByteBuf content) {
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
        ContextlessMessageBodyHandlerRegistry registry = new ContextlessMessageBodyHandlerRegistry(applicationConfiguration, NettyByteBufferFactory.DEFAULT, new ByteBufRawMessageBodyHandler(), new NettyWritableBodyWriter(applicationConfiguration));
        JsonMapper mapper = JsonMapper.createDefault();
        registry.add(MediaType.APPLICATION_JSON_TYPE, new NettyJsonHandler<>(mapper));
        registry.add(MediaType.APPLICATION_JSON_STREAM_TYPE, new NettyJsonStreamHandler<>(mapper));
        return registry;
    }

    static boolean isSecureScheme(String scheme) {
        return io.micronaut.http.HttpRequest.SCHEME_HTTPS.equalsIgnoreCase(scheme) || SCHEME_WSS.equalsIgnoreCase(scheme);
    }

    private <E extends HttpClientException> E decorate(E exc) {
        return HttpClientExceptionUtils.populateServiceId(exc, informationalServiceId, configuration);
    }

    /**
     * Key used for connection pooling and determining host/port.
     */
    public static final class RequestKey {
        private final String host;
        private final int port;
        private final boolean secure;

        /**
         * @param ctx The HTTP client that created this request key. Only used for exception
         *            context, not stored
         * @param requestURI The request URI
         */
        public RequestKey(DefaultHttpClient ctx, URI requestURI) {
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
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            RequestKey that = (RequestKey) o;
            return port == that.port &&
                    secure == that.secure &&
                    Objects.equals(host, that.host);
        }

        @Override
        public int hashCode() {
            return ObjectUtils.hash(host, port, secure);
        }

        private <E extends HttpClientException> E decorate(DefaultHttpClient ctx, E exc) {
            return HttpClientExceptionUtils.populateServiceId(exc, ctx.informationalServiceId, ctx.configuration);
        }
    }

    /**
     * A Netty request writer.
     */
    private interface NettyRequestWriter {
        /**
         * @param poolHandle The pool handle to write to. Can be tainted, but should not be
         *                   released by the writer
         */
        void write(ConnectionManager.PoolHandle poolHandle);

        HttpRequest nettyRequest();
    }

    private abstract static class ContinueHandler extends ChannelInboundHandlerAdapter {
        private boolean continued;

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            if (!continued) {
                discard();
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            io.netty.handler.codec.http.HttpResponse response = (io.netty.handler.codec.http.HttpResponse) msg;
            if (response.status() == HttpResponseStatus.CONTINUE) {
                continued = true;
                continueBody(ctx);
            }
            ctx.pipeline().remove(this);
        }

        protected abstract void discard();

        protected abstract void continueBody(ChannelHandlerContext ctx);
    }

    private record FullRequestWriter(FullHttpRequest nettyRequest) implements NettyRequestWriter {
        @Override
        public void write(ConnectionManager.PoolHandle poolHandle) {
            Channel channel = poolHandle.channel();
            if (HttpUtil.is100ContinueExpected(nettyRequest)) {
                channel.pipeline().addBefore(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE, "continue-handler", new ContinueHandler() {
                    @Override
                    protected void discard() {
                        nettyRequest.release();
                    }

                    @Override
                    protected void continueBody(ChannelHandlerContext ctx) {
                        ctx.writeAndFlush(new DefaultLastHttpContent(nettyRequest.content()), ctx.voidPromise());
                    }
                });
                channel.writeAndFlush(new DefaultHttpRequest(
                    nettyRequest.protocolVersion(),
                    nettyRequest.method(),
                    nettyRequest.uri(),
                    nettyRequest.headers()
                ), channel.voidPromise());
            } else {
                channel.writeAndFlush(nettyRequest, channel.voidPromise());
            }
        }
    }

    private record ReactiveRequestWriter(
        HttpRequest nettyRequest,
        Publisher<HttpContent> data
    ) implements NettyRequestWriter {
        @Override
        public void write(ConnectionManager.PoolHandle poolHandle) {
            Channel channel = poolHandle.channel();
            channel.writeAndFlush(nettyRequest, channel.voidPromise());
            if (HttpUtil.is100ContinueExpected(nettyRequest)) {
                channel.pipeline().addBefore(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE, "continue-handler", new ContinueHandler() {
                    @Override
                    protected void discard() {
                        if (data instanceof HotObservable<HttpContent> hot) {
                            hot.closeIfNoSubscriber();
                        }
                    }

                    @Override
                    protected void continueBody(ChannelHandlerContext ctx) {
                        channel.pipeline().addLast(new ReactiveClientWriter(data));
                    }
                });
            } else {
                channel.pipeline().addLast(new ReactiveClientWriter(data));
            }
        }
    }

    private record ChunkedPostRequestWriter(HttpRequest nettyRequest,
                                            HttpPostRequestEncoder encoder) implements NettyRequestWriter {

        @Override
        public void write(ConnectionManager.PoolHandle poolHandle) {
            Channel channel = poolHandle.channel();
            channel.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_HTTP_CHUNK, new ChunkedWriteHandler());
            if (HttpUtil.is100ContinueExpected(nettyRequest)) {
                channel.writeAndFlush(nettyRequest, channel.voidPromise());
                channel.pipeline().addBefore(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE, "continue-handler", new ContinueHandler() {
                    @Override
                    protected void discard() {
                        encoder.cleanFiles();
                        channel.pipeline().remove(ChannelPipelineCustomizer.HANDLER_HTTP_CHUNK);
                    }

                    @Override
                    protected void continueBody(ChannelHandlerContext ctx) {
                        channel.writeAndFlush(encoder).addListener((ChannelFutureListener) future -> {
                            if (!future.isSuccess()) {
                                channel.pipeline().fireExceptionCaught(future.cause());
                            }
                            discard();
                        });
                    }
                });
            } else {
                channel.write(nettyRequest, channel.voidPromise());
                channel.writeAndFlush(encoder).addListener((ChannelFutureListener) future -> {
                    if (!future.isSuccess()) {
                        channel.pipeline().fireExceptionCaught(future.cause());
                    }
                    encoder.cleanFiles();
                    channel.pipeline().remove(ChannelPipelineCustomizer.HANDLER_HTTP_CHUNK);
                });
            }
        }
    }

    /**
     * Used as a holder for the current SSE event.
     */
    private static class CurrentEvent {
        byte[] data;
        String id;
        String name;
        Duration retry;
    }

    private abstract class BaseHttpResponseHandler<O> extends SimpleChannelInboundHandlerInstrumented<HttpObject> {
        final Promise<? super O> responsePromise;
        final io.micronaut.http.HttpRequest<?> parentRequest;
        final io.micronaut.http.HttpRequest<?> finalRequest;

        public BaseHttpResponseHandler(Promise<? super O> responsePromise, io.micronaut.http.HttpRequest<?> parentRequest, io.micronaut.http.HttpRequest<?> finalRequest) {
            super(false);
            this.responsePromise = responsePromise;
            this.parentRequest = parentRequest;
            this.finalRequest = finalRequest;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            ctx.read();
        }

        @Override
        public final boolean acceptInboundMessage(Object msg) {
            return msg instanceof HttpObject;
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            String message = cause.getMessage();
            if (message == null) {
                message = cause.getClass().getSimpleName();
            }
            if (log.isTraceEnabled()) {
                log.trace("HTTP Client exception ({}) occurred for request : {} {}",
                    message, finalRequest.getMethodName(), finalRequest.getUri());
            }

            HttpClientException result;
            if (cause instanceof TooLongFrameException) {
                result = decorate(new ContentLengthExceededException(configuration.getMaxContentLength()));
            } else if (cause instanceof io.netty.handler.timeout.ReadTimeoutException) {
                result = ReadTimeoutException.TIMEOUT_EXCEPTION;
            } else {
                result = decorate(new HttpClientException("Error occurred reading HTTP response: " + message, cause));
            }
            responsePromise.tryFailure(result);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            // connection became inactive before this handler was removed (i.e. before channelRead)
            responsePromise.tryFailure(new ResponseClosedException("Connection closed before response was received"));
            ctx.fireChannelInactive();
        }

        private static void setRedirectHeaders(@Nullable io.micronaut.http.HttpRequest<?> request, MutableHttpRequest<Object> redirectRequest) {
            if (request != null) {
                for (Map.Entry<String, List<String>> originalHeader : request.getHeaders()) {
                    if (!REDIRECT_HEADER_BLOCKLIST.contains(originalHeader.getKey())) {
                        final List<String> originalHeaderValue = originalHeader.getValue();
                        if (originalHeaderValue != null && !originalHeaderValue.isEmpty()) {
                            for (String value : originalHeaderValue) {
                                if (value != null) {
                                    redirectRequest.header(originalHeader.getKey(), value);
                                }
                            }
                        }
                    }
                }
            }
        }

        protected final boolean handleResponse(io.netty.handler.codec.http.HttpResponse msg) {
            int code = msg.status().code();
            HttpHeaders headers1 = msg.headers();
            if (code > 300 && code < 400 && configuration.isFollowRedirects() && headers1.contains(HttpHeaderNames.LOCATION)) {
                String location = headers1.get(HttpHeaderNames.LOCATION);

                MutableHttpRequest<Object> redirectRequest;
                if (code == 307 || code == 308) {
                    redirectRequest = io.micronaut.http.HttpRequest.create(finalRequest.getMethod(), location);
                    finalRequest.getBody().ifPresent(redirectRequest::body);
                } else {
                    redirectRequest = io.micronaut.http.HttpRequest.GET(location);
                }

                setRedirectHeaders(finalRequest, redirectRequest);
                Flux.from(resolveRedirectURI(parentRequest, redirectRequest))
                    .flatMap(makeRedirectHandler(parentRequest, redirectRequest))
                    .subscribe(new NettyPromiseSubscriber<>(responsePromise));
                return false;
            } else {
                HttpHeaders headers = msg.headers();
                if (log.isTraceEnabled()) {
                    log.trace("HTTP Client Response Received ({}) for Request: {} {}", msg.status(), finalRequest.getMethodName(), finalRequest.getUri());
                    HttpHeadersUtil.trace(log, headers.names(), headers::getAll);
                }
                return true;
            }
        }

        protected abstract Function<URI, Publisher<? extends O>> makeRedirectHandler(io.micronaut.http.HttpRequest<?> parentRequest, MutableHttpRequest<Object> redirectRequest);
    }

    private class FullHttpResponseHandler<O> extends BaseHttpResponseHandler<HttpResponse<O>> {
        private final Argument<O> bodyType;
        private final Argument<?> errorType;
        private final ConnectionManager.PoolHandle poolHandle;

        public FullHttpResponseHandler(
            Promise<HttpResponse<O>> responsePromise,
            ConnectionManager.PoolHandle poolHandle,
            io.micronaut.http.HttpRequest<?> request,
            Argument<O> bodyType,
            Argument<?> errorType) {
            super(responsePromise, request, request);
            this.bodyType = bodyType;
            this.errorType = errorType;
            this.poolHandle = poolHandle;
        }

        @Override
        protected Function<URI, Publisher<? extends HttpResponse<O>>> makeRedirectHandler(io.micronaut.http.HttpRequest<?> parentRequest, MutableHttpRequest<Object> redirectRequest) {
            return uri -> exchangeImpl(uri, parentRequest, redirectRequest, bodyType, errorType, null);
        }

        @Override
        protected void channelReadInstrumented(ChannelHandlerContext ctx, HttpObject obj) throws Exception {
            if (!(obj instanceof FullHttpResponse fullResponse)) {
                ReferenceCountUtil.release(obj);
                exceptionCaught(ctx, new IllegalArgumentException("Expected full response"));
                return;
            }

            try {
                if (handleResponse(fullResponse)) {
                    forwardResponseToPromise(fullResponse);
                }
            } finally {
                if (!HttpUtil.isKeepAlive(fullResponse)) {
                    poolHandle.taint();
                }
                ctx.pipeline().remove(this);
                fullResponse.release();
            }
        }

        private void forwardResponseToPromise(FullHttpResponse msg) {
            try {
                if (log.isTraceEnabled()) {
                    traceBody("Response", msg.content());
                }

                if (msg.status().equals(HttpResponseStatus.NO_CONTENT)) {
                    // normalize the NO_CONTENT header, since http content aggregator adds it even if not present in the response
                    msg.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
                }

                boolean convertBodyWithBodyType = shouldConvertWithBodyType(msg, DefaultHttpClient.this.configuration, bodyType, errorType);
                FullNettyClientHttpResponse<O> response
                        = new FullNettyClientHttpResponse<>(msg, handlerRegistry, bodyType, convertBodyWithBodyType, conversionService);

                if (convertBodyWithBodyType) {
                    responsePromise.trySuccess(response);
                    response.onComplete();
                } else { // error flow
                    try {
                        responsePromise.tryFailure(makeErrorFromRequestBody(msg.status(), response));
                        response.onComplete();
                    } catch (HttpClientResponseException t) {
                        responsePromise.tryFailure(t);
                        response.onComplete();
                    } catch (Exception t) {
                        response.onComplete();
                        responsePromise.tryFailure(makeErrorBodyParseError(msg, t));
                    }
                }
            } catch (HttpClientResponseException t) {
                responsePromise.tryFailure(t);
            } catch (Exception t) {
                makeNormalBodyParseError(msg, t, cause -> {
                    if (!responsePromise.tryFailure(cause) && log.isWarnEnabled()) {
                        log.warn("Exception fired after handler completed: {}", t.getMessage(), t);
                    }
                });
            }
        }

        private static <O, E> boolean shouldConvertWithBodyType(FullHttpResponse msg,
                                                                HttpClientConfiguration configuration,
                                                                Argument<O> bodyType,
                                                                Argument<E> errorType) {
            if (msg.status().code() < 400) {
                return true;
            }
            return !configuration.isExceptionOnErrorStatus() && bodyType.equalsType(errorType);

        }

        /**
         * Create a {@link HttpClientResponseException} from a response with a failed HTTP status.
         */
        private HttpClientResponseException makeErrorFromRequestBody(HttpResponseStatus status, FullNettyClientHttpResponse<?> response) {
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
            // this onComplete call disables further parsing by HttpClientResponseException
            errorResponse.onComplete();
            return decorate(new HttpClientResponseException(
                "Error decoding HTTP error response body: " + t.getMessage(),
                t,
                errorResponse,
                null
            ));
        }

        private void makeNormalBodyParseError(FullHttpResponse fullResponse, Throwable t, Consumer<HttpClientResponseException> forward) {
            FullNettyClientHttpResponse<Object> response = new FullNettyClientHttpResponse<>(
                    fullResponse,
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
            try {
                forward.accept(clientResponseError);
            } finally {
                response.onComplete();
            }
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            ctx.pipeline().remove(ChannelPipelineCustomizer.HANDLER_HTTP_AGGREGATOR);
            poolHandle.release();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            super.exceptionCaught(ctx, cause);
            poolHandle.taint();
            ctx.pipeline().remove(this);
        }
    }

    private class StreamHttpResponseHandler extends BaseHttpResponseHandler<MutableHttpResponse<?>> {
        static final String NAME_FLOW_CONTROL = ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE + "-flow-control";
        static final String NAME_PUBLISHER = ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE + "-publisher";

        private final ConnectionManager.PoolHandle poolHandle;
        private boolean handoff = false;

        public StreamHttpResponseHandler(
            Promise<? super MutableHttpResponse<?>> responsePromise,
            io.micronaut.http.HttpRequest<?> parentRequest,
            io.micronaut.http.HttpRequest<?> finalRequest, ConnectionManager.PoolHandle poolHandle) {

            super(responsePromise, parentRequest, finalRequest);
            this.poolHandle = poolHandle;
        }

        private static boolean hasBody(io.netty.handler.codec.http.HttpResponse response) {
            if (response.status().code() >= HttpStatus.CONTINUE.getCode() && response.status().code() < HttpStatus.OK.getCode()) {
                return false;
            }

            if (response.status().equals(HttpResponseStatus.NO_CONTENT) ||
                response.status().equals(HttpResponseStatus.NOT_MODIFIED)) {
                return false;
            }

            if (HttpUtil.isTransferEncodingChunked(response)) {
                return true;
            }

            if (HttpUtil.isContentLengthSet(response)) {
                return HttpUtil.getContentLength(response) > 0;
            }

            return true;
        }

        @Override
        protected void channelReadInstrumented(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
            io.netty.handler.codec.http.HttpResponse response = (io.netty.handler.codec.http.HttpResponse) msg;
            if (handleResponse(response)) {
                Publisher<HttpContent> body;
                if (msg instanceof FullHttpResponse fhr) {
                    if (fhr.content().isReadable()) {
                        body = Publishers.just(new DefaultLastHttpContent(fhr.content()));
                    } else {
                        body = Publishers.empty();
                        fhr.release();
                    }
                } else if (!hasBody(response)) {
                    skipContent(ctx, msg);
                    body = Publishers.empty();
                } else {
                    boolean autoRead = ctx.channel().config().isAutoRead();
                    ctx.channel().config().setAutoRead(false);
                    FlowControlHandler flowControlHandler = new FlowControlHandler();
                    ReactiveClientReader reader = new ReactiveClientReader() {
                        @Override
                        protected void remove(ChannelHandlerContext ctx) {
                            ctx.pipeline().remove(NAME_FLOW_CONTROL);
                            ctx.pipeline().remove(NAME_PUBLISHER);
                            ctx.channel().config().setAutoRead(autoRead);
                            poolHandle.release();
                        }
                    };
                    ctx.pipeline()
                        .addBefore(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE, NAME_FLOW_CONTROL, flowControlHandler)
                        .addBefore(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE, NAME_PUBLISHER, reader);
                    handoff = true;
                    ctx.pipeline().remove(ctx.name());

                    body = reader;
                }

                DefaultStreamedHttpResponse nettyResponse = new DefaultStreamedHttpResponse(
                    response.protocolVersion(),
                    response.status(),
                    response.headers(),
                    body
                );
                responsePromise.trySuccess(new NettyStreamedHttpResponse<>(nettyResponse, conversionService));
            } else {
                skipContent(ctx, msg);
            }
        }

        private void skipContent(ChannelHandlerContext ctx, HttpObject msg) {
            if (!(msg instanceof LastHttpContent)) {
                // add a handler to skip any contents
                ctx.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        ((HttpContent) msg).release();
                        if (msg instanceof LastHttpContent) {
                            ctx.pipeline().remove(ctx.name());
                        }
                    }

                    @Override
                    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
                        poolHandle.release();
                    }
                });
                handoff = true;
            }
            ctx.pipeline().remove(ctx.name());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            super.exceptionCaught(ctx, cause);
            poolHandle.taint();
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            if (!handoff) {
                poolHandle.release();
            }
        }

        @Override
        protected Function<URI, Publisher<? extends MutableHttpResponse<?>>> makeRedirectHandler(io.micronaut.http.HttpRequest<?> parentRequest, MutableHttpRequest<Object> redirectRequest) {
            return uri -> buildStreamExchange(parentRequest, redirectRequest, uri, null);
        }
    }
}
