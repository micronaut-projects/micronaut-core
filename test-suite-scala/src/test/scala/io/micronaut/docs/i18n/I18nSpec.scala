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
package io.micronaut.docs.i18n

import io.micronaut.context.ApplicationContext
import io.micronaut.context.MessageSource
import io.micronaut.context.MessageSource.MessageContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.util.Collections
import java.util.Locale
import scala.jdk.CollectionConverters.*

class I18nSpec:

  @Test
  def itIsPossibleToCreateAMessageSourceFromResourceBundle(): Unit =
    val context = ApplicationContext.run(
      Map[String, Object]("spec.name" -> "I18nSpec").asJava
    )
    try
      val messageSource = context.getBean(classOf[MessageSource])
      val spanish = Locale.forLanguageTag("es")

      //tag::test[]
      assertEquals("Hola", messageSource.getMessage("hello", MessageContext.of(spanish)).get())
      assertEquals("Hello", messageSource.getMessage("hello", MessageContext.of(Locale.ENGLISH)).get())
      //end::test[]

      assertTrue(messageSource.getMessage("hello", spanish).isPresent)
      assertEquals("Hola", messageSource.getMessage("hello", spanish).get())
      assertEquals("Hello", messageSource.getMessage("hello", Locale.ENGLISH).get())
      assertTrue(messageSource.getMessage("hello", Locale.ENGLISH).isPresent)

      assertTrue(messageSource.getMessage("hello.name", spanish, "Sergio").isPresent)
      assertEquals("Hola Sergio", messageSource.getMessage("hello.name", spanish, "Sergio").get())
      assertTrue(messageSource.getMessage("hello.name", Locale.ENGLISH, "Sergio").isPresent)
      assertEquals("Hello Sergio", messageSource.getMessage("hello.name", Locale.ENGLISH, "Sergio").get())

      assertTrue(messageSource.getMessage("hello.name", spanish, Collections.singletonMap("0", "Sergio")).isPresent)
      assertEquals("Hola Sergio", messageSource.getMessage("hello.name", spanish, Collections.singletonMap("0", "Sergio")).get())
      assertTrue(messageSource.getMessage("hello.name", Locale.ENGLISH, Collections.singletonMap("0", "Sergio")).isPresent)
      assertEquals("Hello Sergio", messageSource.getMessage("hello.name", Locale.ENGLISH, Collections.singletonMap("0", "Sergio")).get())
    finally context.close()
