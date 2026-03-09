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
package io.micronaut.annotation.processing.visitor;

import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Java port of JavaVisitorSpec.
 */
class JavaVisitorTest extends AbstractTypeElementTest {
    private List<TypeElementVisitor<?, ?>> localTypeElementVisitors;

    @Override
    protected Collection<TypeElementVisitor<?, ?>> getLocalTypeElementVisitors() {
        return localTypeElementVisitors == null ? List.of() : localTypeElementVisitors;
    }

    @Test
    void visitEnumMethods() {
        List<MethodElement> methods = new ArrayList<>();

        TypeElementVisitor<Object, Object> enumVisitor = new TypeElementVisitor<>() {
            @Override
            public void visitMethod(MethodElement element, VisitorContext context) {
                methods.add(element);
            }
        };
        localTypeElementVisitors = List.of(enumVisitor);

        buildClassLoader("example.Foo", """
            package example;
            enum Foo {A, B;
                public String getValue() {
                    return this == A ? "AA" : "BB";
                }
            }
            """);

        methods.sort(Comparator.comparing(MethodElement::getName));

        assertEquals(3, methods.size());
        assertEquals("getValue", methods.get(0).getName());
        assertEquals("java.lang.String", methods.get(0).getReturnType().getName());
        assertEquals(0, methods.get(0).getParameters().length);

        assertEquals("valueOf", methods.get(1).getName());
        assertEquals("values", methods.get(2).getName());
    }
}
