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
package io.micronaut.docs.datavalidation.params

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
      Map[String, Object]("spec.name" -> "datavalidationparams").asJava
    )
    try
      val client = server.getApplicationContext.createBean(classOf[HttpClient], server.getURL)
      try test(client)
      finally client.stop()
    finally
      server.stop()

  // tag::paramsvalidated[]
  @Test
  def testParametersAreValidated(): Unit =
    withClient { client =>
      val e = assertThrows(
        classOf[HttpClientResponseException],
        () => client.toBlocking.exchange(HttpRequest.GET[Any]("/email/send?subject=Hi&recipient="))
      )
      var response: HttpResponse[?] = e.getResponse

      assertEquals(HttpStatus.BAD_REQUEST, response.getStatus)

      response = client.toBlocking.exchange(
        HttpRequest.GET[Any]("/email/send?subject=Hi&recipient=me@micronaut.example")
      )

      assertEquals(HttpStatus.OK, response.getStatus)
    }
  // end::paramsvalidated[]
