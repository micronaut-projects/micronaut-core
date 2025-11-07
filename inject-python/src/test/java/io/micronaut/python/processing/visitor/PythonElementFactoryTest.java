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
package io.micronaut.python.processing.visitor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.EnumConstantElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.python.processing.PythonAstParser;
import io.micronaut.python.processing.PythonEnvironment;
import io.micronaut.python.processing.PythonProcessingEnvironment;

class PythonElementFactoryTest {

    @Test
    void testFactoryCreation() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        try (PythonEnvironment environment = pythonProcessor.parse("""
            class TestClass:
                pass
            """);
             PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment)) {

            PythonElementFactory factory = new PythonElementFactory(processingEnvironment);

            // Verify the factory was created
            assertNotNull(factory);
        }
    }

    @Test
    void testNewClassElement() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        try (PythonEnvironment environment = pythonProcessor.parse("""
            class TestClass:
                pass
            """);
             PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment)) {

            PythonElementFactory factory = new PythonElementFactory(processingEnvironment);

            // Create a regular class
            ClassDef classDef = new ClassDef("NewTestClass");
            ClassElement classElement = factory.newClassElement(classDef, processingEnvironment.metadataFactory());

            assertNotNull(classElement);
            assertEquals("NewTestClass", classElement.getName());
            assertTrue(classElement instanceof PythonClassElement);
            assertFalse(classElement.isEnum());
        }
    }

    @Test
    void testNewEnumClassElement() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        try (PythonEnvironment environment = pythonProcessor.parse("""
            from enum import Enum

            class TestEnum(Enum):
                VALUE1 = 1
                VALUE2 = 2
            """);
             PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment)) {

            PythonElementFactory factory = new PythonElementFactory(processingEnvironment);

            // Create an enum class
            ClassDef enumDef = new ClassDef("NewTestEnum").withEnum(true, List.of("VALUE1", "VALUE2"));
            ClassElement enumElement = factory.newClassElement(enumDef, processingEnvironment.metadataFactory());

            assertNotNull(enumElement);
            assertEquals("NewTestEnum", enumElement.getName());
            assertTrue(enumElement instanceof PythonEnumElement);
            assertTrue(enumElement.isEnum());

            // Verify enum values
            PythonEnumElement pythonEnum = (PythonEnumElement) enumElement;
            assertEquals(List.of("VALUE1", "VALUE2"), pythonEnum.values());
        }
    }

    @Test
    void testNewSourceClassElement() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        try (PythonEnvironment environment = pythonProcessor.parse("""
            class TestClass:
                pass
            """);
             PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment)) {

            PythonElementFactory factory = new PythonElementFactory(processingEnvironment);

            ClassDef classDef = new ClassDef("SourceClass");
            ClassElement classElement = factory.newSourceClassElement(classDef, processingEnvironment.metadataFactory());

            assertNotNull(classElement);
            assertEquals("SourceClass", classElement.getName());
            // For Python, source and regular class elements are the same
            assertTrue(classElement instanceof PythonClassElement);
        }
    }

    @Test
    void testNewMethodElement() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        try (PythonEnvironment environment = pythonProcessor.parse("""
            class TestClass:
                pass
            """);
             PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment)) {

            PythonElementFactory factory = new PythonElementFactory(processingEnvironment);

            // Create a class and method
            ClassDef classDef = new ClassDef("TestClass");
            ClassElement classElement = factory.newClassElement(classDef, processingEnvironment.metadataFactory());

            FunctionDef methodDef = new FunctionDef("testMethod");
            MethodElement methodElement = factory.newMethodElement(classElement, methodDef, processingEnvironment.metadataFactory());

            assertNotNull(methodElement);
            assertEquals("testMethod", methodElement.getName());
            assertTrue(methodElement instanceof PythonMethodElement);

            // Verify relationships
            assertEquals(classElement, methodElement.getDeclaringType());
            assertEquals(classElement, methodElement.getOwningType());
        }
    }

    @Test
    void testNewSourceMethodElement() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        try (PythonEnvironment environment = pythonProcessor.parse("""
            class TestClass:
                pass
            """);
             PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment)) {

            PythonElementFactory factory = new PythonElementFactory(processingEnvironment);

            ClassDef classDef = new ClassDef("TestClass");
            ClassElement classElement = factory.newClassElement(classDef, processingEnvironment.metadataFactory());

            FunctionDef methodDef = new FunctionDef("sourceMethod");
            MethodElement methodElement = factory.newSourceMethodElement(classElement, methodDef, processingEnvironment.metadataFactory());

            assertNotNull(methodElement);
            assertEquals("sourceMethod", methodElement.getName());
            assertTrue(methodElement instanceof PythonMethodElement);
        }
    }

    @Test
    void testNewConstructorElement() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        try (PythonEnvironment environment = pythonProcessor.parse("""
            class TestClass:
                pass
            """);
             PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment)) {

            PythonElementFactory factory = new PythonElementFactory(processingEnvironment);

            ClassDef classDef = new ClassDef("TestClass");
            ClassElement classElement = factory.newClassElement(classDef, processingEnvironment.metadataFactory());

            // Create a constructor (__init__ method)
            FunctionDef constructorDef = new FunctionDef("__init__");
            ConstructorElement constructorElement = factory.newConstructorElement(classElement, constructorDef, processingEnvironment.metadataFactory());

            assertNotNull(constructorElement);
            assertEquals("<init>", constructorElement.getName()); // ConstructorElement overrides getName()
            assertEquals(classElement, constructorElement.getReturnType()); // Constructor returns the class type
            // Since ConstructorElement extends MethodElement, the underlying implementation is still PythonMethodElement
            assertTrue(constructorElement instanceof MethodElement);
        }
    }

    @Test
    void testNewFieldElement() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        try (PythonEnvironment environment = pythonProcessor.parse("""
            class TestClass:
                testField: str = "value"
            """);
             PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment)) {

            PythonElementFactory factory = new PythonElementFactory(processingEnvironment);

            ClassDef classDef = new ClassDef("TestClass");
            ClassElement classElement = factory.newClassElement(classDef, processingEnvironment.metadataFactory());

            AttributeDef fieldDef = new AttributeDef("testField", "str", "str", null, List.of(), null, false, null);
            FieldElement fieldElement = factory.newFieldElement(classElement, fieldDef, processingEnvironment.metadataFactory());

            assertNotNull(fieldElement);
            assertEquals("testField", fieldElement.getName());
            assertTrue(fieldElement instanceof PythonFieldElement);

            // Verify relationships
            assertEquals(classElement, fieldElement.getDeclaringType());
            assertEquals(classElement, fieldElement.getOwningType());
        }
    }

    @Test
    void testNewEnumConstantElement() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        try (PythonEnvironment environment = pythonProcessor.parse("""
            from enum import Enum

            class TestEnum(Enum):
                CONSTANT = 1
            """);
             PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment)) {

            PythonElementFactory factory = new PythonElementFactory(processingEnvironment);

            ClassDef enumDef = new ClassDef("TestEnum").withEnum(true, List.of("CONSTANT"));
            ClassElement enumElement = factory.newClassElement(enumDef, processingEnvironment.metadataFactory());

            AttributeDef constantDef = new AttributeDef("CONSTANT", "TestEnum", "TestEnum", null, List.of(), null, true, null);
            EnumConstantElement constantElement = factory.newEnumConstantElement(enumElement, constantDef, processingEnvironment.metadataFactory());

            assertNotNull(constantElement);
            assertEquals("CONSTANT", constantElement.getName());
            assertTrue(constantElement instanceof PythonEnumConstantElement);

            // Verify enum constant properties
            assertTrue(constantElement.isStatic());
            assertTrue(constantElement.isFinal());
            assertEquals(enumElement, constantElement.getType()); // Enum constants have enum type
        }
    }

    @Test
    void testNewEnumConstantElementRequiresEnumOwningClass() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        try (PythonEnvironment environment = pythonProcessor.parse("""
            class RegularClass:
                pass
            """);
             PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment)) {

            PythonElementFactory factory = new PythonElementFactory(processingEnvironment);

            // Try to create enum constant with regular class (should fail)
            ClassDef classDef = new ClassDef("RegularClass");
            ClassElement classElement = factory.newClassElement(classDef, processingEnvironment.metadataFactory());

            AttributeDef constantDef = new AttributeDef("CONSTANT");

            assertThrows(IllegalArgumentException.class, () ->
                factory.newEnumConstantElement(classElement, constantDef, processingEnvironment.metadataFactory())
            );
        }
    }

    @Test
    void testEnumElementsMethod() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        try (PythonEnvironment environment = pythonProcessor.parse("""
            from enum import Enum

            class TestEnum(Enum):
                VALUE1 = 1
                VALUE2 = 2
                VALUE3 = 3
            """);
             PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment)) {

            PythonElementFactory factory = new PythonElementFactory(processingEnvironment);

            ClassDef enumDef = new ClassDef("TestEnum").withEnum(true, List.of("VALUE1", "VALUE2", "VALUE3"));
            ClassElement enumElement = factory.newClassElement(enumDef, processingEnvironment.metadataFactory());

            assertTrue(enumElement instanceof PythonEnumElement);

            PythonEnumElement pythonEnum = (PythonEnumElement) enumElement;
            List<EnumConstantElement> elements = pythonEnum.elements();

            assertNotNull(elements);
            assertEquals(3, elements.size());

            // Check each enum constant
            assertEquals("VALUE1", elements.get(0).getName());
            assertEquals("VALUE2", elements.get(1).getName());
            assertEquals("VALUE3", elements.get(2).getName());

            // Verify they are static and final
            for (EnumConstantElement constant : elements) {
                assertTrue(constant.isStatic());
                assertTrue(constant.isFinal());
                assertEquals(enumElement, constant.getType()); // Type should be the enum
            }
        }
    }


}
