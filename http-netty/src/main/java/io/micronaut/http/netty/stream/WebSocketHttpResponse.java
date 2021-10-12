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
package io.micronaut.http.netty.stream;

import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import org.reactivestreams.Processor;

/**
 * Combines {@link HttpResponse} and {@link Processor}
 * into one message. So it represents an http response with a processor that can handle
 * a WebSocket.
 *
 * This is only used for server side responses. For client side websocket requests, it's
 * better to configure the reactive streams pipeline directly.
 *
 * @author jroper
 * @author Graeme Rocher
 */
public interface WebSocketHttpResponse extends HttpResponse, Processor<WebSocketFrame, WebSocketFrame> {
    /**
     * Get the handshaker factory to use to reconfigure the channel.
     *
     * @return The handshaker factory.
     */
    WebSocketServerHandshakerFactory handshakerFactory();
}
