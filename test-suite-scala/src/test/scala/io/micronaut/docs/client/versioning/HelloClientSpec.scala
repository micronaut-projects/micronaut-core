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
package io.micronaut.docs.client.versioning

import io.micronaut.context.ApplicationContext
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.annotation.Get
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HelloClientSpec:

  @Test
  def generatedClientExposesExecutableMethodMetadata(): Unit =
    val context = ApplicationContext.run()
    try
      val definition = context.getBeanDefinition(classOf[HelloClient])

      assertTrue(definition.hasAnnotation(classOf[Version]))
      assertEquals("1", definition.stringValue(classOf[Version]).orElse(null))

      val hello = definition.findMethod("sayHello", classOf[String]).orElse(null)
      assertNotNull(hello)
      assertTrue(hello.hasAnnotation(classOf[Get]))

      val helloTwo = definition.findMethod("sayHelloTwo", classOf[String]).orElse(null)
      assertNotNull(helloTwo)
      assertTrue(helloTwo.hasDeclaredAnnotation(classOf[Version]))
      assertEquals("2", helloTwo.stringValue(classOf[Version]).orElse(null))
      assertTrue(helloTwo.hasAnnotation(classOf[SingleResult]))
    finally
      context.close()
