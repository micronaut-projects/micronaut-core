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

import io.micronaut.core.annotation.AnnotationMetadataResolver;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.ProxyHttpClient;
import io.micronaut.http.client.ProxyRequestOptions;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.client.AsyncHttpClient;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.http.client.filter.ClientFilterResolutionContext;
import io.micronaut.http.client.netty.ssl.ClientSslBuilder;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.http.filter.HttpClientFilterResolver;
import io.micronaut.http.filter.HttpFilterResolver;
import io.micronaut.http.sse.Event;
import io.micronaut.websocket.WebSocketClient;
import io.micronaut.websocket.context.WebSocketBeanRegistry;
import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.resolver.AddressResolverGroup;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.reactivestreams.Publisher;

import java.io.Closeable;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;

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
    RawHttpClient,
    Closeable,
    AutoCloseable {

    private final NettyHttpClient nettyHttpClient;
    private final AsyncHttpClient asyncHttpClient;

    DefaultHttpClient(DefaultHttpClientBuilder builder) {
        this(builder.nettyBuilder().build());
    }

    /**
     * Create a {@link DefaultHttpClient} that wraps the given {@link NettyHttpClient}.
     *
     * @param nettyHttpClient The delegate client
     * @since 5.0
     */
    DefaultHttpClient(NettyHttpClient nettyHttpClient) {
        this.nettyHttpClient = Objects.requireNonNull(nettyHttpClient, "nettyHttpClient");
        this.asyncHttpClient = new DefaultAsyncHttpClient(nettyHttpClient);
    }

    /**
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
     * @deprecated Please go through the {@link #builder()} instead. If you need access to properties that are not public in the builder, make them public in core and document their usage.
     */
    @Deprecated
    public DefaultHttpClient(@Nullable LoadBalancer loadBalancer,
                             HttpClientConfiguration configuration,
                             @Nullable String contextPath,
                             @Nullable ThreadFactory threadFactory,
                             ClientSslBuilder nettyClientSslBuilder,
                             MediaTypeCodecRegistry codecRegistry,
                             MessageBodyHandlerRegistry handlerRegistry,
                             @Nullable AnnotationMetadataResolver annotationMetadataResolver,
                             ConversionService conversionService,
                             HttpClientFilter... filters) {
        this(
            builder()
                .loadBalancer(loadBalancer)
                .configuration(configuration)
                .contextPath(contextPath)
                .threadFactory(threadFactory)
                .nettyClientSslBuilder(nettyClientSslBuilder)
                .codecRegistry(codecRegistry)
                .handlerRegistry(handlerRegistry)
                .conversionService(conversionService)
                .annotationMetadataResolver(annotationMetadataResolver)
                .filters(filters)
        );
    }

    /**
     * Construct a client for the given arguments.
     * @param loadBalancer                    The {@link LoadBalancer} to use for selecting servers
     * @param explicitHttpVersion             The HTTP version to use. Can be null and defaults to {@link io.micronaut.http.HttpVersion#HTTP_1_1}
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
     * @param resolverGroup                   Optional predefined resolver group
     * @deprecated Please go through the {@link #builder()} instead. If you need access to properties that are not public in the builder, make them public in core and document their usage.
     */
    @Deprecated
    public DefaultHttpClient(@Nullable LoadBalancer loadBalancer,
                             @Nullable HttpVersionSelection explicitHttpVersion,
                             HttpClientConfiguration configuration,
                             @Nullable String contextPath,
                             HttpClientFilterResolver<ClientFilterResolutionContext> filterResolver,
                             List<HttpFilterResolver.FilterEntry> clientFilterEntries,
                             @Nullable ThreadFactory threadFactory,
                             ClientSslBuilder nettyClientSslBuilder,
                             MediaTypeCodecRegistry codecRegistry,
                             MessageBodyHandlerRegistry handlerRegistry,
                             WebSocketBeanRegistry webSocketBeanRegistry,
                             RequestBinderRegistry requestBinderRegistry,
                             @Nullable EventLoopGroup eventLoopGroup,
                             ChannelFactory<? extends SocketChannel> socketChannelFactory,
                             ChannelFactory<? extends DatagramChannel> udpChannelFactory,
                             NettyClientCustomizer clientCustomizer,
                             @Nullable String informationalServiceId,
                             ConversionService conversionService,
                             @Nullable AddressResolverGroup<?> resolverGroup
    ) {
        this(
            builder()
                .loadBalancer(loadBalancer)
                .explicitHttpVersion(explicitHttpVersion)
                .configuration(configuration)
                .contextPath(contextPath)
                .filterResolver(filterResolver)
                .clientFilterEntries(clientFilterEntries)
                .threadFactory(threadFactory)
                .nettyClientSslBuilder(nettyClientSslBuilder)
                .codecRegistry(codecRegistry)
                .handlerRegistry(handlerRegistry)
                .webSocketBeanRegistry(webSocketBeanRegistry)
                .requestBinderRegistry(requestBinderRegistry)
                .eventLoopGroup(eventLoopGroup)
                .socketChannelFactory(socketChannelFactory)
                .udpChannelFactory(udpChannelFactory)
                .clientCustomizer(clientCustomizer)
                .informationalServiceId(informationalServiceId)
                .conversionService(conversionService)
                .resolverGroup(resolverGroup)
        );
    }

    /**
     * @param uri The URL
     * @deprecated Please go through the {@link #builder()} instead.
     */
    @Deprecated
    public DefaultHttpClient(@Nullable URI uri) {
        this(builder().uri(uri));
    }

    /**
     * @deprecated Please go through the {@link #builder()} instead.
     */
    @Deprecated
    public DefaultHttpClient() {
        this(builder());
    }

    /**
     * @param uri           The URI
     * @param configuration The {@link HttpClientConfiguration} object
     * @deprecated Please go through the {@link #builder()} instead.
     */
    @Deprecated
    public DefaultHttpClient(@Nullable URI uri, HttpClientConfiguration configuration) {
        this(
            builder()
                .uri(uri)
                .configuration(configuration)
        );
    }

    /**
     * Constructor used by micronaut-oracle-cloud.
     *
     * @param uri           The URI
     * @param configuration The {@link HttpClientConfiguration} object
     * @param clientSslBuilder The SSL builder
     * @deprecated Please go through the {@link #builder()} instead.
     */
    @Deprecated
    public DefaultHttpClient(@Nullable URI uri, HttpClientConfiguration configuration, ClientSslBuilder clientSslBuilder) {
        this(
            builder()
                .uri(uri)
                .configuration(configuration)
                .nettyClientSslBuilder(clientSslBuilder)
        );
    }

    /**
     * @param loadBalancer  The {@link LoadBalancer} to use for selecting servers
     * @param configuration The {@link HttpClientConfiguration} object
     * @deprecated Please go through the {@link #builder()} instead. If you need access to properties that are not public in the builder, make them public in core and document their usage.
     */
    @Deprecated
    public DefaultHttpClient(@Nullable LoadBalancer loadBalancer, HttpClientConfiguration configuration) {
        this(
            builder()
                .loadBalancer(loadBalancer)
                .configuration(configuration)
        );
    }

    /**
     * @return The configuration used by this client
     */
    public HttpClientConfiguration getConfiguration() {
        return nettyHttpClient.getConfiguration();
    }

    /**
     * @return The client-specific logger name
     */
    public Logger getLog() {
        return nettyHttpClient.getLog();
    }

    /**
     * Access to the connection manager, for micronaut-oracle-cloud.
     *
     * @return The connection manager of this client
     */
    public ConnectionManager connectionManager() {
        return nettyHttpClient.connectionManager();
    }

    /**
     * @return The current connection manager.
     */
    public ConnectionManager getConnectionManager() {
        return connectionManager();
    }

    /**
     * Replace the underlying {@link ConnectionManager}. Intended for testing.
     *
     * @param connectionManager The new connection manager
     * @since 5.0
     */
    @Internal
    public void setConnectionManager(ConnectionManager connectionManager) {
        nettyHttpClient.setConnectionManager(connectionManager);
    }

    /**
     * @return The underlying {@link NettyHttpClient} delegate.
     * @since 5.0
     */
    @Internal
    NettyHttpClient getNettyHttpClient() {
        return nettyHttpClient;
    }

    @Override
    public HttpClient start() {
        nettyHttpClient.start();
        return this;
    }

    @Override
    public boolean isRunning() {
        return nettyHttpClient.isRunning();
    }

    @Override
    public HttpClient stop() {
        nettyHttpClient.stop();
        return this;
    }

    @Override
    public HttpClient refresh() {
        nettyHttpClient.refresh();
        return this;
    }

    /**
     * @return The {@link MediaTypeCodecRegistry} used by this client
     * @deprecated Use body handlers instead
     */
    @Deprecated
    public MediaTypeCodecRegistry getMediaTypeCodecRegistry() {
        return nettyHttpClient.getMediaTypeCodecRegistry();
    }

    /**
     * Sets the {@link MediaTypeCodecRegistry} used by this client.
     *
     * @param mediaTypeCodecRegistry The registry to use. Should not be null
     * @deprecated Use builder instead
     */
    @Deprecated(forRemoval = true)
    public void setMediaTypeCodecRegistry(MediaTypeCodecRegistry mediaTypeCodecRegistry) {
        nettyHttpClient.setMediaTypeCodecRegistry(mediaTypeCodecRegistry);
    }

    /**
     * Get the handler registry for this client.
     *
     * @return The handler registry
     */
    public final MessageBodyHandlerRegistry getHandlerRegistry() {
        return nettyHttpClient.getHandlerRegistry();
    }

    /**
     * Set the handler registry for this client.
     *
     * @param handlerRegistry The handler registry
     * @deprecated Use builder instead
     */
    @Deprecated(forRemoval = true)
    public final void setHandlerRegistry(MessageBodyHandlerRegistry handlerRegistry) {
        nettyHttpClient.setHandlerRegistry(handlerRegistry);
    }

    @Override
    public BlockingHttpClient toBlocking() {
        return nettyHttpClient.toBlocking();
    }

    @Override
    public <I> Publisher<Event<ByteBuffer<?>>> eventStream(HttpRequest<I> request) {
        return nettyHttpClient.eventStream(request);
    }

    @Override
    public <I, B> Publisher<Event<B>> eventStream(HttpRequest<I> request, Argument<B> eventType) {
        return nettyHttpClient.eventStream(request, eventType);
    }

    @Override
    public <I, B> Publisher<Event<B>> eventStream(HttpRequest<I> request, Argument<B> eventType, Argument<?> errorType) {
        return nettyHttpClient.eventStream(request, eventType, errorType);
    }

    @Override
    public <I> Publisher<ByteBuffer<?>> dataStream(HttpRequest<I> request) {
        return nettyHttpClient.dataStream(request);
    }

    @Override
    public <I> Publisher<ByteBuffer<?>> dataStream(HttpRequest<I> request, @Nullable Argument<?> errorType) {
        return nettyHttpClient.dataStream(request, errorType);
    }

    @Override
    public <I> Publisher<HttpResponse<ByteBuffer<?>>> exchangeStream(HttpRequest<I> request) {
        return nettyHttpClient.exchangeStream(request);
    }

    @Override
    public <I> Publisher<HttpResponse<ByteBuffer<?>>> exchangeStream(HttpRequest<I> request, Argument<?> errorType) {
        return nettyHttpClient.exchangeStream(request, errorType);
    }

    @Override
    public <I, O> Publisher<O> jsonStream(HttpRequest<I> request, Argument<O> type) {
        return nettyHttpClient.jsonStream(request, type);
    }

    @Override
    public <I, O> Publisher<O> jsonStream(HttpRequest<I> request, Argument<O> type, Argument<?> errorType) {
        return nettyHttpClient.jsonStream(request, type, errorType);
    }

    @Override
    public <I> Publisher<Map<String, Object>> jsonStream(HttpRequest<I> request) {
        return nettyHttpClient.jsonStream(request);
    }

    @Override
    public <I, O> Publisher<O> jsonStream(HttpRequest<I> request, Class<O> type) {
        return nettyHttpClient.jsonStream(request, type);
    }

    @Override
    public <I, O, E> Publisher<HttpResponse<O>> exchange(HttpRequest<I> request, @Nullable Argument<O> bodyType, Argument<E> errorType) {
        return nettyHttpClient.exchange(request, bodyType, errorType);
    }

    @Override
    public <I, O, E> Publisher<O> retrieve(HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType) {
        return nettyHttpClient.retrieve(request, bodyType, errorType);
    }

    @Override
    public <T extends AutoCloseable> Publisher<T> connect(Class<T> type, MutableHttpRequest<?> request) {
        return nettyHttpClient.connect(type, request);
    }

    @Override
    public <T extends AutoCloseable> Publisher<T> connect(Class<T> type, Map<String, Object> serviceIdentifier) {
        return nettyHttpClient.connect(type, serviceIdentifier);
    }

    @Override
    public void close() {
        nettyHttpClient.close();
    }

    @Override
    public AsyncHttpClient toAsync() {
        return asyncHttpClient;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> proxy(HttpRequest<?> request) {
        return nettyHttpClient.proxy(request);
    }

    @Override
    public Publisher<MutableHttpResponse<?>> proxy(HttpRequest<?> request, ProxyRequestOptions options) {
        return nettyHttpClient.proxy(request, options);
    }

    @Override
    public Publisher<? extends HttpResponse<?>> exchange(HttpRequest<?> parentRequest, @Nullable CloseableByteBody body, @Nullable Thread originatingThread) {
        return nettyHttpClient.exchange(parentRequest, body, originatingThread);
    }

    /**
     * @param request The request
     * @param <I>     The input type
     * @return A {@link Publisher} with the resolved URI
     * @deprecated Avoid inheriting and accessing this method
     */
    @Deprecated(since = "5.0", forRemoval = true)
    protected final <I> ExecutionFlow<URI> resolveRequestURI(HttpRequest<I> request) {
        return nettyHttpClient.resolveRequestURI(request);
    }

    /**
     * @param request            The request
     * @param includeContextPath Whether to prepend the client context path
     * @param <I>                The input type
     * @return A {@link Publisher} with the resolved URI
     * @deprecated Avoid inheriting and accessing this method
     */
    @Deprecated(since = "5.0", forRemoval = true)
    protected final <I> ExecutionFlow<URI> resolveRequestURI(HttpRequest<I> request, boolean includeContextPath) {
        return nettyHttpClient.resolveRequestURI(request, includeContextPath);
    }

    /**
     * @param parentRequest      The parent request
     * @param request            The redirect location request
     * @param <I>                The input type
     * @return A {@link Publisher} with the resolved URI
     * @deprecated Avoid inheriting and accessing this method
     */
    @Deprecated(since = "5.0", forRemoval = true)
    protected final <I> ExecutionFlow<URI> resolveRedirectURI(HttpRequest<?> parentRequest, HttpRequest<I> request) {
        return nettyHttpClient.resolveRedirectURI(parentRequest, request);
    }

    /**
     * @param request The request object
     * @return The discriminator to use when selecting a server for the purposes of load balancing (defaults to {@link io.micronaut.http.HttpRequest})
     * @deprecated Avoid inheriting and accessing this method
     */
    @Deprecated(since = "5.0", forRemoval = true)
    protected final Object getLoadBalancerDiscriminator(HttpRequest<?> request) {
        return nettyHttpClient.getLoadBalancerDiscriminator(request);
    }

    /**
     * Create a new builder for a {@link DefaultHttpClient}.
     *
     * @return The builder
     * @since 4.7.0
     */
    public static DefaultHttpClientBuilder builder() {
        return new DefaultHttpClientBuilder();
    }

    /**
     * Compatibility wrapper around {@link NettyHttpClient.RequestKey}.
     *
     * @since 4.8.0
     */
    public static final class RequestKey extends NettyHttpClient.RequestKey {
        public RequestKey(DefaultHttpClient ctx, URI requestURI) {
            super(ctx.nettyHttpClient, requestURI);
        }
    }
}
