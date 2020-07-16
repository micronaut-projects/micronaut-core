/*
 * Copyright 2017-2020 original authors
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

import java.util.Set;

import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpHeaders;

/**
 * LocalHostElement LogElement. The local host.
 *
 * @author croudet
 * @since 2.0
 */
final class LocalHostElement implements LogElement {
    /**
     * The local host marker.
     */
    public static final String LOCAL_HOST = "v";

    /**
     * The LocalHostElement instance.
     */
    static final LocalHostElement INSTANCE = new LocalHostElement();

    private LocalHostElement() {

    }

    @Override
    public Set<Event> events() {
        return Event.REQUEST_HEADERS_EVENTS;
    }

    @Override
    public String onRequestHeaders(SocketChannel channel, String method, HttpHeaders headers, String uri, String protocol) {
        return channel.localAddress().getAddress().getHostName();
    }

    @Override
    public LogElement copy() {
        return this;
    }

    @Override
    public String toString() {
        return '%' + LOCAL_HOST;
    }
}
