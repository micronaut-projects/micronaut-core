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

import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.reactivex.Single

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Future

@ClientWebSocket("/pojo/chat/{topic}/{username}")
abstract class PojoChatClientWebSocket implements AutoCloseable {

    private String topic
    private String username
    private Collection<Message> replies = new ConcurrentLinkedQueue<>()

    @OnOpen
    void onOpen(String topic, String username) {
        this.topic = topic
        this.username = username
    }

    String getTopic() {
        return topic
    }

    String getUsername() {
        return username
    }

    Collection<Message> getReplies() {
        return replies
    }

    @OnMessage
    void onMessage(
            Message message) {
        System.out.println("Client received message = " + message)
        replies.add(message)
    }

    abstract void send(Message message)

    abstract Future<Message> sendAsync(Message message)

    abstract Single<Message> sendRx(Message message)
}
