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
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseWrapper;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.bind.DefaultRequestBinderRegistry;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.ProxyHttpClient;
import io.micronaut.http.client.ProxyRequestOptions;
import io.micronaut.http.client.ServiceHttpClientConfiguration;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.client.exceptions.ContentLengthExceededException;
import io.micronaut.http.client.exceptions.HttpClientErrorDecoder;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.exceptions.NoHostException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.client.filter.ClientFilterResolutionContext;
import io.micronaut.http.client.filter.DefaultHttpClientFilterResolver;
import io.micronaut.http.client.filters.ClientServerContextFilter;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.client.multipart.MultipartDataFactory;
import io.micronaut.http.client.netty.ssl.NettyClientSslBuilder;
import io.micronaut.http.client.netty.websocket.NettyWebSocketClientHandler;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.http.filter.HttpClientFilterResolver;
import io.micronaut.http.filter.HttpFilterResolver;
import io.micronaut.http.multipart.MultipartException;
import io.micronaut.http.netty.AbstractNettyHttpRequest;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.micronaut.http.netty.NettyHttpRequestBuilder;
import io.micronaut.http.netty.NettyHttpResponseBuilder;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.netty.channel.ChannelPipelineListener;
import io.micronaut.http.netty.stream.DefaultStreamedHttpResponse;
import io.micronaut.http.netty.stream.JsonSubscriber;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.micronaut.http.netty.stream.StreamedHttpResponse;
import io.micronaut.http.sse.Event;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.http.uri.UriTemplate;
import io.micronaut.jackson.databind.JacksonDatabindMapper;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.codec.JsonMediaTypeCodec;
import io.micronaut.json.codec.JsonStreamMediaTypeCodec;
import io.micronaut.json.codec.MapperMediaTypeCodec;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;
import io.micronaut.scheduling.instrument.InvocationInstrumenterFactory;
import io.micronaut.websocket.WebSocketClient;
import io.micronaut.websocket.annotation.ClientWebSocket;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.context.WebSocketBean;
import io.micronaut.websocket.context.WebSocketBeanRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.EmptyByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Promise;
import org.reactivestreams.Processor;
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
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.micronaut.scheduling.instrument.InvocationInstrumenter.NOOP;

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

    private static final Supplier<Pattern> HEADER_MASK_PATTERNS = SupplierUtil.memoized(() ->
        Pattern.compile(".*(password|cred|cert|key|secret|token|auth|signat).*", Pattern.CASE_INSENSITIVE)
    );
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

    final ConnectionManager connectionManager;

    private final List<HttpFilterResolver.FilterEntry<HttpClientFilter>> clientFilterEntries;
    private final LoadBalancer loadBalancer;
    private final HttpClientConfiguration configuration;
    private final String contextPath;
    private final Charset defaultCharset;
    private final Logger log;
    private final HttpClientFilterResolver<ClientFilterResolutionContext> filterResolver;
    private final WebSocketBeanRegistry webSocketRegistry;
    private final RequestBinderRegistry requestBinderRegistry;
    private final List<InvocationInstrumenterFactory> invocationInstrumenterFactories;
    private final String informationalServiceId;

    /**
     * Construct a client for the given arguments.
     *
     * @param loadBalancer                    The {@link LoadBalancer} to use for selecting servers
     * @param configuration                   The {@link HttpClientConfiguration} object
     * @param contextPath                     The base URI to prepend to request uris
     * @param threadFactory                   The thread factory to use for client threads
     * @param nettyClientSslBuilder           The SSL builder
     * @param codecRegistry                   The {@link MediaTypeCodecRegistry} to use for encoding and decoding objects
     * @param annotationMetadataResolver      The annotation metadata resolver
     * @param invocationInstrumenterFactories The invocation instrumeter factories to instrument netty handlers execution with
     * @param filters                         The filters to use
     */
    public DefaultHttpClient(@Nullable LoadBalancer loadBalancer,
            @NonNull HttpClientConfiguration configuration,
            @Nullable String contextPath,
            @Nullable ThreadFactory threadFactory,
            NettyClientSslBuilder nettyClientSslBuilder,
            MediaTypeCodecRegistry codecRegistry,
            @Nullable AnnotationMetadataResolver annotationMetadataResolver,
            List<InvocationInstrumenterFactory> invocationInstrumenterFactories,
            HttpClientFilter... filters) {
        this(loadBalancer,
            configuration.getHttpVersion(),
            configuration,
            contextPath,
            new DefaultHttpClientFilterResolver(annotationMetadataResolver, Arrays.asList(filters)),
            null,
            threadFactory,
            nettyClientSslBuilder,
            codecRegistry,
            WebSocketBeanRegistry.EMPTY,
            new DefaultRequestBinderRegistry(ConversionService.SHARED),
            null,
            NioSocketChannel::new,
            Collections.emptySet(),
            CompositeNettyClientCustomizer.EMPTY,
            invocationInstrumenterFactories, null);
    }

    /**
     * Construct a client for the given arguments.
     *  @param loadBalancer                    The {@link LoadBalancer} to use for selecting servers
     * @param explicitHttpVersion                     The HTTP version to use. Can be null and defaults to {@link io.micronaut.http.HttpVersion#HTTP_1_1}
     * @param configuration                   The {@link HttpClientConfiguration} object
     * @param contextPath                     The base URI to prepend to request uris
     * @param filterResolver                  The http client filter resolver
     * @param clientFilterEntries             The client filter entries
     * @param threadFactory                   The thread factory to use for client threads
     * @param nettyClientSslBuilder           The SSL builder
     * @param codecRegistry                   The {@link MediaTypeCodecRegistry} to use for encoding and decoding objects
     * @param webSocketBeanRegistry           The websocket bean registry
     * @param requestBinderRegistry           The request binder registry
     * @param eventLoopGroup                  The event loop group to use
     * @param socketChannelFactory            The socket channel factory
     * @param pipelineListeners               The listeners to call for pipeline customization
     * @param clientCustomizer                The pipeline customizer
     * @param invocationInstrumenterFactories The invocation instrumeter factories to instrument netty handlers execution with
     * @param informationalServiceId          Optional service ID that will be passed to exceptions created by this client
     */
    public DefaultHttpClient(@Nullable LoadBalancer loadBalancer,
                             @Nullable io.micronaut.http.HttpVersion explicitHttpVersion,
                             @NonNull HttpClientConfiguration configuration,
                             @Nullable String contextPath,
                             @NonNull HttpClientFilterResolver<ClientFilterResolutionContext> filterResolver,
                             List<HttpFilterResolver.FilterEntry<HttpClientFilter>> clientFilterEntries,
                             @Nullable ThreadFactory threadFactory,
                             @NonNull NettyClientSslBuilder nettyClientSslBuilder,
                             @NonNull MediaTypeCodecRegistry codecRegistry,
                             @NonNull WebSocketBeanRegistry webSocketBeanRegistry,
                             @NonNull RequestBinderRegistry requestBinderRegistry,
                             @Nullable EventLoopGroup eventLoopGroup,
                             @NonNull ChannelFactory socketChannelFactory,
                             Collection<ChannelPipelineListener> pipelineListeners,
                             NettyClientCustomizer clientCustomizer,
                             List<InvocationInstrumenterFactory> invocationInstrumenterFactories,
                             @Nullable String informationalServiceId
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

        this.invocationInstrumenterFactories =
                invocationInstrumenterFactories == null ? Collections.emptyList() : invocationInstrumenterFactories;

        this.mediaTypeCodecRegistry = codecRegistry;
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

        this.connectionManager = new ConnectionManager(
            log,
            eventLoopGroup,
            threadFactory,
            configuration,
            explicitHttpVersion,
            combineFactories(),
            socketChannelFactory,
            nettyClientSslBuilder,
            clientCustomizer,
            pipelineListeners,
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
        this(null, new DefaultHttpClientConfiguration(), Collections.emptyList());
    }

    /**
     * @param uri           The URI
     * @param configuration The {@link HttpClientConfiguration} object
     */
    public DefaultHttpClient(@Nullable URI uri, @NonNull HttpClientConfiguration configuration) {
        this(
                uri == null ? null : LoadBalancer.fixed(uri), configuration, null, new DefaultThreadFactory(MultithreadEventLoopGroup.class),
                new NettyClientSslBuilder(new ResourceResolver()),
                createDefaultMediaTypeRegistry(),
                AnnotationMetadataResolver.DEFAULT,
                Collections.emptyList());
    }

    /**
     * @param loadBalancer  The {@link LoadBalancer} to use for selecting servers
     * @param configuration The {@link HttpClientConfiguration} object
     * @param invocationInstrumenterFactories The invocation instrumeter factories to instrument netty handlers execution with
     */
    public DefaultHttpClient(@Nullable LoadBalancer loadBalancer, HttpClientConfiguration configuration, List<InvocationInstrumenterFactory> invocationInstrumenterFactories) {
        this(loadBalancer,
                configuration, null, new DefaultThreadFactory(MultithreadEventLoopGroup.class),
                new NettyClientSslBuilder(new ResourceResolver()),
                createDefaultMediaTypeRegistry(),
                AnnotationMetadataResolver.DEFAULT,
                invocationInstrumenterFactories);
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
    public void setMediaTypeCodecRegistry(MediaTypeCodecRegistry mediaTypeCodecRegistry) {
        if (mediaTypeCodecRegistry != null) {
            this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        }
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
                Flux<HttpResponse<O>> publisher = Flux.from(DefaultHttpClient.this.exchange(request, bodyType, errorType));
                return publisher.doOnNext(res -> {
                    Optional<ByteBuf> byteBuf = res.getBody(ByteBuf.class);
                    byteBuf.ifPresent(bb -> {
                        if (bb.refCnt() > 0) {
                            ReferenceCountUtil.safeRelease(bb);
                        }
                    });
                    if (res instanceof FullNettyClientHttpResponse) {
                        ((FullNettyClientHttpResponse) res).onComplete();
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
                        throw customizeException(new HttpClientResponseException(
                            String.format("Failed to decode the body for the given content type [%s]", response.getContentType().orElse(null)),
                            response
                        ));
                    } else {
                        return body.orElseThrow(() -> customizeException(new HttpClientResponseException(
                            "Empty body",
                            response
                        )));
                    }
                }
            }
        };
    }

    @SuppressWarnings("SubscriberImplementation")
    @Override
    public <I> Publisher<Event<ByteBuffer<?>>> eventStream(@NonNull io.micronaut.http.HttpRequest<I> request) {
        return eventStreamOrError(request, null);
    }

    private <I> Publisher<Event<ByteBuffer<?>>> eventStreamOrError(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<?> errorType) {

        if (request instanceof MutableHttpRequest) {
            ((MutableHttpRequest) request).accept(MediaType.TEXT_EVENT_STREAM_TYPE);
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
                            if (buffer instanceof ReferenceCounted) {
                                ((ReferenceCounted) buffer).release();
                            }
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        dataSubscription.cancel();
                        if (t instanceof HttpClientException) {
                            emitter.error(t);
                        } else {
                            emitter.error(customizeException(new HttpClientException("Error consuming Server Sent Events: " + t.getMessage(), t)));
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
        return eventStream(request, eventType, DEFAULT_ERROR_TYPE);
    }

    @Override
    public <I, B> Publisher<Event<B>> eventStream(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<B> eventType, @NonNull Argument<?> errorType) {
        return Flux.from(eventStreamOrError(request, errorType)).map(byteBufferEvent -> {
            ByteBuffer<?> data = byteBufferEvent.getData();
            Optional<MediaTypeCodec> registeredCodec;

            if (mediaTypeCodecRegistry != null) {
                registeredCodec = mediaTypeCodecRegistry.findCodec(MediaType.APPLICATION_JSON_TYPE);
            } else {
                registeredCodec = Optional.empty();
            }

            if (registeredCodec.isPresent()) {
                B decoded = registeredCodec.get().decode(eventType, data);
                return Event.of(byteBufferEvent, decoded);
            } else {
                throw new CodecException("JSON codec not present");
            }
        });
    }

    @Override
    public <I> Publisher<ByteBuffer<?>> dataStream(@NonNull io.micronaut.http.HttpRequest<I> request) {
        return dataStream(request, DEFAULT_ERROR_TYPE);
    }

    @Override
    public <I> Publisher<ByteBuffer<?>> dataStream(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<?> errorType) {
        final io.micronaut.http.HttpRequest<Object> parentRequest = ServerRequestContext.currentRequest().orElse(null);
        return new MicronautFlux<>(Flux.from(resolveRequestURI(request))
                .flatMap(requestURI -> dataStreamImpl(request, errorType, parentRequest, requestURI)))
                .doAfterNext(buffer -> {
                    Object o = buffer.asNativeBuffer();
                    if (o instanceof ByteBuf) {
                        ByteBuf byteBuf = (ByteBuf) o;
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
        io.micronaut.http.HttpRequest<Object> parentRequest = ServerRequestContext.currentRequest().orElse(null);
        return new MicronautFlux<>(Flux.from(resolveRequestURI(request))
                .flatMap(uri -> exchangeStreamImpl(parentRequest, request, errorType, uri)))
                .doAfterNext(byteBufferHttpResponse -> {
                    ByteBuffer<?> buffer = byteBufferHttpResponse.body();
                    if (buffer instanceof ReferenceCounted) {
                        ((ReferenceCounted) buffer).release();
                    }
                });
    }

    @Override
    public <I, O> Publisher<O> jsonStream(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<O> type) {
        return jsonStream(request, type, DEFAULT_ERROR_TYPE);
    }

    @Override
    public <I, O> Publisher<O> jsonStream(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<O> type, @NonNull Argument<?> errorType) {
        final io.micronaut.http.HttpRequest<Object> parentRequest = ServerRequestContext.currentRequest().orElse(null);
        return Flux.from(resolveRequestURI(request))
                .flatMap(requestURI -> jsonStreamImpl(parentRequest, request, type, errorType, requestURI));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <I> Publisher<Map<String, Object>> jsonStream(@NonNull io.micronaut.http.HttpRequest<I> request) {
        return (Publisher) jsonStream(request, Map.class);
    }

    @Override
    public <I, O> Publisher<O> jsonStream(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Class<O> type) {
        return jsonStream(request, io.micronaut.core.type.Argument.of(type));
    }

    @Override
    public <I, O, E> Publisher<io.micronaut.http.HttpResponse<O>> exchange(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<O> bodyType, @NonNull Argument<E> errorType) {
        final io.micronaut.http.HttpRequest<Object> parentRequest = ServerRequestContext.currentRequest().orElse(null);
        Publisher<URI> uriPublisher = resolveRequestURI(request);
        return Flux.from(uriPublisher)
                .switchMap(uri -> exchangeImpl(uri, parentRequest, request, bodyType, errorType));
    }

    @Override
    public <I, O, E> Publisher<O> retrieve(io.micronaut.http.HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType) {
        // mostly same as default impl, but with exception customization
        return Flux.from(exchange(request, bodyType, errorType)).map(response -> {
            if (bodyType.getType() == HttpStatus.class) {
                return (O) response.getStatus();
            } else {
                Optional<O> body = response.getBody();
                if (!body.isPresent() && response.getBody(byte[].class).isPresent()) {
                    throw customizeException(new HttpClientResponseException(
                        String.format("Failed to decode the body for the given content type [%s]", response.getContentType().orElse(null)),
                        response
                    ));
                } else {
                    return body.orElseThrow(() -> customizeException(new HttpClientResponseException(
                        "Empty body",
                        response
                    )));
                }
            }
        });
    }

    @Override
    public <T extends AutoCloseable> Publisher<T> connect(Class<T> clientEndpointType, io.micronaut.http.MutableHttpRequest<?> request) {
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
        if (headers instanceof NettyHttpHeaders) {
            customHeaders = ((NettyHttpHeaders) headers).getNettyHeaders();
        }
        if (StringUtils.isNotEmpty(subprotocol)) {
            customHeaders.add("Sec-WebSocket-Protocol", subprotocol);
        }

        NettyWebSocketClientHandler<T> handler = new NettyWebSocketClientHandler<>(
            request,
            webSocketBean,
            WebSocketClientHandshakerFactory.newHandshaker(
                webSocketURL, protocolVersion, subprotocol, true, customHeaders, maxFramePayloadLength),
            requestBinderRegistry,
            mediaTypeCodecRegistry);

        return connectionManager.connectForWebsocket(requestKey, handler)
            .then(handler.getHandshakeCompletedMono());
    }

    private <I> Flux<HttpResponse<ByteBuffer<?>>> exchangeStreamImpl(io.micronaut.http.HttpRequest<Object> parentRequest, io.micronaut.http.HttpRequest<I> request, Argument<?> errorType, URI requestURI) {
        Flux<HttpResponse<Object>> streamResponsePublisher = Flux.from(buildStreamExchange(parentRequest, request, requestURI, errorType));
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
                        NettyStreamedHttpResponse<ByteBuffer<?>> thisResponse = new NettyStreamedHttpResponse<>(
                                streamedHttpResponse,
                                response.status()
                        );
                        thisResponse.setBody(byteBuffer);
                        return (HttpResponse<ByteBuffer<?>>) new HttpResponseWrapper<>(thisResponse);
                    });
        }).doOnTerminate(() -> {
            final Object o = request.getAttribute(NettyClientHttpRequest.CHANNEL).orElse(null);
            if (o instanceof Channel) {
                final Channel c = (Channel) o;
                if (c.isOpen()) {
                    c.close();
                }
            }
        });
    }

    private <I, O> Flux<O> jsonStreamImpl(io.micronaut.http.HttpRequest<?> parentRequest, io.micronaut.http.HttpRequest<I> request, Argument<O> type, Argument<?> errorType, URI requestURI) {
        Flux<HttpResponse<Object>> streamResponsePublisher =
                Flux.from(buildStreamExchange(parentRequest, request, requestURI, errorType));
        return streamResponsePublisher.switchMap(response -> {
            if (!(response instanceof NettyStreamedHttpResponse)) {
                throw new IllegalStateException("Response has been wrapped in non streaming type. Do not wrap the response in client filters for stream requests");
            }

            MapperMediaTypeCodec mediaTypeCodec = (MapperMediaTypeCodec) mediaTypeCodecRegistry.findCodec(MediaType.APPLICATION_JSON_TYPE)
                    .orElseThrow(() -> new IllegalStateException("No JSON codec found"));

            StreamedHttpResponse streamResponse = NettyHttpResponseBuilder.toStreamResponse(response);
            Flux<HttpContent> httpContentReactiveSequence = Flux.from(streamResponse);

            boolean isJsonStream = response.getContentType().map(mediaType -> mediaType.equals(MediaType.APPLICATION_JSON_STREAM_TYPE)).orElse(false);
            boolean streamArray = !Iterable.class.isAssignableFrom(type.getType()) && !isJsonStream;
            Processor<byte[], JsonNode> jsonProcessor = mediaTypeCodec.getJsonMapper().createReactiveParser(p -> {
                httpContentReactiveSequence.map(content -> {
                    ByteBuf chunk = content.content();
                    if (log.isTraceEnabled()) {
                        log.trace("HTTP Client Streaming Response Received Chunk (length: {}) for Request: {} {}",
                                chunk.readableBytes(), request.getMethodName(), request.getUri());
                        traceBody("Chunk", chunk);
                    }
                    try {
                        return ByteBufUtil.getBytes(chunk);
                    } finally {
                        chunk.release();
                    }
                }).subscribe(p);
            }, streamArray);
            return Flux.from(jsonProcessor)
                    .map(jsonNode -> mediaTypeCodec.decode(type, jsonNode));
        }).doOnTerminate(() -> {
            final Object o = request.getAttribute(NettyClientHttpRequest.CHANNEL).orElse(null);
            if (o instanceof Channel) {
                final Channel c = (Channel) o;
                if (c.isOpen()) {
                    c.close();
                }
            }
        });
    }

    private <I> Flux<ByteBuffer<?>> dataStreamImpl(io.micronaut.http.HttpRequest<I> request, Argument<?> errorType, io.micronaut.http.HttpRequest<Object> parentRequest, URI requestURI) {
        Flux<HttpResponse<Object>> streamResponsePublisher = Flux.from(buildStreamExchange(parentRequest, request, requestURI, errorType));
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
                })
                .doOnTerminate(() -> {
                    final Object o = request.getAttribute(NettyClientHttpRequest.CHANNEL).orElse(null);
                    if (o instanceof Channel) {
                        final Channel c = (Channel) o;
                        if (c.isOpen()) {
                            c.close();
                        }
                    }
                });
    }

    /**
     * Implementation of {@link #jsonStream}, {@link #dataStream}, {@link #exchangeStream}.
     */
    @SuppressWarnings("MagicNumber")
    private  <I> Publisher<MutableHttpResponse<Object>> buildStreamExchange(
            @Nullable io.micronaut.http.HttpRequest<?> parentRequest,
            @NonNull io.micronaut.http.HttpRequest<I> request,
            @NonNull URI requestURI,
            @Nullable Argument<?> errorType) {

        AtomicReference<io.micronaut.http.HttpRequest<?>> requestWrapper = new AtomicReference<>(request);
        Flux<MutableHttpResponse<Object>> streamResponsePublisher = connectAndStream(parentRequest, request, requestURI, requestWrapper, false, true);

        streamResponsePublisher = readBodyOnError(errorType, streamResponsePublisher);

        // apply filters
        streamResponsePublisher = Flux.from(
                applyFilterToResponsePublisher(parentRequest, request, requestURI, requestWrapper, streamResponsePublisher)
        );

        return streamResponsePublisher.subscribeOn(connectionManager.getEventLoopScheduler());
    }

    @Override
    public Publisher<MutableHttpResponse<?>> proxy(@NonNull io.micronaut.http.HttpRequest<?> request) {
        return proxy(request, ProxyRequestOptions.getDefault());
    }

    @Override
    public Publisher<MutableHttpResponse<?>> proxy(@NonNull io.micronaut.http.HttpRequest<?> request, @NonNull ProxyRequestOptions options) {
        Objects.requireNonNull(options, "options");
        return Flux.from(resolveRequestURI(request))
                .flatMap(requestURI -> {
                    io.micronaut.http.MutableHttpRequest<?> httpRequest = request instanceof MutableHttpRequest
                            ? (io.micronaut.http.MutableHttpRequest<?>) request
                            : request.mutate();
                    if (!options.isRetainHostHeader()) {
                        httpRequest.headers(headers -> headers.remove(HttpHeaderNames.HOST));
                    }

                    AtomicReference<io.micronaut.http.HttpRequest<?>> requestWrapper = new AtomicReference<>(httpRequest);
                    Flux<MutableHttpResponse<Object>> proxyResponsePublisher = connectAndStream(request, request, requestURI, requestWrapper, true, false);
                    // apply filters
                    //noinspection unchecked
                    proxyResponsePublisher = Flux.from(
                            applyFilterToResponsePublisher(
                                    request,
                                    requestWrapper.get(),
                                    requestURI,
                                    requestWrapper,
                                    (Publisher) proxyResponsePublisher
                            )
                    );
                    return proxyResponsePublisher;
                });
    }

    private <I> Flux<MutableHttpResponse<Object>> connectAndStream(
            io.micronaut.http.HttpRequest<?> parentRequest,
            io.micronaut.http.HttpRequest<I> request,
            URI requestURI,
            AtomicReference<io.micronaut.http.HttpRequest<?>> requestWrapper,
            boolean isProxy,
            boolean failOnError
    ) {
        RequestKey requestKey;
        try {
            requestKey = new RequestKey(this, requestURI);
        } catch (Exception e) {
            return Flux.error(e);
        }
        return connectionManager.connectForStream(requestKey, isProxy, isAcceptEvents(request)).flatMapMany(poolHandle -> {
            request.setAttribute(NettyClientHttpRequest.CHANNEL, poolHandle.channel);
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
            io.micronaut.http.HttpRequest<I> request,
            @NonNull Argument<O> bodyType,
            @NonNull Argument<E> errorType) {
        AtomicReference<io.micronaut.http.HttpRequest<?>> requestWrapper = new AtomicReference<>(request);

        RequestKey requestKey;
        try {
            requestKey = new RequestKey(this, requestURI);
        } catch (HttpClientException e) {
            return Flux.error(e);
        }

        Mono<ConnectionManager.PoolHandle> handlePublisher = connectionManager.connectForExchange(requestKey, MediaType.MULTIPART_FORM_DATA_TYPE.equals(request.getContentType().orElse(null)), isAcceptEvents(request));

        Flux<io.micronaut.http.HttpResponse<O>> responsePublisher = handlePublisher.flatMapMany(poolHandle -> {
            return Flux.create(emitter -> {
                try {
                    sendRequestThroughChannel(
                        requestWrapper.get(),
                        bodyType,
                        errorType,
                        emitter,
                        poolHandle.channel,
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
                requestWrapper,
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
                finalReactiveSequence = finalReactiveSequence.timeout(duration)
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
     * @param requestURI The request URI
     * @return A URI that is prepended with the contextPath, if set
     */
    protected URI prependContextPath(URI requestURI) {
        if (StringUtils.isNotEmpty(contextPath)) {
            try {
                return new URI(StringUtils.prependUri(contextPath, requestURI.toString()));
            } catch (URISyntaxException e) {
                throw customizeException(new HttpClientException("Failed to construct the request URI", e));
            }
        }
        return requestURI;
    }

    /**
     * @return The discriminator to use when selecting a server for the purposes of load balancing (defaults to null)
     */
    protected Object getLoadBalancerDiscriminator() {
        return null;
    }

    private <I, O, R extends io.micronaut.http.HttpResponse<O>> Publisher<R> applyFilterToResponsePublisher(
            io.micronaut.http.HttpRequest<?> parentRequest,
            io.micronaut.http.HttpRequest<I> request,
            URI requestURI,
            AtomicReference<io.micronaut.http.HttpRequest<?>> requestWrapper,
            Publisher<R> responsePublisher) {

        if (request instanceof MutableHttpRequest) {
            ((MutableHttpRequest<I>) request).uri(requestURI);

            List<HttpClientFilter> filters =
                    filterResolver.resolveFilters(request, clientFilterEntries);
            if (parentRequest != null) {
                filters.add(new ClientServerContextFilter(parentRequest));
            }

            OrderUtil.reverseSort(filters);
            Publisher<R> finalResponsePublisher = responsePublisher;
            filters.add((req, chain) -> finalResponsePublisher);

            ClientFilterChain filterChain = buildChain(requestWrapper, filters);
            if (parentRequest != null) {
                responsePublisher = ServerRequestContext.with(parentRequest, (Supplier<Publisher<R>>) () -> {
                    try {
                        return Flux.from((Publisher<R>) filters.get(0).doFilter(request, filterChain))
                                .contextWrite(ctx -> ctx.put(ServerRequestContext.KEY, parentRequest));
                    } catch (Throwable t) {
                        return Flux.error(t);
                    }
                });
            } else {
                try {
                    responsePublisher = (Publisher<R>) filters.get(0).doFilter(request, filterChain);
                } catch (Throwable t) {
                    responsePublisher = Flux.error(t);
                }
            }
        }

        return responsePublisher;
    }

    /**
     * @param request                The request
     * @param requestURI             The URI of the request
     * @param requestContentType     The request content type
     * @param permitsBody            Whether permits body
     * @param bodyType               The body type
     * @param onError                Called when the body publisher encounters an error
     * @return A {@link NettyRequestWriter}
     * @throws HttpPostRequestEncoder.ErrorDataEncoderException if there is an encoder exception
     */
    protected NettyRequestWriter buildNettyRequest(
            MutableHttpRequest request,
            URI requestURI,
            MediaType requestContentType,
            boolean permitsBody,
            @Nullable Argument<?> bodyType,
            Consumer<? super Throwable> onError) throws HttpPostRequestEncoder.ErrorDataEncoderException {

        io.netty.handler.codec.http.HttpRequest nettyRequest;
        HttpPostRequestEncoder postRequestEncoder = null;
        if (permitsBody) {
            Optional body = request.getBody();
            boolean hasBody = body.isPresent();
            if (requestContentType.equals(MediaType.APPLICATION_FORM_URLENCODED_TYPE) && hasBody) {
                Object bodyValue = body.get();
                if (bodyValue instanceof CharSequence) {
                    ByteBuf byteBuf = charSequenceToByteBuf((CharSequence) bodyValue, requestContentType);
                    request.body(byteBuf);
                    nettyRequest = NettyHttpRequestBuilder.toHttpRequest(request);
                } else {
                    postRequestEncoder = buildFormDataRequest(request, bodyValue);
                    nettyRequest = postRequestEncoder.finalizeRequest();
                }
            } else if (requestContentType.equals(MediaType.MULTIPART_FORM_DATA_TYPE) && hasBody) {
                Object bodyValue = body.get();
                postRequestEncoder = buildMultipartRequest(request, bodyValue);
                nettyRequest = postRequestEncoder.finalizeRequest();
            } else {
                ByteBuf bodyContent = null;
                if (hasBody) {
                    Object bodyValue = body.get();
                    if (Publishers.isConvertibleToPublisher(bodyValue)) {
                        boolean isSingle = Publishers.isSingle(bodyValue.getClass());

                        Publisher<?> publisher = ConversionService.SHARED.convert(bodyValue, Publisher.class).orElseThrow(() ->
                                new IllegalArgumentException("Unconvertible reactive type: " + bodyValue)
                        );

                        Flux<HttpContent> requestBodyPublisher = Flux.from(publisher).map(o -> {
                            if (o instanceof CharSequence) {
                                ByteBuf textChunk = Unpooled.copiedBuffer(((CharSequence) o), requestContentType.getCharset().orElse(StandardCharsets.UTF_8));
                                if (log.isTraceEnabled()) {
                                    traceChunk(textChunk);
                                }
                                return new DefaultHttpContent(textChunk);
                            } else if (o instanceof ByteBuf) {
                                ByteBuf byteBuf = (ByteBuf) o;
                                if (log.isTraceEnabled()) {
                                    log.trace("Sending Bytes Chunk. Length: {}", byteBuf.readableBytes());
                                }
                                return new DefaultHttpContent(byteBuf);
                            } else if (o instanceof byte[]) {
                                byte[] bodyBytes = (byte[]) o;
                                if (log.isTraceEnabled()) {
                                    log.trace("Sending Bytes Chunk. Length: {}", bodyBytes.length);
                                }
                                return new DefaultHttpContent(Unpooled.wrappedBuffer(bodyBytes));
                            } else if (o instanceof ByteBuffer) {
                                ByteBuffer<?> byteBuffer = (ByteBuffer<?>) o;
                                Object nativeBuffer = byteBuffer.asNativeBuffer();
                                if (log.isTraceEnabled()) {
                                    log.trace("Sending Bytes Chunk. Length: {}", byteBuffer.readableBytes());
                                }
                                if (nativeBuffer instanceof ByteBuf) {
                                    return new DefaultHttpContent((ByteBuf) nativeBuffer);
                                } else {
                                    return new DefaultHttpContent(Unpooled.wrappedBuffer(byteBuffer.toByteArray()));
                                }
                            } else if (mediaTypeCodecRegistry != null) {
                                Optional<MediaTypeCodec> registeredCodec = mediaTypeCodecRegistry.findCodec(requestContentType);
                                ByteBuf encoded = registeredCodec.map(codec -> {
                                            if (bodyType != null && bodyType.isInstance(o)) {
                                                return codec.encode((Argument<Object>) bodyType, o, byteBufferFactory).asNativeBuffer();
                                            } else {
                                                return codec.encode(o, byteBufferFactory).asNativeBuffer();
                                            }
                                        })
                                        .orElse(null);
                                if (encoded != null) {
                                    if (log.isTraceEnabled()) {
                                        traceChunk(encoded);
                                    }
                                    return new DefaultHttpContent(encoded);
                                }
                            }
                            throw new CodecException("Cannot encode value [" + o + "]. No possible encoders found");
                        });

                        if (!isSingle && MediaType.APPLICATION_JSON_TYPE.equals(requestContentType)) {
                           requestBodyPublisher = JsonSubscriber.lift(requestBodyPublisher);
                        }

                        requestBodyPublisher = requestBodyPublisher.doOnError(onError);

                        request.body(requestBodyPublisher);
                        nettyRequest = NettyHttpRequestBuilder.toHttpRequest(request);
                        try {
                            nettyRequest.setUri(requestURI.toURL().getFile());
                        } catch (MalformedURLException e) {
                            //should never happen
                        }
                        return new NettyRequestWriter(nettyRequest, null);
                    } else if (bodyValue instanceof CharSequence) {
                        bodyContent = charSequenceToByteBuf((CharSequence) bodyValue, requestContentType);
                    } else if (mediaTypeCodecRegistry != null) {
                        Optional<MediaTypeCodec> registeredCodec = mediaTypeCodecRegistry.findCodec(requestContentType);
                        bodyContent = registeredCodec.map(codec -> {
                                    if (bodyType != null && bodyType.isInstance(bodyValue)) {
                                        return codec.encode((Argument<Object>) bodyType, bodyValue, byteBufferFactory).asNativeBuffer();
                                    } else {
                                        return codec.encode(bodyValue, byteBufferFactory).asNativeBuffer();
                                    }
                                })
                                .orElse(null);
                    }
                    if (bodyContent == null) {
                        bodyContent = ConversionService.SHARED.convert(bodyValue, ByteBuf.class).orElseThrow(() ->
                                customizeException(new HttpClientException("Body [" + bodyValue + "] cannot be encoded to content type [" + requestContentType + "]. No possible codecs or converters found."))
                        );
                    }
                }
                request.body(bodyContent);
                try {
                    nettyRequest = NettyHttpRequestBuilder.toHttpRequest(request);
                } finally {
                    // reset body after encoding request in case of retry
                    request.body(body.orElse(null));
                }
            }
        } else {
            nettyRequest = NettyHttpRequestBuilder.toHttpRequest(request);
        }
        try {
            nettyRequest.setUri(requestURI.toURL().getFile());
        } catch (MalformedURLException e) {
            //should never happen
        }
        return new NettyRequestWriter(nettyRequest, postRequestEncoder);
    }

    private Flux<MutableHttpResponse<Object>> readBodyOnError(@Nullable Argument<?> errorType, @NonNull Flux<MutableHttpResponse<Object>> publisher) {
        if (errorType != null && errorType != HttpClient.DEFAULT_ERROR_TYPE) {
            return publisher.onErrorResume(clientException -> {
                if (clientException instanceof HttpClientResponseException) {
                    final HttpResponse<?> response = ((HttpClientResponseException) clientException).getResponse();
                    if (response instanceof NettyStreamedHttpResponse) {
                        return Mono.create(emitter -> {
                            NettyStreamedHttpResponse<?> streamedResponse = (NettyStreamedHttpResponse<?>) response;
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
                                        final FullNettyClientHttpResponse<Object> fullNettyClientHttpResponse = new FullNettyClientHttpResponse<>(fullHttpResponse, response.status(), mediaTypeCodecRegistry, byteBufferFactory, (Argument<Object>) errorType, true);
                                        fullNettyClientHttpResponse.onComplete();
                                        emitter.error(customizeException(new HttpClientResponseException(
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
            return Flux.error(customizeException(new NoHostException("Request URI specifies no host to connect to")));
        }

        return Flux.from(loadBalancer.select(getLoadBalancerDiscriminator())).map(server -> {
                    Optional<String> authInfo = server.getMetadata().get(io.micronaut.http.HttpHeaders.AUTHORIZATION_INFO, String.class);
                    if (request instanceof MutableHttpRequest && authInfo.isPresent()) {
                        ((MutableHttpRequest) request).getHeaders().auth(authInfo.get());
                    }
                    return server.resolve(includeContextPath ? prependContextPath(requestURI) : requestURI);
                }
        );
    }

    private <I, O, E> void sendRequestThroughChannel(
            io.micronaut.http.HttpRequest<I> finalRequest,
            Argument<O> bodyType,
            Argument<E> errorType,
            FluxSink<? super HttpResponse<O>> emitter,
            Channel channel,
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
                bodyType,
                throwable -> {
                    if (!emitter.isCancelled()) {
                        emitter.error(throwable);
                    }
                }
        );
        HttpRequest nettyRequest = requestWriter.getNettyRequest();

        prepareHttpHeaders(
                requestURI,
                finalRequest,
                nettyRequest,
                permitsBody,
                !poolHandle.canReturn()
        );

        if (log.isDebugEnabled()) {
            debugRequest(requestURI, nettyRequest);
        }

        if (log.isTraceEnabled()) {
            traceRequest(finalRequest, nettyRequest);
        }

        Promise<HttpResponse<O>> responsePromise = channel.eventLoop().newPromise();
        channel.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_FULL_HTTP_RESPONSE,
                new FullHttpResponseHandler<>(responsePromise, poolHandle, secure, finalRequest, bodyType, errorType));
        poolHandle.notifyRequestPipelineBuilt();
        Publisher<HttpResponse<O>> publisher = new NettyFuturePublisher<>(responsePromise, true);
        if (bodyType != null && bodyType.isVoid()) {
            // don't emit response if bodyType is void
            publisher = Flux.from(publisher).filter(r -> false);
        }
        publisher.subscribe(new ForwardingSubscriber<>(emitter));

        requestWriter.write(channel, secure, emitter);
    }

    private Flux<MutableHttpResponse<Object>> streamRequestThroughChannel(
            io.micronaut.http.HttpRequest<?> parentRequest,
            io.micronaut.http.HttpRequest<?> request,
            ConnectionManager.PoolHandle poolHandle,
            boolean failOnError,
            boolean secure) {
        return Flux.<MutableHttpResponse<Object>>create(sink -> {
            try {
                streamRequestThroughChannel0(parentRequest, request, sink, poolHandle, secure);
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
            return Flux.error(customizeException(new HttpClientResponseException(response.getStatus().getReason(), response)));
        } else {
            return Flux.just(response);
        }
    }

    private void streamRequestThroughChannel0(
            io.micronaut.http.HttpRequest<?> parentRequest,
            final io.micronaut.http.HttpRequest<?> finalRequest,
            FluxSink emitter,
            ConnectionManager.PoolHandle poolHandle,
            boolean secure) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        NettyRequestWriter requestWriter = prepareRequest(
                finalRequest,
                finalRequest.getUri(),
                emitter
        );
        HttpRequest nettyRequest = requestWriter.getNettyRequest();
        Promise<HttpResponse<?>> responsePromise = poolHandle.channel.eventLoop().newPromise();
        ChannelPipeline pipeline = poolHandle.channel.pipeline();
        pipeline.addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE_FULL, new StreamFullHttpResponseHandler(responsePromise, parentRequest, finalRequest));
        pipeline.addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE_STREAM, new StreamStreamHttpResponseHandler(responsePromise, parentRequest, finalRequest));
        poolHandle.notifyRequestPipelineBuilt();

        if (log.isDebugEnabled()) {
            debugRequest(finalRequest.getUri(), nettyRequest);
        }

        if (log.isTraceEnabled()) {
            traceRequest(finalRequest, nettyRequest);
        }

        requestWriter.write(poolHandle.channel, secure, emitter);
        responsePromise.addListener(future -> {
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
            URI requestURI,
            io.micronaut.http.HttpRequest<I> request,
            io.netty.handler.codec.http.HttpRequest nettyRequest,
            boolean permitsBody,
            boolean closeConnection) {
        HttpHeaders headers = nettyRequest.headers();

        if (!headers.contains(HttpHeaderNames.HOST)) {
            headers.set(HttpHeaderNames.HOST, getHostHeader(requestURI));
        }

        // HTTP/2 assumes keep-alive connections
        if (connectionManager.httpVersion != io.micronaut.http.HttpVersion.HTTP_2_0) {
            if (closeConnection) {
                headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            } else {
                headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }
        }

        if (permitsBody) {
            Optional<I> body = request.getBody();
            if (body.isPresent()) {
                if (!headers.contains(HttpHeaderNames.CONTENT_TYPE)) {
                    MediaType mediaType = request.getContentType().orElse(MediaType.APPLICATION_JSON_TYPE);
                    headers.set(HttpHeaderNames.CONTENT_TYPE, mediaType);
                }
                if (nettyRequest instanceof FullHttpRequest) {
                    FullHttpRequest fullHttpRequest = (FullHttpRequest) nettyRequest;
                    headers.set(HttpHeaderNames.CONTENT_LENGTH, fullHttpRequest.content().readableBytes());
                } else {
                    if (!headers.contains(HttpHeaderNames.CONTENT_LENGTH) && !headers.contains(HttpHeaderNames.TRANSFER_ENCODING)) {
                        headers.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                    }
                }
            } else if (!(nettyRequest instanceof StreamedHttpRequest)) {
                headers.set(HttpHeaderNames.CONTENT_LENGTH, 0);
            }
        }
    }

    private ClientFilterChain buildChain(AtomicReference<io.micronaut.http.HttpRequest<?>> requestWrapper, List<HttpClientFilter> filters) {
        AtomicInteger integer = new AtomicInteger();
        int len = filters.size();
        return new ClientFilterChain() {
            @Override
            public Publisher<? extends io.micronaut.http.HttpResponse<?>> proceed(MutableHttpRequest<?> request) {

                int pos = integer.incrementAndGet();
                if (pos > len) {
                    throw new IllegalStateException("The FilterChain.proceed(..) method should be invoked exactly once per filter execution. The method has instead been invoked multiple times by an erroneous filter definition.");
                }
                HttpClientFilter httpFilter = filters.get(pos);
                try {
                    return httpFilter.doFilter(requestWrapper.getAndSet(request), this);
                } catch (Throwable t) {
                    return Flux.error(t);
                }
            }
        };
    }

    private HttpPostRequestEncoder buildFormDataRequest(MutableHttpRequest clientHttpRequest, Object bodyValue) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        HttpPostRequestEncoder postRequestEncoder = new HttpPostRequestEncoder(NettyHttpRequestBuilder.toHttpRequest(clientHttpRequest), false);

        Map<String, Object> formData;
        if (bodyValue instanceof Map) {
            formData = (Map<String, Object>) bodyValue;
        } else {
            formData = BeanMap.of(bodyValue);
        }
        for (Map.Entry<String, Object> entry : formData.entrySet()) {
            Object value = entry.getValue();
            if (value != null) {
                if (value instanceof Collection) {
                    Collection collection = (Collection) value;
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
        Optional<String> converted = ConversionService.SHARED.convert(value, String.class);
        if (converted.isPresent()) {
            postRequestEncoder.addBodyAttribute(key, converted.get());
        }
    }

    private HttpPostRequestEncoder buildMultipartRequest(MutableHttpRequest clientHttpRequest, Object bodyValue) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        HttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);
        io.netty.handler.codec.http.HttpRequest request = NettyHttpRequestBuilder.toHttpRequest(clientHttpRequest);
        HttpPostRequestEncoder postRequestEncoder = new HttpPostRequestEncoder(factory, request, true, CharsetUtil.UTF_8, HttpPostRequestEncoder.EncoderMode.HTML5);
        if (bodyValue instanceof MultipartBody.Builder) {
            bodyValue = ((MultipartBody.Builder) bodyValue).build();
        }
        if (bodyValue instanceof MultipartBody) {
            final MultipartBody multipartBody = (MultipartBody) bodyValue;
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
                    if (fileUploadObject instanceof FileUpload) {
                        FileUpload fu = (FileUpload) fileUploadObject;
                        if (content instanceof InputStream) {
                            fu.setContent((InputStream) content);
                        } else if (content instanceof File) {
                            fu.setContent((File) content);
                        } else if (content instanceof byte[]) {
                            final ByteBuf buffer = Unpooled.wrappedBuffer((byte[]) content);
                            fu.setContent(buffer);
                        }
                    }
                }
            }));
        } else {
            throw new MultipartException(String.format("The type %s is not a supported type for a multipart request body", bodyValue.getClass().getName()));
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
        traceHeaders(headers);
        if (io.micronaut.http.HttpMethod.permitsRequestBody(request.getMethod()) && request.getBody().isPresent() && nettyRequest instanceof FullHttpRequest) {
            FullHttpRequest fullHttpRequest = (FullHttpRequest) nettyRequest;
            ByteBuf content = fullHttpRequest.content();
            if (log.isTraceEnabled()) {
                traceBody("Request", content);
            }
        }
    }

    private void traceBody(String type, ByteBuf content) {
        log.trace(type + " Body");
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

    private void traceHeaders(HttpHeaders headers) {
        for (String name : headers.names()) {
            boolean isMasked = HEADER_MASK_PATTERNS.get().matcher(name).matches();
            List<String> all = headers.getAll(name);
            if (all.size() > 1) {
                for (String value : all) {
                    String maskedValue = isMasked ? mask(value) : value;
                    log.trace("{}: {}", name, maskedValue);
                }
            } else if (!all.isEmpty()) {
                String maskedValue = isMasked ? mask(all.get(0)) : all.get(0);
                log.trace("{}: {}", name, maskedValue);
            }
        }
    }

    @Nullable
    private String mask(@Nullable String value) {
        if (value == null) {
            return null;
        }
        return "*MASKED*";
    }

    private static MediaTypeCodecRegistry createDefaultMediaTypeRegistry() {
        JsonMapper mapper = new JacksonDatabindMapper();
        ApplicationConfiguration configuration = new ApplicationConfiguration();
        return MediaTypeCodecRegistry.of(
                new JsonMediaTypeCodec(mapper, configuration, null),
                new JsonStreamMediaTypeCodec(mapper, configuration, null)
        );
    }

    private <I> NettyRequestWriter prepareRequest(
            io.micronaut.http.HttpRequest<I> request,
            URI requestURI,
            FluxSink<HttpResponse<Object>> emitter) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        MediaType requestContentType = request
                .getContentType()
                .orElse(MediaType.APPLICATION_JSON_TYPE);

        boolean permitsBody = io.micronaut.http.HttpMethod.permitsRequestBody(request.getMethod());

        if (!(request instanceof MutableHttpRequest)) {
            throw new IllegalArgumentException("A MutableHttpRequest is required");
        }
        MutableHttpRequest clientHttpRequest = (MutableHttpRequest) request;
        NettyRequestWriter requestWriter = buildNettyRequest(
                clientHttpRequest,
                requestURI,
                requestContentType,
                permitsBody,
                null,
                throwable -> {
                    if (!emitter.isCancelled()) {
                        emitter.error(throwable);
                    }
                }
        );
        io.netty.handler.codec.http.HttpRequest nettyRequest = requestWriter.getNettyRequest();
        prepareHttpHeaders(requestURI, request, nettyRequest, permitsBody, true);
        return requestWriter;
    }

    private @NonNull InvocationInstrumenter combineFactories() {
        if (CollectionUtils.isEmpty(invocationInstrumenterFactories)) {
            return NOOP;
        }
        return InvocationInstrumenter.combine(invocationInstrumenterFactories.stream()
                .map(InvocationInstrumenterFactory::newInvocationInstrumenter)
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
    }

    static boolean isSecureScheme(String scheme) {
        return io.micronaut.http.HttpRequest.SCHEME_HTTPS.equalsIgnoreCase(scheme) || SCHEME_WSS.equalsIgnoreCase(scheme);
    }

    private <E extends HttpClientException> E customizeException(E exc) {
        customizeException0(configuration, informationalServiceId, exc);
        return exc;
    }

    static void customizeException0(HttpClientConfiguration configuration, String informationalServiceId, HttpClientException exc) {
        if (informationalServiceId != null) {
            exc.setServiceId(informationalServiceId);
        } else if (configuration instanceof ServiceHttpClientConfiguration) {
            exc.setServiceId(((ServiceHttpClientConfiguration) configuration).getServiceId());
        }
    }

    @FunctionalInterface
    interface ThrowingBiConsumer<T1, T2> {
        void accept(T1 t1, T2 t2) throws Exception;
    }

    /**
     * Key used for connection pooling and determining host/port.
     */
    static final class RequestKey {
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
                    throw ctx.customizeException(new NoHostException("URI specifies no host to connect to"));
                }

                final int i = host.indexOf(':');
                if (i > -1) {
                    final String portStr = host.substring(i + 1);
                    host = host.substring(0, i);
                    try {
                        port = Integer.parseInt(portStr);
                    } catch (NumberFormatException e) {
                        throw ctx.customizeException(new HttpClientException("URI specifies an invalid port: " + portStr));
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
            return Objects.hash(host, port, secure);
        }
    }

    /**
     * A Netty request writer.
     */
    private class NettyRequestWriter {

        private final HttpRequest nettyRequest;
        private final HttpPostRequestEncoder encoder;

        /**
         * @param scheme                 The scheme
         * @param nettyRequest           The Netty request
         * @param encoder                The encoder
         */
        NettyRequestWriter(HttpRequest nettyRequest, HttpPostRequestEncoder encoder) {
            this.nettyRequest = nettyRequest;
            this.encoder = encoder;
        }

        /**
         * @param channel     The channel
         * @param channelPool The channel pool
         * @param emitter     The emitter
         */
        protected void write(Channel channel, boolean isSecure, FluxSink<?> emitter) {
            final ChannelPipeline pipeline = channel.pipeline();
            if (connectionManager.httpVersion == io.micronaut.http.HttpVersion.HTTP_2_0) {
                if (isSecure) {
                    nettyRequest.headers().add(AbstractNettyHttpRequest.HTTP2_SCHEME, HttpScheme.HTTPS);
                } else {
                    nettyRequest.headers().add(AbstractNettyHttpRequest.HTTP2_SCHEME, HttpScheme.HTTP);
                }
            }
            processRequestWrite(channel, emitter, pipeline);
        }

        private void processRequestWrite(Channel channel, FluxSink<?> emitter, ChannelPipeline pipeline) {
            ChannelFuture writeFuture;
            if (encoder != null && encoder.isChunked()) {
                channel.attr(AttributeKey.valueOf(ChannelPipelineCustomizer.HANDLER_HTTP_CHUNK)).set(true);
                pipeline.addAfter(ChannelPipelineCustomizer.HANDLER_HTTP_STREAM, ChannelPipelineCustomizer.HANDLER_HTTP_CHUNK, new ChunkedWriteHandler());
                channel.write(nettyRequest);
                writeFuture = channel.writeAndFlush(encoder);
            } else {
                writeFuture = channel.writeAndFlush(nettyRequest);
            }

            connectionManager.addInstrumentedListener(writeFuture, f -> {
                try {
                    if (!f.isSuccess()) {
                        if (!emitter.isCancelled()) {
                            emitter.error(f.cause());
                        }
                    } else {
                        // reset to read mode
                        channel.read();
                    }
                } finally {
                    if (encoder != null) {
                        encoder.cleanFiles();
                    }
                    channel.attr(AttributeKey.valueOf(ChannelPipelineCustomizer.HANDLER_HTTP_CHUNK)).set(null);
                }
            });
        }

        /**
         * @return The Netty request
         */
        HttpRequest getNettyRequest() {
            return nettyRequest;
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

    private abstract class BaseHttpResponseHandler<R extends io.netty.handler.codec.http.HttpResponse, O> extends SimpleChannelInboundHandlerInstrumented<R> {
        private final Promise<O> responsePromise;
        private final io.micronaut.http.HttpRequest<?> parentRequest;
        private final io.micronaut.http.HttpRequest<?> finalRequest;

        public BaseHttpResponseHandler(Promise<O> responsePromise, io.micronaut.http.HttpRequest<?> parentRequest, io.micronaut.http.HttpRequest<?> finalRequest) {
            super(connectionManager.instrumenter);
            this.responsePromise = responsePromise;
            this.parentRequest = parentRequest;
            this.finalRequest = finalRequest;
        }

        @Override
        public abstract boolean acceptInboundMessage(Object msg);

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
                result = customizeException(new ContentLengthExceededException(configuration.getMaxContentLength()));
            } else if (cause instanceof io.netty.handler.timeout.ReadTimeoutException) {
                result = ReadTimeoutException.TIMEOUT_EXCEPTION;
            } else {
                result = customizeException(new HttpClientException("Error occurred reading HTTP response: " + message, cause));
            }
            responsePromise.tryFailure(result);
        }

        @Override
        protected void channelReadInstrumented(ChannelHandlerContext ctx, R msg) throws Exception {
            if (responsePromise.isDone()) {
                return;
            }

            if (log.isDebugEnabled()) {
                log.debug("Received response {} from {}", msg.status().code(), finalRequest.getUri());
            }

            int code = msg.status().code();
            HttpHeaders headers1 = msg.headers();
            if (code > 300 && code < 400 && configuration.isFollowRedirects() && headers1.contains(HttpHeaderNames.LOCATION)) {
                String location = headers1.get(HttpHeaderNames.LOCATION);

                MutableHttpRequest<Object> redirectRequest;
                if (code == 307) {
                    redirectRequest = io.micronaut.http.HttpRequest.create(finalRequest.getMethod(), location);
                    finalRequest.getBody().ifPresent(redirectRequest::body);
                } else {
                    redirectRequest = io.micronaut.http.HttpRequest.GET(location);
                }

                setRedirectHeaders(finalRequest, redirectRequest);
                Flux.from(resolveRedirectURI(parentRequest, redirectRequest))
                        .flatMap(makeRedirectHandler(parentRequest, redirectRequest))
                        .subscribe(new NettyPromiseSubscriber<>(responsePromise));
                return;
            }

            HttpResponseStatus status = msg.status();
            int statusCode = status.code();
            HttpStatus httpStatus;
            try {
                httpStatus = HttpStatus.valueOf(statusCode);
            } catch (IllegalArgumentException e) {
                responsePromise.tryFailure(e);
                return;
            }

            HttpHeaders headers = msg.headers();
            if (log.isTraceEnabled()) {
                log.trace("HTTP Client Response Received ({}) for Request: {} {}", msg.status(), finalRequest.getMethodName(), finalRequest.getUri());
                traceHeaders(headers);
            }
            buildResponse(responsePromise, msg, httpStatus);
        }

        private void setRedirectHeaders(@Nullable io.micronaut.http.HttpRequest<?> request, MutableHttpRequest<Object> redirectRequest) {
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

        protected abstract Function<URI, Publisher<? extends O>> makeRedirectHandler(io.micronaut.http.HttpRequest<?> parentRequest, MutableHttpRequest<Object> redirectRequest);

        protected abstract void buildResponse(Promise<? super O> promise, R msg, HttpStatus httpStatus);
    }

    private class FullHttpResponseHandler<O> extends BaseHttpResponseHandler<FullHttpResponse, HttpResponse<O>> {
        private final boolean secure;
        private final Argument<O> bodyType;
        private final Argument<?> errorType;
        private final ConnectionManager.PoolHandle poolHandle;

        public FullHttpResponseHandler(
                Promise<HttpResponse<O>> responsePromise,
                ConnectionManager.PoolHandle poolHandle,
                boolean secure,
                io.micronaut.http.HttpRequest<?> request,
                Argument<O> bodyType,
                Argument<?> errorType) {
            super(responsePromise, request, request);
            this.secure = secure;
            this.bodyType = bodyType;
            this.errorType = errorType;
            this.poolHandle = poolHandle;
        }

        @Override
        public boolean acceptInboundMessage(Object msg) {
            return msg instanceof FullHttpResponse;
        }

        @Override
        protected Function<URI, Publisher<? extends HttpResponse<O>>> makeRedirectHandler(io.micronaut.http.HttpRequest<?> parentRequest, MutableHttpRequest<Object> redirectRequest) {
            return uri -> exchangeImpl(uri, parentRequest, redirectRequest, bodyType, errorType);
        }

        @Override
        protected void channelReadInstrumented(ChannelHandlerContext channelHandlerContext, FullHttpResponse fullResponse) throws Exception {
            try {
                // corresponding release is the SimpleChannelInboundHandler autoRelease
                // this should probably be dropped at some point
                fullResponse.retain();
                super.channelReadInstrumented(channelHandlerContext, fullResponse);
            } finally {
                // leave one reference for SimpleChannelInboundHandler autoRelease
                if (fullResponse.refCnt() > 1) {
                    try {
                        ReferenceCountUtil.release(fullResponse);
                    } catch (Exception e) {
                        if (log.isDebugEnabled()) {
                            log.debug("Failed to release response: {}", fullResponse);
                        }
                    }
                }
                if (!HttpUtil.isKeepAlive(fullResponse)) {
                    poolHandle.taint();
                }
                channelHandlerContext.pipeline().remove(this);
            }
        }

        @Override
        protected void buildResponse(Promise<? super HttpResponse<O>> promise, FullHttpResponse msg, HttpStatus httpStatus) {
            try {
                if (log.isTraceEnabled()) {
                    traceBody("Response", msg.content());
                }

                if (httpStatus == HttpStatus.NO_CONTENT) {
                    // normalize the NO_CONTENT header, since http content aggregator adds it even if not present in the response
                    msg.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
                }

                boolean convertBodyWithBodyType = httpStatus.getCode() < 400 ||
                        (!DefaultHttpClient.this.configuration.isExceptionOnErrorStatus() && bodyType.equalsType(errorType));
                FullNettyClientHttpResponse<O> response
                        = new FullNettyClientHttpResponse<>(msg, httpStatus, mediaTypeCodecRegistry, byteBufferFactory, bodyType, convertBodyWithBodyType);

                if (convertBodyWithBodyType) {
                    promise.trySuccess(response);
                    response.onComplete();
                } else { // error flow
                    try {
                        promise.tryFailure(makeErrorFromRequestBody(msg.status(), response));
                        response.onComplete();
                    } catch (HttpClientResponseException t) {
                        promise.tryFailure(t);
                        response.onComplete();
                    } catch (Exception t) {
                        response.onComplete();
                        promise.tryFailure(makeErrorBodyParseError(msg, httpStatus, t));
                    }
                }
            } catch (HttpClientResponseException t) {
                promise.tryFailure(t);
            } catch (Exception t) {
                makeNormalBodyParseError(msg, httpStatus, t, cause -> {
                    if (!promise.tryFailure(cause) && log.isWarnEnabled()) {
                        log.warn("Exception fired after handler completed: " + t.getMessage(), t);
                    }
                });
            }
        }

        /**
         * Create a {@link HttpClientResponseException} from a response with a failed HTTP status.
         */
        private HttpClientResponseException makeErrorFromRequestBody(HttpResponseStatus status, FullNettyClientHttpResponse<?> response) {
            if (errorType != null && errorType != HttpClient.DEFAULT_ERROR_TYPE) {
                return customizeException(new HttpClientResponseException(
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
                return customizeException(new HttpClientResponseException(status.reasonPhrase(), response));
            }
        }

        /**
         * Create a {@link HttpClientResponseException} if parsing of the HTTP error body failed.
         */
        private HttpClientResponseException makeErrorBodyParseError(FullHttpResponse fullResponse, HttpStatus httpStatus, Throwable t) {
            FullNettyClientHttpResponse<Object> errorResponse = new FullNettyClientHttpResponse<>(
                    fullResponse,
                    httpStatus,
                    mediaTypeCodecRegistry,
                    byteBufferFactory,
                    null,
                    false
            );
            // this onComplete call disables further parsing by HttpClientResponseException
            errorResponse.onComplete();
            return customizeException(new HttpClientResponseException(
                "Error decoding HTTP error response body: " + t.getMessage(),
                t,
                errorResponse,
                null
            ));
        }

        private void makeNormalBodyParseError(FullHttpResponse fullResponse, HttpStatus httpStatus, Throwable t, Consumer<HttpClientResponseException> forward) {
            FullNettyClientHttpResponse<Object> response = new FullNettyClientHttpResponse<>(
                    fullResponse,
                    httpStatus,
                    mediaTypeCodecRegistry,
                    byteBufferFactory,
                    null,
                    false
            );
            HttpClientResponseException clientResponseError = customizeException(new HttpClientResponseException(
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
            poolHandle.release();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            super.exceptionCaught(ctx, cause);
            poolHandle.taint();
            ctx.pipeline().remove(this);
        }
    }

    private class StreamFullHttpResponseHandler extends BaseHttpResponseHandler<FullHttpResponse, HttpResponse<?>> {
        public StreamFullHttpResponseHandler(Promise<HttpResponse<?>> responsePromise, io.micronaut.http.HttpRequest<?> parentRequest, io.micronaut.http.HttpRequest<?> finalRequest) {
            super(responsePromise, parentRequest, finalRequest);
        }

        @Override
        public boolean acceptInboundMessage(Object msg) {
            return msg instanceof FullHttpResponse;
        }

        @Override
        protected void buildResponse(Promise<? super HttpResponse<?>> promise, FullHttpResponse msg, HttpStatus httpStatus) {
            Publisher<HttpContent> bodyPublisher;
            if (msg.content() instanceof EmptyByteBuf) {
                bodyPublisher = Publishers.empty();
            } else {
                bodyPublisher = Publishers.just(new DefaultLastHttpContent(msg.content()));
            }
            DefaultStreamedHttpResponse nettyResponse = new DefaultStreamedHttpResponse(
                    msg.protocolVersion(),
                    msg.status(),
                    msg.headers(),
                    bodyPublisher
            );
            promise.trySuccess(new NettyStreamedHttpResponse<>(nettyResponse, httpStatus));
        }

        @Override
        protected Function<URI, Publisher<? extends HttpResponse<?>>> makeRedirectHandler(io.micronaut.http.HttpRequest<?> parentRequest, MutableHttpRequest<Object> redirectRequest) {
            return uri -> buildStreamExchange(parentRequest, redirectRequest, uri, null);
        }
    }

    private class StreamStreamHttpResponseHandler extends BaseHttpResponseHandler<StreamedHttpResponse, HttpResponse<?>> {
        public StreamStreamHttpResponseHandler(Promise<HttpResponse<?>> responsePromise, io.micronaut.http.HttpRequest<?> parentRequest, io.micronaut.http.HttpRequest<?> finalRequest) {
            super(responsePromise, parentRequest, finalRequest);
        }

        @Override
        public boolean acceptInboundMessage(Object msg) {
            return msg instanceof StreamedHttpResponse;
        }

        @Override
        protected void buildResponse(Promise<? super HttpResponse<?>> promise, StreamedHttpResponse msg, HttpStatus httpStatus) {
            promise.trySuccess(new NettyStreamedHttpResponse<>(msg, httpStatus));
        }

        @Override
        protected Function<URI, Publisher<? extends HttpResponse<?>>> makeRedirectHandler(io.micronaut.http.HttpRequest<?> parentRequest, MutableHttpRequest<Object> redirectRequest) {
            return uri -> buildStreamExchange(parentRequest, redirectRequest, uri, null);
        }
    }
}
