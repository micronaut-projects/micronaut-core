/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.netty.channel;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Primary;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.http.netty.configuration.NettyGlobalConfiguration;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.util.ResourceLeakDetector;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/**
 * The default factory for {@link EventLoopGroup} instances.
 *
 * @author graemerocher
 * @since 1.0
 */
@Primary
@Singleton
@BootstrapContextCompatible
public class DefaultEventLoopGroupFactory implements EventLoopGroupFactory {
    static final List<String> FACTORY_PRIORITY = List.of(
        IoUringEventLoopGroupFactory.NAME,
        EpollEventLoopGroupFactory.NAME,
        KQueueEventLoopGroupFactory.NAME,
        NioEventLoopGroupFactory.NAME
    );

    private final Map<String, EventLoopGroupFactory> factories;

    /**
     * Default constructor.
     * @param nioEventLoopGroupFactory The NIO factory
     * @param nativeFactory The native factory if available
     */
    @Deprecated
    public DefaultEventLoopGroupFactory(
            NioEventLoopGroupFactory nioEventLoopGroupFactory,
            @Nullable @Named(EventLoopGroupFactory.NATIVE) EventLoopGroupFactory nativeFactory) {
        this(nioEventLoopGroupFactory, nativeFactory, null);
    }

    @Deprecated
    public DefaultEventLoopGroupFactory(
        NioEventLoopGroupFactory nioEventLoopGroupFactory,
        @Nullable @Named(EventLoopGroupFactory.NATIVE) EventLoopGroupFactory nativeFactory,
        @Nullable NettyGlobalConfiguration nettyGlobalConfiguration) {
        this(
            nativeFactory == null ? Map.of(NioEventLoopGroupFactory.NAME, nioEventLoopGroupFactory) :
                Map.of(NioEventLoopGroupFactory.NAME, nioEventLoopGroupFactory, EventLoopGroupFactory.NATIVE, nativeFactory),
            nettyGlobalConfiguration
        );
    }

    /**
     * Default constructor.
     * @param eventLoopGroupFactories The available transports
     * @param nettyGlobalConfiguration The netty global configuration
     */
    @Inject
    public DefaultEventLoopGroupFactory(
        Map<String, EventLoopGroupFactory> eventLoopGroupFactories,
        @Nullable NettyGlobalConfiguration nettyGlobalConfiguration) {

        this.factories = eventLoopGroupFactories;

        if (nettyGlobalConfiguration != null && nettyGlobalConfiguration.getResourceLeakDetectorLevel() != null) {
            ResourceLeakDetector.setLevel(nettyGlobalConfiguration.getResourceLeakDetectorLevel());
        } else if (ResourceLeakDetector.getLevel() == ResourceLeakDetector.Level.SIMPLE &&
            System.getProperty("io.netty.leakDetectionLevel") == null &&
            System.getProperty("io.netty.leakDetection.level") == null) {
            // disable leak detection for performance if it's not explicitly enabled in a system
            // property, via config, or via setLevel
            ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
        }

        // maxOrder=3 (64 KiB chunks) keeps the footprint of the pooled allocator small. It only
        // applies to PooledByteBufAllocator, which is no longer Netty's default allocator (Netty 4.2
        // uses the adaptive allocator unless io.netty.allocator.type says otherwise), so only set it
        // when the pooled allocator is selected. Like all io.netty.allocator.* properties, it only
        // takes effect if PooledByteBufAllocator has not been initialized yet.
        if (System.getProperty("io.netty.allocator.maxOrder") == null && isPooledAllocatorSelected()) {
            System.setProperty("io.netty.allocator.maxOrder", "3");
        }
    }

    /**
     * Whether {@code io.netty.allocator.type} selects the pooled allocator as the default allocator.
     * This mirrors the selection in Netty's {@code ByteBufUtil}: {@code unpooled} and
     * {@code adaptive} (the default when unset) select other allocators, any other value falls back
     * to the pooled allocator.
     *
     * @return {@code true} if the pooled allocator is the default allocator
     */
    private static boolean isPooledAllocatorSelected() {
        String allocatorType = System.getProperty("io.netty.allocator.type");
        return allocatorType != null && !allocatorType.equals("adaptive") && !allocatorType.equals("unpooled");
    }

    private EventLoopGroupFactory nativeFactory() {
        return factories.entrySet().stream()
            .min(Comparator.comparingInt(e -> FACTORY_PRIORITY.indexOf(e.getKey())))
            .orElseThrow()
            .getValue();
    }

    @Override
    public IoHandlerFactory createIoHandlerFactory() {
        return nativeFactory().createIoHandlerFactory();
    }

    @Override
    public IoHandlerFactory createIoHandlerFactory(EventLoopGroupConfiguration configuration) {
        return getFactory(configuration).createIoHandlerFactory(configuration);
    }

    @Override
    @Deprecated
    public EventLoopGroup createEventLoopGroup(EventLoopGroupConfiguration configuration, ThreadFactory threadFactory) {
        ArgumentUtils.requireNonNull("configuration", configuration);
        ArgumentUtils.requireNonNull("threadFactory", threadFactory);

        return getFactory(configuration).createEventLoopGroup(configuration, threadFactory);
    }

    @Override
    @Deprecated
    public EventLoopGroup createEventLoopGroup(int threads, Executor executor, @Nullable Integer ioRatio) {
        return nativeFactory().createEventLoopGroup(threads, executor, ioRatio);
    }

    @Override
    @Deprecated
    public EventLoopGroup createEventLoopGroup(int threads, @Nullable ThreadFactory threadFactory, @Nullable Integer ioRatio) {
        return nativeFactory().createEventLoopGroup(threads, threadFactory, ioRatio);
    }

    @Override
    public Class<? extends Channel> channelClass(NettyChannelType type) throws UnsupportedOperationException {
        return nativeFactory().channelClass(type);
    }

    @Override
    public Class<? extends Channel> channelClass(NettyChannelType type, @Nullable EventLoopGroupConfiguration configuration) {
        return getFactory(configuration).channelClass(type, configuration);
    }

    @Override
    public Channel channelInstance(NettyChannelType type, @Nullable EventLoopGroupConfiguration configuration) {
        return getFactory(configuration).channelInstance(type, configuration);
    }

    @Override
    public Channel channelInstance(NettyChannelType type, @Nullable EventLoopGroupConfiguration configuration, @Nullable Channel parent, int fd) {
        return getFactory(configuration).channelInstance(type, configuration, parent, fd);
    }

    private EventLoopGroupFactory getFactory(@Nullable EventLoopGroupConfiguration configuration) {
        if (configuration != null) {
            return configuration.getTransport().stream()
                .map(factories::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No matching transport was found. Configured transports are " + configuration.getTransport() + ", available transports are " + factories.keySet()));
        } else {
            return Objects.requireNonNull(factories.get(NioEventLoopGroupFactory.NAME));
        }
    }

}
