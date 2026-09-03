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
package io.micronaut.docs.resources

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class ResourceLoaderTest:

  @Test
  @throws[Exception]
  def testExampleForResourceResolver(): Unit =
    val applicationContext = ApplicationContext.run(
      Map[String, Object]("spec.name" -> "ResourceLoaderTest").asJava,
      "test"
    )
    try
      val myResourceLoader = applicationContext.getBean(classOf[MyResourceLoader])

      assertNotNull(myResourceLoader)
      val text = myResourceLoader.getClasspathResourceAsText("hello.txt")
      assertTrue(text.isPresent)
      assertEquals("Hello!", text.get().trim)
    finally
      applicationContext.stop()
