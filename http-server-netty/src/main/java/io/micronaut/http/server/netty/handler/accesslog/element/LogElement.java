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

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpHeaders;

/**
 * Represents a http request or response element.
 *
 * @author croudet
 * @since 2.0
 */
public interface LogElement {
    /**
     * Events.
     */
    enum Event {
        ON_REQUEST_HEADERS, ON_RESPONSE_HEADERS, ON_RESPONSE_WRITE, ON_LAST_RESPONSE_WRITE;

        public static final Set<Event> REQUEST_HEADERS_EVENTS = Collections.unmodifiableSet(EnumSet.of(Event.ON_REQUEST_HEADERS));
        public static final Set<Event> RESPONSE_HEADERS_EVENTS = Collections.unmodifiableSet(EnumSet.of(Event.ON_RESPONSE_HEADERS));
    }

    /**
     * The sets of events that this log element must process. Empty for ConstantElement.
     * @return A list of events.
     */
    Set<Event> events();

    /**
     * Reset the computed value.
     */
    default void reset() {

    }

    /**
     * Responds to an ON_REQUEST_HEADERS event.
     * Also used for ConstantElement with all parameters as null.
     *
     * @param channel The socket channel.
     * @param method The http method.
     * @param headers The request headers.
     * @param uri The request uri.
     * @param protocol The request protocol.
     * @return The processed value.
     */
    default String onRequestHeaders(SocketChannel channel, String method, HttpHeaders headers, String uri, String protocol) {
        return ConstantElement.UNKNOWN_VALUE;
    }

    /**
     * Responds to an ON_RESPONSE_HEADERS event.
     *
     * @param ctx The ChannelHandlerContext.
     * @param headers The response headers.
     * @param status The response status.
     * @return The processed value.
     */
    default String onResponseHeaders(ChannelHandlerContext ctx, HttpHeaders headers, String status) {
        return ConstantElement.UNKNOWN_VALUE;
    }

    /**
     * Responds to an ON_RESPONSE_WRITE event.
     *
     * @param bytesSent The number of bytes sent.
     */
    default void onResponseWrite(int bytesSent) {

    }

    /**
     * Responds to an ON_LAST_RESPONSE_WRITE event.
     *
     * @param bytesSent The number of bytes sent.
     * @return The processed value.
     */
    default String onLastResponseWrite(int bytesSent) {
        return ConstantElement.UNKNOWN_VALUE;
    }

    /**
     * Copy this log element when it is not stateless.
     * @return A copy of this log element.
     */
    LogElement copy();
}
