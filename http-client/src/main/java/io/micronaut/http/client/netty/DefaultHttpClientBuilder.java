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
package io.micronaut.http.client.netty;

import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.AnnotationMetadataResolver;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.filter.ClientFilterResolutionContext;
import io.micronaut.http.client.filter.DefaultHttpClientFilterResolver;
import io.micronaut.http.client.netty.ssl.ClientSslBuilder;
import io.micronaut.http.client.netty.ssl.NettyClientSslFactory;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.http.filter.HttpClientFilterResolver;
import io.micronaut.http.filter.HttpFilterResolver;
import io.micronaut.http.ssl.CertificateProvider;
import io.micronaut.websocket.context.WebSocketBeanRegistry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.resolver.AddressResolverGroup;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * While {@link DefaultHttpClient} is internal API, there are a few uses outside micronaut-core
 * that use it directly, in particular micronaut-oracle-cloud. This builder acts as API for those
 * users.
 * <p>
 * If you need to make a method of this builder public, please document the module that uses it.
 *
 * @author Jonas Konrad
 * @since 4.7.0
 */
@Internal
public final class DefaultHttpClientBuilder {
    @Nullable
    LoadBalancer loadBalancer = null;
    @Nullable
    HttpVersionSelection explicitHttpVersion = null;
    HttpClientConfiguration configuration;
    @Nullable
    String contextPath = null;
    @NonNull
    AnnotationMetadataResolver annotationMetadataResolver = AnnotationMetadataResolver.DEFAULT;
    HttpClientFilterResolver<ClientFilterResolutionContext> filterResolver;
    List<HttpFilterResolver.FilterEntry> clientFilterEntries = null;
    @Nullable
    ThreadFactory threadFactory;
    ClientSslBuilder nettyClientSslBuilder;
    NettyClientSslFactory sslFactory;
    BeanProvider<CertificateProvider> certificateProviders;
    MediaTypeCodecRegistry codecRegistry;
    MessageBodyHandlerRegistry handlerRegistry;
    @NonNull
    WebSocketBeanRegistry webSocketBeanRegistry = WebSocketBeanRegistry.EMPTY;
    RequestBinderRegistry requestBinderRegistry;
    @Nullable
    EventLoopGroup eventLoopGroup = null;
    @NonNull
    ChannelFactory<? extends Channel> socketChannelFactory = NioSocketChannel::new;
    @NonNull
    ChannelFactory<? extends Channel> udpChannelFactory = NioDatagramChannel::new;
    NettyClientCustomizer clientCustomizer = CompositeNettyClientCustomizer.EMPTY;
    @Nullable
    String informationalServiceId = null;
    @NonNull
    ConversionService conversionService = ConversionService.SHARED;
    @Nullable
    AddressResolverGroup<?> resolverGroup = null;
    @Nullable
    ExecutorService blockingExecutor = null;

    DefaultHttpClientBuilder() {
    }

    @NonNull
    DefaultHttpClientBuilder loadBalancer(@Nullable LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
        return this;
    }

    /**
     * Set the optional URI for this client to use as the root.
     *
     * @param uri The URI
     * @return This builder
     */
    @NonNull
    public DefaultHttpClientBuilder uri(@Nullable URI uri) {
        return loadBalancer(uri == null ? null : LoadBalancer.fixed(uri));
    }

    @NonNull
    DefaultHttpClientBuilder explicitHttpVersion(@Nullable HttpVersionSelection explicitHttpVersion) {
        this.explicitHttpVersion = explicitHttpVersion;
        return this;
    }

    /**
     * Set the configuration.
     *
     * @param configuration The client configuration
     * @return This builder
     */
    @NonNull
    public DefaultHttpClientBuilder configuration(@NonNull HttpClientConfiguration configuration) {
        ArgumentUtils.requireNonNull("configuration", configuration);
        this.configuration = configuration;
        return this;
    }

    @NonNull
    DefaultHttpClientBuilder contextPath(@Nullable String contextPath) {
        this.contextPath = contextPath;
        return this;
    }

    @NonNull
    DefaultHttpClientBuilder filterResolver(@NonNull HttpClientFilterResolver<ClientFilterResolutionContext> filterResolver) {
        ArgumentUtils.requireNonNull("filterResolver", filterResolver);
        this.filterResolver = filterResolver;
        return this;
    }

    @NonNull
    DefaultHttpClientBuilder annotationMetadataResolver(@Nullable AnnotationMetadataResolver annotationMetadataResolver) {
        if (annotationMetadataResolver != null) {
            this.annotationMetadataResolver = annotationMetadataResolver;
        }
        return this;
    }

    @NonNull
    DefaultHttpClientBuilder filters(HttpClientFilter... filters) {
        return filterResolver(new DefaultHttpClientFilterResolver(null, annotationMetadataResolver, Arrays.asList(filters)));
    }

