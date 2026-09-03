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
package io.micronaut.docs.datavalidation.groups

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class EmailControllerSpec:

  private def withClient(test: HttpClient => Unit): Unit =
    val server = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object]("spec.name" -> "datavalidationgroups").asJava
    )
    try
      val client = server.getApplicationContext.createBean(classOf[HttpClient], server.getURL)
      try test(client)
      finally client.stop()
    finally
      server.stop()

  // tag::pojovalidateddefault[]
  @Test
  def testPojoValidationDefaultGroup(): Unit =
    withClient { client =>
      val e = assertThrows(
        classOf[HttpClientResponseException],
        () =>
          val email = Email("", "")
          client.toBlocking.exchange(HttpRequest.POST("/email/createDraft", email))
      )
      var response: HttpResponse[?] = e.getResponse

      assertEquals(HttpStatus.BAD_REQUEST, response.getStatus)

      val email = Email("Hi", "")
      response = client.toBlocking.exchange(HttpRequest.POST("/email/createDraft", email))

      assertEquals(HttpStatus.OK, response.getStatus)
    }
  // end::pojovalidateddefault[]

  // tag::pojovalidatedfinal[]
  @Test
  def testPojoValidationFinalValidationGroup(): Unit =
    withClient { client =>
      val e = assertThrows(
        classOf[HttpClientResponseException],
        () =>
          val email = Email("Hi", "")
          client.toBlocking.exchange(HttpRequest.POST("/email/send", email))
      )
      var response: HttpResponse[?] = e.getResponse

      assertEquals(HttpStatus.BAD_REQUEST, response.getStatus)

      val email = Email("Hi", "me@micronaut.example")
      response = client.toBlocking.exchange(HttpRequest.POST("/email/send", email))

      assertEquals(HttpStatus.OK, response.getStatus)
    }
  // end::pojovalidatedfinal[]
