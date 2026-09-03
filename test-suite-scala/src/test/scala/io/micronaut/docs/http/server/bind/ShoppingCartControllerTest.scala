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
package io.micronaut.docs.http.server.bind

import io.micronaut.context.ApplicationContext
import io.micronaut.core.`type`.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.cookie.Cookie
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

import java.util.{List as JList}
import java.util.{Map as JMap}
import scala.jdk.CollectionConverters.*

class ShoppingCartControllerTest:

  private def withClient(test: HttpClient => Unit): Unit =
    val server = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object]("spec.name" -> "ShoppingCartControllerTest").asJava
    )
    try
      val client = server.getApplicationContext.createBean(classOf[HttpClient], server.getURL)
      try test(client)
      finally client.stop()
    finally
      server.stop()

  @Test
  def testBindingBadCredentials(): Unit =
    withClient { client =>
      val request = HttpRequest.GET("/customBinding/annotated")
        .cookie(Cookie.of("shoppingCart", "{}"))
      val responseException = assertThrows(
        classOf[HttpClientResponseException],
        () => client.toBlocking.exchange(request)
      )

      val body = responseException.getResponse.getBody(classOf[JMap[String, Object]]).get()
      val embedded = body.get("_embedded").asInstanceOf[JMap[String, Object]]
      val errors = embedded.get("errors").asInstanceOf[JList[JMap[String, Object]]]
      val message = errors.get(0).get("message")

      assertEquals("Required ShoppingCart [sessionId] not specified", message)
    }

  @Test
  def testAnnotationBinding(): Unit =
    withClient { client =>
      val request = HttpRequest.GET("/customBinding/annotated")
        .cookie(Cookie.of("shoppingCart", "{\"sessionId\": 5}"))
      val response = client.toBlocking.retrieve(request)

      assertEquals("Session:5", response)
    }

  @Test
  def testTypeBinding(): Unit =
    withClient { client =>
      val request = HttpRequest.GET("/customBinding/typed")
        .cookie(Cookie.of("shoppingCart", "{\"sessionId\": 5, \"total\": 20}"))

      val body = client.toBlocking.retrieve(request, Argument.mapOf(classOf[String], classOf[Object]))

      assertEquals("5", body.get("sessionId"))
      assertEquals(20, body.get("total"))
    }
