/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.TypeHint;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.NettyRuntime;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.internal.SystemPropertyUtil;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.concurrent.ThreadFactory;

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
   accessType = {TypeHint.AccessType.ALL_DECLARED_CONSTRUCTORS, TypeHint.AccessType.ALL_DECLARED_FIELDS}
)
@BootstrapContextCompatible
public class NettyThreadFactory {

    /**
     * Name for Netty thread factory.
     */
    public static final String NAME = "netty";

    /**
     * Constant with the default threads in the event loop.
     */
    public static final int DEFAULT_EVENT_LOOP_THREADS = Math.max(1, SystemPropertyUtil.getInt("io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 2));

    /**
     * Constructs the default thread factory used by the HTTP client.
     *
     * @return The thread factory
     */
    @Singleton
    @Named(NAME)
    @BootstrapContextCompatible
    protected ThreadFactory nettyThreadFactory() {
        return new DefaultThreadFactory(NioEventLoopGroup.class);
    }
}
