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
package io.micronaut.http.server.netty.handler.accesslog.element

import io.netty.channel.socket.SocketChannel
import spock.lang.Specification

/**
 * The %h and %v elements run on the event loop and must not do a blocking reverse DNS lookup.
 */
class HostElementNoReverseLookupSpec extends Specification {

    void "the host elements log the IP literal"() {
        given:
        // an address without a host name, like the remote address of an accepted connection
        InetSocketAddress address = new InetSocketAddress(InetAddress.getByAddress([127, 0, 0, 1] as byte[]), 8080)
        SocketChannel channel = [remoteAddress: { -> address }, localAddress: { -> address }] as SocketChannel

        expect:
        RemoteHostElement.INSTANCE.onRequestHeaders(channel, "GET", null, "/", "HTTP/1.1") == "127.0.0.1"
        LocalHostElement.INSTANCE.onRequestHeaders(channel, "GET", null, "/", "HTTP/1.1") == "127.0.0.1"
    }
}
