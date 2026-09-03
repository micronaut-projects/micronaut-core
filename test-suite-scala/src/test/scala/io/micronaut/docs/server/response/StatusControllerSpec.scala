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
package io.micronaut.docs.server.response

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class StatusControllerSpec:

  @Test
  def statusCanBeSetSeveralWays(): Unit =
    val server = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object]("spec.name" -> "httpstatus").asJava
    )
    try
      val client = server.getApplicationContext.createBean(classOf[HttpClient], server.getURL)
      try
        var response = client.toBlocking.exchange(HttpRequest.GET[Any]("/status"), classOf[String])
        assertEquals(HttpStatus.CREATED, response.getStatus)
        assertEquals("success", response.body())

        response = client.toBlocking.exchange(HttpRequest.GET[Any]("/status/http-response"), classOf[String])
        assertEquals(HttpStatus.CREATED, response.getStatus)
        assertEquals("success", response.body())

        response = client.toBlocking.exchange(HttpRequest.GET[Any]("/status/http-status"), classOf[String])
        assertEquals(HttpStatus.CREATED, response.getStatus)
      finally client.stop()
    finally server.stop()