    @NonNull
    DefaultHttpClientBuilder clientFilterEntries(@Nullable List<HttpFilterResolver.FilterEntry> clientFilterEntries) {
        this.clientFilterEntries = clientFilterEntries;
        return this;
    }

    @NonNull
    DefaultHttpClientBuilder threadFactory(@Nullable ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
        return this;
    }

    /**
     * The netty SSL context builder. Used by the micronaut-oracle-cloud OKE workload identity
     * client.
     *
     * @param nettyClientSslBuilder The SSL context builder
     * @return This builder
     */
    @NonNull
    public DefaultHttpClientBuilder nettyClientSslBuilder(@NonNull ClientSslBuilder nettyClientSslBuilder) {
        ArgumentUtils.requireNonNull("nettyClientSslBuilder", nettyClientSslBuilder);
        this.nettyClientSslBuilder = nettyClientSslBuilder;
        return this;
    }

    @NonNull
    public DefaultHttpClientBuilder sslFactory(@NonNull NettyClientSslFactory sslFactory, @NonNull BeanProvider<CertificateProvider> certificateProviders) {
        ArgumentUtils.requireNonNull("sslFactory", sslFactory);
        ArgumentUtils.requireNonNull("certificateProviders", certificateProviders);
        this.sslFactory = sslFactory;
        this.certificateProviders = certificateProviders;
        return this;
    }

    /**
     * Set the codec registry. This has mostly been replaced by body handlers by now.
     *
     * @param codecRegistry The codec registry
     * @return This builder
     * @deprecated Use body handlers instead
     */
    @NonNull
    @Deprecated
    DefaultHttpClientBuilder codecRegistry(@NonNull MediaTypeCodecRegistry codecRegistry) {
        ArgumentUtils.requireNonNull("codecRegistry", codecRegistry);
        this.codecRegistry = codecRegistry;
        return this;
    }

    @NonNull
    DefaultHttpClientBuilder handlerRegistry(@NonNull MessageBodyHandlerRegistry handlerRegistry) {
        ArgumentUtils.requireNonNull("handlerRegistry", handlerRegistry);
        this.handlerRegistry = handlerRegistry;
        return this;
    }

    @NonNull
    DefaultHttpClientBuilder webSocketBeanRegistry(@NonNull WebSocketBeanRegistry webSocketBeanRegistry) {
        ArgumentUtils.requireNonNull("webSocketBeanRegistry", webSocketBeanRegistry);
        this.webSocketBeanRegistry = webSocketBeanRegistry;
        return this;
    }

    @NonNull
    DefaultHttpClientBuilder requestBinderRegistry(@NonNull RequestBinderRegistry requestBinderRegistry) {
        ArgumentUtils.requireNonNull("requestBinderRegistry", requestBinderRegistry);
        this.requestBinderRegistry = requestBinderRegistry;
        return this;
    }

    @NonNull
    DefaultHttpClientBuilder eventLoopGroup(@Nullable EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
        return this;
    }

    @NonNull
    DefaultHttpClientBuilder socketChannelFactory(@NonNull ChannelFactory<? extends Channel> socketChannelFactory) {
        ArgumentUtils.requireNonNull("socketChannelFactory", socketChannelFactory);
        this.socketChannelFactory = socketChannelFactory;
        return this;
    }

    @NonNull
    DefaultHttpClientBuilder udpChannelFactory(@NonNull ChannelFactory<? extends Channel> udpChannelFactory) {
        ArgumentUtils.requireNonNull("udpChannelFactory", udpChannelFactory);
        this.udpChannelFactory = udpChannelFactory;
        return this;
    }

    @NonNull
    DefaultHttpClientBuilder clientCustomizer(@NonNull NettyClientCustomizer clientCustomizer) {
        ArgumentUtils.requireNonNull("clientCustomizer", clientCustomizer);
        this.clientCustomizer = clientCustomizer;
        return this;
    }

    @NonNull
    DefaultHttpClientBuilder informationalServiceId(@Nullable String informationalServiceId) {
        this.informationalServiceId = informationalServiceId;
        return this;
    }

    @NonNull
    DefaultHttpClientBuilder conversionService(@NonNull ConversionService conversionService) {
        ArgumentUtils.requireNonNull("conversionService", conversionService);
        this.conversionService = conversionService;
        return this;
    }

    @NonNull
    DefaultHttpClientBuilder resolverGroup(@Nullable AddressResolverGroup<?> resolverGroup) {
        this.resolverGroup = resolverGroup;
        return this;
    }

    @NonNull
    DefaultHttpClientBuilder blockingExecutor(@Nullable ExecutorService blockingExecutor) {
        this.blockingExecutor = blockingExecutor;
        return this;
    }

    /**
     * Build the final HTTP client. This method may only be called once.
     *
     * @return The client
     */
    @NonNull
    public DefaultHttpClient build() {
        return new DefaultHttpClient(this);
    }
}
