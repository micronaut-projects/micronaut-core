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
package io.micronaut.docs.writable

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TemplateControllerSpec:

  @Test
  def writableResponseRendersTemplate(): Unit =
    val server = ApplicationContext.run(classOf[EmbeddedServer])
    try
      val client = server.getApplicationContext.createBean(classOf[HttpClient], server.getURL)
      try
        assertEquals(
          "Dear Fred Flintstone. Nice to meet you.",
          client.toBlocking.retrieve("/template/welcome")
        )
      finally client.stop()
    finally server.stop()
