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
package io.micronaut.docs.server.binding

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.cookie.Cookie
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class BindingControllerSpec:

  private def withClient(test: HttpClient => Unit): Unit =
    val server = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object]("spec.name" -> "BindingControllerSpec").asJava
    )
    try
      val client = server.getApplicationContext.createBean(classOf[HttpClient], server.getURL)
      try test(client)
      finally client.stop()
    finally
      server.stop()

  @Test
  def testCookieBinding(): Unit =
    withClient { client =>
      var body = client.toBlocking.retrieve(
        HttpRequest.GET("/binding/cookieName").cookie(Cookie.of("myCookie", "cookie value"))
      )

      assertNotNull(body)
      assertEquals("cookie value", body)

      body = client.toBlocking.retrieve(
        HttpRequest.GET("/binding/cookieInferred").cookie(Cookie.of("myCookie", "cookie value"))
      )

      assertNotNull(body)
      assertEquals("cookie value", body)
    }

  @Test
  def testCookiesBinding(): Unit =
    withClient { client =>
      val cookies = Set(
        Cookie.of("myCookieA", "cookie A value"),
        Cookie.of("myCookieB", "cookie B value")
      ).asJava

      val body = client.toBlocking.retrieve(HttpRequest.GET("/binding/cookieMultiple").cookies(cookies))

      assertNotNull(body)
      assertEquals("[\"cookie A value\",\"cookie B value\"]", body)
    }

  @Test
  def testHeaderBinding(): Unit =
    withClient { client =>
      var body = client.toBlocking.retrieve(HttpRequest.GET("/binding/headerName").header("Content-Type", "test"))

      assertNotNull(body)
      assertEquals("test", body)

      body = client.toBlocking.retrieve(HttpRequest.GET("/binding/headerInferred").header("Content-Type", "test"))

      assertNotNull(body)
      assertEquals("test", body)

      val ex = assertThrows(
        classOf[HttpClientResponseException],
        () => client.toBlocking.retrieve(HttpRequest.GET("/binding/headerNullable"))
      )

      assertEquals(HttpStatus.NOT_FOUND, ex.getResponse.getStatus)
    }

  @Test
  def testHeaderDateBinding(): Unit =
    withClient { client =>
      var body = client.toBlocking.retrieve(
        HttpRequest.GET("/binding/date").header("date", "Tue, 3 Jun 2008 11:05:30 GMT")
      )

      assertNotNull(body)
      assertEquals("2008-06-03T11:05:30Z", body)

      body = client.toBlocking.retrieve(
        HttpRequest.GET("/binding/dateFormat").header("date", "03/06/2008 11:05:30 AM GMT")
      )

      assertNotNull(body)
      assertEquals("2008-06-03T11:05:30Z[GMT]", body)
    }
