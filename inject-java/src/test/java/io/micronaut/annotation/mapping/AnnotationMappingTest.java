/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.annotation.mapping;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.test.AbstractTypeElementTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Java port of AnnotationMappingSpec.
 */
class AnnotationMappingTest extends AbstractTypeElementTest {

    @Test
    void testIsMapped() {
        ClassElement elementMapped = buildClassElement("""
            package test;

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
            """);

        ClassElement elementNotMapped = buildClassElement("""
            package test;

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
            """);

        PropertyElement idWithMapped = elementMapped.getBeanProperties().get(0);
        PropertyElement idWithoutMapped = elementNotMapped.getBeanProperties().get(0);

        assertTrue(idWithMapped.hasStereotype("io.micronaut.annotation.mapping.Id"));
        assertTrue(idWithMapped.hasStereotype("io.micronaut.annotation.mapping.EmbeddedId"));
        assertTrue(idWithoutMapped.hasStereotype("io.micronaut.annotation.mapping.Id"));
        assertTrue(idWithoutMapped.hasStereotype("io.micronaut.annotation.mapping.EmbeddedId"));

        List<String> withMapped = new ArrayList<>(idWithMapped.getAnnotationNames());
        List<String> withoutMapped = new ArrayList<>(idWithoutMapped.getAnnotationNames());
        List<String> expected = new ArrayList<>(withoutMapped);
        expected.add("io.micronaut.annotation.mapping.CustomEmbeddedId");
        assertEquals(expected, withMapped);

        List<String> withMappedDeclared = new ArrayList<>(idWithMapped.getDeclaredAnnotationNames());
        List<String> withoutMappedDeclared = new ArrayList<>(idWithoutMapped.getDeclaredAnnotationNames());
        List<String> expectedDeclared = new ArrayList<>(withoutMappedDeclared);
        expectedDeclared.add("io.micronaut.annotation.mapping.CustomEmbeddedId");
        assertEquals(expectedDeclared, withMappedDeclared);
    }

    @Test
    void testNonNullStereotypeFromNullable() {
        var pojo = buildBeanIntrospection("test.Pojo", """
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
            """);

        assertFalse(pojo.getProperty("surname").orElseThrow().isNonNull());
    }
}
