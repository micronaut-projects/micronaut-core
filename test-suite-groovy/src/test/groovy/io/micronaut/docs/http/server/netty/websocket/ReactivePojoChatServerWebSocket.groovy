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

import io.micronaut.websocket.WebSocketBroadcaster
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import org.reactivestreams.Publisher

import java.util.function.Predicate

@ServerWebSocket("/pojo/chat/{topic}/{username}")
 class ReactivePojoChatServerWebSocket {

    private WebSocketBroadcaster broadcaster

     ReactivePojoChatServerWebSocket(WebSocketBroadcaster broadcaster) {
        this.broadcaster = broadcaster
    }

    @OnOpen
     Publisher<Message> onOpen(String topic, String username, WebSocketSession session) {
        String text = "[" + username + "] Joined!"
        Message message = new Message(text)
        broadcaster.broadcast(message, isValid(topic, session))
    }

    // tag::onmessage[]
    @OnMessage
     Publisher<Message> onMessage(
            String topic,
            String username,
            Message message,
            WebSocketSession session) {

        String text = "[" + username + "] " + message.getText()
        Message newMessage = new Message(text)
        broadcaster.broadcast(newMessage, isValid(topic, session))
    }
    // end::onmessage[]

    @OnClose
     Publisher<Message> onClose(
            String topic,
            String username,
            WebSocketSession session) {

        String text = "[" + username + "] Disconnected!"
        Message message = new Message(text)
        broadcaster.broadcast(message, isValid(topic, session))
    }

    private Predicate<WebSocketSession> isValid(String topic, WebSocketSession session) {
        { s -> s != session && topic.equalsIgnoreCase(s.getUriVariables().get("topic", String.class, null)) }
    }
}
