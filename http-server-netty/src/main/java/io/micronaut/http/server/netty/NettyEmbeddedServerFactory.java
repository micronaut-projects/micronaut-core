/*
 * Copyright 2017-2021 original authors
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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.ssl.ServerSslConfiguration;

/**
 * A factory / strategy interface for creating instances of {@link io.micronaut.http.server.netty.NettyEmbeddedServer}.
 *
 * @author graemerocher
 * @since 3.1.0
 */
public interface NettyEmbeddedServerFactory {

    /**
     * Builds a {@link io.micronaut.http.server.netty.NettyEmbeddedServer} for the given configuration.
     *
     * <p>Note that the returned server instance should be closed gracefully by calling the {@link NettyEmbeddedServer#stop()} method.</p>
     *
     * @param configuration The configuration, never {@code null}
     * @return A {@link io.micronaut.http.server.netty.NettyEmbeddedServer} instance
     */
    @NonNull
    NettyEmbeddedServer build(@NonNull NettyHttpServerConfiguration configuration);

    /**
     * Builds a {@link io.micronaut.http.server.netty.NettyEmbeddedServer} for the given configuration.
     *
     * <p>Note that the returned server instance should be closed gracefully by calling the {@link NettyEmbeddedServer#stop()} method.</p>
     *
     * @param configuration The configuration, never {@code null}
     * @param sslConfiguration The SSL configuration, can be {@code null} if SSL is not required
     * @return A {@link io.micronaut.http.server.netty.NettyEmbeddedServer} instance
     * @since 3.1.4
     */
    @NonNull
    default NettyEmbeddedServer build(@NonNull NettyHttpServerConfiguration configuration, @Nullable ServerSslConfiguration sslConfiguration) {
        return build(configuration, null);
    }
}
