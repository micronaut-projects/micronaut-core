/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.NonNull;
import io.netty.channel.Channel;

/**
 * Interface implemented by users to hook into the pipeline setup of the netty HTTP server.
 * <br>
 * The internal pipeline of our HTTP server is by necessity complex and unstable across versions.
 * While we strive to retain compatibility, please do not make too many assumptions about the
 * Micronaut HTTP server pipeline structure when implementing this interface.
 * <br>
 * Implementations of this interface are scoped to different lifetimes. The root customizers can be
 * added through the {@link Registry}, and live while the server is running. Customizers with
 * narrower scope are created from the root customizers through
 * {@link #specializeForChannel specialization} when new netty channels are created by the
 * server, e.g. when a client connects. These specialized customizers will then receive
 * notifications when a certain step in the pipeline setup is reached, so they can do their own
 * customizations on the channel and the channel pipeline they received in the specialization step.
 *
 * @since 3.6.0
 * @author yawkat
 */
public interface NettyServerCustomizer {
    /**
     * Specialize this customizer for the given channel. In the "boring" case, the customizer will
     * first be specialized for a {@link io.netty.channel.socket.ServerSocketChannel} when the
     * server binds to a configured TCP port, and then to a
     * {@link io.netty.channel.socket.SocketChannel} when a client connects to that port.
     * <br>
     * However, there are a lot of weird cases where the actual behavior might diverge from this.
     * For example, the channel types will different if the server is bound to a unix domain socket
     * ({@link io.netty.channel.unix.ServerDomainSocketChannel} and
     * {@link io.netty.channel.unix.DomainSocketChannel} respectively). In the case of an
     * {@link io.netty.channel.embedded.EmbeddedChannel} used for testing, there might not be a
     * listener channel at all. For HTTP/2, each HTTP stream may get its own channel that is
     * specialized from the overall connection channel. And finally, HTTP/3 support has to use
     * datagram channels instead of the socket-based ones.
     *
     * @param channel The new channel to specialize for.
     * @param role The role (or scope) of the channel.
     * @return The new customizer, or {@code this} if no specialization needs to take place.
     */
    @NonNull
    default NettyServerCustomizer specializeForChannel(@NonNull Channel channel, @NonNull ChannelRole role) {
        return this;
    }

    /**
     * Called when the <i>initial</i> connection pipeline has been built, before any incoming data
     * has been processed.
     */
    default void onInitialPipelineBuilt() {
    }

    /**
     * Called when the "final" stream pipeline has been built for processing http requests. For
     * HTTP/1, this is immediately after {@link #onInitialPipelineBuilt()}. For TLS-based HTTP/2
     * support, where HTTP/1 or HTTP/2 is negotiated through TLS ALPN, this will be called when
     * negotiation is complete. However, for HTTP/2 specifically, this may be changed in the future
     * when we switch to netty channel multiplexing, where each HTTP/2 stream gets its own channel.
     * <br>
     * Another case is h2c with upgrade from HTTP/1. As with ALPN this method will be called after
     * we know whether to use HTTP/1 or HTTP/2. At that point, the first request (that potentially
     * contained the upgrade request) is already "in flight" inside the channel pipeline. It will
     * be forwarded downstream from the upgrade handler after this method is called.
     */
    default void onStreamPipelineBuilt() {
    }

    /**
     * Interface implemented by the HTTP server to register customizers.
     */
    interface Registry {
        /**
         * Register a new customizer with this server. Note that this method must be called before
         * the server is started: When a listener launches, it may only respect the customizers
         * that were registered at the time, and ignore future additions.
         *
         * @param customizer The customizer to register.
         */
        void register(@NonNull NettyServerCustomizer customizer);
    }

    /**
     * Enum to describe the role of the channel passed to
     * {@link #specializeForChannel(Channel, ChannelRole)}.
     */
    enum ChannelRole {
        /**
         * The channel is a "listener" channel, e.g. a
         * {@link io.netty.channel.socket.ServerSocketChannel} representing a TCP listener the
         * server is bound to.
         */
        LISTENER,
        /**
         * The channel is a connection channel, e.g. a
         * {@link io.netty.channel.socket.SocketChannel}, representing an HTTP connection.
         */
        CONNECTION,
        /**
         * The channel is a channel representing an individual HTTP2 stream.
         * <p>
         * Note: As of 4.5.0, there is no separate channel for each request anymore for performance
         * reasons. You can revert to the old behavior using the
         * {@code micronaut.server.netty.legacy-multiplex-handlers=true} configuration property.
         */
        REQUEST_STREAM,
        /**
         * The channel is a channel representing an individual HTTP2 stream, created for a push promise.
         * <p>
         * Note: As of 4.5.0, there is no separate channel for each request anymore for performance
         * reasons. You can revert to the old behavior using the
         * {@code micronaut.server.netty.legacy-multiplex-handlers=true} configuration property.
         */
        PUSH_PROMISE_STREAM,
    }
}
