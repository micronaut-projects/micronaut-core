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

@Internal
public record SslContextHolder(
    @Nullable SslContext sslContext,
    @Nullable QuicSslContext quicSslContext
) {
    public void retain() {
        if (sslContext != null) {
            ReferenceCountUtil.retain(sslContext);
        }
        if (quicSslContext != null) {
            ReferenceCountUtil.retain(quicSslContext);
        }
    }

    public void release() {
        if (sslContext != null) {
            ReferenceCountUtil.release(sslContext);
        }
        if (quicSslContext != null) {
            ReferenceCountUtil.release(quicSslContext);
        }
    }
}
