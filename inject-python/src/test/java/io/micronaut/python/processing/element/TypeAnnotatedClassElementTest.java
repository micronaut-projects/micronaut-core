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
package io.micronaut.python.processing.element;

import io.micronaut.annotation.processing.visitor.ElementProvider;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.python.processing.PythonAstParser;
import io.micronaut.python.processing.PythonEnvironment;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import org.junit.jupiter.api.Test;

import javax.lang.model.element.TypeElement;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TypeAnnotatedClassElementTest {

    @Test
    void providesTheNativeElementOfTheWrappedType() {
        TypeElement originatingElement = (TypeElement) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {TypeElement.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "Origin";
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        PythonAstParser pythonProcessor = new PythonAstParser();
        try (PythonEnvironment environment = pythonProcessor.parse("""
            from enum import Enum

            class Color(Enum):
                RED = "red"

            class Shape:
                sides: int = 3

            class Holder:
                color: Color | None = None
                shape: Shape | None = None

                def favourite(self) -> Color | None:
                    return self.color
            """, "example");
             PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment, null, originatingElement)) {
            ClassElement holder = processingEnvironment.classes().get("example.Holder");
            assertNotNull(holder);

            ClassElement color = field(holder, "color").getType();
            ClassElement shape = field(holder, "shape").getType();
            ClassElement favourite = holder.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared().named("favourite"))
                .stream()
                .findFirst()
                .map(MethodElement::getReturnType)
                .orElseThrow();

            assertInstanceOf(TypeAnnotatedEnumElement.class, color);
            assertInstanceOf(TypeAnnotatedClassElement.class, shape);
            assertInstanceOf(TypeAnnotatedEnumElement.class, favourite);
            assertSame(originatingElement, assertInstanceOf(ElementProvider.class, color).element());
            assertSame(originatingElement, assertInstanceOf(ElementProvider.class, shape).element());
            assertSame(originatingElement, assertInstanceOf(ElementProvider.class, favourite).element());
        }
    }

    private static FieldElement field(ClassElement owner, String name) {
        return owner.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyDeclared().named(name))
            .stream()
            .findFirst()
            .orElseThrow();
    }
}
