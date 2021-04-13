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
package io.micronaut.docs.http.server.netty.websocket

// tag::imports[]
import io.micronaut.http.HttpRequest
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.reactivex.Single

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Future
// end::imports[]

// tag::class[]
@ClientWebSocket("/chat/{topic}/{username}") // <1>
abstract class ChatClientWebSocket implements AutoCloseable { // <2>

    private WebSocketSession session
    private HttpRequest request
    private String topic
    private String username
    private Collection<String> replies = new ConcurrentLinkedQueue<>()

    @OnOpen
    void onOpen(String topic, String username,
                WebSocketSession session, HttpRequest request) { // <3>
        this.topic = topic
        this.username = username
        this.session = session
        this.request = request
    }

    String getTopic() {
        topic
    }

    String getUsername() {
        username
    }

    Collection<String> getReplies() {
        replies
    }

    WebSocketSession getSession() {
        session
    }

    HttpRequest getRequest() {
        request
    }

    @OnMessage
    void onMessage(String message) {
        replies << message // <4>
    }

// end::class[]
    abstract void send(String message)

    abstract Future<String> sendAsync(String message)

    abstract Single<String> sendRx(String message)
}
