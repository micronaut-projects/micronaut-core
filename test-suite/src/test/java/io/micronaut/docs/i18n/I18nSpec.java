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

import io.micronaut.context.MessageSource.MessageContext;
import io.micronaut.context.i18n.ResourceBundleMessageSource;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class I18nSpec {
    @Test
    void itIsPossibleToCreateAMessageSourceFromResourceBundle() {
        //tag::test[]
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource("io.micronaut.docs.i18n.messages");
        assertEquals("Hola", ms.getMessage("hello", MessageContext.of(new Locale("es"))).get());
        assertEquals("Hello", ms.getMessage("hello", MessageContext.of(Locale.ENGLISH)).get());
        //end::test[]
    }
}
