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
package io.micronaut.docs.httpclientexceptionbody

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.core.`type`.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class BindHttpClientExceptionBodySpec:

  private def withClient(test: HttpClient => Unit): Unit =
    val server = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object](
        "spec.name" -> "BindHttpClientExceptionBodySpec",
        "spec.lang" -> "scala"
      ).asJava,
      Environment.TEST
    )
    val client = server.getApplicationContext.createBean(classOf[HttpClient], server.getURL)
    try
      test(client)
    finally
      client.close()
      server.close()

  //tag::test[]
  @Test
  def afterAnHttpClientExceptionTheResponseBodyCanBeBoundToAPOJO(): Unit =
    withClient { client =>
      try
        client.toBlocking.exchange(
          HttpRequest.GET[Any]("/books/1680502395"),
          Argument.of(classOf[Book]), // <1>
          Argument.of(classOf[CustomError]) // <2>
        )
        fail("Expected an HTTP client response exception")
      catch
        case e: HttpClientResponseException =>
          assertEquals(HttpStatus.UNAUTHORIZED, e.getResponse.getStatus)
          val jsonError = e.getResponse.getBody(classOf[CustomError])
          assertTrue(jsonError.isPresent)
          assertEquals(401, jsonError.get().status)
          assertEquals("Unauthorized", jsonError.get().error)
          assertEquals("No message available", jsonError.get().message)
          assertEquals("/books/1680502395", jsonError.get().path)
    }
  //end::test[]
