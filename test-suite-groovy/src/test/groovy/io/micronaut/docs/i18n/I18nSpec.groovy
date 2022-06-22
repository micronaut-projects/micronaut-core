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
import io.micronaut.context.MessageSource.MessageContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(startApplication = false)
class I18nSpec extends Specification {

    @Inject
    MessageSource messageSource

    void "it is possible to create a MessageSource from resource bundle"() {
        //tag::test[]
        expect:
        messageSource.getMessage("hello", MessageContext.of(new Locale("es"))).get() == 'Hola'

        and:
        messageSource.getMessage("hello", MessageContext.of(Locale.ENGLISH)).get() == 'Hello'
        //end::test[]

        messageSource.getMessage("hello", new Locale("es")).isPresent()
        "Hola" == messageSource.getMessage("hello", new Locale("es")).get()
        "Hello" == messageSource.getMessage("hello", Locale.ENGLISH).get()
        messageSource.getMessage("hello", Locale.ENGLISH).isPresent()

        messageSource.getMessage("hello.name", new Locale("es"), "Sergio").isPresent()
        "Hola Sergio" == messageSource.getMessage("hello.name", new Locale("es"), "Sergio").get()
        messageSource.getMessage("hello.name", Locale.ENGLISH, "Sergio").isPresent()
        "Hello Sergio" == messageSource.getMessage("hello.name", Locale.ENGLISH, "Sergio").get()

        messageSource.getMessage("hello.name", new Locale("es"), ["0": "Sergio"]).isPresent()
        "Hola Sergio" == messageSource.getMessage("hello.name", new Locale("es"), ["0": "Sergio"]).get()
        messageSource.getMessage("hello.name", Locale.ENGLISH, ["0": "Sergio"]).isPresent()
        "Hello Sergio" == messageSource.getMessage("hello.name", Locale.ENGLISH, ["0": "Sergio"]).get()
    }
}
