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
import spock.lang.Specification

class I18nSpec extends Specification {

    void "it is possible to create a MessageSource from resource bundle"() {
        //tag::test[]
        given:
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource("io.micronaut.docs.i18n.messages")

        expect:
        ms.getMessage("hello", MessageSource.MessageContext.of(new Locale("es"))).get() == 'Hola'

        and:
        ms.getMessage("hello", MessageSource.MessageContext.of(Locale.ENGLISH)).get() == 'Hello'
        //end::test[]
    }
}
