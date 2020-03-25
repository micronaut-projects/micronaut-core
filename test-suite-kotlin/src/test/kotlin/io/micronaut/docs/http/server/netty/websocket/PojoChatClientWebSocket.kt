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
abstract class PojoChatClientWebSocket : AutoCloseable {

    var topic: String? = null
        private set
    var username: String? = null
        private set
    private val replies = ConcurrentLinkedQueue<Message>()

    @OnOpen
    fun onOpen(topic: String, username: String) {
        this.topic = topic
        this.username = username
    }

    fun getReplies(): Collection<Message> {
        return replies
    }

    @OnMessage
    fun onMessage(
            message: Message) {
        println("Client received message = $message")
        replies.add(message)
    }

    abstract fun send(message: Message)

    abstract fun sendAsync(message: Message): Future<Message>

    abstract fun sendRx(message: Message): Single<Message>
}
