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
package io.micronaut.http.server.netty.handler.accesslog.element;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The %h and %v elements run on the event loop and must not do a blocking reverse DNS lookup.
 */
class HostElementNoReverseLookupTest {

    @Test
    void remoteHostIsTheIpLiteral() throws Exception {
        // an address without a host name, like the remote address of an accepted connection
        InetSocketAddress address = new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), 8080);
        ConnectionMetadata metadata = metadata(address);
        assertEquals("127.0.0.1", RemoteHostElement.INSTANCE.onRequestHeaders(metadata, "GET", null, "/", "HTTP/1.1"));
        assertEquals("127.0.0.1", LocalHostElement.INSTANCE.onRequestHeaders(metadata, "GET", null, "/", "HTTP/1.1"));
    }

    private static ConnectionMetadata metadata(SocketAddress address) {
        return new ConnectionMetadata() {
            @Override
            public Optional<SocketAddress> localAddress() {
                return Optional.of(address);
            }

            @Override
            public Optional<SocketAddress> remoteAddress() {
                return Optional.of(address);
            }
        };
    }
}
