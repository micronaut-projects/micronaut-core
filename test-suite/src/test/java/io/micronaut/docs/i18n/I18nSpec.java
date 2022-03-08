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
package io.micronaut.docs.i18n;

import io.micronaut.context.MessageSource;
import io.micronaut.context.MessageSource.MessageContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(startApplication = false)
class I18nSpec {

    @Inject
    MessageSource messageSource;

    @Test
    void itIsPossibleToCreateAMessageSourceFromResourceBundle() {
        //tag::test[]
        assertEquals("Hola", messageSource.getMessage("hello", MessageContext.of(new Locale("es"))).get());
        assertEquals("Hello", messageSource.getMessage("hello", MessageContext.of(Locale.ENGLISH)).get());
        //end::test[]

        assertTrue(messageSource.getMessage("hello", new Locale("es")).isPresent());
        assertEquals("Hola", messageSource.getMessage("hello", new Locale("es")).get());
        assertEquals("Hello", messageSource.getMessage("hello", Locale.ENGLISH).get());
        assertTrue(messageSource.getMessage("hello", Locale.ENGLISH).isPresent());

        assertTrue(messageSource.getMessage("hello.name", new Locale("es"), "Sergio").isPresent());
        assertEquals("Hola Sergio", messageSource.getMessage("hello.name", new Locale("es"), "Sergio").get());
        assertTrue(messageSource.getMessage("hello.name", Locale.ENGLISH, "Sergio").isPresent());
        assertEquals("Hello Sergio", messageSource.getMessage("hello.name", Locale.ENGLISH, "Sergio").get());

        assertTrue(messageSource.getMessage("hello.name", new Locale("es"), Collections.singletonMap("0", "Sergio")).isPresent());
        assertEquals("Hola Sergio", messageSource.getMessage("hello.name", new Locale("es"), Collections.singletonMap("0", "Sergio")).get());
        assertTrue(messageSource.getMessage("hello.name", Locale.ENGLISH, Collections.singletonMap("0", "Sergio")).isPresent());
        assertEquals("Hello Sergio", messageSource.getMessage("hello.name", Locale.ENGLISH, Collections.singletonMap("0", "Sergio")).get());
    }
}
