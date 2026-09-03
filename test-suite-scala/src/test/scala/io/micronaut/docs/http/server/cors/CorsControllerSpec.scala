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
package io.micronaut.docs.http.server.cors

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.uri.UriBuilder
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class CorsControllerSpec:

  private def withClient(test: HttpClient => Unit): Unit =
    val server = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object]("spec.name" -> "CorsControllerSpec").asJava
    )
    try
      val client = server.getApplicationContext.createBean(classOf[HttpClient], server.getURL)
      try test(client)
      finally client.stop()
    finally server.stop()

  @Test
  def crossOriginWithAllowedOrigin(): Unit =
    withClient { client =>
      assertDoesNotThrow(() => client.toBlocking.exchange(preflight("https://myui.com", HttpMethod.GET)))
    }

  @Test
  def crossOriginWithNotAllowedOrigin(): Unit =
    withClient { client =>
      assertThrows(
        classOf[HttpClientResponseException],
        () => client.toBlocking.exchange(preflight("https://google.com", HttpMethod.GET))
      )
    }

  private def preflight(originValue: String, method: HttpMethod): MutableHttpRequest[?] =
    HttpRequest.OPTIONS(UriBuilder.of("/hello").build())
      .header(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN)
      .header(HttpHeaders.ORIGIN, originValue)
      .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
