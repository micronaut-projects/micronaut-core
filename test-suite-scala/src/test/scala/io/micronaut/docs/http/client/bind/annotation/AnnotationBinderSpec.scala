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
package io.micronaut.docs.http.client.bind.annotation

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class AnnotationBinderSpec:

  @Test
  def testBindingToTheRequest(): Unit =
    val server = ApplicationContext.run(classOf[EmbeddedServer])
    try
      val client = server.getApplicationContext.getBean(classOf[MetadataClient])

      val metadata = Map[String, Object](
        "version" -> java.lang.Double.valueOf(3.6),
        "deploymentId" -> java.lang.Long.valueOf(42L)
      ).asJava
      val response = client.get(metadata)
      assertEquals("3.6", response)
    finally
      server.close()
