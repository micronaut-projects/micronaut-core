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
package io.micronaut.http.server.netty;

import java.util.OptionalInt;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

import javax.inject.Singleton;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
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
class NioEventLoopGroupFactory implements EventLoopGroupFactory {

    private static NioEventLoopGroup setIoRatio(NioEventLoopGroup group, OptionalInt ioRatio) {
        ioRatio.ifPresent(group::setIoRatio);
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
    public EventLoopGroup createEventLoopGroup(int threads, OptionalInt ioRatio) {
        return setIoRatio(new NioEventLoopGroup(threads), ioRatio);
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
    public EventLoopGroup createEventLoopGroup(int threads, ThreadFactory threadFactory, OptionalInt ioRatio) {
        return setIoRatio(new NioEventLoopGroup(threads, threadFactory), ioRatio);
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
    public EventLoopGroup createEventLoopGroup(int threads, Executor executor, OptionalInt ioRatio) {
        return setIoRatio(new NioEventLoopGroup(threads, executor), ioRatio);
    }

    /**
     * Creates a default NioEventLoopGroup.
     * 
     * @param ioRatio The io ratio.
     * @return A NioEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(OptionalInt ioRatio) {
        return setIoRatio(new NioEventLoopGroup(), ioRatio);
    }

    /**
     * Returns the server channel class.
     * 
     * @return NioServerSocketChannel.
     */
    public Class<? extends ServerSocketChannel> serverSocketChannelClass() {
        return NioServerSocketChannel.class;
    }
}
