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
package io.micronaut.docs.server.json

import io.micronaut.context.ApplicationContext
import io.micronaut.core.`type`.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.util.{Map as JMap}
import scala.jdk.CollectionConverters.*

class PersonControllerSpec:

  private def withClient(test: HttpClient => Unit): Unit =
    val server = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object]("spec.name" -> "PersonControllerSpec").asJava
    )
    try
      val client = server.getApplicationContext.createBean(classOf[HttpClient], server.getURL)
      try test(client)
      finally client.stop()
    finally
      server.stop()

  @Test
  def testGlobalErrorHandler(): Unit =
    withClient { client =>
      val e = assertThrows(
        classOf[HttpClientResponseException],
        () => client.toBlocking.exchange(HttpRequest.GET[Any]("/people/error"), classOf[JMap[String, Object]])
      )
      val response = e.getResponse

      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus)
      assertEquals(
        "Bad Things Happened: Something went wrong",
        response.getBody(classOf[JMap[String, Object]]).get().get("message")
      )
    }

  @Test
  def testSave(): Unit =
    withClient { client =>
      var response = client.toBlocking.exchange(
        HttpRequest.POST("/people", """{"firstName":"Fred","lastName":"Flintstone","age":45}"""),
        classOf[Person]
      )
      assertTrue(response.getBody.isPresent)
      var person = response.getBody.get()

      assertEquals("Fred", person.firstName)
      assertEquals(HttpStatus.CREATED, response.getStatus)

      response = client.toBlocking.exchange(HttpRequest.GET[Any]("/people/Fred"), classOf[Person])
      person = response.getBody.get()

      assertEquals("Fred", person.firstName)
      assertEquals(HttpStatus.OK, response.getStatus)
    }

  @Test
  def testSaveReactive(): Unit =
    withClient { client =>
      val response = client.toBlocking.exchange(
        HttpRequest.POST("/people/saveReactive", """{"firstName":"Wilma","lastName":"Flintstone","age":36}"""),
        classOf[Person]
      )
      assertTrue(response.getBody.isPresent)
      val person = response.getBody.get()

      assertEquals("Wilma", person.firstName)
      assertEquals(HttpStatus.CREATED, response.getStatus)
    }

  @Test
  def testSaveFuture(): Unit =
    withClient { client =>
      val response = client.toBlocking.exchange(
        HttpRequest.POST("/people/saveFuture", """{"firstName":"Pebbles","lastName":"Flintstone","age":0}"""),
        classOf[Person]
      )
      assertTrue(response.getBody.isPresent)
      val person = response.getBody.get()

      assertEquals("Pebbles", person.firstName)
      assertEquals(HttpStatus.CREATED, response.getStatus)
    }

  @Test
  def testSaveArgs(): Unit =
    withClient { client =>
      val response = client.toBlocking.exchange(
        HttpRequest.POST("/people/saveWithArgs", """{"firstName":"Dino","lastName":"Flintstone","age":3}"""),
        classOf[Person]
      )
      assertTrue(response.getBody.isPresent)
      val person = response.getBody.get()

      assertEquals("Dino", person.firstName)
      assertEquals(HttpStatus.CREATED, response.getStatus)
    }

  @Test
  def testPersonNotFound(): Unit =
    withClient { client =>
      val e = assertThrows(
        classOf[HttpClientResponseException],
        () => client.toBlocking.exchange(HttpRequest.GET[Any]("/people/Sally"), classOf[JMap[String, Object]])
      )
      val response = e.getResponse

      assertEquals("Person Not Found", response.getBody(classOf[JMap[String, Object]]).get().get("message"))
      assertEquals(HttpStatus.NOT_FOUND, response.getStatus)
    }

  @Test
  def testSaveInvalidJson(): Unit =
    withClient { client =>
      val e = assertThrows(
        classOf[HttpClientResponseException],
        () =>
          client.toBlocking.exchange(
            HttpRequest.POST("/people", "{\""),
            Argument.of(classOf[Person]),
            Argument.of(classOf[JMap[String, Object]])
          )
      )
      val response: HttpResponse[?] = e.getResponse
      assertTrue(
        response.getBody(classOf[JMap[String, Object]]).get().get("message").toString
          .startsWith("Invalid JSON: Unexpected end-of-input")
      )
      assertEquals(HttpStatus.BAD_REQUEST, response.getStatus)
    }
