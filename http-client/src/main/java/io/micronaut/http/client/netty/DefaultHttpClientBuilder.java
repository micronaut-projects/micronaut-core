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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.filter.ClientFilterResolutionContext;
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
import io.netty.resolver.AddressResolverGroup;
import org.jspecify.annotations.Nullable;

import java.net.URI;
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
    private final NettyHttpClientBuilder delegate = new NettyHttpClientBuilder();

    DefaultHttpClientBuilder() {
    }

    NettyHttpClientBuilder nettyBuilder() {
        return delegate;
    }

    DefaultHttpClientBuilder loadBalancer(@Nullable LoadBalancer loadBalancer) {
        delegate.loadBalancer(loadBalancer);
        return this;
    }

    /**
     * Set the optional URI for this client to use as the root.
     *
     * @param uri The URI
     * @return This builder
     */
    public DefaultHttpClientBuilder uri(@Nullable URI uri) {
        delegate.uri(uri);
        return this;
    }

    DefaultHttpClientBuilder explicitHttpVersion(@Nullable HttpVersionSelection explicitHttpVersion) {
        delegate.explicitHttpVersion(explicitHttpVersion);
        return this;
    }

    /**
     * Set the configuration.
     *
     * @param configuration The client configuration
     * @return This builder
     */
    public DefaultHttpClientBuilder configuration(HttpClientConfiguration configuration) {
        delegate.configuration(configuration);
        return this;
    }

    DefaultHttpClientBuilder contextPath(@Nullable String contextPath) {
        delegate.contextPath(contextPath);
        return this;
    }

    DefaultHttpClientBuilder filterResolver(HttpClientFilterResolver<ClientFilterResolutionContext> filterResolver) {
        delegate.filterResolver(filterResolver);
        return this;
    }

    DefaultHttpClientBuilder annotationMetadataResolver(@Nullable AnnotationMetadataResolver annotationMetadataResolver) {
        delegate.annotationMetadataResolver(annotationMetadataResolver);
        return this;
    }

    DefaultHttpClientBuilder filters(HttpClientFilter... filters) {
        delegate.filters(filters);
        return this;
    }

    DefaultHttpClientBuilder clientFilterEntries(@Nullable List<HttpFilterResolver.FilterEntry> clientFilterEntries) {
        delegate.clientFilterEntries(clientFilterEntries);
        return this;
    }

    DefaultHttpClientBuilder threadFactory(@Nullable ThreadFactory threadFactory) {
        delegate.threadFactory(threadFactory);
        return this;
    }

    /**
     * The netty SSL context builder. Used by the micronaut-oracle-cloud OKE workload identity
     * client.
     *
     * @param nettyClientSslBuilder The SSL context builder
     * @return This builder
     */
    public DefaultHttpClientBuilder nettyClientSslBuilder(ClientSslBuilder nettyClientSslBuilder) {
        delegate.nettyClientSslBuilder(nettyClientSslBuilder);
        return this;
    }

    public DefaultHttpClientBuilder sslFactory(NettyClientSslFactory sslFactory, BeanProvider<CertificateProvider> certificateProviders) {
        delegate.sslFactory(sslFactory, certificateProviders);
        return this;
    }

    /**
     * Set the codec registry. This has mostly been replaced by body handlers by now.
     *
     * @param codecRegistry The codec registry
     * @return This builder
     * @deprecated Use body handlers instead
     */
    @Deprecated
    DefaultHttpClientBuilder codecRegistry(MediaTypeCodecRegistry codecRegistry) {
        delegate.codecRegistry(codecRegistry);
        return this;
    }

    DefaultHttpClientBuilder handlerRegistry(MessageBodyHandlerRegistry handlerRegistry) {
        delegate.handlerRegistry(handlerRegistry);
        return this;
    }

    DefaultHttpClientBuilder webSocketBeanRegistry(WebSocketBeanRegistry webSocketBeanRegistry) {
        delegate.webSocketBeanRegistry(webSocketBeanRegistry);
        return this;
    }

    DefaultHttpClientBuilder requestBinderRegistry(RequestBinderRegistry requestBinderRegistry) {
        delegate.requestBinderRegistry(requestBinderRegistry);
        return this;
    }

    DefaultHttpClientBuilder eventLoopGroup(@Nullable EventLoopGroup eventLoopGroup) {
        delegate.eventLoopGroup(eventLoopGroup);
        return this;
    }

    DefaultHttpClientBuilder socketChannelFactory(ChannelFactory<? extends Channel> socketChannelFactory) {
        delegate.socketChannelFactory(socketChannelFactory);
        return this;
    }

    DefaultHttpClientBuilder udpChannelFactory(ChannelFactory<? extends Channel> udpChannelFactory) {
        delegate.udpChannelFactory(udpChannelFactory);
        return this;
    }

    DefaultHttpClientBuilder clientCustomizer(NettyClientCustomizer clientCustomizer) {
        delegate.clientCustomizer(clientCustomizer);
        return this;
    }

    DefaultHttpClientBuilder informationalServiceId(@Nullable String informationalServiceId) {
        delegate.informationalServiceId(informationalServiceId);
        return this;
    }

    DefaultHttpClientBuilder conversionService(ConversionService conversionService) {
        delegate.conversionService(conversionService);
        return this;
    }

    DefaultHttpClientBuilder resolverGroup(@Nullable AddressResolverGroup<?> resolverGroup) {
        delegate.resolverGroup(resolverGroup);
        return this;
    }

    DefaultHttpClientBuilder blockingExecutor(@Nullable ExecutorService blockingExecutor) {
        delegate.blockingExecutor(blockingExecutor);
        return this;
    }

    /**
     * Build the final HTTP client. This method may only be called once.
     *
     * @return The client
     */
    public DefaultHttpClient build() {
        boolean createdDefaultConfiguration = false;
        boolean restoreLoggerName = false;
        String originalLoggerName = null;
        if (delegate.configuration == null) {
            delegate.configuration = new DefaultHttpClientConfiguration();
            createdDefaultConfiguration = true;
        }
        if (delegate.configuration.getLoggerName().isEmpty()) {
            if (!createdDefaultConfiguration) {
                originalLoggerName = delegate.configuration.getLoggerName().orElse(null);
                restoreLoggerName = true;
            }
            delegate.configuration.setLoggerName(DefaultHttpClient.class.getName());
        }
        DefaultHttpClient client;
        try {
            client = new DefaultHttpClient(delegate.build());
        } catch (RuntimeException | Error e) {
            if (restoreLoggerName) {
                delegate.configuration.setLoggerName(originalLoggerName);
            }
            throw e;
        }
        if (restoreLoggerName) {
            delegate.configuration.setLoggerName(originalLoggerName);
        }
        return client;
    }
}
