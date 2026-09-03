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
package io.micronaut.docs.replaces

import io.micronaut.context.ApplicationContext
import io.micronaut.docs.requires.Book
import io.micronaut.docs.requires.BookService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class ReplacesTest:

  @Test
  def testReplaces(): Unit =
    val applicationContext = ApplicationContext.run(
      Map[String, Object]("spec.name" -> "ReplacesTest").asJava
    )
    try
      assertTrue(applicationContext.getBean(classOf[BookService]).isInstanceOf[MockBookService])
      assertEquals("An OK Novel", applicationContext.getBean(classOf[Book]).title)
      assertEquals("Learning 305", applicationContext.getBean(classOf[TextBook]).title)
    finally applicationContext.close()
