package io.micronaut.http.server.netty.websocket;

import io.micronaut.http.HttpRequest;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.ClientWebSocket;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;

@ClientWebSocket("/charity")
public class QueryParamClientWebSocket {

    private WebSocketSession session;
    private HttpRequest request;
    private String dinner;

    @OnOpen
    public void onOpen(String dinner, WebSocketSession session, HttpRequest request) { // <3>
        this.session = session;
        this.request = request;
        this.dinner = dinner;
    }

    @OnMessage
    public void onMessage(
            String message) {
    }

    public WebSocketSession getSession() {
        return session;
    }

    public HttpRequest getRequest() {
        return request;
    }

    public String getDinner() {
        return dinner;
    }
}
