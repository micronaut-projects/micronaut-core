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
package io.micronaut.http.server.netty.websocket.errors;

import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.*;

@ClientWebSocket
public abstract class ErrorsClient implements AutoCloseable {

    private Throwable lastError;
    private CloseReason lastReason;
    private WebSocketSession session;

    @OnOpen
    public void onOpen(WebSocketSession session) {
        this.session = session;
    }

    @OnMessage
    public void onMessage(String message) {
        System.out.println("message = " + message);
    }

    @OnError
    public void onError(Throwable error) {
        this.lastError = error;
    }

    @OnClose
    public void onClose(CloseReason closeReason) {
        this.lastReason = closeReason;
    }

    public abstract void send(String message);

    public Throwable getLastError() {
        return lastError;
    }

    public CloseReason getLastReason() {
        return lastReason;
    }

    public WebSocketSession getSession() {
        return session;
    }
}
