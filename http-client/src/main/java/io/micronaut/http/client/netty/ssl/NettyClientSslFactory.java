/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.http.client.netty.ssl;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.netty.NettySslContextBuilder;
import jakarta.inject.Singleton;

/**
 * Factory for creating client-side Netty SSL context builders.
 * Used by the Micronaut HTTP client to construct TCP/HTTP and QUIC/HTTP/3
 * SSL contexts based on client configuration.
 *
 * @author Jonas Konrad
 * @since 4.10.0
 */
@Singleton
@BootstrapContextCompatible
public class NettyClientSslFactory {
    /**
     * Create a client-side SSL context builder for the given client configuration.
     * Current implementation delegates to {@link #builder()}. This method allows for
     * experimental client-specific customization.
     *
     * @param configuration client configuration
     * @return a client-mode {@link NettySslContextBuilder}
     */
    @Experimental
    public @NonNull NettySslContextBuilder builder(@NonNull HttpClientConfiguration configuration) {
        return builder();
    }

    /**
     * Create a client-side SSL context builder.
     *
     * @return a client-mode {@link NettySslContextBuilder}
     */
    public @NonNull NettySslContextBuilder builder() {
        return new NettySslContextBuilder(false);
    }
}
