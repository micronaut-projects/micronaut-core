/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.http.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.AnnotationMetadataResolver;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.beans.BeanMap;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.PathMatcher;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.client.exceptions.ContentLengthExceededException;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.client.sse.RxSseClient;
import io.micronaut.http.client.ssl.NettyClientSslBuilder;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.http.multipart.MultipartException;
import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.http.netty.channel.NettyThreadFactory;
import io.micronaut.http.netty.content.HttpContentUtil;
import io.micronaut.http.netty.stream.HttpStreamsClientHandler;
import io.micronaut.http.netty.stream.StreamedHttpResponse;
import io.micronaut.http.sse.Event;
import io.micronaut.http.ssl.ClientSslConfiguration;
import io.micronaut.jackson.ObjectMapperFactory;
import io.micronaut.jackson.codec.JsonMediaTypeCodec;
import io.micronaut.jackson.codec.JsonStreamMediaTypeCodec;
import io.micronaut.jackson.parser.JacksonProcessor;
import io.micronaut.runtime.ApplicationConfiguration;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import io.reactivex.*;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Cancellable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Closeable;
import java.net.*;
import java.net.Proxy.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default implementation of the {@link HttpClient} interface based on Netty.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Prototype
public class DefaultHttpClient implements RxHttpClient, RxStreamingHttpClient, RxSseClient, Closeable, AutoCloseable {

    protected static final String HANDLER_AGGREGATOR = "http-aggregator";
    protected static final String HANDLER_CHUNK = "chunk-writer";
    protected static final String HANDLER_STREAM = "stream-handler";
    protected static final String HANDLER_DECODER = "http-decoder";

    private static final Logger LOG = LoggerFactory.getLogger(DefaultHttpClient.class);
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;

    protected final Bootstrap bootstrap;
    protected EventLoopGroup group;
    protected MediaTypeCodecRegistry mediaTypeCodecRegistry;
    protected ByteBufferFactory<ByteBufAllocator, ByteBuf> byteBufferFactory = new NettyByteBufferFactory();

    private final Scheduler scheduler;
    private final LoadBalancer loadBalancer;
    private final HttpClientConfiguration configuration;
    private final String contextPath;
    private final SslContext sslContext;
    private final AnnotationMetadataResolver annotationMetadataResolver;
    private final ThreadFactory threadFactory;

    private final HttpClientFilter[] filters;
    private final Charset defaultCharset;

    private Set<String> clientIdentifiers = Collections.emptySet();

    /**
     * Construct a client for the given arguments.
     *
     * @param loadBalancer               The {@link LoadBalancer} to use for selecting servers
     * @param configuration              The {@link HttpClientConfiguration} object
     * @param contextPath                The base URI to prepend to request uris
     * @param threadFactory              The thread factory to use for client threads
     * @param nettyClientSslBuilder      The SSL builder
     * @param codecRegistry              The {@link MediaTypeCodecRegistry} to use for encoding and decoding objects
     * @param annotationMetadataResolver The annotation metadata resolver
     * @param filters                    The filters to use
     */
    @Inject
    public DefaultHttpClient(@Parameter LoadBalancer loadBalancer,
                             @Parameter HttpClientConfiguration configuration,
                             @Parameter @Nullable String contextPath,
                             @Named(NettyThreadFactory.NAME) @Nullable ThreadFactory threadFactory,
                             NettyClientSslBuilder nettyClientSslBuilder,
                             MediaTypeCodecRegistry codecRegistry,
                             @Nullable AnnotationMetadataResolver annotationMetadataResolver,
                             HttpClientFilter... filters) {

        this.loadBalancer = loadBalancer;
        this.defaultCharset = configuration.getDefaultCharset();
        this.contextPath = contextPath;
        this.bootstrap = new Bootstrap();
        this.configuration = configuration;
        this.sslContext = nettyClientSslBuilder.build().orElse(null);
        this.group = createEventLoopGroup(configuration, threadFactory);
        this.scheduler = Schedulers.from(group);
        this.threadFactory = threadFactory;
        this.bootstrap.group(group)
            .channel(NioSocketChannel.class)
            .option(ChannelOption.SO_KEEPALIVE, true);

        Optional<Duration> connectTimeout = configuration.getConnectTimeout();
        connectTimeout.ifPresent(duration -> this.bootstrap.option(
                ChannelOption.CONNECT_TIMEOUT_MILLIS,
                Long.valueOf(duration.toMillis()).intValue()
        ));

        for (Map.Entry<ChannelOption, Object> entry : configuration.getChannelOptions().entrySet()) {
            Object v = entry.getValue();
            if (v != null) {
                ChannelOption channelOption = entry.getKey();
                bootstrap.option(channelOption, v);
            }
        }
        this.mediaTypeCodecRegistry = codecRegistry;
        this.filters = filters;
        this.annotationMetadataResolver = annotationMetadataResolver != null ? annotationMetadataResolver : AnnotationMetadataResolver.DEFAULT;
    }

    /**
     * @param url                   The URL
     * @param configuration         The {@link HttpClientConfiguration} object
     * @param nettyClientSslBuilder The SSL builder
     * @param codecRegistry         The {@link MediaTypeCodecRegistry} to use for encoding and decoding objects
     * @param filters               The filters to use
     */
    public DefaultHttpClient(URL url,
                             HttpClientConfiguration configuration,
                             NettyClientSslBuilder nettyClientSslBuilder,
                             MediaTypeCodecRegistry codecRegistry,
                             HttpClientFilter... filters) {
        this(LoadBalancer.fixed(url), configuration, null, new DefaultThreadFactory(MultithreadEventLoopGroup.class), nettyClientSslBuilder, codecRegistry, AnnotationMetadataResolver.DEFAULT, filters);
    }

    /**
     * @param loadBalancer The {@link LoadBalancer} to use for selecting servers
     */
    public DefaultHttpClient(LoadBalancer loadBalancer) {
        this(loadBalancer,
            new DefaultHttpClientConfiguration(),
            null,
            new DefaultThreadFactory(MultithreadEventLoopGroup.class),
            new NettyClientSslBuilder(new ClientSslConfiguration(), new ResourceResolver()),
            createDefaultMediaTypeRegistry(), AnnotationMetadataResolver.DEFAULT);
    }

    /**
     * @param url The URL
     */
    public DefaultHttpClient(@Parameter URL url) {
        this(url, new DefaultHttpClientConfiguration());
    }

    /**
     * @param url           The URL
     * @param configuration The {@link HttpClientConfiguration} object
     */
    public DefaultHttpClient(URL url, HttpClientConfiguration configuration) {
        this(
                LoadBalancer.fixed(url), configuration, null, new DefaultThreadFactory(MultithreadEventLoopGroup.class),
                createSslBuilder(), createDefaultMediaTypeRegistry(),
                AnnotationMetadataResolver.DEFAULT
        );
    }

    /**
     * @param url           The URL
     * @param configuration The {@link HttpClientConfiguration} object
     * @param contextPath   The base URI to prepend to request uris
     */
    public DefaultHttpClient(URL url, HttpClientConfiguration configuration, String contextPath) {
        this(
                LoadBalancer.fixed(url), configuration, contextPath, new DefaultThreadFactory(MultithreadEventLoopGroup.class),
                createSslBuilder(), createDefaultMediaTypeRegistry(),
                AnnotationMetadataResolver.DEFAULT
        );
    }

