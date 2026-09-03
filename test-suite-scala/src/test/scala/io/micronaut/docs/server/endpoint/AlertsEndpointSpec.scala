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
import io.micronaut.core.`type`.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class AlertsEndpointSpec:

  @Test
  def testAddingAnAlert(): Unit =
    withClient() { client =>
      val exception = assertThrows(
        classOf[HttpClientResponseException],
        () => client.toBlocking.exchange(
          HttpRequest.POST("/alerts", "First alert").contentType(MediaType.TEXT_PLAIN_TYPE),
          classOf[String]
        )
      )
      assertEquals(HttpStatus.UNAUTHORIZED.getCode, exception.getStatus.getCode)
    }

  @Test
  def testAddingAnAlertNotSensitive(): Unit =
    withClient(Map[String, Object]("endpoints.alerts.add.sensitive" -> java.lang.Boolean.FALSE)) { client =>
      val response = client.toBlocking.exchange(
        HttpRequest.POST("/alerts", "First alert").contentType(MediaType.TEXT_PLAIN_TYPE),
        classOf[String]
      )
      assertEquals(HttpStatus.OK, response.status())

      val listResponse = client.toBlocking.exchange(HttpRequest.GET("/alerts"), Argument.LIST_OF_STRING)
      assertEquals(HttpStatus.OK, listResponse.status())
      assertEquals("First alert", listResponse.body().get(0))
    }

  @Test
  def testClearingAlerts(): Unit =
    withClient() { client =>
      val exception = assertThrows(
        classOf[HttpClientResponseException],
        () => client.toBlocking.exchange(HttpRequest.DELETE("/alerts"), classOf[String])
      )
      assertEquals(HttpStatus.UNAUTHORIZED.getCode, exception.getStatus.getCode)
    }

  private def withClient(extra: Map[String, Object] = Map.empty)(body: HttpClient => Unit): Unit =
    val properties = (Map[String, Object]("spec.name" -> "AlertsEndpointSpec") ++ extra).asJava
    val server = ApplicationContext.run(classOf[EmbeddedServer], properties)
    val client = server.getApplicationContext.createBean(classOf[HttpClient], server.getURL)
    try body(client)
    finally
      client.close()
      server.close()
