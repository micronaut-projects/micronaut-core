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

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Java port of JavaVisitorContextSpec.
 */
class JavaVisitorContextTest extends AbstractTypeElementTest {
    private List<TypeElementVisitor<?, ?>> localTypeElementVisitors;

    @Override
    protected Collection<TypeElementVisitor<?, ?>> getLocalTypeElementVisitors() {
        return localTypeElementVisitors == null ? List.of() : localTypeElementVisitors;
    }

    @Test
    void returnEnumFromGetClassElement() {
        final ClassElement[] originalHolder = new ClassElement[1];
        final Optional<ClassElement>[] lookedUpHolder = new Optional[1];

        TypeElementVisitor<Object, Object> enumVisitor = new TypeElementVisitor<>() {
            @Override
            public void visitClass(ClassElement element, VisitorContext context) {
                originalHolder[0] = element;
                lookedUpHolder[0] = context.getClassElement(element.getName());
            }
        };
        localTypeElementVisitors = List.of(enumVisitor);

        buildClassLoader("example.Foo", """
            package example;
            enum Foo {}
            """);

        assertNotNull(lookedUpHolder[0]);
        assertTrue(lookedUpHolder[0].isPresent());
        assertTrue(lookedUpHolder[0].get().isEnum());

        assertNotNull(originalHolder[0]);
        assertEquals("example.Foo", originalHolder[0].getName());
        assertEquals("example.Foo", lookedUpHolder[0].get().getName());
    }

    @Test
    void returnEnumFromGetClassElements() {
        final ClassElement[][] lookedUpHolder = new ClassElement[1][];

        TypeElementVisitor<Object, Object> enumVisitor = new TypeElementVisitor<>() {
            @Override
            public void visitClass(ClassElement element, VisitorContext context) {
                lookedUpHolder[0] = context.getClassElements(element.getPackageName(), "*");
            }
        };
        localTypeElementVisitors = List.of(enumVisitor);

        buildClassLoader("example.Foo", """
            package example;
            enum Foo {}
            """);

        assertNotNull(lookedUpHolder[0]);
        assertEquals(1, lookedUpHolder[0].length);
        assertTrue(lookedUpHolder[0][0].isEnum());
        assertEquals("example.Foo", lookedUpHolder[0][0].getName());
    }

    @Test
    void returnInnerClassFromGetClassElements() {
        final ClassElement[][] lookedUpHolder = new ClassElement[1][];

        TypeElementVisitor<Object, Object> typeVisitor = new TypeElementVisitor<>() {
            @Override
            public void visitClass(ClassElement element, VisitorContext context) {
                lookedUpHolder[0] = context.getClassElements(element.getPackageName(), "*");
            }
        };
        localTypeElementVisitors = List.of(typeVisitor);

        buildClassLoader("example.Foo", """
            package example;
            class Foo {
              class Bar {}
            }
            """);

        assertNotNull(lookedUpHolder[0]);
        assertEquals(2, lookedUpHolder[0].length);

        Set<String> names = Set.of(lookedUpHolder[0][0].getName(), lookedUpHolder[0][1].getName());
        assertTrue(names.contains("example.Foo"));
        assertTrue(names.contains("example.Foo$Bar"));
    }
}
