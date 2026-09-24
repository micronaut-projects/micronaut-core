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
package io.micronaut.http;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.body.CloseableByteBody;
import org.jspecify.annotations.Nullable;

/**
 * A {@code 101 Switching Protocols} response whose connection now carries another protocol,
 * e.g. WebSocket, in both directions: {@link #byteBody()} is what the peer sends after the
 * switch, and {@link #send(CloseableByteBody)} sends bytes to the peer. The connection is
 * closed when either direction ends or fails, or when the response is {@link #close() closed}.
 * <p>The raw HTTP client returns one when a request that allows upgrades is answered with
 * {@code 101}, see {@code RawRequestOptions#isAllowUpgrade()}. A route that returns it relays
 * the switched protocol: the server writes the {@code 101}, streams {@link #byteBody()} to its
 * client and passes the bytes of its client to {@link #send(CloseableByteBody)}.
 *
 * @param <B> The object body type, always empty
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface UpgradedHttpResponse<B> extends ByteBodyHttpResponse<B> {

    /**
     * Send bytes to the peer after the switch. The ownership of the body transfers to the
     * connection, which closes it when it is sent or when the connection is closed. Can be
     * called once.
     *
     * @param outbound The bytes to send
     */
    void send(CloseableByteBody outbound);

    /**
     * @return The protocol the connection switched to, the value of the {@code Upgrade} header
     * of the response
     */
    @Nullable
    String getProtocol();

    /**
     * Find the upgraded response a response carries: the response itself, or the one it wraps
     * as a {@link HttpResponseWrapper} or as the {@link MutableHttpResponse} of an upgraded
     * response, as long as no wrapper replaced the body.
     *
     * @param response The response
     * @return The upgraded response, or {@code null} if the response does not carry one
     */
    @Nullable
    static UpgradedHttpResponse<?> unwrap(HttpResponse<?> response) {
        HttpResponse<?> current = response;
        while (current != null) {
            if (current instanceof UpgradedHttpResponse<?> upgraded) {
                return upgraded;
            } else if (current instanceof DefaultMutableByteBodyHttpResponse<?> mutable) {
                if (!mutable.hasByteBody()) {
                    return null;
                }
                current = mutable.original();
            } else if (current instanceof HttpResponseWrapper<?> wrapper) {
                if (wrapper.getBody().isPresent()) {
                    return null;
                }
                current = wrapper.getDelegate();
            } else {
                return null;
            }
        }
        return null;
    }
}
