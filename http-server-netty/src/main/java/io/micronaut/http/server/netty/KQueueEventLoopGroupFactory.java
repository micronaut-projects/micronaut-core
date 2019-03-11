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
import io.micronaut.core.util.StringUtils;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.socket.ServerSocketChannel;

/**
 * Factory for KQueueEventLoopGroup.
 * 
 * @author croudet
 */
@Singleton
@Requires(property = "micronaut.server.netty.use-native-transport", value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
@Requires(classes = KQueue.class, condition = KQueueAvailabilityCondition.class)
@Internal
class KQueueEventLoopGroupFactory implements EventLoopGroupFactory {

    private static KQueueEventLoopGroup setIoRatio(KQueueEventLoopGroup group, OptionalInt ioRatio) {
        ioRatio.ifPresent(group::setIoRatio);
        return group;
    }

    /**
     * Creates a KQueueEventLoopGroup.
     * 
     * @param threads The number of threads to use.
     * @param ioRatio The io ratio.
     * @return A KQueueEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(int threads, OptionalInt ioRatio) {
        return setIoRatio(new KQueueEventLoopGroup(threads), ioRatio);
    }

    /**
     * Creates a KQueueEventLoopGroup.
     * 
     * @param threads       The number of threads to use.
     * @param threadFactory The thread factory.
     * @param ioRatio       The io ratio.
     * @return A KQueueEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(int threads, ThreadFactory threadFactory, OptionalInt ioRatio) {
        return setIoRatio(new KQueueEventLoopGroup(threads, threadFactory), ioRatio);
    }

    /**
     * Creates a KQueueEventLoopGroup.
     * 
     * @param threads  The number of threads to use.
     * @param executor An Executor.
     * @param ioRatio  The io ratio.
     * @return A KQueueEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(int threads, Executor executor, OptionalInt ioRatio) {
        return setIoRatio(new KQueueEventLoopGroup(threads, executor), ioRatio);
    }

    /**
     * Creates a default KQueueEventLoopGroup.
     * 
     * @param ioRatio The io ratio.
     * @return A KQueueEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(OptionalInt ioRatio) {
        return setIoRatio(new KQueueEventLoopGroup(), ioRatio);
    }

    /**
     * Returns the server channel class.
     * 
     * @return KQueueServerSocketChannel.
     */
    public Class<? extends ServerSocketChannel> serverSocketChannelClass() {
        return KQueueServerSocketChannel.class;
    }
}
