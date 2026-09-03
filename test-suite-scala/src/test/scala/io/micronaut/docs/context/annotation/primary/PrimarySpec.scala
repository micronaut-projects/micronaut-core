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
package io.micronaut.docs.context.annotation.primary

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class PrimarySpec:

  @Test
  def testPrimaryAnnotatedBeanIsInjectedWhenMultipleOptionsExist(): Unit =
    val embeddedServer = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object](
        "spec.name" -> "primaryspec",
        "spec.lang" -> "scala"
      ).asJava,
      Environment.TEST
    )
    val client = embeddedServer.getApplicationContext.createBean(classOf[HttpClient], embeddedServer.getURL)
    try
      assertEquals(2, embeddedServer.getApplicationContext.getBeansOfType(classOf[ColorPicker]).size())

      val response = client.toBlocking.exchange(HttpRequest.GET("/testPrimary"), classOf[String])

      assertEquals(HttpStatus.OK, response.status())
      assertEquals("green", response.body())
    finally
      client.close()
      embeddedServer.close()
