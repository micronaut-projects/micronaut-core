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
package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;

/**
 * Interface implemented by users to hook into the pipeline setup of the netty HTTP client.
 * <br>
 * The internal pipeline of our HTTP client is by necessity complex and unstable across versions.
 * While we strive to retain compatibility, please do not make too many assumptions about the
 * Micronaut HTTP client pipeline structure when implementing this interface.
 * <br>
 * Implementations of this interface are scoped to different lifetimes. The root customizers can be
 * added through the {@link Registry}, and live while the context is running. Customizers with
 * narrower scope are created from the root customizers through
 * {@link #specializeForChannel specialization} when new netty channels are created by the
 * client, e.g. when a new request is made. These specialized customizers will then receive
 * notifications when a certain step in the pipeline setup is reached, so they can do their own
 * customizations on the channel and the channel pipeline they received in the specialization step.
 *
 * @since 3.6.0
 * @author yawkat
 */
public interface NettyClientCustomizer {
    /**
     * @param channel The new channel to specialize for.
     * @param role The role (or scope) of the channel.
     * @return The new customizer, or {@code this} if no specialization needs to take place.
     */
    @NonNull
    default NettyClientCustomizer specializeForChannel(@NonNull Channel channel, @NonNull ChannelRole role) {
        return this;
    }

    /**
     * @param bootstrap The bootstrap that will be used to connect
     * @return The new customizer, or {@code this} if no specialization needs to take place.
     * @since 4.7.0
     */
    @Experimental
    @NonNull
    default NettyClientCustomizer specializeForBootstrap(@NonNull Bootstrap bootstrap) {
        return this;
    }

    /**
     * Called when the <i>initial</i> connection pipeline has been built, before any incoming data
     * has been processed.
     */
    default void onInitialPipelineBuilt() {
    }

    /**
     * Called when the stream pipeline has been built, after any TLS or HTTP upgrade handshake.
     */
    default void onStreamPipelineBuilt() {
    }

    /**
     * Called when the "final" request pipeline has been built for processing http requests. This
     * is called for each request, potentially multiple times for the same connection if the
     * connection is pooled. In the future, for HTTP2, this may use a new channel for each request
     * stream.
     */
    default void onRequestPipelineBuilt() {
    }

    /**
     * Enum to describe the role of the channel passed to
     * {@link #specializeForChannel(Channel, ChannelRole)}.
     */
    enum ChannelRole {
        /**
         * The channel is a connection channel, e.g. a
         * {@link io.netty.channel.socket.SocketChannel}, representing an HTTP connection.
         */
        CONNECTION,
        /**
         * The channel is a HTTP2 stream channel.
         *
         * @since 4.0.0
         */
        HTTP2_STREAM,
    }

    /**
     * Interface implemented by the HTTP client registry to register customizers.
     */
    interface Registry {
        /**
         * Register a new customizer with this registry.
         *
         * @param customizer The customizer to register.
         */
        void register(@NonNull NettyClientCustomizer customizer);
    }
}
