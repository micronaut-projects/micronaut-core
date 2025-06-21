/*
 * Copyright 2017-2023 original authors
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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.ssl.SslConfiguration;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.ssl.SslContext;

/**
 * Interface used by the netty HTTP client to construct the SSL context.
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
public interface ClientSslBuilder {
    /**
     * Build the ssl context.
     *
     * @param ssl              The configuration
     * @param versionSelection The HTTP versions to support
     * @return The ssl context
     */
    @NonNull
    SslContext build(SslConfiguration ssl, HttpVersionSelection versionSelection);

    /**
     * Build the ssl context for QUIC.
     *
     * @param ssl The configuration
     * @return The ssl context
     */
    @Experimental
    @NonNull
    default QuicSslContext buildHttp3(SslConfiguration ssl) {
        throw new UnsupportedOperationException("QUIC not supported");
    }
}
