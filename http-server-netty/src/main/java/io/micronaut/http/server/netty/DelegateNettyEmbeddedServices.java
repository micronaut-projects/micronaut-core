/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.http.server.netty;

import java.util.List;
import java.util.concurrent.ExecutorService;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration;
import io.micronaut.http.netty.channel.EventLoopGroupRegistry;
import io.micronaut.http.netty.channel.converters.ChannelOptionFactory;
import io.micronaut.http.server.RouteExecutor;
import io.micronaut.http.server.netty.ssl.ServerSslBuilder;
import io.micronaut.web.router.resource.StaticResourceResolver;
import io.micronaut.websocket.context.WebSocketBeanRegistry;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;

/**
 * A delegating Netty embedded services instance.
 *
 * @since 3.1.4
 * @author graemerocher
 */
@Internal
interface DelegateNettyEmbeddedServices extends NettyEmbeddedServices {
    /**
     * @return The instance to delegate to.
     */
    @NonNull
    NettyEmbeddedServices getDelegate();

    @Override
    default List<ChannelOutboundHandler> getOutboundHandlers() {
        return getDelegate().getOutboundHandlers();
    }

    @Override
    default ApplicationContext getApplicationContext() {
        return getDelegate().getApplicationContext();
    }

    @Override
    default RouteExecutor getRouteExecutor() {
        return getDelegate().getRouteExecutor();
    }

    @Override
    default MediaTypeCodecRegistry getMediaTypeCodecRegistry() {
        return getDelegate().getMediaTypeCodecRegistry();
    }

    @Override
    default StaticResourceResolver getStaticResourceResolver() {
        return getDelegate().getStaticResourceResolver();
    }

    @Override
    default ServerSslBuilder getServerSslBuilder() {
        return getDelegate().getServerSslBuilder();
    }

    @Override
    default ChannelOptionFactory getChannelOptionFactory() {
        return getDelegate().getChannelOptionFactory();
    }

    @Override
    default HttpCompressionStrategy getHttpCompressionStrategy() {
        return getDelegate().getHttpCompressionStrategy();
    }

    @Override
    default WebSocketBeanRegistry getWebSocketBeanRegistry() {
        return getDelegate().getWebSocketBeanRegistry();
    }

    @Override
    default EventLoopGroupRegistry getEventLoopGroupRegistry() {
        return getDelegate().getEventLoopGroupRegistry();
    }

    @Override
    default EventLoopGroup createEventLoopGroup(EventLoopGroupConfiguration config) {
        return getDelegate().createEventLoopGroup(config);
    }

    @Override
    default EventLoopGroup createEventLoopGroup(int numThreads, ExecutorService executorService, Integer ioRatio) {
        return getDelegate().createEventLoopGroup(numThreads, executorService, ioRatio);
    }

    @Override
    default ServerSocketChannel getServerSocketChannelInstance(EventLoopGroupConfiguration workerConfig) {
        return getDelegate().getServerSocketChannelInstance(workerConfig);
    }

    @Override
    default <E> ApplicationEventPublisher<E> getEventPublisher(Class<E> eventClass) {
        return getDelegate().getEventPublisher(eventClass);
    }
}
