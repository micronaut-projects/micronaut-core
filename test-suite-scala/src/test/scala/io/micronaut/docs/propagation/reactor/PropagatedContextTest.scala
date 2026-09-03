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
package io.micronaut.docs.propagation.reactor

import io.micronaut.context.ApplicationContext
import io.micronaut.core.`type`.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.uri.UriBuilder
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class PropagatedContextTest:

  @Test
  def testMonoRequest(): Unit =
    val embeddedServer = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object]("spec.name" -> "PropagatedContextSpec").asJava
    )
    try
      val client = HttpClient.create(embeddedServer.getURL)
      try
        val uri = UriBuilder.of("/hello").queryParam("name", "Dean").build()
        val hello = client.toBlocking.retrieve(HttpRequest.GET(uri), Argument.of(classOf[String]))
        assertEquals("Hello, Dean", hello)
      finally
        client.close()
    finally
      embeddedServer.close()
