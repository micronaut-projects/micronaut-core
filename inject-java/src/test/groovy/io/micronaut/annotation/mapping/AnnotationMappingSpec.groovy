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
package io.micronaut.annotation.mapping

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class AnnotationMappingSpec extends AbstractTypeElementSpec {

    void "test is mapped"() {
        given:
            def elementMapped = buildClassElement("""
package test;

import java.lang.annotation.*;

class MyEntity {

    @io.micronaut.annotation.mapping.CustomEmbeddedId
    private Key id;

    public MyEntity(final Key id) {
        this.id = id;
    }

    public Key getId() {
        return this.id;
    }

    public void setId(final Key id) {
        this.id = id;
    }

}
class Key {
}
""")
            def elementNotMapped = buildClassElement("""
package test;

import java.lang.annotation.*;

class MyEntity {

    @io.micronaut.annotation.mapping.EmbeddedId
    private Key id;

    public MyEntity(final Key id) {
        this.id = id;
    }

    public Key getId() {
        return this.id;
    }

    public void setId(final Key id) {
        this.id = id;
    }

}
class Key {
}
""")
        def idWithMapped = elementMapped.getBeanProperties().get(0)
        def idWithoutMapped = elementNotMapped.getBeanProperties().get(0)
        expect:
            idWithMapped.hasStereotype(Id.class)
            idWithMapped.hasStereotype(EmbeddedId.class)
            idWithoutMapped.hasStereotype(Id.class)
            idWithoutMapped.hasStereotype(EmbeddedId.class)
            idWithMapped.getAnnotationNames() == (idWithoutMapped.getAnnotationNames() + ["io.micronaut.annotation.mapping.CustomEmbeddedId"])
            idWithMapped.getDeclaredAnnotationNames() == (idWithoutMapped.getDeclaredAnnotationNames() + ["io.micronaut.annotation.mapping.CustomEmbeddedId"])
    }

    void "test @NonNull stereotype from @Nullable"() {
        when:
        def pojo = buildBeanIntrospection('test.Pojo', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.annotation.mapping.NonNullStereotyped;

@Introspected
public final class Pojo {
    @NonNullStereotyped
    private final String surname;

    public Pojo(String surname) {
        this.surname = surname;
    }

    public String getSurname() {
        return this.surname;
    }
}
''')
        then:
        !pojo.getProperty("surname").get().isNonNull()
    }

}
