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
package io.micronaut.docs.basics

import io.micronaut.context.ApplicationContext
import io.micronaut.core.`type`.Argument
import io.micronaut.http.HttpRequest.{GET, POST}
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.uri.UriBuilder
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux

import scala.jdk.CollectionConverters.*

class HelloControllerSpec:

  private def withClient(test: HttpClient => Unit): Unit =
    val embeddedServer = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object]("spec.name" -> "HelloControllerSpec").asJava
    )
    try
      val client = embeddedServer.getApplicationContext.createBean(classOf[HttpClient], embeddedServer.getURL)
      try test(client)
      finally client.close()
    finally
      embeddedServer.close()

  @Test
  def testSimpleRetrieve(): Unit =
    withClient { client =>
      // tag::simple[]
      val uri = UriBuilder.of("/hello/{name}")
        .expand(Map[String, Object]("name" -> "John").asJava)
        .toString
      assertEquals("/hello/John", uri)

      val result = client.toBlocking.retrieve(uri)

      assertEquals("Hello John", result)
      // end::simple[]
    }

  @Test
  def testRetrieveWithHeaders(): Unit =
    withClient { client =>
      // tag::headers[]
      val response = Flux.from(client.retrieve(
        GET("/hello/John")
          .header("X-My-Header", "SomeValue")
      ))
      // end::headers[]

      assertEquals("Hello John", response.blockFirst())
    }

  @Test
  def testRetrieveWithJson(): Unit =
    withClient { client =>
      // tag::jsonmap[]
      var response = Flux.from(client.retrieve(
        GET("/greet/John"),
        classOf[java.util.Map[?, ?]]
      ))
      // end::jsonmap[]

      assertEquals("Hello John", response.blockFirst().get("text"))

      // tag::jsonmaptypes[]
      response = Flux.from(client.retrieve(
        GET("/greet/John"),
        Argument.of(classOf[java.util.Map[?, ?]], classOf[String], classOf[String]) // <1>
      ))
      // end::jsonmaptypes[]

      assertEquals("Hello John", response.blockFirst().get("text"))
    }

  @Test
  def testRetrieveWithPojo(): Unit =
    withClient { client =>
      // tag::jsonpojo[]
      val response = Flux.from(client.retrieve(
        GET("/greet/John"),
        classOf[Message]
      ))

      assertEquals("Hello John", response.blockFirst().text)
      // end::jsonpojo[]
    }

  @Test
  def testRetrieveWithPojoResponse(): Unit =
    withClient { client =>
      // tag::pojoresponse[]
      val call = Flux.from(client.exchange(
        GET("/greet/John"),
        classOf[Message] // <1>
      ))

      val response = call.blockFirst()
      val message = response.getBody(classOf[Message]) // <2>
      // check the status
      assertEquals(HttpStatus.OK, response.getStatus) // <3>
      // check the body
      assertTrue(message.isPresent)
      assertEquals("Hello John", message.get().text)
      // end::pojoresponse[]
    }

  @Test
  def testPostRequestWithString(): Unit =
    withClient { client =>
      // tag::poststring[]
      val call = Flux.from(client.exchange(
        POST("/hello", "Hello John") // <1>
          .contentType(MediaType.TEXT_PLAIN_TYPE)
          .accept(MediaType.TEXT_PLAIN_TYPE), // <2>
        classOf[String] // <3>
      ))
      // end::poststring[]

      val response = call.blockFirst()
      val message = response.getBody(classOf[String])
      // check the status
      assertEquals(HttpStatus.CREATED, response.getStatus)
      // check the body
      assertTrue(message.isPresent)
      assertEquals("Hello John", message.get())
    }

  @Test
  def testPostRequestWithPojo(): Unit =
    withClient { client =>
      // tag::postpojo[]
      val call = Flux.from(client.exchange(
        POST("/greet", Message("Hello John")), // <1>
        classOf[Message] // <2>
      ))
      // end::postpojo[]

      val response = call.blockFirst()
      val message = response.getBody(classOf[Message])
      // check the status
      assertEquals(HttpStatus.CREATED, response.getStatus)
      // check the body
      assertTrue(message.isPresent)
      assertEquals("Hello John", message.get().text)
    }
