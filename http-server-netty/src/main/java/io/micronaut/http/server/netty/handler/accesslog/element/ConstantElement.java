/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty.handler.accesslog.element;

import java.util.EnumSet;
import java.util.Set;

import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpHeaders;

/**
 * ConstantElement LogElement. Represents a fixed value.
 *
 * @author croudet
 * @since 2.0
 */
class ConstantElement implements LogElement {

    /**
     * The unknown value: '-'
     */
    public static final String UNKNOWN_VALUE = "-";

    /**
     * The unknown LogElement.
     */
    public static final ConstantElement UNKNOWN = new ConstantElement(UNKNOWN_VALUE);

    private static final Set<Event> EVENTS = EnumSet.noneOf(Event.class);

    private final String value;

    /**
     * Creates a constant LogElement.
     *
     * @param value The constant value.
     */
    ConstantElement(final String value) {
        this.value = value;
    }

    @Override
    public String onRequestHeaders(SocketChannel channel, String method, HttpHeaders headers, String uri, String protocol) {
        return value;
    }

    @Override
    public Set<Event> events() {
        return EVENTS;
    }

    @Override
    public LogElement copy() {
        return this;
    }

    @Override
    public String toString() {
        return value.replace("%", "%%");
    }

}