    /**
     * @param loadBalancer  The {@link LoadBalancer} to use for selecting servers
     * @param configuration The {@link HttpClientConfiguration} object
     */
    public DefaultHttpClient(LoadBalancer loadBalancer, HttpClientConfiguration configuration) {
        this(loadBalancer,
            configuration, null, new DefaultThreadFactory(MultithreadEventLoopGroup.class),
            new NettyClientSslBuilder(new ClientSslConfiguration(), new ResourceResolver()),
            createDefaultMediaTypeRegistry(), AnnotationMetadataResolver.DEFAULT);
    }

    /**
     * @param loadBalancer  The {@link LoadBalancer} to use for selecting servers
     * @param configuration The {@link HttpClientConfiguration} object
     * @param contextPath   The base URI to prepend to request uris
     */
    public DefaultHttpClient(LoadBalancer loadBalancer, HttpClientConfiguration configuration, String contextPath) {
        this(loadBalancer,
                configuration, contextPath, new DefaultThreadFactory(MultithreadEventLoopGroup.class),
                new NettyClientSslBuilder(new ClientSslConfiguration(), new ResourceResolver()),
                createDefaultMediaTypeRegistry(), AnnotationMetadataResolver.DEFAULT);
    }

    @Override
    public HttpClient start() {
        if (!isRunning()) {
            this.group = createEventLoopGroup(configuration, threadFactory);
        }
        return this;
    }

    @Override
    public boolean isRunning() {
        return !group.isShutdown();
    }

    @Override
    @PreDestroy
    public HttpClient stop() {
        if (isRunning()) {
            Duration shutdownTimeout = configuration.getShutdownTimeout().orElse(Duration.ofMillis(100));
            Future<?> future = this.group.shutdownGracefully(
                    1,
                    shutdownTimeout.toMillis(),
                    TimeUnit.MILLISECONDS
            );
            future.addListener(f -> {
                if (!f.isSuccess() && LOG.isErrorEnabled()) {
                    Throwable cause = f.cause();
                    LOG.error("Error shutting down HTTP client: " + cause.getMessage(), cause);
                }
            });
            try {
                future.await(shutdownTimeout.toMillis());
            } catch (InterruptedException e) {
                // ignore
            }
        }
        return this;
    }

    /**
     * Sets the client identifiers that this client applies to. Used to select a subset of {@link HttpClientFilter}.
     * The client identifiers are equivalents to the value of {@link Client#id()}
     *
     * @param clientIdentifiers The client identifiers
     */
    public void setClientIdentifiers(Set<String> clientIdentifiers) {
        if (clientIdentifiers != null) {
            this.clientIdentifiers = clientIdentifiers;
        }
    }

