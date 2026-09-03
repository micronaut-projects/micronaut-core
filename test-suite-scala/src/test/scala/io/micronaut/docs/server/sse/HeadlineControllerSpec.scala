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
package io.micronaut.docs.server.sse

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.sse.SseClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux

import java.time.Duration
import scala.jdk.CollectionConverters.*

class HeadlineControllerSpec:

  @Test
  def testConsumeEventStreamObject(): Unit =
    val server = ApplicationContext.run(classOf[EmbeddedServer])
    try
      val client = server.getApplicationContext.createBean(classOf[SseClient], server.getURL)
      val events = Flux.from(client.eventStream(HttpRequest.GET[Any]("/headlines"), classOf[Headline]))
        .take(2)
        .collectList()
        .block(Duration.ofSeconds(3))
      assertEquals(2, events.size())
      assertEquals("Micronaut 1.0 Released", events.get(0).getData.title)
      assertEquals("Come and get it", events.get(0).getData.description)
    finally server.stop()
