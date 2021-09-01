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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArgumentUtils;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/**
 * Factory for EventLoopGroup.
 *
 * @author croudet
 * @author graemerocher
 * @since 1.2.0
 */
public interface EventLoopGroupFactory {
    /**
     * Qualifier used to resolve the native factory.
     */
    String NATIVE = "native";

    /**
     * @return Is this a native factory.
     */
    default boolean isNative() {
        return false;
    }

    /**
     * Creates an EventLoopGroup.
     *
     * @param threads  The number of threads to use.
     * @param executor An Executor.
     * @param ioRatio  The io ratio.
     * @return An EventLoopGroup.
     */
    EventLoopGroup createEventLoopGroup(
            int threads,
            Executor executor,
            @Nullable Integer ioRatio
    );

    /**
     * Create an event loop group for the given configuration and thread factory.
     * @param configuration The configuration
     * @param threadFactory The thread factory
     * @return The event loop group
     */
    default EventLoopGroup createEventLoopGroup(
            EventLoopGroupConfiguration configuration, ThreadFactory threadFactory) {
        ArgumentUtils.requireNonNull("configuration", configuration);
        ArgumentUtils.requireNonNull("threadFactory", threadFactory);
        return createEventLoopGroup(
                configuration.getNumThreads(),
                threadFactory,
                configuration.getIoRatio().orElse(null)
        );
    }

    /**
     * Creates an EventLoopGroup.
     *
     * @param threads       The number of threads to use.
     * @param threadFactory The thread factory.
     * @param ioRatio       The io ratio.
     * @return An EventLoopGroup.
     */
    EventLoopGroup createEventLoopGroup(
            int threads,
            @Nullable ThreadFactory threadFactory,
            @Nullable Integer ioRatio
    );

    /**
     * Creates an EventLoopGroup.
     *
     * @param threads The number of threads to use.
     * @param ioRatio The io ratio.
     * @return An EventLoopGroup.
     */
    default EventLoopGroup createEventLoopGroup(int threads, @Nullable Integer ioRatio) {
        return createEventLoopGroup(threads, (ThreadFactory) null, ioRatio);
    }

    /**
     * Returns the server channel class.
     *
     * @return A ServerChannelClass.
     */
    @NonNull Class<? extends ServerSocketChannel> serverSocketChannelClass();

    /**
     * Returns the server channel class.
     *
     * @param configuration The configuration
     * @return A ServerSocketChannel class.
     */
    default @NonNull Class<? extends ServerSocketChannel> serverSocketChannelClass(@Nullable EventLoopGroupConfiguration configuration) {
        return serverSocketChannelClass();
    }

    /**
     * Returns the server channel class instance.
     *
     * @param configuration The configuration
     * @return A ServerSocketChannel instance.
     */
    default @NonNull ServerSocketChannel serverSocketChannelInstance(@Nullable EventLoopGroupConfiguration configuration) {
        try {
            return serverSocketChannelClass(configuration).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Cannot instantiate server socket channel instance");
        }
    }

    /**
     * Returns the client channel class.
     *
     * @param configuration The configuration
     * @return A SocketChannel class.
     */
    @NonNull Class<? extends SocketChannel> clientSocketChannelClass(@Nullable EventLoopGroupConfiguration configuration);

    /**
     * Returns the client channel class instance.
     *
     * @param configuration The configuration
     * @return A SocketChannel instance.
     */
    default @NonNull SocketChannel clientSocketChannelInstance(@Nullable EventLoopGroupConfiguration configuration) {
        try {
            return clientSocketChannelClass(configuration).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Cannot instantiate server socket channel instance");
        }
    }

}
