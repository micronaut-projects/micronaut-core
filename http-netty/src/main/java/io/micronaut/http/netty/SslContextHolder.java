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
package io.micronaut.http.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.ssl.SslContext;
import io.netty.util.ReferenceCountUtil;

/**
 * Holder for Netty SSL context instances for TCP ({@link SslContext}) and QUIC/HTTP3
 * ({@link QuicSslContext}). Manages Netty reference counting via {@link #retain()} and
 * {@link #release()} to ensure contexts are safely shared and swapped.
 *
 * @param sslContext TCP ssl context
 * @param quicSslContextObject QUIC ssl context. Type Object to avoid native-image issues when the
 *                             class is missing
 * @author Jonas Konrad
 * @since 4.10.0
 */
@Internal
public record SslContextHolder(
    @Nullable SslContext sslContext,
    @Nullable Object quicSslContextObject
) {
    /**
     * Retain the underlying Netty contexts for safe reuse.
     */
    public void retain() {
        if (sslContext != null) {
            ReferenceCountUtil.retain(sslContext);
        }
        if (quicSslContextObject != null) {
            ReferenceCountUtil.retain(quicSslContextObject);
        }
    }

    /**
     * Release the underlying Netty contexts when no longer needed.
     */
    public void release() {
        if (sslContext != null) {
            ReferenceCountUtil.release(sslContext);
        }
        if (quicSslContextObject != null) {
            ReferenceCountUtil.release(quicSslContextObject);
        }
    }

    public QuicSslContext quicSslContext() {
        return (QuicSslContext) quicSslContextObject;
    }
}
