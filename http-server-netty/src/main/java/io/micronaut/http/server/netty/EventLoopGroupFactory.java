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

import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;

import javax.annotation.Nullable;

/**
 * Factory for EventLoopGroup.
 * 
 * @author croudet
 */
public interface EventLoopGroupFactory {

    /**
     * Creates an EventLoopGroup.
     * 
     * @param threads  The number of threads to use.
     * @param executor An Executor.
     * @param ioRatio  The io ratio.
     * @return An EventLoopGroup.
     */
    EventLoopGroup createEventLoopGroup(int threads, Executor executor, @Nullable Integer ioRatio);

    /**
     * Creates an EventLoopGroup.
     * 
     * @param threads The number of threads to use.
     * @param ioRatio The io ratio.
     * @return An EventLoopGroup.
     */
    EventLoopGroup createEventLoopGroup(int threads, @Nullable Integer ioRatio);

    /**
     * Creates an EventLoopGroup.
     * 
     * @param threads       The number of threads to use.
     * @param threadFactory The thread factory.
     * @param ioRatio       The io ratio.
     * @return An EventLoopGroup.
     */
    EventLoopGroup createEventLoopGroup(int threads, ThreadFactory threadFactory, @Nullable Integer ioRatio);

    /**
     * Creates a default EventLoopGroup.
     * 
     * @param ioRatio The io ratio.
     * @return An EventLoopGroup.
     */
    EventLoopGroup createEventLoopGroup(@Nullable Integer ioRatio);

    /**
     * Returns the server channel class.
     * 
     * @return A ServerChannelClass.
     */
    Class<? extends ServerSocketChannel> serverSocketChannelClass();
}
