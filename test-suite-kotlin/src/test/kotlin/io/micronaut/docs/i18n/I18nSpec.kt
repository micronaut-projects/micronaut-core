/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.context.MessageSource
import io.micronaut.context.i18n.ResourceBundleMessageSource
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.*

@MicronautTest(startApplication = false)
class I18nTest {
    @Inject
    lateinit var messageSource: MessageSource

    @Test
    fun itIsPossibleToCreateAMessageSourceFromResourceBundle() {
        //tag::test[]
        Assertions.assertEquals("Hola", messageSource.getMessage("hello", MessageSource.MessageContext.of(Locale("es"))).get())
        Assertions.assertEquals("Hello", messageSource.getMessage("hello", MessageSource.MessageContext.of(Locale.ENGLISH)).get())
        //end::test[]

        Assertions.assertEquals("Hola", messageSource.getMessage("hello", Locale("es")).get())
        Assertions.assertEquals("Hello", messageSource.getMessage("hello", Locale.ENGLISH).get())

        Assertions.assertTrue(messageSource.getMessage("hello.name", Locale("es"), "Sergio").isPresent)
        Assertions.assertEquals("Hola Sergio", messageSource.getMessage("hello.name", Locale("es"), "Sergio").get())
        Assertions.assertTrue(messageSource.getMessage("hello.name", Locale.ENGLISH, "Sergio").isPresent)
        Assertions.assertEquals("Hello Sergio", messageSource.getMessage("hello.name", Locale.ENGLISH, "Sergio").get())

        Assertions.assertTrue(messageSource.getMessage("hello.name", Locale("es"), mapOf(Pair("0", "Sergio"))).isPresent)
        Assertions.assertEquals(
            "Hola Sergio",
            messageSource.getMessage("hello.name", Locale("es"), mapOf(Pair("0", "Sergio"))).get()
        )
        Assertions.assertTrue(messageSource.getMessage("hello.name", Locale.ENGLISH, mapOf(Pair("0", "Sergio"))).isPresent)
        Assertions.assertEquals(
            "Hello Sergio",
            messageSource.getMessage("hello.name", Locale.ENGLISH, mapOf(Pair("0", "Sergio"))).get()
        )
    }
}