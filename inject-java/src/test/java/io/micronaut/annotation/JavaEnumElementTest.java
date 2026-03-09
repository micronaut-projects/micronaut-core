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
package io.micronaut.annotation;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.EnumConstantElement;
import io.micronaut.inject.ast.EnumElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.test.AbstractTypeElementTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaEnumElementTest extends AbstractTypeElementTest {

    @Test
    void testIsEnum() {
        buildClassElement("""
            package test;

            enum MyEnum {
                A, B
            }
            """, element -> {
            assertTrue(element.isEnum());
            assertTrue(element.getPrimaryConstructor().orElseThrow().getDeclaringType().isEnum());
            return null;
        });
    }

    @Test
    void testInnerEnumIsEnum() {
        buildClassElement("""
            package test;

            class Foo {

                enum MyEnum {
                    A, B
                }
            }
            """, element -> {
            ClassElement inner = element.getEnclosedElement(ElementQuery.of(ClassElement.class)).orElseThrow();
            assertTrue(inner.getPrimaryConstructor().orElseThrow().getDeclaringType().isEnum());
            return null;
        });
    }

    @Test
    void getEnumConstantAnnotation() {
        buildClassElement("""
            package test;

            enum MyEnum {
                @io.micronaut.annotation.EnumConstantAnn("C")
                A,
                B
            }
            """, element -> {
            EnumElement enumElement = (EnumElement) element;
            boolean seen = false;
            for (EnumConstantElement constant : enumElement.elements()) {
                if (!constant.getName().equals("A")) {
                    continue;
                }
                var ann = constant.getAnnotation(EnumConstantAnn.class);
                assertNotNull(ann);
                assertEquals("C", ann.stringValue().orElseThrow());
                seen = true;
            }
            assertTrue(seen, "Expected to see enum constant A");
            return null;
        });
    }

    @Test
    void testEnumValues() {
        buildClassElement("""
            package test;

            enum MyEnum {
                A, B
            }
            """, element -> {
            EnumElement enumElement = (EnumElement) element;
            assertEquals(2, enumElement.values().size());
            return null;
        });
    }

    @Test
    void getEnumConstantValueForFinalFields() {
        buildClassElement("""
            package test;

            enum MyEnum {

              ENUM_VAL1(10),
              ENUM_VAL2(11),
              UNRECOGNIZED(-1),
              ;

              public static final int ENUM_VAL1_VALUE = 10;
              public static final int ENUM_VAL2_VALUE = 11;

              private final int value;

              MyEnum(int value) {
                this.value = value;
              }
            }
            """, element -> {
            EnumElement enumElement = (EnumElement) element;
            for (FieldElement field : enumElement.getFields()) {
                if (field.getName().equals("ENUM_VAL1_VALUE")) {
                    assertEquals(10, field.getConstantValue());
                } else if (field.getName().equals("ENUM_VAL2_VALUE")) {
                    assertEquals(11, field.getConstantValue());
                }
            }
            return null;
        });
    }
}