    /**
     * @param clientIdentifiers The client identifiers
     * @see #setClientIdentifiers(Set)
     */
    public void setClientIdentifiers(String... clientIdentifiers) {
        if (clientIdentifiers != null) {
            this.clientIdentifiers = new HashSet<>(Arrays.asList(clientIdentifiers));
        }
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
            public <I, O> io.micronaut.http.HttpResponse<O> exchange(io.micronaut.http.HttpRequest<I> request, io.micronaut.core.type.Argument<O> bodyType) {
                Flowable<io.micronaut.http.HttpResponse<O>> publisher = DefaultHttpClient.this.exchange(request, bodyType);
                return publisher.doOnNext((res) -> {
                    Optional<ByteBuf> byteBuf = res.getBody(ByteBuf.class);
                    byteBuf.ifPresent(bb -> {
                        if (bb.refCnt() > 0) {
                            ReferenceCountUtil.safeRelease(bb);
                        }
                    });
                }).blockingFirst();
            }
        };
    }

    @SuppressWarnings("SubscriberImplementation")
    @Override
    public <I> Flowable<Event<ByteBuffer<?>>> eventStream(io.micronaut.http.HttpRequest<I> request) {

        if (request instanceof MutableHttpRequest) {
            ((MutableHttpRequest) request).accept(MediaType.TEXT_EVENT_STREAM_TYPE);
        }

        Flowable<Event<ByteBuffer<?>>> eventFlowable = Flowable.create(emitter ->
            dataStream(request).subscribe(new Subscriber<ByteBuffer<?>>() {
                private Subscription dataSubscription;
                private CurrentEvent currentEvent = new CurrentEvent(
                        byteBufferFactory.getNativeAllocator().compositeBuffer(10)
                );

                @Override
                public void onSubscribe(Subscription s) {
                    this.dataSubscription = s;
                    Cancellable cancellable = () -> dataSubscription.cancel();
                    emitter.setCancellable(cancellable);
                    if (!emitter.isCancelled() && emitter.requested() > 0) {
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
                                emitter.onNext(
                                        event
                                );
                            } finally {
                                currentEvent.data.release();
                                currentEvent = new CurrentEvent(
                                        byteBufferFactory.getNativeAllocator().compositeBuffer(10)
                                );
                            }
                        } else {
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
                                            ByteBuf nativeBuffer = (ByteBuf) content.asNativeBuffer();
                                            currentEvent.data.addComponent(true, nativeBuffer);
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

                        if (emitter.requested() > 0 && !emitter.isCancelled()) {
                            dataSubscription.request(1);
                        }
                    } catch (Throwable e) {
                        onError(e);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    dataSubscription.cancel();
                    if (t instanceof HttpClientException) {
                        emitter.onError(t);
                    } else {
                        emitter.onError(new HttpClientException("Error consuming Server Sent Events: " + t.getMessage(), t));
                    }
                }

                @Override
                public void onComplete() {
                    emitter.onComplete();
                }
        }), BackpressureStrategy.BUFFER);

        return eventFlowable;
    }

    @Override
    public <I, B> Flowable<Event<B>> eventStream(io.micronaut.http.HttpRequest<I> request, Argument<B> eventType) {
        return eventStream(request).map(byteBufferEvent -> {
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

    @SuppressWarnings("unchecked")
    @Override
    public <I> Flowable<ByteBuffer<?>> dataStream(io.micronaut.http.HttpRequest<I> request) {
        return Flowable.fromPublisher(resolveRequestURI(request))
            .flatMap(buildDataStreamPublisher(request));

    }

    @Override
    public <I> Flowable<io.micronaut.http.HttpResponse<ByteBuffer<?>>> exchangeStream(io.micronaut.http.HttpRequest<I> request) {
        return Flowable.fromPublisher(resolveRequestURI(request))
            .flatMap(buildExchangeStreamPublisher(request));
    }

    @Override
    public <I, O> Flowable<O> jsonStream(io.micronaut.http.HttpRequest<I> request, io.micronaut.core.type.Argument<O> type) {
        return Flowable.fromPublisher(resolveRequestURI(request))
            .flatMap(buildJsonStreamPublisher(request, type));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <I> Flowable<Map<String, Object>> jsonStream(io.micronaut.http.HttpRequest<I> request) {
        Flowable flowable = jsonStream(request, Map.class);
        return flowable;
    }

    @Override
    public <I, O> Flowable<O> jsonStream(io.micronaut.http.HttpRequest<I> request, Class<O> type) {
        return jsonStream(request, io.micronaut.core.type.Argument.of(type));
    }

    @Override
    public <I, O> Flowable<io.micronaut.http.HttpResponse<O>> exchange(io.micronaut.http.HttpRequest<I> request, io.micronaut.core.type.Argument<O> bodyType) {
        Publisher<URI> uriPublisher = resolveRequestURI(request);
        return Flowable.fromPublisher(uriPublisher)
            .switchMap(buildExchangePublisher(request, bodyType));
    }

    /**
     * @param request The request
     * @param <I>     The input type
     * @return A {@link Function}
     */
    protected <I> Function<URI, Flowable<io.micronaut.http.HttpResponse<ByteBuffer<?>>>> buildExchangeStreamPublisher(io.micronaut.http.HttpRequest<I> request) {
        return requestURI -> {
            Flowable<io.micronaut.http.HttpResponse<Object>> streamResponsePublisher = buildStreamExchange(request, requestURI);
            return streamResponsePublisher.switchMap(response -> {
                if (!(response instanceof NettyStreamedHttpResponse)) {
                    throw new IllegalStateException("Response has been wrapped in non streaming type. Do not wrap the response in client filters for stream requests");
                }
                NettyStreamedHttpResponse<ByteBuffer<?>> nettyStreamedHttpResponse = (NettyStreamedHttpResponse) response;
                Flowable<HttpContent> httpContentFlowable = Flowable.fromPublisher(nettyStreamedHttpResponse.getNettyResponse());
                return httpContentFlowable.map((Function<HttpContent, io.micronaut.http.HttpResponse<ByteBuffer<?>>>) message -> {
                    ByteBuf byteBuf = message.content();
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("HTTP Client Streaming Response Received Chunk (length: {})", byteBuf.readableBytes());
                        traceBody("Response", byteBuf);
                    }
                    ByteBuffer<?> byteBuffer = byteBufferFactory.wrap(byteBuf);
                    nettyStreamedHttpResponse.setBody(byteBuffer);
                    return nettyStreamedHttpResponse;
                });
            });
        };
    }

    /**
     * @param request The request
     * @param type    The type
     * @param <I>     The input type
     * @param <O>     The output type
     * @return A {@link Function}
     */
    protected <I, O> Function<URI, Flowable<O>> buildJsonStreamPublisher(io.micronaut.http.HttpRequest<I> request, io.micronaut.core.type.Argument<O> type) {
        return requestURI -> {
            Flowable<io.micronaut.http.HttpResponse<Object>> streamResponsePublisher = buildStreamExchange(request, requestURI);
            return streamResponsePublisher.switchMap(response -> {
                if (!(response instanceof NettyStreamedHttpResponse)) {
                    throw new IllegalStateException("Response has been wrapped in non streaming type. Do not wrap the response in client filters for stream requests");
                }

                JsonMediaTypeCodec mediaTypeCodec = (JsonMediaTypeCodec) mediaTypeCodecRegistry.findCodec(MediaType.APPLICATION_JSON_TYPE)
                    .orElseThrow(() -> new IllegalStateException("No JSON codec found"));

                NettyStreamedHttpResponse<?> nettyStreamedHttpResponse = (NettyStreamedHttpResponse) response;
                Flowable<HttpContent> httpContentFlowable = Flowable.fromPublisher(nettyStreamedHttpResponse.getNettyResponse());

                boolean isJsonStream = request.getContentType().map(mediaType -> mediaType.equals(MediaType.APPLICATION_JSON_STREAM_TYPE)).orElse(false);
                boolean streamArray = !Iterable.class.isAssignableFrom(type.getType()) && !isJsonStream;
                JacksonProcessor jacksonProcessor = new JacksonProcessor(mediaTypeCodec.getObjectMapper().getFactory(), streamArray) {
                    @Override
                    public void subscribe(Subscriber<? super JsonNode> downstreamSubscriber) {
                        httpContentFlowable.map(content -> {
                            ByteBuf chunk = content.content();
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("HTTP Client Streaming Response Received Chunk (length: {})", chunk.readableBytes());
                                traceBody("Chunk", chunk);
                            }
                            try {
                                return ByteBufUtil.getBytes(chunk);
                            } finally {
                                chunk.release();
                            }
                        }).subscribe(this);
                        super.subscribe(downstreamSubscriber);
                    }
                };
                return Flowable.fromPublisher(jacksonProcessor).map(jsonNode ->
                    mediaTypeCodec.decode(type, jsonNode)
                );
            });
        };
    }

    /**
     * @param request The request
     * @param <I>     The input type
     * @return A {@link Function}
     */
    protected <I> Function<URI, Flowable<ByteBuffer<?>>> buildDataStreamPublisher(io.micronaut.http.HttpRequest<I> request) {
        return requestURI -> {
            Flowable<io.micronaut.http.HttpResponse<Object>> streamResponsePublisher = buildStreamExchange(request, requestURI);
            Function<HttpContent, ByteBuffer<?>> contentMapper = message -> {
                ByteBuf byteBuf = message.content();
                return byteBufferFactory.wrap(byteBuf);
            };
            return streamResponsePublisher.switchMap(response -> {
                if (!(response instanceof NettyStreamedHttpResponse)) {
                    throw new IllegalStateException("Response has been wrapped in non streaming type. Do not wrap the response in client filters for stream requests");
                }
                NettyStreamedHttpResponse nettyStreamedHttpResponse = (NettyStreamedHttpResponse) response;
                Flowable<HttpContent> httpContentFlowable = Flowable.fromPublisher(nettyStreamedHttpResponse.getNettyResponse());
                return httpContentFlowable.map(contentMapper);
            });
        };
    }

    /**
     * @param request    The request
     * @param requestURI The request URI
     * @param <I>        The input type
     * @return A {@link Flowable}
     */
    @SuppressWarnings("MagicNumber")
    protected <I> Flowable<io.micronaut.http.HttpResponse<Object>> buildStreamExchange(io.micronaut.http.HttpRequest<I> request, URI requestURI) {
        SslContext sslContext = buildSslContext(requestURI);

        AtomicReference<io.micronaut.http.HttpRequest> requestWrapper = new AtomicReference<>(request);
        Flowable<io.micronaut.http.HttpResponse<Object>> streamResponsePublisher = Flowable.create(emitter -> {
                ChannelFuture channelFuture = doConnect(request, requestURI, sslContext, true);
                Disposable disposable = buildDisposableChannel(channelFuture);
                emitter.setDisposable(disposable);
                emitter.setCancellable(disposable::dispose);


                channelFuture
                    .addListener((ChannelFutureListener) f -> {
                        if (f.isSuccess()) {
                            Channel channel = f.channel();

                            NettyRequestWriter requestWriter = prepareRequest(requestWrapper.get(), requestURI);
                            io.netty.handler.codec.http.HttpRequest nettyRequest = requestWriter.getNettyRequest();
                            ChannelPipeline pipeline = channel.pipeline();
                            pipeline.addLast(new SimpleChannelInboundHandler<StreamedHttpResponse>() {

                                AtomicBoolean received = new AtomicBoolean(false);

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    if (received.compareAndSet(false, true)) {
                                        emitter.onError(cause);
                                    }
                                }

                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, StreamedHttpResponse msg) throws Exception {
                                    if (received.compareAndSet(false, true)) {
                                        NettyStreamedHttpResponse response = new NettyStreamedHttpResponse(msg);
                                        HttpHeaders headers = msg.headers();
                                        if (LOG.isTraceEnabled()) {
                                            LOG.trace("HTTP Client Streaming Response Received: {}", msg.status());
                                            traceHeaders(headers);
                                        }

                                        int statusCode = response.getStatus().getCode();
                                        if (statusCode > 300 && statusCode < 400 && configuration.isFollowRedirects() && headers.contains(HttpHeaderNames.LOCATION)) {
                                            String location = headers.get(HttpHeaderNames.LOCATION);
                                            Flowable<io.micronaut.http.HttpResponse<Object>> redirectedExchange;
                                            try {
                                                MutableHttpRequest<Object> redirectRequest = io.micronaut.http.HttpRequest.GET(location);
                                                redirectedExchange = Flowable.fromPublisher(resolveRequestURI(redirectRequest))
                                                    .flatMap(uri -> buildStreamExchange(redirectRequest, uri));

                                                //noinspection SubscriberImplementation
                                                redirectedExchange.subscribe(new Subscriber<io.micronaut.http.HttpResponse<Object>>() {
                                                    Subscription sub;

                                                    @Override
                                                    public void onSubscribe(Subscription s) {
                                                        s.request(1);
                                                        this.sub = s;
                                                    }

                                                    @Override
                                                    public void onNext(io.micronaut.http.HttpResponse<Object> objectHttpResponse) {
                                                        emitter.onNext(objectHttpResponse);
                                                        sub.cancel();
                                                    }

                                                    @Override
                                                    public void onError(Throwable t) {
                                                        emitter.onError(t);
                                                        sub.cancel();
                                                    }

                                                    @Override
                                                    public void onComplete() {
                                                        emitter.onComplete();
                                                    }
                                                });
                                            } catch (Exception e) {
                                                emitter.onError(e);
                                            }
                                        } else {
                                            boolean errorStatus = statusCode >= 400;
                                            if (errorStatus) {
                                                emitter.onError(new HttpClientResponseException(response.getStatus().getReason(), response));
                                            } else {
                                                emitter.onNext(response);
                                                emitter.onComplete();
                                            }

                                        }

                                    }
                                }
                            });
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Sending HTTP Request: {} {}", nettyRequest.method(), nettyRequest.uri());
                                LOG.debug("Chosen Server: {}({})", requestURI.getHost(), requestURI.getPort());
                            }
                            if (LOG.isTraceEnabled()) {
                                traceRequest(requestWrapper.get(), nettyRequest);
                            }

                            requestWriter.writeAndClose(channel, emitter);
                        } else {
                            Throwable cause = f.cause();
                            emitter.onError(
                                new HttpClientException("Connect error:" + cause.getMessage(), cause)
                            );
                        }
                    });
            }, BackpressureStrategy.BUFFER
        );
        // apply filters
        streamResponsePublisher = Flowable.fromPublisher(
                applyFilterToResponsePublisher(request, requestURI, requestWrapper, streamResponsePublisher)
        );

        return streamResponsePublisher.subscribeOn(scheduler);
    }

    /**
     * @param request  The request
     * @param bodyType The body type
     * @param <I>      The input type
     * @param <O>      The output type
     * @return A {@link Function}
     */
    protected <I, O> Function<URI, Publisher<? extends io.micronaut.http.HttpResponse<O>>> buildExchangePublisher(io.micronaut.http.HttpRequest<I> request, io.micronaut.core.type.Argument<O> bodyType) {
        AtomicReference<io.micronaut.http.HttpRequest> requestWrapper = new AtomicReference<>(request);
        return requestURI -> {
            Flowable<io.micronaut.http.HttpResponse<O>> responsePublisher = Flowable.create(emitter -> {
                SslContext sslContext = buildSslContext(requestURI);

                ChannelFuture connectionFuture = doConnect(request, requestURI, sslContext, false);
                connectionFuture.addListener(future -> {
                    if (future.isSuccess()) {
                        try {
                            Channel channel = connectionFuture.channel();
                            io.micronaut.http.HttpRequest<I> finalRequest = requestWrapper.get();
                            MediaType requestContentType = finalRequest
                                .getContentType()
                                .orElse(MediaType.APPLICATION_JSON_TYPE);

                            boolean permitsBody = io.micronaut.http.HttpMethod.permitsRequestBody(request.getMethod());

                            NettyClientHttpRequest clientHttpRequest = (NettyClientHttpRequest) finalRequest;
                            NettyRequestWriter requestWriter = buildNettyRequest(clientHttpRequest, requestURI, requestContentType, permitsBody);
                            io.netty.handler.codec.http.HttpRequest nettyRequest = requestWriter.getNettyRequest();

                            prepareHttpHeaders(requestURI, finalRequest, nettyRequest, permitsBody);
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Sending HTTP Request: {} {}", nettyRequest.method(), nettyRequest.uri());
                                LOG.debug("Chosen Server: {}({})", requestURI.getHost(), requestURI.getPort());
                            }
                            if (LOG.isTraceEnabled()) {
                                traceRequest(finalRequest, nettyRequest);
                            }

                            addFullHttpResponseHandler(request, channel, emitter, bodyType);
                            requestWriter.writeAndClose(channel, emitter);
                        } catch (Exception e) {
                            emitter.onError(e);
                        }
                    } else {
                        Throwable cause = future.cause();
                        emitter.onError(
                            new HttpClientException("Connect Error: " + cause.getMessage(), cause)
                        );
                    }
                });
            }, BackpressureStrategy.ERROR);
            Publisher<io.micronaut.http.HttpResponse<O>> finalPublisher = applyFilterToResponsePublisher(request, requestURI, requestWrapper, responsePublisher);
            Flowable<io.micronaut.http.HttpResponse<O>> finalFlowable;
            if (finalPublisher instanceof Flowable) {
                finalFlowable = (Flowable<io.micronaut.http.HttpResponse<O>>) finalPublisher;
            } else {
                finalFlowable = Flowable.fromPublisher(finalPublisher);
            }
            // apply timeout to flowable too in case a filter applied another policy
            Optional<Duration> readTimeout = configuration.getReadTimeout();
            if (readTimeout.isPresent()) {
                // add an additional second, because generally the timeout should occur
                // from the Netty request handling pipeline
                Duration duration = readTimeout.get().plus(Duration.ofSeconds(1));
                finalFlowable = finalFlowable.timeout(
                        duration.toMillis(),
                        TimeUnit.MILLISECONDS
                ).onErrorResumeNext(throwable -> {
                    if (throwable instanceof TimeoutException) {
                        return Flowable.error(ReadTimeoutException.TIMEOUT_EXCEPTION);
                    }
                    return Flowable.error(throwable);
                });
            }
            return finalFlowable.subscribeOn(scheduler);
        };
    }

    /**
     * @param channel The channel to close asynchronously
     */
    protected void closeChannelAsync(Channel channel) {
        if (channel.isOpen()) {

            ChannelFuture closeFuture = channel.closeFuture();
            closeFuture.addListener(f2 -> {
                if (!f2.isSuccess()) {
                    if (LOG.isErrorEnabled()) {
                        Throwable cause = f2.cause();
                        LOG.error("Error closing request connection: " + cause.getMessage(), cause);
                    }
                }
            });
        }
    }

    /**
     * @param request The request
     * @param <I>     The input type
     * @return A {@link Publisher} with the resolved URI
     */
    protected <I> Publisher<URI> resolveRequestURI(io.micronaut.http.HttpRequest<I> request) {
        URI requestURI = request.getUri();
        if (requestURI.getScheme() != null) {
            // if the request URI includes a scheme then it is fully qualified so use the direct server
            return Publishers.just(requestURI);
        } else {

            return Publishers.map(loadBalancer.select(getLoadBalancerDiscriminator()), server -> {
                    Optional<String> authInfo = server.getMetadata().get(io.micronaut.http.HttpHeaders.AUTHORIZATION_INFO, String.class);
                    if (request instanceof MutableHttpRequest) {
                        if (authInfo.isPresent()) {
                            ((MutableHttpRequest) request).getHeaders().auth(authInfo.get());
                        }
                    }
                    return server.resolve(resolveRequestURI(requestURI));
                }
            );
        }
    }

    /**
     * @param requestURI The request URI
     * @return A URI that is prepended with the contextPath, if set
     */
    protected URI resolveRequestURI(URI requestURI) {
        if (StringUtils.isNotEmpty(contextPath)) {
            try {
                return new URI(StringUtils.prependUri(contextPath, requestURI.toString()));
            } catch (URISyntaxException e) {
                //should never happen
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


    /**
     * Creates an initial connection to the given remote host.
     *
     * @param request The request
     * @param uri    The URI to connect to
     * @param sslCtx The SslContext instance
     * @param isStream Is the connection a stream connection
     * @return A ChannelFuture
     */
    protected ChannelFuture doConnect(
            io.micronaut.http.HttpRequest<?> request,
            URI uri,
            @Nullable SslContext sslCtx,
            boolean isStream) {
        String host = uri.getHost();

        int port = uri.getPort() > -1 ? uri.getPort() : sslCtx != null ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT;
        return doConnect(request, host, port, sslCtx, isStream);
    }

    /**
     * Creates an initial connection to the given remote host.
     *
     * @param request The request
     * @param host   The host
     * @param port   The port
     * @param sslCtx The SslContext instance
     * @param isStream Is the connection a stream connection
     * @return A ChannelFuture
     */
    protected ChannelFuture doConnect(
            io.micronaut.http.HttpRequest<?> request,
            String host,
            int port,
            @Nullable SslContext sslCtx,
            boolean isStream) {
        Bootstrap localBootstrap = this.bootstrap.clone();
        localBootstrap.handler(new HttpClientInitializer(
                sslCtx,
                request,
                host,
                port,
                isStream)
        );
        return doConnect(localBootstrap, host, port);
    }


    /**
     * Creates the {@link NioEventLoopGroup} for this client.
     *
     * @param configuration The configuration
     * @param threadFactory The thread factory
     * @return The group
     */
    protected NioEventLoopGroup createEventLoopGroup(HttpClientConfiguration configuration, ThreadFactory threadFactory) {
        OptionalInt numOfThreads = configuration.getNumOfThreads();
        Optional<Class<? extends ThreadFactory>> threadFactoryType = configuration.getThreadFactory();
        boolean hasThreads = numOfThreads.isPresent();
        boolean hasFactory = threadFactoryType.isPresent();
        NioEventLoopGroup group;
        if (hasThreads && hasFactory) {
            group = new NioEventLoopGroup(numOfThreads.getAsInt(), InstantiationUtils.instantiate(threadFactoryType.get()));
        } else if (hasThreads) {
            if (threadFactory != null) {
                group = new NioEventLoopGroup(numOfThreads.getAsInt(), threadFactory);
            } else {
                group = new NioEventLoopGroup(numOfThreads.getAsInt());
            }
        } else {
            if (threadFactory != null) {
                group = new NioEventLoopGroup(NettyThreadFactory.DEFAULT_EVENT_LOOP_THREADS, threadFactory);
            } else {

                group = new NioEventLoopGroup();
            }
        }
        return group;
    }

    /**
     * Creates an initial connection with the given bootstrap and remote host.
     *
     * @param bootstrap The bootstrap instance
     * @param host      The host
     * @param port      The port
     * @return The ChannelFuture
     */
    protected ChannelFuture doConnect(Bootstrap bootstrap, String host, int port) {
        return bootstrap.connect(host, port);
    }

    /**
     * Builds an {@link SslContext} for the given URI if necessary.
     *
     * @param uriObject The URI
     * @return The {@link SslContext} instance
     */
    protected SslContext buildSslContext(URI uriObject) {
        final SslContext sslCtx;
        if (uriObject.getScheme().equals("https")) {
            sslCtx = sslContext;
            if (sslCtx == null) {
                throw new HttpClientException("Cannot send HTTPS request. SSL is disabled");
            }
        } else {
            sslCtx = null;
        }
        return sslCtx;
    }

    /**
     * Resolve the filters for the request path.
     *
     * @param request The path
     * @param requestURI The URI of the request
     * @return The filters
     */
    protected List<HttpClientFilter> resolveFilters(io.micronaut.http.HttpRequest<?> request, URI requestURI) {
        List<HttpClientFilter> filterList = new ArrayList<>();
        String requestPath = requestURI.getPath();
        io.micronaut.http.HttpMethod method = request.getMethod();
        for (HttpClientFilter filter : filters) {
            if (filter instanceof Toggleable && !((Toggleable) filter).isEnabled()) {
                continue;
            }
            Optional<AnnotationValue<Filter>> filterOpt = annotationMetadataResolver.resolveMetadata(filter).findAnnotation(Filter.class);
            if (filterOpt.isPresent()) {
                AnnotationValue<Filter> filterAnn = filterOpt.get();
                String[] clients = filterAnn.get("serviceId", String[].class).orElse(null);
                if (!clientIdentifiers.isEmpty() && ArrayUtils.isNotEmpty(clients)) {
                    if (Arrays.stream(clients).noneMatch(id -> clientIdentifiers.contains(id))) {
                        // no matching clients
                        continue;
                    }
                }
                io.micronaut.http.HttpMethod[] methods = filterAnn.get("methods", io.micronaut.http.HttpMethod[].class, null);
                if (ArrayUtils.isNotEmpty(methods)) {
                    if (!Arrays.asList(methods).contains(method)) {
                        continue;
                    }
                }
                String[] patterns = filterAnn.getValue(String[].class).orElse(StringUtils.EMPTY_STRING_ARRAY);
                if (patterns.length == 0) {
                    filterList.add(filter);
                } else {
                    for (String pathPattern : patterns) {
                        if (PathMatcher.ANT.matches(pathPattern, requestPath)) {
                            filterList.add(filter);
                        }
                    }
                }
            } else {
                filterList.add(filter);
            }
        }
        return filterList;
    }

    /**
     * Configures the HTTP proxy for the pipeline.
     *
     * @param pipeline     The pipeline
     * @param proxyType    The proxy type
     * @param proxyAddress The proxy address
     */
    protected void configureProxy(ChannelPipeline pipeline, Type proxyType, SocketAddress proxyAddress) {
        String username = configuration.getProxyUsername().orElse(null);
        String password = configuration.getProxyPassword().orElse(null);

        if (StringUtils.isNotEmpty(username) && StringUtils.isNotEmpty(password)) {
            switch (proxyType) {
                case HTTP:
                    pipeline.addLast(new HttpProxyHandler(proxyAddress, username, password));
                    break;
                case SOCKS:
                    pipeline.addLast(new Socks5ProxyHandler(proxyAddress, username, password));
                    break;
                default:
                    // no-op
            }
        } else {
            switch (proxyType) {
                case HTTP:
                    pipeline.addLast(new HttpProxyHandler(proxyAddress));
                    break;
                case SOCKS:
                    pipeline.addLast(new Socks5ProxyHandler(proxyAddress));
                    break;
                default:
                    // no-op
            }
        }
    }

    /**
     * @param request           The request
     * @param requestURI        The URI of the request
     * @param requestWrapper    The request wrapper
     * @param responsePublisher The response publisher
     * @param <I>               The input type
     * @param <O>               The output type
     * @return The {@link Publisher} for the response
     */
    protected <I, O> Publisher<io.micronaut.http.HttpResponse<O>> applyFilterToResponsePublisher(
            io.micronaut.http.HttpRequest<I> request,
            URI requestURI,
            AtomicReference<io.micronaut.http.HttpRequest> requestWrapper,
            Publisher<io.micronaut.http.HttpResponse<O>> responsePublisher) {
        if (filters.length > 0) {
            List<HttpClientFilter> httpClientFilters = resolveFilters(request, requestURI);
            OrderUtil.reverseSort(httpClientFilters);
            httpClientFilters.add((req, chain) -> responsePublisher);

            ClientFilterChain filterChain = buildChain(requestWrapper, httpClientFilters);
            return (Publisher<io.micronaut.http.HttpResponse<O>>) httpClientFilters.get(0)
                .doFilter(request, filterChain);
        } else {

            return responsePublisher;
        }
    }

    /**
     * @param request            The request
     * @param requestURI         The URI of the request
     * @param requestContentType The request content type
     * @param permitsBody        Whether permits body
     * @return A {@link NettyRequestWriter}
     * @throws HttpPostRequestEncoder.ErrorDataEncoderException if there is an encoder exception
     */
    protected NettyRequestWriter buildNettyRequest(
        io.micronaut.http.MutableHttpRequest request,
        URI requestURI,
        MediaType requestContentType,
        boolean permitsBody) throws HttpPostRequestEncoder.ErrorDataEncoderException {

        io.netty.handler.codec.http.HttpRequest nettyRequest;
        NettyClientHttpRequest clientHttpRequest = (NettyClientHttpRequest) request;
        HttpPostRequestEncoder postRequestEncoder = null;
        if (permitsBody) {
            Optional body = clientHttpRequest.getBody();
            boolean hasBody = body.isPresent();
            if (requestContentType.equals(MediaType.APPLICATION_FORM_URLENCODED_TYPE) && hasBody) {
                Object bodyValue = body.get();
                postRequestEncoder = buildFormDataRequest(clientHttpRequest, bodyValue);
                nettyRequest = postRequestEncoder.finalizeRequest();
            } else if (requestContentType.equals(MediaType.MULTIPART_FORM_DATA_TYPE) && hasBody) {
                Object bodyValue = body.get();
                postRequestEncoder = buildMultipartRequest(clientHttpRequest, bodyValue);
                nettyRequest = postRequestEncoder.finalizeRequest();
            } else {
                ByteBuf bodyContent = null;
                if (hasBody) {
                    Object bodyValue = body.get();

                    if (Publishers.isConvertibleToPublisher(bodyValue)) {
                        boolean isSingle = Publishers.isSingle(bodyValue.getClass());

                        Flowable<?> publisher = ConversionService.SHARED.convert(bodyValue, Flowable.class).orElseThrow(() ->
                            new IllegalArgumentException("Unconvertible reactive type: " + bodyValue)
                        );

                        Flowable<HttpContent> requestBodyPublisher = publisher.map(o -> {
                            if (o instanceof CharSequence) {
                                ByteBuf textChunk = Unpooled.copiedBuffer(((CharSequence) o), requestContentType.getCharset().orElse(StandardCharsets.UTF_8));
                                if (LOG.isTraceEnabled()) {
                                    traceChunk(textChunk);
                                }
                                return new DefaultHttpContent(textChunk);
                            } else if (o instanceof ByteBuf) {
                                ByteBuf byteBuf = (ByteBuf) o;
                                if (LOG.isTraceEnabled()) {
                                    LOG.trace("Sending Bytes Chunk. Length: {}", byteBuf.readableBytes());
                                }
                                return new DefaultHttpContent(byteBuf);
                            } else if (o instanceof byte[]) {
                                byte[] bodyBytes = (byte[]) o;
                                if (LOG.isTraceEnabled()) {
                                    LOG.trace("Sending Bytes Chunk. Length: {}", bodyBytes.length);
                                }
                                return new DefaultHttpContent(Unpooled.wrappedBuffer(bodyBytes));
                            } else if (mediaTypeCodecRegistry != null) {
                                Optional<MediaTypeCodec> registeredCodec = mediaTypeCodecRegistry.findCodec(requestContentType);
                                ByteBuf encoded = registeredCodec.map(codec -> (ByteBuf) codec.encode(o, byteBufferFactory).asNativeBuffer())
                                    .orElse(null);
                                if (encoded != null) {
                                    if (LOG.isTraceEnabled()) {
                                        traceChunk(encoded);
                                    }
                                    return new DefaultHttpContent(encoded);
                                }
                            }
                            throw new CodecException("Cannot encode value [" + o + "]. No possible encoders found");
                        });

                        if (!isSingle && requestContentType == MediaType.APPLICATION_JSON_TYPE) {
                            requestBodyPublisher = requestBodyPublisher.map(new Function<HttpContent, HttpContent>() {
                                boolean first = true;

                                @Override
                                public HttpContent apply(HttpContent httpContent) {
                                    if (!first) {
                                        return HttpContentUtil.prefixComma(httpContent);
                                    } else {
                                        first = false;
                                        return httpContent;
                                    }
                                }
                            });
                            requestBodyPublisher = Flowable.concat(
                                Flowable.fromCallable(HttpContentUtil::openBracket),
                                requestBodyPublisher,
                                Flowable.fromCallable(HttpContentUtil::closeBracket)
                            );
                        }

                        nettyRequest = clientHttpRequest.getStreamedRequest(
                            requestBodyPublisher
                        );
                        return new NettyRequestWriter(nettyRequest, null);
                    } else if (bodyValue instanceof CharSequence) {
                        bodyContent = charSequenceToByteBuf((CharSequence) bodyValue, requestContentType);
                    } else if (mediaTypeCodecRegistry != null) {
                        Optional<MediaTypeCodec> registeredCodec = mediaTypeCodecRegistry.findCodec(requestContentType);
                        bodyContent = registeredCodec.map(codec -> (ByteBuf) codec.encode(bodyValue, byteBufferFactory).asNativeBuffer())
                            .orElse(null);
                    }
                    if (bodyContent == null) {
                        bodyContent = ConversionService.SHARED.convert(bodyValue, ByteBuf.class).orElseThrow(() ->
                            new HttpClientException("Body [" + bodyValue + "] cannot be encoded to content type [" + requestContentType + "]. No possible codecs or converters found.")
                        );
                    }
                }
                nettyRequest = clientHttpRequest.getFullRequest(bodyContent);
            }
        } else {
            nettyRequest = clientHttpRequest.getFullRequest(null);
        }
        try {
            nettyRequest.setUri(requestURI.toURL().getFile());
        } catch (MalformedURLException e) {
            //should never happen
        }
        return new NettyRequestWriter(nettyRequest, postRequestEncoder);
    }

    private ByteBuf charSequenceToByteBuf(CharSequence bodyValue, MediaType requestContentType) {
        CharSequence charSequence = bodyValue;
        return byteBufferFactory.copiedBuffer(
            charSequence.toString().getBytes(
                requestContentType.getCharset().orElse(defaultCharset)
            )
        ).asNativeBuffer();
    }

    private <I> void prepareHttpHeaders(URI requestURI, io.micronaut.http.HttpRequest<I> request, io.netty.handler.codec.http.HttpRequest nettyRequest, boolean permitsBody) {
        HttpHeaders headers = nettyRequest.headers();
        headers.set(HttpHeaderNames.HOST, requestURI.getHost());
        headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);

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
                    headers.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                }
            }
        }
    }

    @SuppressWarnings("MagicNumber")
    private <O> void addFullHttpResponseHandler(
            io.micronaut.http.HttpRequest<?> request,
            Channel channel,
            Emitter<io.micronaut.http.HttpResponse<O>> emitter,
            io.micronaut.core.type.Argument<O> bodyType) {
        channel.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpResponse>() {

            AtomicBoolean complete = new AtomicBoolean(false);

            @Override
            protected void channelRead0(ChannelHandlerContext channelHandlerContext, FullHttpResponse fullResponse) {

                HttpResponseStatus status = fullResponse.status();
                HttpHeaders headers = fullResponse.headers();
                if (LOG.isTraceEnabled()) {
                    LOG.trace("HTTP Client Response Received for Request: {} {}", request.getMethod(), request.getUri());
                    LOG.trace("Status Code: {}", status);
                    traceHeaders(headers);
                    traceBody("Response", fullResponse.content());
                }
                int statusCode = status.code();
                // it is a redirect
                if (statusCode > 300 && statusCode < 400 && configuration.isFollowRedirects() && headers.contains(HttpHeaderNames.LOCATION)) {
                    String location = headers.get(HttpHeaderNames.LOCATION);
                    Flowable<io.micronaut.http.HttpResponse<O>> redirectedRequest = exchange(io.micronaut.http.HttpRequest.GET(location), bodyType);
                    redirectedRequest.subscribe(new Subscriber<io.micronaut.http.HttpResponse<O>>() {
                        Subscription sub;

                        @Override
                        public void onSubscribe(Subscription s) {
                            this.sub = s;
                            s.request(1);
                        }

                        @Override
                        public void onNext(io.micronaut.http.HttpResponse<O> oHttpResponse) {
                            emitter.onNext(oHttpResponse);
                            emitter.onComplete();
                            sub.cancel();
                        }

                        @Override
                        public void onError(Throwable t) {
                            emitter.onError(t);
                            sub.cancel();
                        }

                        @Override
                        public void onComplete() {

                        }
                    });
                    return;
                }
                if (statusCode == HttpStatus.NO_CONTENT.getCode()) {
                    // normalize the NO_CONTENT header, since http content aggregator adds it even if not present in the response
                    headers.remove(HttpHeaderNames.CONTENT_LENGTH);
                }
                boolean errorStatus = statusCode >= 400;
                FullNettyClientHttpResponse<O> response
                    = new FullNettyClientHttpResponse<>(fullResponse, mediaTypeCodecRegistry, byteBufferFactory, bodyType, errorStatus);

                if (complete.compareAndSet(false, true)) {

                    try {
                        if (errorStatus) {
                            try {
                                HttpClientResponseException clientError = new HttpClientResponseException(
                                        status.reasonPhrase(),
                                        response
                                );
                                emitter.onError(clientError);
                            } catch (Exception e) {
                                emitter.onError(new HttpClientException("Exception occurred decoding error response: " + e.getMessage(), e));
                            }
                        } else {
                            emitter.onNext(response);
                        }
                        emitter.onComplete();
                    } finally {
                        closeChannelAsync(channel);
                    }
                }
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                if (complete.compareAndSet(false, true)) {

                    String message = cause.getMessage();
                    if (message == null) {
                        message = cause.getClass().getSimpleName();
                    }
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("HTTP Client exception ({}) occurred for request : {} {}", message, request.getMethod(), request.getUri());
                    }

                    if (cause instanceof TooLongFrameException) {
                        emitter.onError(new ContentLengthExceededException(configuration.getMaxContentLength()));
                    } else if (cause instanceof io.netty.handler.timeout.ReadTimeoutException) {
                        emitter.onError(ReadTimeoutException.TIMEOUT_EXCEPTION);
                    } else {
                        emitter.onError(new HttpClientException("Error occurred reading HTTP response: " + message, cause));
                    }
                }
            }
        });
    }

    private ClientFilterChain buildChain(AtomicReference<io.micronaut.http.HttpRequest> requestWrapper, List<HttpClientFilter> filters) {

        AtomicInteger integer = new AtomicInteger();
        int len = filters.size();
        return new ClientFilterChain() {
            @SuppressWarnings("unchecked")
            @Override
            public Publisher<? extends io.micronaut.http.HttpResponse<?>> proceed(MutableHttpRequest<?> request) {

                int pos = integer.incrementAndGet();
                if (pos > len) {
                    throw new IllegalStateException("The FilterChain.proceed(..) method should be invoked exactly once per filter execution. The method has instead been invoked multiple times by an erroneous filter definition.");
                }
                HttpClientFilter httpFilter = filters.get(pos);
                return httpFilter.doFilter(requestWrapper.getAndSet(request), this);
            }
        };
    }

    private HttpPostRequestEncoder buildFormDataRequest(NettyClientHttpRequest clientHttpRequest, Object bodyValue) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        HttpPostRequestEncoder postRequestEncoder = new HttpPostRequestEncoder(clientHttpRequest.getFullRequest(null), false);

        Object requestBody = bodyValue;
        Map<String, Object> formData;
        if (requestBody instanceof Map) {
            formData = (Map<String, Object>) requestBody;
        } else {
            formData = BeanMap.of(requestBody);
        }
        for (Map.Entry<String, Object> entry : formData.entrySet()) {
            Object value = entry.getValue();
            if (value != null) {
                Optional<String> converted = ConversionService.SHARED.convert(value, String.class);
                if (converted.isPresent()) {
                    postRequestEncoder.addBodyAttribute(entry.getKey(), converted.get());
                }
            }
        }
        return postRequestEncoder;
    }

    private HttpPostRequestEncoder buildMultipartRequest(NettyClientHttpRequest clientHttpRequest, Object bodyValue) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        HttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);
        io.netty.handler.codec.http.HttpRequest request = clientHttpRequest.getFullRequest(null);
        HttpPostRequestEncoder postRequestEncoder = new HttpPostRequestEncoder(factory, request, true, CharsetUtil.UTF_8, HttpPostRequestEncoder.EncoderMode.HTML5);
        if (bodyValue instanceof MultipartBody.Builder) {
            bodyValue = ((MultipartBody.Builder) bodyValue).build();
        }
        if (bodyValue instanceof MultipartBody) {
            postRequestEncoder.setBodyHttpDatas(((MultipartBody) bodyValue).getData(request, factory));
        } else {
            throw new MultipartException(String.format("The type %s is not a supported type for a multipart request body", bodyValue.getClass().getName()));
        }

        return postRequestEncoder;
    }

    private void traceRequest(io.micronaut.http.HttpRequest<?> request, io.netty.handler.codec.http.HttpRequest nettyRequest) {
        HttpHeaders headers = nettyRequest.headers();
        traceHeaders(headers);
        if (io.micronaut.http.HttpMethod.permitsRequestBody(request.getMethod()) && request.getBody().isPresent() && nettyRequest instanceof FullHttpRequest) {
            FullHttpRequest fullHttpRequest = (FullHttpRequest) nettyRequest;
            ByteBuf content = fullHttpRequest.content();
            if (LOG.isTraceEnabled()) {
                traceBody("Request", content);
            }
        }
    }

    private void traceBody(String type, ByteBuf content) {
        LOG.trace(type + " Body");
        LOG.trace("----");
        LOG.trace(content.toString(defaultCharset));
        LOG.trace("----");
    }

    private void traceChunk(ByteBuf content) {
        LOG.trace("Sending Chunk");
        LOG.trace("----");
        LOG.trace(content.toString(defaultCharset));
        LOG.trace("----");
    }

    private void traceHeaders(HttpHeaders headers) {
        for (String name : headers.names()) {
            List<String> all = headers.getAll(name);
            if (all.size() > 1) {
                for (String value : all) {
                    LOG.trace("{}: {}", name, value);
                }
            } else if (!all.isEmpty()) {
                LOG.trace("{}: {}", name, all.get(0));
            }
        }
    }

    private static MediaTypeCodecRegistry createDefaultMediaTypeRegistry() {
        ObjectMapper objectMapper = new ObjectMapperFactory().objectMapper(Optional.empty(), Optional.empty());
        ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration();
        return MediaTypeCodecRegistry.of(
            new JsonMediaTypeCodec(objectMapper, applicationConfiguration, null), new JsonStreamMediaTypeCodec(objectMapper, applicationConfiguration, null)
        );
    }

    private static NettyClientSslBuilder createSslBuilder() {
        return new NettyClientSslBuilder(new ClientSslConfiguration(), new ResourceResolver());
    }

    private <I> NettyRequestWriter prepareRequest(io.micronaut.http.HttpRequest<I> request, URI requestURI) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        MediaType requestContentType = request
            .getContentType()
            .orElse(MediaType.APPLICATION_JSON_TYPE);

        boolean permitsBody = io.micronaut.http.HttpMethod.permitsRequestBody(request.getMethod());
        NettyClientHttpRequest clientHttpRequest = (NettyClientHttpRequest) request;
        NettyRequestWriter requestWriter = buildNettyRequest(clientHttpRequest, requestURI, requestContentType, permitsBody);
        io.netty.handler.codec.http.HttpRequest nettyRequest = requestWriter.getNettyRequest();
        prepareHttpHeaders(requestURI, request, nettyRequest, permitsBody);
        return requestWriter;
    }

    private Disposable buildDisposableChannel(ChannelFuture channelFuture) {
        return new Disposable() {
            boolean disposed = false;

            @Override
            public void dispose() {
                if (!disposed) {
                    Channel channel = channelFuture.channel();
                    if (channel.isOpen()) {
                        closeChannelAsync(channel);
                    }
                    disposed = true;
                }
            }

            @Override
            public boolean isDisposed() {
                return disposed;
            }
        };
    }



    /**
     * Initializes the HTTP client channel.
     */
    protected class HttpClientInitializer extends ChannelInitializer<Channel> {

        final SslContext sslContext;
        final boolean stream;
        final String host;
        final int port;
        private final io.micronaut.http.HttpRequest<?> request;

        /**
         * @param sslContext The ssl context
         * @param request The request
         * @param host The host
         * @param port The port
         * @param stream     Whether is stream
         */
        protected HttpClientInitializer(SslContext sslContext, io.micronaut.http.HttpRequest<?> request, String host, int port, boolean stream) {
            this.sslContext = sslContext;
            this.request = request;
            this.stream = stream;
            this.host = host;
            this.port = port;
        }

        /**
         * @param ch The channel
         */
        protected void initChannel(Channel ch) {
            ChannelPipeline p = ch.pipeline();

            if (stream) {
                // for streaming responses we disable auto read
                // so that the consumer is in charge of back pressure
                ch.config().setAutoRead(false);
            }

            if (sslContext != null) {
                SslHandler sslHandler = sslContext.newHandler(
                        ch.alloc(),
                        host,
                        port
                );
                p.addFirst("ssl-handler", sslHandler);
            }

            Optional<SocketAddress> proxy = configuration.getProxyAddress();
            if (proxy.isPresent()) {
                Type proxyType = configuration.getProxyType();
                SocketAddress proxyAddress = proxy.get();
                configureProxy(p, proxyType, proxyAddress);

            }

            // read timeout settings are not applied to streamed requests.
            // instead idle timeout settings are applied.
            if (!stream) {
                Optional<Duration> readTimeout = configuration.getReadTimeout();
                readTimeout.ifPresent(duration -> {
                    if (!duration.isNegative()) {
                        p.addLast(new ReadTimeoutHandler(duration.toMillis(), TimeUnit.MILLISECONDS));
                    }
                });
            } else {
                Optional<Duration> readIdleTime = configuration.getReadIdleTime();
                if (readIdleTime.isPresent()) {
                    Duration duration = readIdleTime.get();
                    p.addLast(new IdleStateHandler(duration.toMillis(), duration.toMillis(), duration.toMillis(), TimeUnit.MILLISECONDS));
                }
            }
            p.addLast("http-client-codec", new HttpClientCodec());

            p.addLast(HANDLER_DECODER, new HttpContentDecompressor());

            int maxContentLength = configuration.getMaxContentLength();

            if (!stream) {
                p.addLast(HANDLER_AGGREGATOR, new HttpObjectAggregator(maxContentLength) {
                    @Override
                    protected void finishAggregation(FullHttpMessage aggregated) throws Exception {
                        if (!HttpUtil.isContentLengthSet(aggregated)) {
                            if (aggregated.content().readableBytes() > 0) {
                                super.finishAggregation(aggregated);
                            }
                        }
                    }
                });
            }

            // if the content type is a SSE event stream we add a decoder
            // to delimit the content by lines
            if (acceptsEventStream()) {
                p.addLast(new SimpleChannelInboundHandler<HttpContent>() {

                    LineBasedFrameDecoder decoder = new LineBasedFrameDecoder(
                            configuration.getMaxContentLength(),
                            true,
                            true
                    );

                    @Override
                    public boolean acceptInboundMessage(Object msg) {
                        return msg instanceof HttpContent && !(msg instanceof LastHttpContent);
                    }

                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, HttpContent msg) throws Exception {
                        ByteBuf content = msg.content();
                        decoder.channelRead(ctx, content);
                    }

                });

                p.addLast(new SimpleChannelInboundHandler<ByteBuf>() {

                    @Override
                    public boolean acceptInboundMessage(Object msg) {
                        return msg instanceof ByteBuf;
                    }

                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                        ctx.fireChannelRead(new DefaultHttpContent(msg.retain()));
                    }
                });
            }
            p.addLast(HANDLER_STREAM, new HttpStreamsClientHandler() {
                @Override
                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                    if (evt instanceof IdleStateEvent) {
                        // close the connection if it is idle for too long
                        close(ctx, ctx.voidPromise());
                    } else {
                        super.userEventTriggered(ctx, evt);
                    }
                }
            });
        }

        private boolean acceptsEventStream() {
            return request.getHeaders().get(io.micronaut.http.HttpHeaders.ACCEPT, String.class).map(ct -> ct.equals(MediaType.TEXT_EVENT_STREAM)).orElse(false);
        }
    }

    /**
     * A Netty request writer.
     */
    protected class NettyRequestWriter {

        private final HttpRequest nettyRequest;
        private final HttpPostRequestEncoder encoder;

        /**
         * @param nettyRequest The Netty request
         * @param encoder      The encoder
         */
        NettyRequestWriter(HttpRequest nettyRequest, HttpPostRequestEncoder encoder) {
            this.nettyRequest = nettyRequest;
            this.encoder = encoder;
        }

        /**
         * @param channel The channel
         * @param emitter The emitter
         */
        protected void writeAndClose(Channel channel, FlowableEmitter<?> emitter) {
            ChannelFuture channelFuture;
            if (encoder != null && encoder.isChunked()) {
                channel.pipeline().replace(HANDLER_STREAM, HANDLER_CHUNK, new ChunkedWriteHandler());
                channel.write(nettyRequest);
                channelFuture = channel.writeAndFlush(encoder);
            } else {
                channelFuture = channel.writeAndFlush(nettyRequest);
            }

            closeChannel(channel, emitter, channelFuture);
        }

        private void closeChannel(Channel channel, FlowableEmitter<?> emitter, ChannelFuture channelFuture) {
            channelFuture.addListener(f -> {
                try {
                    if (!f.isSuccess()) {
                        emitter.onError(f.cause());
                    } else {
                        channel.read();
                    }
                } finally {
                    if (encoder != null) {
                        encoder.cleanFiles();
                    }
                    closeChannelAsync(channel);
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
    private class CurrentEvent {
        final CompositeByteBuf data;
        String id;
        String name;
        Duration retry;

        CurrentEvent(CompositeByteBuf data) {
            this.data = data;
        }
    }
}
