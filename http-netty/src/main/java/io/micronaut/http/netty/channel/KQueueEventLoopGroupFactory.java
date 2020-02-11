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
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/**
 * Factory for KQueueEventLoopGroup.
 * 
 * @author croudet
 */
@Singleton
@Internal
@Requires(classes = KQueue.class, condition = KQueueAvailabilityCondition.class)
@Named(EventLoopGroupFactory.NATIVE)
@BootstrapContextCompatible
class KQueueEventLoopGroupFactory implements EventLoopGroupFactory {

    /**
     * Creates a KQueueEventLoopGroup.
     *
     * @param threads The number of threads to use.
     * @param ioRatio The io ratio.
     * @return A KQueueEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(int threads, @Nullable Integer ioRatio) {
        return withIoRatio(new KQueueEventLoopGroup(threads), ioRatio);
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
    public EventLoopGroup createEventLoopGroup(int threads, ThreadFactory threadFactory, @Nullable Integer ioRatio) {
        return withIoRatio(new KQueueEventLoopGroup(threads, threadFactory), ioRatio);
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
    public EventLoopGroup createEventLoopGroup(int threads, Executor executor, @Nullable Integer ioRatio) {
        return withIoRatio(new KQueueEventLoopGroup(threads, executor), ioRatio);
    }

    /**
     * Creates a default KQueueEventLoopGroup.
     *
     * @param ioRatio The io ratio.
     * @return A KQueueEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(@Nullable Integer ioRatio) {
        return withIoRatio(new KQueueEventLoopGroup(), ioRatio);
    }

    @Override
    public boolean isNative() {
        return true;
    }

    /**
     * Returns the server channel class.
     *
     * @return KQueueServerSocketChannel.
     */
    public Class<? extends ServerSocketChannel> serverSocketChannelClass() {
        return KQueueServerSocketChannel.class;
    }

    @NonNull
    @Override
    public Class<? extends SocketChannel> clientSocketChannelClass(@Nullable EventLoopGroupConfiguration configuration) {
        return KQueueSocketChannel.class;
    }

    private static KQueueEventLoopGroup withIoRatio(KQueueEventLoopGroup group, @Nullable Integer ioRatio) {
        if (ioRatio != null) {
            group.setIoRatio(ioRatio);
        }
        return group;
    }
}
