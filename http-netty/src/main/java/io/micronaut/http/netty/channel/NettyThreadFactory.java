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
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.netty.configuration.NettyGlobalConfiguration;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.NettyRuntime;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.internal.SystemPropertyUtil;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import reactor.core.scheduler.NonBlocking;

import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

/**
 * The Default thread factory the Netty {@link io.netty.channel.nio.NioEventLoopGroup} will use within Micronaut to
 * create threads.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Factory
@TypeHint(value = {
    NioServerSocketChannel.class,
    NioSocketChannel.class
}, typeNames = {"sun.security.ssl.SSLContextImpl$TLSContext"},
    accessType = {TypeHint.AccessType.ALL_DECLARED_CONSTRUCTORS, TypeHint.AccessType.ALL_DECLARED_FIELDS, TypeHint.AccessType.ALL_PUBLIC_CONSTRUCTORS}
)
@BootstrapContextCompatible
public class NettyThreadFactory {

    /**
     * Name for Netty thread factory.
     */
    public static final String NAME = "netty";

    /**
     * Constant with the default threads in the event loop.
     *
     * @deprecated Non-functional, replaced by {@link #getDefaultEventLoopThreads()}.
     */
    @Deprecated(forRemoval = true, since = "4.2.0")
    public static final int DEFAULT_EVENT_LOOP_THREADS = 1;

    private final NettyGlobalConfiguration configuration;

    /**
     * Create a new netty ThreadFactory factory.
     *
     * @param configuration The configuration
     * @since 4.4.0
     */
    @Inject
    public NettyThreadFactory(NettyGlobalConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Create a new netty ThreadFactory factory.
     *
     * @deprecated Pass the config instead, through {@link #NettyThreadFactory(NettyGlobalConfiguration)}
     */
    @Deprecated
    public NettyThreadFactory() {
        this(new NettyGlobalConfiguration());
    }

    /**
     * Get the default number of threads in the event loop.
     *
     * @return The number of threads
     */
    @Deprecated(forRemoval = true)
    public static int getDefaultEventLoopThreads() {
        return Math.max(1, SystemPropertyUtil.getInt("io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 2));
    }

    /**
     * Constructs the default thread factory used by the HTTP client.
     *
     * @return The thread factory
     */
    @Singleton
    @Named(NAME)
    @BootstrapContextCompatible
    protected ThreadFactory nettyThreadFactory() {
        return new EventLoopCustomizableThreadFactory(
            configuration.isDefaultThreadFactoryDaemon(),
            configuration.getDefaultThreadFactoryPriority(),
            configuration.isDefaultThreadFactoryReactorNonBlocking());
    }

    /**
     * We only create one ThreadFactory singleton, but in order to get different names for
     * different event loops, we need one {@link DefaultThreadFactory} for each event loop. When
     * the event loop factory sees this class, it uses it as a template to create an individual
     * ThreadFactory for each event loop group.
     */
    @Internal
    public static final class EventLoopCustomizableThreadFactory implements ThreadFactory {
        private final boolean daemon;
        private final int priority;
        private final boolean nonBlocking;

        private final Supplier<ThreadFactory> fallbackDelegate = SupplierUtil.memoized(this::customizeForEventLoop);

        public EventLoopCustomizableThreadFactory(boolean daemon, int priority, boolean nonBlocking) {
            this.daemon = daemon;
            this.priority = priority;
            this.nonBlocking = nonBlocking;
        }

        public ThreadFactory customizeForEventLoop() {
            return new CustomizedThreadFactory(daemon, priority, nonBlocking);
        }

        @Override
        public Thread newThread(Runnable r) {
            return fallbackDelegate.get().newThread(r);
        }
    }

    private static final class CustomizedThreadFactory extends DefaultThreadFactory {
        private final boolean nonBlocking;

        public CustomizedThreadFactory(boolean daemon, int priority, boolean nonBlocking) {
            super("default-eventLoopGroup", daemon, priority);
            this.nonBlocking = nonBlocking;
        }

        @Override
        @SuppressWarnings("InstantiatingAThreadWithDefaultRunMethod")
        protected Thread newThread(Runnable r, String name) {
            if (nonBlocking) {
                return new NonBlockingFastThreadLocalThread(threadGroup, r, name);
            } else {
                return super.newThread(r, name);
            }
        }
    }

    private static final class NonBlockingFastThreadLocalThread extends FastThreadLocalThread implements NonBlocking {
        public NonBlockingFastThreadLocalThread(ThreadGroup group, Runnable target, String name) {
            super(group, target, name);
        }
    }
}
