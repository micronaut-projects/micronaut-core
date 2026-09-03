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
import io.micronaut.http.HttpRequest.POST
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux

import scala.jdk.CollectionConverters.*

class BookControllerSpec:

  private def withClient(test: HttpClient => Unit): Unit =
    val embeddedServer = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object]("spec.name" -> "BookControllerSpec").asJava
    )
    try
      val client = embeddedServer.getApplicationContext.createBean(classOf[HttpClient], embeddedServer.getURL)
      try test(client)
      finally client.close()
    finally
      embeddedServer.close()

  @Test
  def testPostWithURITemplate(): Unit =
    withClient { client =>
      // tag::posturitemplate[]
      val call = Flux.from(client.exchange(
        POST("/amazon/book/{title}", Book("The Stand")),
        classOf[Book]
      ))
      // end::posturitemplate[]

      val response: HttpResponse[Book] = call.blockFirst()
      val message = response.getBody(classOf[Book]) // <2>
      // check the status
      assertEquals(HttpStatus.CREATED, response.getStatus) // <3>
      // check the body
      assertTrue(message.isPresent)
      assertEquals("The Stand", message.get().title)
    }

  @Test
  def testPostFormData(): Unit =
    withClient { client =>
      // tag::postform[]
      val call = Flux.from(client.exchange(
        POST("/amazon/book/{title}", Book("The Stand"))
          .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE),
        classOf[Book]
      ))
      // end::postform[]

      val response: HttpResponse[Book] = call.blockFirst()
      val message = response.getBody(classOf[Book]) // <2>
      // check the status
      assertEquals(HttpStatus.CREATED, response.getStatus) // <3>
      // check the body
      assertTrue(message.isPresent)
      assertEquals("The Stand", message.get().title)
    }
