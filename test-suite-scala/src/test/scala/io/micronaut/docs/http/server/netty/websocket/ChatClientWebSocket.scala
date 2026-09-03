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

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.http.HttpRequest
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import org.reactivestreams.Publisher

import java.util.Collection
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Future
// end::imports[]

// tag::class[]
@Requires(property = "spec.name", value = "WebSocketSpec")
@ClientWebSocket("/chat/{topic}/{username}") // <1>
abstract class ChatClientWebSocket extends AutoCloseable: // <2>

  private var session: WebSocketSession = null
  private var request: HttpRequest[?] = null
  private var topic: String = null
  private var username: String = null
  private val replies: Collection[String] = ConcurrentLinkedQueue[String]()

  @OnOpen
  def onOpen(topic: String, username: String, session: WebSocketSession, request: HttpRequest[?]): Unit = // <3>
    this.topic = topic
    this.username = username
    this.session = session
    this.request = request

  def getTopic: String = topic

  def getUsername: String = username

  def getReplies: Collection[String] = replies

  def getSession: WebSocketSession = session

  def getRequest: HttpRequest[?] = request

  @OnMessage
  def onMessage(message: String): Unit =
    replies.add(message) // <4>

// end::class[]
  def send(message: String): Unit

  def sendAsync(message: String): Future[String]

  @SingleResult
  def sendRx(message: String): Publisher[String]
