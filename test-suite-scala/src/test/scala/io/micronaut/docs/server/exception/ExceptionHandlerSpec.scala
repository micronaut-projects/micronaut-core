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
package io.micronaut.docs.server.exception

import io.micronaut.context.ApplicationContext
import io.micronaut.core.`type`.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

import java.util.{List as JList}
import java.util.{Map as JMap}
import scala.jdk.CollectionConverters.*

class ExceptionHandlerSpec:

  private def withClient(test: HttpClient => Unit): Unit =
    val server = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object]("spec.name" -> "ExceptionHandlerSpec").asJava
    )
    try
      val client = server.getApplicationContext.createBean(classOf[HttpClient], server.getURL)
      try test(client)
      finally client.stop()
    finally
      server.stop()

  @Test
  def testExceptionIsHandled(): Unit =
    withClient { client =>
      val request = HttpRequest.GET[Any]("/books/stock/1234")
      val errorType = Argument.mapOf(classOf[String], classOf[Object])
      val ex = assertThrows(
        classOf[HttpClientResponseException],
        () => client.toBlocking.exchange(request, Argument.LONG, errorType)
      )

      val response = ex.getResponse
      val body = response.getBody(errorType).get()
      val embedded = body.get("_embedded").asInstanceOf[JMap[String, Object]]
      val message = embedded.get("errors")
        .asInstanceOf[JList[JMap[String, Object]]]
        .get(0)
        .get("message")

      assertEquals(HttpStatus.BAD_REQUEST, response.status())
      assertEquals("No stock available", message)
    }
