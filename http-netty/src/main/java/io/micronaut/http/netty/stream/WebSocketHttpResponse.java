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
 */
public interface WebSocketHttpResponse extends HttpResponse, Processor<WebSocketFrame, WebSocketFrame> {
    /**
     * Get the handshaker factory to use to reconfigure the channel.
     *
     * @return The handshaker factory.
     */
    WebSocketServerHandshakerFactory handshakerFactory();
}
