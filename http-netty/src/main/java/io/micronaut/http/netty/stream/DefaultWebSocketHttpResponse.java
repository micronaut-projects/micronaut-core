package io.micronaut.http.netty.stream;

import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * A default WebSocket HTTP response.
 */
public class DefaultWebSocketHttpResponse extends DefaultHttpResponse implements WebSocketHttpResponse {

    private final Processor<WebSocketFrame, WebSocketFrame> processor;
    private final WebSocketServerHandshakerFactory handshakerFactory;

    public DefaultWebSocketHttpResponse(HttpVersion version, HttpResponseStatus status,
                                        Processor<WebSocketFrame, WebSocketFrame> processor,
                                        WebSocketServerHandshakerFactory handshakerFactory) {
        super(version, status);
        this.processor = processor;
        this.handshakerFactory = handshakerFactory;
    }

    public DefaultWebSocketHttpResponse(HttpVersion version, HttpResponseStatus status, boolean validateHeaders,
                                        Processor<WebSocketFrame, WebSocketFrame> processor,
                                        WebSocketServerHandshakerFactory handshakerFactory) {
        super(version, status, validateHeaders);
        this.processor = processor;
        this.handshakerFactory = handshakerFactory;
    }

    @Override
    public WebSocketServerHandshakerFactory handshakerFactory() {
        return handshakerFactory;
    }

    @Override
    public void subscribe(Subscriber<? super WebSocketFrame> subscriber) {
        processor.subscribe(subscriber);
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        processor.onSubscribe(subscription);
    }

    @Override
    public void onNext(WebSocketFrame webSocketFrame) {
        processor.onNext(webSocketFrame);
    }

    @Override
    public void onError(Throwable error) {
        processor.onError(error);
    }

    @Override
    public void onComplete() {
        processor.onComplete();
    }
}
