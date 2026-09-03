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

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.time.Duration
import scala.jdk.CollectionConverters.*

class WebSocketSpec:

  private def withServer(test: (EmbeddedServer, WebSocketClient) => Unit): Unit =
    val server = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object](
        "spec.name" -> "WebSocketSpec",
        "micronaut.server.netty.log-level" -> "TRACE"
      ).asJava
    )
    try
      val client = server.getApplicationContext.createBean(classOf[WebSocketClient], server.getURI)
      try test(server, client)
      finally client.close()
    finally server.close()

  private def awaitUntil(condition: => Boolean): Unit =
    val deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos
    while !condition && System.nanoTime() < deadline do Thread.sleep(50)
    assertTrue(condition)

  @Test
  @Disabled("pending: scala-docs-219/220 - Scala @ClientWebSocket introduction fails at runtime with a missing setWebSocketSession interceptor.")
  def testSimpleTextWebSocketExchange(): Unit =
    withServer { (_, wsClient) =>
      val fred = Flux.from(wsClient.connect(classOf[ChatClientWebSocket], "/chat/stuff/fred"))
        .blockFirst(Duration.ofSeconds(5))
      val bob = Flux.from(wsClient.connect(classOf[ChatClientWebSocket], "/chat/stuff/bob"))
        .blockFirst(Duration.ofSeconds(5))
      try
        assertNotNull(fred.getSession)
        assertNotNull(fred.getSession.getId)
        assertNotNull(fred.getRequest)
        assertNotEquals(fred.getSession.getId, bob.getSession.getId)
        assertEquals("stuff", fred.getTopic)
        assertEquals("fred", fred.getUsername)
        assertEquals("bob", bob.getUsername)

        awaitUntil(fred.getReplies.contains("[bob] Joined!"))
        assertEquals(1, fred.getReplies.size())

        fred.send("Hello bob!")

        awaitUntil(bob.getReplies.contains("[fred] Hello bob!"))
        assertEquals(1, bob.getReplies.size())

        bob.send("Hi fred. How are things?")

        awaitUntil(fred.getReplies.contains("[bob] Hi fred. How are things?"))
        assertEquals(2, fred.getReplies.size())
        assertTrue(bob.getReplies.contains("[fred] Hello bob!"))
        assertEquals(1, bob.getReplies.size())

        assertEquals("foo", fred.sendAsync("foo").get())
        assertEquals("bar", Mono.from(fred.sendRx("bar")).block())
      finally
        bob.close()
        fred.close()
    }

  @Test
  @Disabled("pending: scala-docs-219/221 - Scala @ClientWebSocket introduction fails at runtime with a missing setWebSocketSession interceptor.")
  def testPojoWebSocketExchange(): Unit =
    withServer { (_, wsClient) =>
      val fred = Flux.from(wsClient.connect(classOf[PojoChatClientWebSocket], "/pojo/chat/stuff/fred"))
        .blockFirst(Duration.ofSeconds(5))
      val bob = Flux.from(wsClient.connect(classOf[PojoChatClientWebSocket], "/pojo/chat/stuff/bob"))
        .blockFirst(Duration.ofSeconds(5))
      try
        assertEquals("stuff", fred.getTopic)
        assertEquals("fred", fred.getUsername)
        assertEquals("bob", bob.getUsername)

        awaitUntil(fred.getReplies.contains(Message("[bob] Joined!")))

        fred.send(Message("Hello bob!"))

        awaitUntil(bob.getReplies.contains(Message("[fred] Hello bob!")))
        assertTrue(fred.getReplies.contains(Message("[bob] Joined!")))
        assertFalse(fred.getReplies.contains(Message("[fred] Hello bob!")))
        assertFalse(bob.getReplies.contains(Message("[bob] Joined!")))

        bob.send(Message("Hi fred. How are things?"))

        awaitUntil(fred.getReplies.contains(Message("[bob] Hi fred. How are things?")))
        assertFalse(bob.getReplies.contains(Message("[bob] Hi fred. How are things?")))
        assertTrue(bob.getReplies.contains(Message("[fred] Hello bob!")))

        assertEquals("foo", fred.sendAsync(Message("foo")).get().getText)
        assertEquals("bar", Mono.from(fred.sendRx(Message("bar"))).block().getText)
      finally
        bob.close()
        fred.close()
    }
