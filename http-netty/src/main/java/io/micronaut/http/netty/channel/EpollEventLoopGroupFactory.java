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

import static io.micronaut.http.netty.channel.DefaultEventLoopGroupFactory.processChannelOptionValue;

import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import javax.inject.Named;
import javax.inject.Singleton;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.unix.UnixChannelOption;

/**
 * Factory for EpollEventLoopGroup.
 *
 * @author croudet
 */
@Singleton
@Requires(classes = Epoll.class, condition = EpollAvailabilityCondition.class)
@Internal
@Named(EventLoopGroupFactory.NATIVE)
@BootstrapContextCompatible
public class EpollEventLoopGroupFactory implements EventLoopGroupFactory {

    static {
        // force loading the class for the wrapChannelOption to work
        EpollChannelOption.EPOLL_MODE.name();
    }

    /**
     * Creates an EpollEventLoopGroup.
     *
     * @param threads The number of threads to use.
     * @param ioRatio The io ratio.
     * @return An EpollEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(int threads, @Nullable Integer ioRatio) {
        return new EpollEventLoopGroup(threads);
    }

    /**
     * Creates an EpollEventLoopGroup.
     *
     * @param threads       The number of threads to use.
     * @param threadFactory The thread factory.
     * @param ioRatio       The io ratio.
     * @return An EpollEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(int threads, ThreadFactory threadFactory, @Nullable Integer ioRatio) {
        return new EpollEventLoopGroup(threads, threadFactory);
    }

    /**
     * Creates an EpollEventLoopGroup.
     *
     * @param threads  The number of threads to use.
     * @param executor An Executor.
     * @param ioRatio  The io ratio.
     * @return An EpollEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(int threads, Executor executor, @Nullable Integer ioRatio) {
        return new EpollEventLoopGroup(threads, executor);
    }

    /**
     * Creates a default EpollEventLoopGroup.
     *
     * @param ioRatio The io ratio.
     * @return An EpollEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(@Nullable Integer ioRatio) {
        return new EpollEventLoopGroup();
    }

    /**
     * Returns the server channel class.
     *
     * @return EpollServerSocketChannel.
     */
    @Override
    public Class<? extends ServerSocketChannel> serverSocketChannelClass() {
        return EpollServerSocketChannel.class;
    }

    @NonNull
    @Override
    public Class<? extends SocketChannel> clientSocketChannelClass(@Nullable EventLoopGroupConfiguration configuration) {
        return EpollSocketChannel.class;
    }

    @Override
    public boolean isNative() {
        return true;
    }

    @Override
    public Entry<ChannelOption, Object> processChannelOption(@Nullable EventLoopGroupConfiguration configuration, Entry<ChannelOption, Object> entry, Environment env) {
        final String name = entry.getKey().name();
        final ChannelOption option = DefaultEventLoopGroupFactory.channelOption(name, EpollChannelOption.class, UnixChannelOption.class);
        final Object value = processChannelOptionValue(EpollChannelOption.class, name, entry.getValue(), env);
        return new SimpleEntry<>(option, value);
    }

}
