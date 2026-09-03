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
package io.micronaut.docs.server.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.time.Instant
import java.util.Date
import scala.jdk.CollectionConverters.*

class CurrentDateEndpointSpec:

  @Test
  def testReadCustomDateEndpoint(): Unit =
    withClient() { client =>
      val response = client.toBlocking.exchange("/date", classOf[String])
      assertEquals(HttpStatus.OK.getCode, response.code())
    }

  @Test
  def testReadCustomDateEndpointWithArgument(): Unit =
    withClient() { client =>
      val response = client.toBlocking.exchange("/date/current_date_is", classOf[String])
      assertEquals(HttpStatus.OK.getCode, response.code())
      assertTrue(response.body().startsWith("current_date_is: "))
      assertEquals(MediaType.TEXT_PLAIN_TYPE, response.getContentType.get)
    }

  @Test
  def testWriteCustomDateEndpoint(): Unit =
    withClient() { client =>
      val original = Date.from(client.toBlocking.exchange("/date", classOf[Instant]).body())

      val postResponse = client.toBlocking.exchange(
        HttpRequest.POST("/date", Map.empty[String, Object].asJava),
        classOf[String]
      )

      assertEquals(HttpStatus.OK.getCode, postResponse.code())
      assertEquals("Current date reset", postResponse.body())

      val reset = Date.from(client.toBlocking.exchange("/date", classOf[Instant]).body())
      assertTrue(reset.getTime >= original.getTime)
    }

  @Test
  def testDisableEndpoint(): Unit =
    withClient(Map[String, Object]("custom.date.enabled" -> java.lang.Boolean.FALSE)) { client =>
      val exception = assertThrows(
        classOf[HttpClientResponseException],
        () => client.toBlocking.exchange("/date", classOf[String])
      )
      assertEquals(HttpStatus.NOT_FOUND.getCode, exception.getResponse.code())
    }

  private def withClient(properties: Map[String, Object] = Map.empty)(body: HttpClient => Unit): Unit =
    val server = ApplicationContext.run(classOf[EmbeddedServer], properties.asJava)
    val client = server.getApplicationContext.createBean(classOf[HttpClient], server.getURL)
    try body(client)
    finally
      client.close()
      server.close()
