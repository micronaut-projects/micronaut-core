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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpHeaders;

import java.util.HashSet;
import java.util.Set;

/**
 * LogElement for ON_REQUEST_HEADERS and ON_RESPONSE_HEADERS events.
 *
 * @author croudet
 * @since 2.0
 */
abstract class AbstractHttpMessageLogElement implements LogElement {
    protected Set<Event> events;

    /**
     * Process the specified headers.
     * @param headers Http headers.
     * @return The value.
     */
    protected abstract String value(HttpHeaders headers);

    private static final Set<Character> keysToEscape = new HashSet<>(){{
        add('\b');
        add('\n');
        add('\t');
        add('\r');
        add('\\');
        add('"');
    }};

    private static String wrapValue(String value) {
        // Does the value contain a " ? If so must encode it
        if (value == null || ConstantElement.UNKNOWN_VALUE.equals(value) || value.isEmpty()) {
            return ConstantElement.UNKNOWN_VALUE;
        }

        /* Wrap all quotes in double quotes. */
        StringBuilder buffer = new StringBuilder(value.length() + 2);
        int i = 0;
        while (i < value.length()) {
            char currentChar = value.charAt(i);
            if (keysToEscape.contains(currentChar)) {
                buffer.append('\\');
                switch (currentChar) {
                    case '\b' -> buffer.append('b');
                    case '\n' -> buffer.append('n');
                    case '\r' -> buffer.append('r');
                    case '\t' -> buffer.append('t');
                    default -> buffer.append(currentChar);
                }
            } else {
                buffer.append(currentChar);
            }
            i++;
        }
        return buffer.toString();
    }

    @Override
    public Set<Event> events() {
        return events;
    }

    @Override
    public String onRequestHeaders(SocketChannel channel, String method, HttpHeaders headers, String uri, String protocol) {
        if (events.contains(Event.ON_REQUEST_HEADERS)) {
            return wrapValue(value(headers));
        } else {
            return ConstantElement.UNKNOWN_VALUE;
        }
    }

    @Override
    public String onResponseHeaders(ChannelHandlerContext ctx, HttpHeaders headers, String status) {
        if (events.contains(Event.ON_RESPONSE_HEADERS)) {
            return wrapValue(value(headers));
        } else {
            return ConstantElement.UNKNOWN_VALUE;
        }
    }

    @Override
    public LogElement copy() {
        return this;
    }

}
