/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.docs.http.server.netty.websocket

import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import org.reactivestreams.Publisher

import java.util.Collection
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Future

@Requires(property = "spec.name", value = "WebSocketSpec")
@ClientWebSocket("/pojo/chat/{topic}/{username}")
abstract class PojoChatClientWebSocket extends AutoCloseable:

  private var topic: String = null
  private var username: String = null
  private val replies: Collection[Message] = ConcurrentLinkedQueue[Message]()

  @OnOpen
  def onOpen(topic: String, username: String): Unit =
    this.topic = topic
    this.username = username

  def getTopic: String = topic

  def getUsername: String = username

  def getReplies: Collection[Message] = replies

  @OnMessage
  def onMessage(message: Message): Unit =
    replies.add(message)

  def send(message: Message): Unit

  def sendAsync(message: Message): Future[Message]

  @SingleResult
  def sendRx(message: Message): Publisher[Message]
