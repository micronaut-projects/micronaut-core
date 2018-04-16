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
import com.typesafe.netty.http.HttpStreamsClientHandler;
import com.typesafe.netty.http.StreamedHttpResponse;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.AnnotationMetadataResolver;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.beans.BeanMap;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.reflect.InstantiationUtils;
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
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.client.ssl.NettyClientSslBuilder;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.http.multipart.MultipartException;
import io.micronaut.http.netty.buffer.NettyByteBufferFactory;
import io.micronaut.http.netty.channel.NettyThreadFactory;
import io.micronaut.http.netty.content.HttpContentUtil;
import io.micronaut.http.ssl.SslConfiguration;
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
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.reactivex.*;
import io.reactivex.disposables.Disposable;
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
import javax.net.ssl.SSLEngine;
import java.io.Closeable;
import java.net.Proxy.Type;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default implementation of the {@link HttpClient} interface based on Netty
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Prototype
public class DefaultHttpClient implements RxHttpClient, RxStreamingHttpClient, Closeable, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultHttpClient.class);
    protected static final String HANDLER_AGGREGATOR = "http-aggregator";
    protected static final String HANDLER_CHUNK = "chunk-writer";
    protected static final String HANDLER_STREAM = "stream-handler";
    protected static final String HANDLER_DECODER = "http-decoder";

    private final LoadBalancer loadBalancer;
    private final HttpClientConfiguration configuration;
    private final SslContext sslContext;
    protected final Bootstrap bootstrap;
    private final AnnotationMetadataResolver annotatationMetadataResolver;
    private final ThreadFactory threadFactory;
    protected EventLoopGroup group;
    private final HttpClientFilter[] filters;
    private final Charset defaultCharset;
    protected MediaTypeCodecRegistry mediaTypeCodecRegistry;
    protected ByteBufferFactory<ByteBufAllocator, ByteBuf> byteBufferFactory = new NettyByteBufferFactory();
    private Set<String> clientIdentifiers = Collections.emptySet();

    /**
     * Construct a client for the given arguments
     *
     * @param loadBalancer  The {@link LoadBalancer} to use for selecting servers
     * @param configuration The {@link HttpClientConfiguration} object
     * @param threadFactory The thread factory to use for client threads
     * @param nettyClientSslBuilder The SSL buidler
     * @param codecRegistry The {@link MediaTypeCodecRegistry} to use for encoding and decoding objects
     * @param annotationMetadataResolver The annotation metadata resolver
     * @param filters The filters to use
     */
    @Inject
    public DefaultHttpClient(@Parameter LoadBalancer loadBalancer,
                             @Parameter HttpClientConfiguration configuration,
                             @Named(NettyThreadFactory.NAME) @Nullable ThreadFactory threadFactory,
                             NettyClientSslBuilder nettyClientSslBuilder,
                             MediaTypeCodecRegistry codecRegistry,
                             @Nullable AnnotationMetadataResolver annotationMetadataResolver,
                             HttpClientFilter... filters) {
        this.loadBalancer = loadBalancer;
        this.defaultCharset = configuration.getDefaultCharset();
        this.bootstrap = new Bootstrap();
        this.configuration = configuration;
        this.sslContext = nettyClientSslBuilder.build().orElse(null);
        this.group = createEventLoopGroup(configuration, threadFactory);
        this.threadFactory = threadFactory;
        this.bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true);

        for (Map.Entry<ChannelOption, Object> entry : configuration.getChannelOptions().entrySet()) {
            Object v = entry.getValue();
            if (v != null) {
                ChannelOption channelOption = entry.getKey();
                bootstrap.option(channelOption, v);
            }
        }
        this.mediaTypeCodecRegistry = codecRegistry;
        this.filters = filters;
        this.annotatationMetadataResolver = annotationMetadataResolver != null ? annotationMetadataResolver : AnnotationMetadataResolver.DEFAULT;
    }

    public DefaultHttpClient(URL url,
                             HttpClientConfiguration configuration,
                             NettyClientSslBuilder nettyClientSslBuilder,
                             MediaTypeCodecRegistry codecRegistry,
                             HttpClientFilter... filters) {
        this(LoadBalancer.fixed(url), configuration, new DefaultThreadFactory(MultithreadEventLoopGroup.class),nettyClientSslBuilder, codecRegistry,AnnotationMetadataResolver.DEFAULT, filters);
    }

    public DefaultHttpClient(LoadBalancer loadBalancer) {
        this(loadBalancer,
                new DefaultHttpClientConfiguration(),
                new DefaultThreadFactory(MultithreadEventLoopGroup.class),
                new NettyClientSslBuilder(new SslConfiguration(), new ResourceResolver()),
                createDefaultMediaTypeRegistry(), AnnotationMetadataResolver.DEFAULT);
    }


    public DefaultHttpClient(@Parameter URL url) {
        this(LoadBalancer.fixed(url));
    }

    public DefaultHttpClient( URL url, HttpClientConfiguration configuration) {
        this(LoadBalancer.fixed(url), configuration,new DefaultThreadFactory(MultithreadEventLoopGroup.class), new NettyClientSslBuilder(new SslConfiguration(), new ResourceResolver()), createDefaultMediaTypeRegistry(), AnnotationMetadataResolver.DEFAULT);
    }

    public DefaultHttpClient( LoadBalancer loadBalancer, HttpClientConfiguration configuration) {
        this(loadBalancer,
                configuration,new DefaultThreadFactory(MultithreadEventLoopGroup.class),
                new NettyClientSslBuilder(new SslConfiguration(), new ResourceResolver()),
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
            this.group.shutdownGracefully().addListener(f -> {
                if (!f.isSuccess() && LOG.isErrorEnabled()) {
                    Throwable cause = f.cause();
                    LOG.error("Error shutting down HTTP client: " + cause.getMessage(), cause);
                }
            });
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
     * Sets the {@link MediaTypeCodecRegistry} used by this client
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
                return publisher.blockingFirst();
            }
        };
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
                        LOG.trace("HTTP Client Streaming Response Received Chunk");
                        traceBody(byteBuf);
                    }
                    ByteBuffer<?> byteBuffer = byteBufferFactory.wrap(byteBuf);
                    nettyStreamedHttpResponse.setBody(byteBuffer);
                    return nettyStreamedHttpResponse;
                });
            });
        };
    }

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
                boolean streamArray = !Iterable.class.isAssignableFrom(type.getType());
                JacksonProcessor jacksonProcessor = new JacksonProcessor(mediaTypeCodec.getObjectMapper().getFactory(),streamArray) {
                    @Override
                    public void subscribe(Subscriber<? super JsonNode> downstreamSubscriber) {
                        httpContentFlowable.map(content -> {
                            ByteBuf chunk = content.content();
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("HTTP Client JSON Streaming Response Received Chunk");
                                traceBody(chunk);
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

    protected <I> Flowable<io.micronaut.http.HttpResponse<Object>> buildStreamExchange(io.micronaut.http.HttpRequest<I> request, URI requestURI) {
        SslContext sslContext = buildSslContext(requestURI);

        AtomicReference<io.micronaut.http.HttpRequest> requestWrapper = new AtomicReference<>(request);
        Flowable<io.micronaut.http.HttpResponse<Object>> streamResponsePublisher = Flowable.create(emitter -> {
                    ChannelFuture channelFuture = doConnect(requestURI, sslContext);
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
                                    pipeline.remove(HANDLER_AGGREGATOR);
                                    pipeline.addLast(new SimpleChannelInboundHandler<StreamedHttpResponse>() {

                                        AtomicBoolean received = new AtomicBoolean(false);

                                        @Override
                                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
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
                                                    emitter.onNext(response);
                                                    emitter.onComplete();
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
        streamResponsePublisher = Flowable.fromPublisher(applyFilterToResponsePublisher(request, requestWrapper, streamResponsePublisher));
        return streamResponsePublisher.subscribeOn(Schedulers.from(group));
    }

    protected <I, O> Function<URI, Publisher<? extends io.micronaut.http.HttpResponse<O>>> buildExchangePublisher(io.micronaut.http.HttpRequest<I> request, io.micronaut.core.type.Argument<O> bodyType) {
        AtomicReference<io.micronaut.http.HttpRequest> requestWrapper = new AtomicReference<>(request);
        return requestURI -> {
            Flowable<io.micronaut.http.HttpResponse<O>> responsePublisher = Flowable.create(emitter -> {
                SslContext sslContext = buildSslContext(requestURI);

                ChannelFuture connectionFuture = doConnect(requestURI, sslContext);
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
                            NettyRequestWriter requestWriter = buildNettyRequest(clientHttpRequest, requestContentType, permitsBody);
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
            Publisher<io.micronaut.http.HttpResponse<O>> finalPublisher = applyFilterToResponsePublisher(request, requestWrapper, responsePublisher);
            Flowable<io.micronaut.http.HttpResponse<O>> finalFlowable;
            if(finalPublisher instanceof Flowable) {
                finalFlowable = (Flowable<io.micronaut.http.HttpResponse<O>>) finalPublisher;
            }
            else {
                finalFlowable = Flowable.fromPublisher(finalPublisher);
            }
            return finalFlowable.subscribeOn(Schedulers.from(group));
        };
    }

    protected void closeChannelAsync(Channel channel) {
        if(channel.isOpen()) {

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

    protected <I> Publisher<URI> resolveRequestURI(io.micronaut.http.HttpRequest<I> request) {
        URI requestURI = request.getUri();
        if (requestURI.getScheme() != null) {
            // if the request URI includes a scheme then it is fully qualified so use the direct server
            return Publishers.just(requestURI);
        } else {

            return Publishers.map(loadBalancer.select(getLoadBalancerDiscriminator()), server -> {
                        Optional<String> authInfo = server.getMetadata().get(io.micronaut.http.HttpHeaders.AUTHORIZATION_INFO, String.class);
                        if (authInfo.isPresent() && request instanceof MutableHttpRequest) {
                            ((MutableHttpRequest) request).getHeaders().auth(authInfo.get());
                        }
                        return server.resolve(requestURI);
                    }

            );
        }
    }

    /**
     * @return The discriminator to use when selecting a server for the purposes of load balancing (defaults to null)
     */
    protected Object getLoadBalancerDiscriminator() {
        return null;
    }


    /**
     * Creates an initial connection to the given remote host
     *
     * @param uri    The URI to connect to
     * @param sslCtx The SslContext instance
     * @return A ChannelFuture
     */
    protected ChannelFuture doConnect(URI uri, @Nullable SslContext sslCtx) {
        String host = uri.getHost();
        int port = uri.getPort() > -1 ? uri.getPort() : sslCtx != null ? 443 : 80;

        return doConnect(host, port, sslCtx);
    }

    /**
     * Creates an initial connection to the given remote host
     *
     * @param host   The host
     * @param port   The port
     * @param sslCtx The SslContext instance
     * @return A ChannelFuture
     */
    protected ChannelFuture doConnect(String host, int port, @Nullable SslContext sslCtx) {
        Bootstrap localBootstrap = this.bootstrap.clone();
        localBootstrap.handler(new HttpClientInitializer(sslCtx, false));
        return doConnect(localBootstrap, host, port);
    }


    /**
     * Creates the {@link NioEventLoopGroup} for this client
     *
     * @param configuration The configuration
     * @param threadFactory
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
            if(threadFactory != null) {
                group = new NioEventLoopGroup(numOfThreads.getAsInt(), threadFactory);
            }
            else {
                group = new NioEventLoopGroup(numOfThreads.getAsInt());
            }
        } else {
            if(threadFactory != null) {
                group = new NioEventLoopGroup(NettyThreadFactory.DEFAULT_EVENT_LOOP_THREADS, threadFactory);
            }
            else {

                group = new NioEventLoopGroup();
            }
        }
        return group;
    }

    /**
     * Creates an initial connection with the given bootstrap and remote host
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
     * Builds an {@link SslContext} for the given URI if necessary
     *
     * @param uriObject The URI
     * @return The {@link SslContext} instance
     */
    protected SslContext buildSslContext(URI uriObject) {
        final SslContext sslCtx;
        if (uriObject.getScheme().equals("https")) {
            sslCtx = sslContext;
        } else {
            sslCtx = null;
        }
        return sslCtx;
    }

    /**
     * Resolve the filters for the request path
     *
     * @param request The path
     * @return The filters
     */
    protected List<HttpClientFilter> resolveFilters(io.micronaut.http.HttpRequest<?> request) {
        List<HttpClientFilter> filterList = new ArrayList<>();
        String requestPath = request.getPath();
        io.micronaut.http.HttpMethod method = request.getMethod();
        for (HttpClientFilter filter : filters) {
            if (filter instanceof Toggleable && !((Toggleable) filter).isEnabled()) {
                continue;
            }
            Filter filterAnn = annotatationMetadataResolver.resolveElement(filter).getAnnotation(Filter.class);
            if (filterAnn != null) {
                String[] clients = filterAnn.clients();
                if (!clientIdentifiers.isEmpty() && ArrayUtils.isNotEmpty(clients)) {
                    if (Arrays.stream(clients).noneMatch(id -> clientIdentifiers.contains(id))) {
                        // no matching clients
                        continue;
                    }
                }
                io.micronaut.http.HttpMethod[] methods = filterAnn.methods();
                if (ArrayUtils.isNotEmpty(methods)) {
                    if (!Arrays.asList(methods).contains(method)) {
                        continue;
                    }
                }
                String[] value = filterAnn.value();
                if (value.length == 0) {
                    filterList.add(filter);
                } else {
                    for (String pathPattern : value) {
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
     * Configures the HTTP proxy for the pipeline
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
            }
        } else {
            switch (proxyType) {
                case HTTP:
                    pipeline.addLast(new HttpProxyHandler(proxyAddress));
                    break;
                case SOCKS:
                    pipeline.addLast(new Socks5ProxyHandler(proxyAddress));
                    break;
            }
        }
    }

    protected <I, O> Publisher<io.micronaut.http.HttpResponse<O>> applyFilterToResponsePublisher(io.micronaut.http.HttpRequest<I> request, AtomicReference<io.micronaut.http.HttpRequest> requestWrapper, Publisher<io.micronaut.http.HttpResponse<O>> responsePublisher) {
        if (filters.length > 0) {
            List<HttpClientFilter> httpClientFilters = resolveFilters(request);
            OrderUtil.reverseSort(httpClientFilters);
            httpClientFilters.add((req, chain) -> responsePublisher);

            ClientFilterChain filterChain = buildChain(requestWrapper, httpClientFilters);
            return (Publisher<io.micronaut.http.HttpResponse<O>>) httpClientFilters.get(0)
                    .doFilter(request, filterChain);
        } else {

            return responsePublisher;
        }
    }

    protected NettyRequestWriter buildNettyRequest(
            io.micronaut.http.MutableHttpRequest request,
            MediaType requestContentType, boolean permitsBody) throws HttpPostRequestEncoder.ErrorDataEncoderException {
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

                    if( Publishers.isConvertibleToPublisher(bodyValue)) {
                        boolean isSingle = Publishers.isSingle(bodyValue.getClass());

                        Flowable<?> publisher = ConversionService.SHARED.convert(bodyValue, Flowable.class).orElseThrow(()->
                            new IllegalArgumentException("Unconvertible reactive type: " + bodyValue)
                        );

                        Flowable<HttpContent> requestBodyPublisher = publisher.map(o -> {
                            if(o instanceof CharSequence) {
                                ByteBuf textChunk = Unpooled.copiedBuffer(((CharSequence) o), requestContentType.getCharset().orElse(StandardCharsets.UTF_8));
                                if(LOG.isTraceEnabled()) {
                                    traceChunk(textChunk);
                                }
                                return new DefaultHttpContent(textChunk);
                            }
                            else if(o instanceof ByteBuf) {
                                ByteBuf byteBuf = (ByteBuf) o;
                                if(LOG.isTraceEnabled()) {
                                    LOG.trace("Stream Bytes Chunk. Length: {}", byteBuf.readableBytes());
                                }
                                return new DefaultHttpContent(byteBuf);
                            }
                            else if(o instanceof byte[]) {
                                byte[] bodyBytes = (byte[]) o;
                                if(LOG.isTraceEnabled()) {
                                    LOG.trace("Stream Bytes Chunk. Length: {}", bodyBytes.length);
                                }
                                return new DefaultHttpContent(Unpooled.wrappedBuffer(bodyBytes));
                            }
                            else if(mediaTypeCodecRegistry != null) {
                                Optional<MediaTypeCodec> registeredCodec = mediaTypeCodecRegistry.findCodec(requestContentType);
                                ByteBuf encoded = registeredCodec.map(codec -> (ByteBuf) codec.encode(o, byteBufferFactory).asNativeBuffer())
                                                                 .orElse(null);
                                if(encoded != null) {
                                    if(LOG.isTraceEnabled()) {
                                        traceChunk(encoded);
                                    }
                                    return new DefaultHttpContent(encoded);
                                }
                            }
                            throw new CodecException("Cannot encode value ["+o+"]. No possible encoders found");
                        });

                        if(!isSingle && requestContentType == MediaType.APPLICATION_JSON_TYPE) {
                            requestBodyPublisher = requestBodyPublisher.map(new Function<HttpContent, HttpContent>() {
                                boolean first = true;
                                @Override
                                public HttpContent apply(HttpContent httpContent) throws Exception {
                                    if(!first) {
                                        return HttpContentUtil.prefixComma(httpContent);
                                    }
                                    else {
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
                    }
                    else if (bodyValue instanceof CharSequence) {
                        bodyContent = charSequenceToByteBuf((CharSequence) bodyValue, requestContentType);
                    } else if (mediaTypeCodecRegistry != null) {
                        Optional<MediaTypeCodec> registeredCodec = mediaTypeCodecRegistry.findCodec(requestContentType);
                        bodyContent = registeredCodec.map(codec -> (ByteBuf) codec.encode(bodyValue, byteBufferFactory).asNativeBuffer())
                                .orElse(null);
                    }
                    if (bodyContent == null) {
                        bodyContent = ConversionService.SHARED.convert(bodyValue, ByteBuf.class).orElse(null);
                    }
                }
                nettyRequest = clientHttpRequest.getFullRequest(bodyContent);
            }
        } else {
            nettyRequest = clientHttpRequest.getFullRequest(null);
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

    private <O> void addFullHttpResponseHandler(io.micronaut.http.HttpRequest<?> request, Channel channel, Emitter<io.micronaut.http.HttpResponse<O>> emitter, io.micronaut.core.type.Argument<O> bodyType) {
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
                    traceBody(fullResponse.content());
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

                if(complete.compareAndSet(false, true)) {

                    try {
                        if (errorStatus) {
                            emitter.onError(new HttpClientResponseException(status.reasonPhrase(), response));
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
                if(complete.compareAndSet(false, true)) {

                    if (LOG.isTraceEnabled()) {
                        LOG.trace("HTTP Client exception ({}) occurred for request : {} {}", cause.getMessage(), request.getMethod(), request.getUri());
                    }

                    if (cause instanceof TooLongFrameException) {
                        emitter.onError(new ContentLengthExceededException(configuration.getMaxContentLength()));
                    } else if (cause instanceof ReadTimeoutException) {
                        emitter.onError(io.micronaut.http.client.exceptions.ReadTimeoutException.TIMEOUT_EXCEPTION);
                    } else {
                        emitter.onError(new HttpClientException("Error occurred reading HTTP response: " + cause.getMessage(), cause));
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
        if (bodyValue instanceof MultipartBody){
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
            traceBody(content);
        }
    }

    private void traceBody(ByteBuf content) {
        LOG.trace("Body");
        LOG.trace("----");
        LOG.trace(content.toString(defaultCharset));
        LOG.trace("----");
    }

    private void traceChunk(ByteBuf content) {
        LOG.trace("Stream Chunk");
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
                new JsonMediaTypeCodec(objectMapper, applicationConfiguration), new JsonStreamMediaTypeCodec(objectMapper, applicationConfiguration)
        );
    }

    private <I> NettyRequestWriter prepareRequest(io.micronaut.http.HttpRequest<I> request, URI requestURI) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        MediaType requestContentType = request
                .getContentType()
                .orElse(MediaType.APPLICATION_JSON_TYPE);

        boolean permitsBody = io.micronaut.http.HttpMethod.permitsRequestBody(request.getMethod());
        NettyClientHttpRequest clientHttpRequest = (NettyClientHttpRequest) request;
        NettyRequestWriter requestWriter = buildNettyRequest(clientHttpRequest, requestContentType, permitsBody);
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
     * Initializes the HTTP client channel
     */
    protected class HttpClientInitializer extends ChannelInitializer<Channel> {

        SslContext sslContext;
        boolean stream;

        protected HttpClientInitializer(SslContext sslContext, boolean stream) {
            this.sslContext = sslContext;
            this.stream = stream;
        }

        protected void initChannel(Channel ch) {
            ChannelPipeline p = ch.pipeline();
            if (sslContext != null) {
                SSLEngine engine = sslContext.newEngine(ch.alloc());
                p.addFirst("ssl-handler", new SslHandler(engine));
            }


            Optional<SocketAddress> proxy = configuration.getProxyAddress();
            if (proxy.isPresent()) {
                Type proxyType = configuration.getProxyType();
                SocketAddress proxyAddress = proxy.get();
                configureProxy(p, proxyType, proxyAddress);

            }
            Optional<Duration> readTimeout = configuration.getReadTimeout();
            readTimeout.ifPresent(duration -> {
                if (!duration.isNegative()) {
                    p.addLast(new ReadTimeoutHandler(duration.toMillis(), TimeUnit.MILLISECONDS));
                }
            });
            p.addLast("http-client-codec", new HttpClientCodec());

            p.addLast(HANDLER_DECODER, new HttpContentDecompressor());

            int maxContentLength = configuration.getMaxContentLength();
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
            p.addLast(HANDLER_STREAM, new HttpStreamsClientHandler());
        }
    }

    protected class NettyRequestWriter {

        private final HttpRequest nettyRequest;
        private final HttpPostRequestEncoder encoder;

        NettyRequestWriter(HttpRequest nettyRequest, HttpPostRequestEncoder encoder) {
            this.nettyRequest = nettyRequest;
            this.encoder = encoder;
        }

        void writeAndClose(Channel channel, FlowableEmitter<?> emitter) {
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
                    if(!f.isSuccess()) {
                        emitter.onError(f.cause());
                    }
                } finally {
                    if (encoder != null) {
                        encoder.cleanFiles();
                    }
                    closeChannelAsync(channel);
                }
            });
        }

        HttpRequest getNettyRequest() {
            return nettyRequest;
        }
    }
}
