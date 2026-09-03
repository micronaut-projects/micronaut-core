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
package io.micronaut.docs.server.binding

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.uri.UriTemplate
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class MovieTicketControllerSpec:

  @Test
  def testBindingBean(): Unit =
    val server = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object]("spec.name" -> "MovieTicketControllerSpec").asJava
    )
    try
      val client = server.getApplicationContext.createBean(classOf[HttpClient], server.getURL)
      try
        val template = UriTemplate("/api/movie/ticket/terminator{?minPrice,maxPrice}")
        val params = Map[String, Object](
          "minPrice" -> java.lang.Double.valueOf(5.0d),
          "maxPrice" -> java.lang.Double.valueOf(20.0d)
        ).asJava

        val response = client.toBlocking.exchange(HttpRequest.GET[Any](template.expand(params)))

        assertEquals(HttpStatus.OK, response.status())
      finally
        client.stop()
    finally
      server.stop()
