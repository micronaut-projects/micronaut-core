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
package io.micronaut.docs.web.router.version

import io.micronaut.context.ApplicationContext
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.annotation.Get
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class VersionedControllerSpec:

  @Test
  def controllerMethodsExposeVersionMetadata(): Unit =
    val context = ApplicationContext.run(
      Map[String, Object]("spec.name" -> "VersionedControllerSpec").asJava
    )
    try
      val definition = context.getBeanDefinition(classOf[VersionedController])

      val helloV1 = definition.findMethod("helloV1").orElseThrow()
      assertTrue(helloV1.hasAnnotation(classOf[Get]))
      assertEquals("1", helloV1.stringValue(classOf[Version]).orElse(null))

      val helloV2 = definition.findMethod("helloV2").orElseThrow()
      assertTrue(helloV2.hasAnnotation(classOf[Get]))
      assertEquals("2", helloV2.stringValue(classOf[Version]).orElse(null))
    finally
      context.close()
