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
package io.micronaut.docs.server.consumes

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.ReflectiveAccess
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.codec.CodecException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

import scala.beans.BeanProperty
import scala.jdk.CollectionConverters.*

class ConsumesControllerSpec:

  @Test
  def testConsumes(): Unit =
    val server = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object]("spec.name" -> "consumesspec").asJava
    )
    try
      val client = server.getApplicationContext.createBean(classOf[HttpClient], server.getURL)
      try
        val book = Book()
        book.title = "The Stand"
        book.pages = Integer.valueOf(1000)

        assertThrows(
          classOf[HttpClientResponseException],
          () => client.toBlocking.exchange(
            HttpRequest.POST("/consumes", book)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
          )
        )

        assertDoesNotThrow(() =>
          client.toBlocking.exchange(
            HttpRequest.POST("/consumes", book)
              .contentType(MediaType.APPLICATION_JSON)
          )
        )

        assertDoesNotThrow(() =>
          client.toBlocking.exchange(
            HttpRequest.POST("/consumes/multiple", book)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
          )
        )

        assertDoesNotThrow(() =>
          client.toBlocking.exchange(
            HttpRequest.POST("/consumes/multiple", book)
              .contentType(MediaType.APPLICATION_JSON)
          )
        )

        assertThrows(
          classOf[CodecException],
          () => client.toBlocking.exchange(
            HttpRequest.POST("/consumes/member", book)
              .contentType(MediaType.TEXT_PLAIN)
          )
        )
      finally
        client.stop()
    finally
      server.stop()

@ReflectiveAccess
@Introspected
class Book:
  @BeanProperty
  var title: String | Null = null

  @BeanProperty
  var pages: Integer | Null = null
