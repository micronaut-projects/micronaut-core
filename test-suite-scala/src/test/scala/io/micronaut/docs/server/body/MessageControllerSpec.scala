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
package io.micronaut.docs.server.body

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class MessageControllerSpec:

  private def withClient(test: HttpClient => Unit): Unit =
    val server = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object]("spec.name" -> "MessageControllerSpec").asJava
    )
    try
      val client = server.getApplicationContext.createBean(classOf[HttpClient], server.getURL)
      try test(client)
      finally client.stop()
    finally
      server.stop()

  @Test
  def bodyAnnotationBindsTextBody(): Unit =
    withClient { client =>
      val request = HttpRequest.POST("/receive/echo", "hello").contentType(MediaType.TEXT_PLAIN_TYPE)
      assertEquals("hello", client.toBlocking.retrieve(request))
    }

  @Test
  def bodyAnnotationBindsReactiveTextBody(): Unit =
    withClient { client =>
      val request = HttpRequest.POST("/receive/echo-publisher", "hello").contentType(MediaType.TEXT_PLAIN_TYPE)
      assertEquals("hello", client.toBlocking.retrieve(request))
    }
