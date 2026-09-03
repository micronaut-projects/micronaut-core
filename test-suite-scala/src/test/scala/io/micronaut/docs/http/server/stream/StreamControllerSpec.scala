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
package io.micronaut.docs.http.server.stream

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class StreamControllerSpec:

  private def withClient(test: HttpClient => Unit): Unit =
    val server = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object]("spec.name" -> "StreamControllerSpec").asJava
    )
    try
      val client = server.getApplicationContext.createBean(classOf[HttpClient], server.getURL)
      try test(client)
      finally client.stop()
    finally
      server.stop()

  @Test
  def testReceivingAStream(): Unit =
    withClient { client =>
      val response = client.toBlocking.retrieve(
        HttpRequest.GET[Any]("/stream/write"),
        classOf[String]
      )

      assertEquals("test", response)
    }

  @Test
  def testReturningAStream(): Unit =
    withClient { client =>
      val body = "My body"
      val response = client.toBlocking.retrieve(
        HttpRequest.POST("/stream/read", body)
          .contentType(MediaType.TEXT_PLAIN_TYPE),
        classOf[String]
      )

      assertEquals(body, response)
    }
