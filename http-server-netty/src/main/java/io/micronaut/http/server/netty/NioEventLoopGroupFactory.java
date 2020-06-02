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
package io.micronaut.http.server.netty;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

import javax.annotation.Nullable;
import javax.inject.Singleton;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * Factory for NioEventLoopGroup.
 *
 * @author croudet
 */
@Singleton
@Internal
@Requires(missingBeans = { EpollEventLoopGroupFactory.class, KQueueEventLoopGroupFactory.class })
public class NioEventLoopGroupFactory implements EventLoopGroupFactory {

    private static NioEventLoopGroup withIoRatio(NioEventLoopGroup group, @Nullable Integer ioRatio) {
        if (ioRatio != null) {
            group.setIoRatio(ioRatio);
        }
        return group;
    }

    /**
     * Creates a NioEventLoopGroup.
     *
     * @param threads The number of threads to use.
     * @param ioRatio The io ratio.
     * @return A NioEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(int threads, @Nullable Integer ioRatio) {
        return withIoRatio(new NioEventLoopGroup(threads), ioRatio);
    }

    /**
     * Creates a NioEventLoopGroup.
     *
     * @param threads       The number of threads to use.
     * @param threadFactory The thread factory.
     * @param ioRatio       The io ratio.
     * @return A NioEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(int threads, ThreadFactory threadFactory, @Nullable Integer ioRatio) {
        return withIoRatio(new NioEventLoopGroup(threads, threadFactory), ioRatio);
    }

    /**
     * Creates a NioEventLoopGroup.
     *
     * @param threads  The number of threads to use.
     * @param executor An Executor.
     * @param ioRatio  The io ratio.
     * @return A NioEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(int threads, Executor executor, @Nullable Integer ioRatio) {
        return withIoRatio(new NioEventLoopGroup(threads, executor), ioRatio);
    }

    /**
     * Creates a default NioEventLoopGroup.
     *
     * @param ioRatio The io ratio.
     * @return A NioEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(@Nullable Integer ioRatio) {
        return withIoRatio(new NioEventLoopGroup(), ioRatio);
    }

    /**
     * Returns the server channel class.
     *
     * @return NioServerSocketChannel.
     */
    @Override
    public Class<? extends ServerSocketChannel> serverSocketChannelClass() {
        return NioServerSocketChannel.class;
    }
}
