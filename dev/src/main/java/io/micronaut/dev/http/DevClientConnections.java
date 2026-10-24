/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.dev.http;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;
import io.micronaut.context.env.DevelopmentActive;
import io.micronaut.core.annotation.Internal;
import io.micronaut.dev.DevRuntime;
import io.micronaut.dev.loader.GenerationClassLoader;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.ServiceHttpClientConfiguration;
import io.micronaut.http.client.netty.RetainedClientConnections;
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration;
import io.micronaut.http.netty.configuration.NettyGlobalConfiguration;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoop;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Keeps the idle connections of the HTTP clients across generations: the TCP connection, and the TLS session of a
 * secure one, are made once, and each generation's clients, with their own codecs, filters and pipeline handlers, take
 * them back. A client shutting down with its generation offers its idle HTTP/1.1 connections, after taking its own
 * handlers off them, and the next generation's client of the same configuration pools them before its first request.
 *
 * <p>Only connections on the {@link DevEventLoopGroups retained event loops} are kept: the others stop with their
 * group. A client whose configuration, TLS builders or channel factory are of the application's classes keeps none;
 * nor does one with a customizer, a proxy, a capture or certificate providers, whose handlers or TLS sessions are of
 * its generation. HTTP/2 and HTTP/3 connections are not kept: their multiplexing handlers and streams belong to the
 * client that opened them.</p>
 *
 * <p>Retained across restarts until a change under the configuration of the clients, of TLS, or of the event loops:
 * the connections kept are then closed, and the next generation's clients connect again. A connection no client took
 * back during the generation after the one that kept it is closed as that generation stops, as is one the remote
 * closes.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Singleton
@Retain(invalidatedBy = {
    DefaultHttpClientConfiguration.PREFIX,
    ServiceHttpClientConfiguration.PREFIX,
    DevClientConnections.SSL_PREFIX,
    EventLoopGroupConfiguration.EVENT_LOOPS,
    NettyGlobalConfiguration.PREFIX
})
@Requires(classes = RetainedClientConnections.class)
@Requires(beans = DevEventLoopGroups.class)
@DevelopmentActive
@Requires(beans = DevRuntime.class)
final class DevClientConnections implements RetainedClientConnections {

    /**
     * The TLS configuration a client's may fall back on.
     */
    static final String SSL_PREFIX = "micronaut.ssl";

    private static final Logger LOG = LoggerFactory.getLogger(DevClientConnections.class);

    private final DevEventLoopGroups groups;
    private final List<Kept> kept = new ArrayList<>();
    /**
     * The number of generations that ended while connections were kept: a connection kept as one stopped is stamped
     * with the count, and closed when the next generation ends without taking it back.
     */
    private long generation;
    private boolean closed;

    DevClientConnections(DevEventLoopGroups groups) {
        this.groups = groups;
    }

    @Override
    public boolean isRetainable(List<?> components) {
        for (Object component : components) {
            if (component != null && component.getClass().getClassLoader() instanceof GenerationClassLoader) {
                // the client would hand the next generation connections made by the application's code
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean retain(String client, Object remote, Channel channel) {
        Kept entry;
        synchronized (this) {
            if (closed || !channel.isActive() || !groups.retains(channel.eventLoop())) {
                return false;
            }
            entry = new Kept(client, remote, channel, generation);
            kept.add(entry);
        }
        channel.closeFuture().addListener(entry.onClose);
        LOG.debug("Connection {} kept across the restart", channel);
        return true;
    }

    @Override
    public synchronized List<Map.Entry<Object, EventLoop>> retained(String client) {
        List<Map.Entry<Object, EventLoop>> retained = new ArrayList<>();
        for (Kept entry : kept) {
            if (entry.client.equals(client)) {
                retained.add(new AbstractMap.SimpleImmutableEntry<>(entry.remote, entry.channel.eventLoop()));
            }
        }
        return retained;
    }

    @Override
    public @Nullable Channel adopt(String client, Object remote, EventLoop eventLoop) {
        Kept adopted = null;
        synchronized (this) {
            for (Iterator<Kept> iterator = kept.iterator(); iterator.hasNext(); ) {
                Kept entry = iterator.next();
                if (entry.client.equals(client) && entry.remote.equals(remote) && entry.channel.eventLoop() == eventLoop) {
                    iterator.remove();
                    adopted = entry;
                    break;
                }
            }
        }
        if (adopted == null) {
            return null;
        }
        adopted.channel.closeFuture().removeListener(adopted.onClose);
        LOG.debug("Connection {} taken back after the restart", adopted.channel);
        return adopted.channel;
    }

    /**
     * Called as a generation stops: the connections kept before it started that no client of it took back are closed.
     */
    void generationEnded() {
        List<Kept> unclaimed = new ArrayList<>();
        synchronized (this) {
            for (Iterator<Kept> iterator = kept.iterator(); iterator.hasNext(); ) {
                Kept entry = iterator.next();
                if (entry.generation < generation) {
                    iterator.remove();
                    unclaimed.add(entry);
                }
            }
            generation++;
        }
        unclaimed.forEach(entry -> entry.channel.close());
    }

    /**
     * Whether a connection is kept.
     *
     * @return The number of connections kept
     */
    synchronized int size() {
        return kept.size();
    }

    /**
     * Closes the connections kept: a change released them, or the development runtime closes.
     */
    @PreDestroy
    void close() {
        List<Kept> all;
        synchronized (this) {
            closed = true;
            all = new ArrayList<>(kept);
            kept.clear();
        }
        all.forEach(entry -> entry.channel.close());
    }

    private synchronized void remove(Kept entry) {
        kept.remove(entry);
    }

    /**
     * A connection kept for the clients of a configuration, to a remote.
     */
    private final class Kept {
        final String client;
        final Object remote;
        final Channel channel;
        final long generation;
        /**
         * Forgets the connection when the remote, or its event loop, closes it.
         */
        final ChannelFutureListener onClose = future -> remove(this);

        Kept(String client, Object remote, Channel channel, long generation) {
            this.client = client;
            this.remote = remote;
            this.channel = channel;
            this.generation = generation;
        }
    }
}
