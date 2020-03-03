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
package io.micronaut.http.server.netty.websocket.errors;

import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.ServerWebSocket;

@ServerWebSocket("/ws/timeout/message")
public class TimeoutErrorSocket {

    boolean closed = false;


    @OnMessage
    public void onMessage(String blah) {
        System.out.println("blah = " + blah);
    }

    @OnClose
    public void onClose() {
        this.closed = true;
    }

    public boolean isClosed() {
        return closed;
    }
}

