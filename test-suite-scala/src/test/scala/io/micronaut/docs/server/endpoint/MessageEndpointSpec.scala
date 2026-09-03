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
package io.micronaut.docs.server.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class MessageEndpointSpec:

  @Test
  def testReadMessageEndpoint(): Unit =
    withClient() { client =>
      val response = client.toBlocking.exchange("/message", classOf[String])
      assertEquals(HttpStatus.OK.getCode, response.code())
      assertEquals("default message", response.body())
    }

  @Test
  def testWriteMessageEndpoint(): Unit =
    withClient() { client =>
      val response = client.toBlocking.exchange(
        HttpRequest
          .POST("/message", Map[String, Object]("newMessage" -> "A new message").asJava)
          .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE),
        classOf[String]
      )

      assertEquals(HttpStatus.OK.getCode, response.code())
      assertEquals("Message updated", response.body())
      assertEquals(MediaType.TEXT_PLAIN_TYPE, response.getContentType.get)
      assertEquals("A new message", client.toBlocking.retrieve("/message"))
    }

  @Test
  def testDeleteMessageEndpoint(): Unit =
    withClient() { client =>
      val response = client.toBlocking.exchange(HttpRequest.DELETE("/message"), classOf[String])
      assertEquals(HttpStatus.OK.getCode, response.code())
      assertEquals("Message deleted", response.body())

      val exception = assertThrows(
        classOf[HttpClientResponseException],
        () => client.toBlocking.exchange("/message", classOf[String])
      )
      assertEquals(HttpStatus.NOT_FOUND.getCode, exception.getStatus.getCode)
    }

  private def withClient()(body: HttpClient => Unit): Unit =
    val properties = Map[String, Object](
      "endpoints.message.enabled" -> java.lang.Boolean.TRUE,
      "spec.name" -> "MessageEndpointSpec"
    ).asJava
    val server = ApplicationContext.run(classOf[EmbeddedServer], properties)
    val client = server.getApplicationContext.createBean(classOf[HttpClient], server.getURL)
    try body(client)
    finally
      client.close()
      server.close()
