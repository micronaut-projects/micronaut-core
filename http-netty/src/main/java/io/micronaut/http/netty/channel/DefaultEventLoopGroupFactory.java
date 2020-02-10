/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.http.netty.channel;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.util.ArgumentUtils;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
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

    private boolean useNativeTransport = false;
    private final EventLoopGroupFactory nativeFactory;
    private final EventLoopGroupFactory defaultFactory;

    /**
     * Default constructor.
     * @param nioEventLoopGroupFactory The NIO factory
     * @param nativeFactory The native factory if available
     */
    public DefaultEventLoopGroupFactory(
            NioEventLoopGroupFactory nioEventLoopGroupFactory,
            @Nullable @Named(EventLoopGroupFactory.NATIVE) EventLoopGroupFactory nativeFactory) {
        this.defaultFactory = nioEventLoopGroupFactory;
        this.nativeFactory = nativeFactory != null ? nativeFactory : defaultFactory;
    }

    /**
     * @deprecated Use {@link DefaultEventLoopGroupConfiguration} instead and {@code micronaut.netty.event-loops.default.prefer-native-transport}
     *
     * @param useNativeTransport Whether to use native transport
     */
    @Deprecated
    @Inject
    protected void setUseNativeTransport(@Property(name = "micronaut.server.netty.use-native-transport") @Nullable Boolean useNativeTransport) {
        if (useNativeTransport != null) {
            this.useNativeTransport = useNativeTransport;
        }
    }

    @Override
    public EventLoopGroup createEventLoopGroup(EventLoopGroupConfiguration configuration, ThreadFactory threadFactory) {
        ArgumentUtils.requireNonNull("configuration", configuration);
        ArgumentUtils.requireNonNull("threadFactory", threadFactory);

        if (useNativeTransport || configuration.isPreferNativeTransport()) {
            return this.nativeFactory.createEventLoopGroup(configuration, threadFactory);
        } else {
            return this.defaultFactory.createEventLoopGroup(configuration, threadFactory);
        }
    }

    @Override
    public EventLoopGroup createEventLoopGroup(int threads, Executor executor, @Nullable Integer ioRatio) {
        return nativeFactory.createEventLoopGroup(threads, executor, ioRatio);
    }

    @Override
    public EventLoopGroup createEventLoopGroup(int threads, @Nullable ThreadFactory threadFactory, @Nullable Integer ioRatio) {
        return nativeFactory.createEventLoopGroup(threads, threadFactory, ioRatio);
    }

    @Override
    public Class<? extends ServerSocketChannel> serverSocketChannelClass() {
        return nativeFactory.serverSocketChannelClass();
    }

    @NonNull
    @Override
    public Class<? extends ServerSocketChannel> serverSocketChannelClass(EventLoopGroupConfiguration configuration) {
        if (useNativeTransport || configuration != null && configuration.isPreferNativeTransport()) {
            return this.nativeFactory.serverSocketChannelClass();
        } else {
            return this.defaultFactory.serverSocketChannelClass();
        }

    }
}
