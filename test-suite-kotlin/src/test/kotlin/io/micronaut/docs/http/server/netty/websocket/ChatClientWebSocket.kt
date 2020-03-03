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
abstract class ChatClientWebSocket : AutoCloseable { // <2>

    var session: WebSocketSession? = null
        private set
    var request: HttpRequest<*>? = null
        private set
    var topic: String? = null
        private set
    var username: String? = null
        private set
    private val replies = ConcurrentLinkedQueue<String>()

    @OnOpen
    fun onOpen(topic: String, username: String, session: WebSocketSession, request: HttpRequest<*>) { // <3>
        this.topic = topic
        this.username = username
        this.session = session
        this.request = request
    }

    fun getReplies(): Collection<String> {
        return replies
    }

    @OnMessage
    fun onMessage(
            message: String) {
        replies.add(message) // <4>
    }

    // end::class[]
    abstract fun send(message: String)

    abstract fun sendAsync(message: String): Future<String>

    abstract fun sendRx(message: String): Single<String>

}
