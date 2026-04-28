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
package io.micronaut.docs.ioc.introspection;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntrospectedPropertyTest {

    @Test
    void testIntrospectedPropertyMetadata() {
        // tag::metadata[]
        BeanIntrospection<Book> introspection = BeanIntrospection.getIntrospection(Book.class);
        BeanProperty<Book, String> property = introspection.getRequiredProperty("title", String.class);
        Optional<String> externalName = property.stringValue(Introspected.Property.class, "name");
        // end::metadata[]

        assertEquals("book_title", externalName.orElseThrow());
    }

    @Test
    void testJacksonPropertyMetadata() {
        BeanIntrospection<User> introspection = BeanIntrospection.getIntrospection(User.class);
        BeanProperty<User, String> property = introspection.getRequiredProperty("displayName", String.class);

        assertEquals("display_name", property.stringValue(Introspected.Property.class, "name").orElseThrow());
    }
}
