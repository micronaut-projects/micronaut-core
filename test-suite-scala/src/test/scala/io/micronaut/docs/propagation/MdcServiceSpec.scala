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
package io.micronaut.docs.propagation

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class MdcServiceSpec:

  @Test
  def testFilterSpec(): Unit =
    val embeddedServer = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object]("mdc.example.service.enabled" -> java.lang.Boolean.TRUE).asJava
    )
    try
      val client = HttpClient.create(embeddedServer.getURL)
      try
        val request = HttpRequest.GET[Any]("/mdc/test")
        val response = client.toBlocking.retrieve(request)
        assertTrue(response.startsWith("New user id: "))
        assertTrue(response.endsWith(" name: Denis"))
      finally
        client.close()
    finally
      embeddedServer.close()
