package io.micronaut.python.processing;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.python.processing.visitor.AbstractPythonClassElement;
import io.micronaut.python.processing.visitor.ArgumentDef;
import io.micronaut.python.processing.visitor.ArgumentsDef;
import io.micronaut.python.processing.visitor.ClassDef;
import io.micronaut.python.processing.visitor.FunctionDef;
import io.micronaut.python.processing.visitor.PythonClassElement;
import io.micronaut.python.processing.visitor.PythonEnumElement;
import io.micronaut.python.processing.visitor.PythonFieldElement;
import io.micronaut.python.processing.visitor.PythonMethodElement;
import jakarta.inject.Named;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.micronaut.inject.ast.ParameterElement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PythonAstParserTest {

    @Test
    void testParse() {
        try (PythonEnvironment environment = buildEnv()) {
            ClassDef myClass = environment.classes().get("MyClass");

            assertNotNull(myClass);
            assertEquals(1, myClass.functions().size());
            assertEquals("f", myClass.functions().get(0).name());

            assertEquals(3, environment.decorators().size());

            assertTrue(environment.decorators().containsKey(Singleton.class.getName()));
            assertTrue(environment.decorators().containsKey(Scope.class.getName()));
            assertTrue(environment.decorators().containsKey(Named.class.getName()));
        }
    }

    @Test
    void testProcessingEnvironment() {
        try (PythonEnvironment environment = buildEnv();
             PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment)) {

            Map<String, ClassElement> classes = processingEnvironment.classes();
            assertNotNull(classes);
            assertEquals(1, classes.size());

            ClassElement myClass = classes.get("MyClass");

            assertNotNull(myClass);
            assertEquals("MyClass", myClass.getName());
        }
    }

    @Test
    void testAttributeParsing() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        try (PythonEnvironment environment = pythonProcessor.parse("""
            from typing import Final, Annotated

            class TestClass:
                # Simple attribute
                simple_attr = 42

                # String attribute
                name = "test"

                # Annotated attribute
                annotated_attr: int = 100

                # Attribute with typing
                final_attr: Final[int] = 200

                # Attribute with complex type annotation
                complex_attr: Annotated[str, "metadata"] = "value"

                # Instance attribute (not parsed as field)
                def __init__(self):
                    self.instance_attr = "not a field"

                # Property (should be ignored for fields)
                @property
                def computed_prop(self):
                    return self.instance_attr

                def regular_method(self):
                    pass
            """)) {

            // Verify parsing completes successfully
            assertNotNull(environment);
            assertEquals(1, environment.classes().size());

            ClassDef testClass = environment.classes().get("TestClass");
            assertNotNull(testClass);
            assertEquals("TestClass", testClass.name());

            // Verify attributes are parsed and stored
            assertEquals(5, testClass.attributes().size(), "Should parse 5 class attributes");

            // Check specific attributes
            var simpleAttr = testClass.attributes().stream()
                .filter(attr -> "simple_attr".equals(attr.name()))
                .findFirst();
            assertTrue(simpleAttr.isPresent(), "simple_attr should be parsed");
            assertEquals(42, simpleAttr.get().value().asInt(), "simple_attr should have value 42");
            assertNull(simpleAttr.get().annotation(), "simple_attr should have no annotation");

            var nameAttr = testClass.attributes().stream()
                .filter(attr -> "name".equals(attr.name()))
                .findFirst();
            assertTrue(nameAttr.isPresent(), "name attribute should be parsed");
            assertEquals("test", nameAttr.get().value().asString(), "name should have value 'test'");

            var annotatedAttr = testClass.attributes().stream()
                .filter(attr -> "annotated_attr".equals(attr.name()))
                .findFirst();
            assertTrue(annotatedAttr.isPresent(), "annotated_attr should be parsed");
            assertEquals("int", annotatedAttr.get().annotation(), "annotated_attr should have int annotation");
            assertEquals(100, annotatedAttr.get().value().asInt(), "annotated_attr should have value 100");

            var finalAttr = testClass.attributes().stream()
                .filter(attr -> "final_attr".equals(attr.name()))
                .findFirst();
            assertTrue(finalAttr.isPresent(), "final_attr should be parsed");
            assertTrue(finalAttr.get().annotation().contains("Final"), "final_attr should have Final annotation");
            assertEquals(200, finalAttr.get().value().asInt(), "final_attr should have value 200");

            var complexAttr = testClass.attributes().stream()
                .filter(attr -> "complex_attr".equals(attr.name()))
                .findFirst();
            assertTrue(complexAttr.isPresent(), "complex_attr should be parsed");
            assertTrue(complexAttr.get().annotation().contains("Annotated"), "complex_attr should have Annotated annotation");
            assertEquals("value", complexAttr.get().value().asString(), "complex_attr should have value 'value'");

            // Should still parse the regular method (properties are ignored)
            assertEquals(1, testClass.functions().size());
            assertEquals("regular_method", testClass.functions().get(0).name());
        }
    }

    @Test
    void testPrimitiveTypeResolution() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        try (PythonEnvironment environment = pythonProcessor.parse("""
            class TestPrimitives:
                int_field: int = 42
                float_field: float = 3.14
                str_field: str = "hello"
                bool_field: bool = True
                complex_field: list = []
            """)) {

            try (PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment)) {
                // Get the class definition
                ClassDef testClassDef = environment.classes().get("TestPrimitives");
                assertNotNull(testClassDef);

                // Get the class element
                ClassElement testClassElement = processingEnvironment.classes().get("TestPrimitives");
                assertNotNull(testClassElement);
                assertTrue(testClassElement instanceof PythonClassElement, "TestPrimitives should be a PythonClassElement");
                PythonClassElement testClass = (PythonClassElement) testClassElement;

                // First verify the attributes are parsed with correct annotations
                var intAttr = testClassDef.attributes().stream()
                    .filter(attr -> "int_field".equals(attr.name()))
                    .findFirst();
                assertTrue(intAttr.isPresent(), "int_field should be present");
                assertEquals("int", intAttr.get().annotation(), "int_field should have int annotation");

                var floatAttr = testClassDef.attributes().stream()
                    .filter(attr -> "float_field".equals(attr.name()))
                    .findFirst();
                assertTrue(floatAttr.isPresent(), "float_field should be present");
                assertEquals("float", floatAttr.get().annotation(), "float_field should have float annotation");

                var strAttr = testClassDef.attributes().stream()
                    .filter(attr -> "str_field".equals(attr.name()))
                    .findFirst();
                assertTrue(strAttr.isPresent(), "str_field should be present");
                assertEquals("str", strAttr.get().annotation(), "str_field should have str annotation");

                var boolAttr = testClassDef.attributes().stream()
                    .filter(attr -> "bool_field".equals(attr.name()))
                    .findFirst();
                assertTrue(boolAttr.isPresent(), "bool_field should be present");
                assertEquals("bool", boolAttr.get().annotation(), "bool_field should have bool annotation");

                var complexAttr = testClassDef.attributes().stream()
                    .filter(attr -> "complex_field".equals(attr.name()))
                    .findFirst();
                assertTrue(complexAttr.isPresent(), "complex_field should be present");
                assertEquals("list", complexAttr.get().annotation(), "complex_field should have list annotation");

                // Now test that PythonFieldElement resolves types correctly
                PythonFieldElement intField = new PythonFieldElement(intAttr.get(), processingEnvironment, testClass, processingEnvironment.metadataFactory());
                assertEquals("int", intField.getType().getName(), "int field should resolve to primitive int");

                PythonFieldElement floatField = new PythonFieldElement(floatAttr.get(), processingEnvironment, testClass, processingEnvironment.metadataFactory());
                assertEquals("double", floatField.getType().getName(), "float field should resolve to primitive double");

                PythonFieldElement strField = new PythonFieldElement(strAttr.get(), processingEnvironment, testClass, processingEnvironment.metadataFactory());
                assertEquals("java.lang.String", strField.getType().getName(), "str field should resolve to String");

                PythonFieldElement boolField = new PythonFieldElement(boolAttr.get(), processingEnvironment, testClass, processingEnvironment.metadataFactory());
                assertEquals("boolean", boolField.getType().getName(), "bool field should resolve to primitive boolean");

                PythonFieldElement complexField = new PythonFieldElement(complexAttr.get(), processingEnvironment, testClass, processingEnvironment.metadataFactory());
                assertEquals("java.lang.Object", complexField.getType().getName(), "complex field should fall back to Object");
            }
        }
    }

    @Test
    void testMethodElementImplementation() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        try (PythonEnvironment environment = pythonProcessor.parse("""
            class TestMethods:
                def public_method(self, x: int, y: str = "default") -> bool:
                    return True

                def _private_method(self, _arg: float) -> str:
                    return "private"

                def no_annotations(self, arg1, arg2):
                    pass

                def return_only(self) -> int:
                    return 42
            """)) {

            try (PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment)) {
                // Get the class definition
                ClassDef testClassDef = environment.classes().get("TestMethods");
                PythonClassElement testClass = new PythonClassElement(testClassDef, processingEnvironment);
                assertNotNull(testClassDef);

                // Test that we can create method elements directly from function definitions
                var publicMethodDef = testClassDef.functions().stream()
                    .filter(func -> "public_method".equals(func.name()))
                    .findFirst();
                assertTrue(publicMethodDef.isPresent(), "public_method should be present");

                PythonMethodElement publicMethod = new PythonMethodElement(publicMethodDef.get(), processingEnvironment, testClass, processingEnvironment.metadataFactory());

                // Test method properties
                assertEquals("public_method", publicMethod.getName());
                assertTrue(publicMethod.isPublic(), "public_method should be public");
                assertFalse(publicMethod.isPrivate(), "public_method should not be private");

                // Test return type
                assertEquals("boolean", publicMethod.getReturnType().getName(), "return type should be boolean");

                // Test parameters
                ParameterElement[] params = publicMethod.getParameters();
                assertEquals(2, params.length, "should have 2 parameters");

                assertEquals("x", params[0].getName(), "first parameter should be x");
                assertEquals("int", params[0].getType().getName(), "first parameter should be int");

                assertEquals("y", params[1].getName(), "second parameter should be y");
                assertEquals("java.lang.String", params[1].getType().getName(), "second parameter should be String");

                // Test private method
                var privateMethodDef = testClassDef.functions().stream()
                    .filter(func -> "_private_method".equals(func.name()))
                    .findFirst();
                assertTrue(privateMethodDef.isPresent(), "_private_method should be present");

                PythonMethodElement privateMethod = new PythonMethodElement(privateMethodDef.get(), processingEnvironment, testClass, processingEnvironment.metadataFactory());
                assertFalse(privateMethod.isPublic(), "_private_method should not be public");
                assertTrue(privateMethod.isPrivate(), "_private_method should be private");

                // Test method with no annotations
                var noAnnotationsDef = testClassDef.functions().stream()
                    .filter(func -> "no_annotations".equals(func.name()))
                    .findFirst();
                assertTrue(noAnnotationsDef.isPresent(), "no_annotations should be present");

                PythonMethodElement noAnnotationsMethod = new PythonMethodElement(noAnnotationsDef.get(), processingEnvironment, testClass, processingEnvironment.metadataFactory());
                assertEquals(2, noAnnotationsMethod.getParameters().length, "should have 2 parameters");
                assertEquals("java.lang.Object", noAnnotationsMethod.getReturnType().getName(), "return type should be Object");

                // Test return-only method
                var returnOnlyDef = testClassDef.functions().stream()
                    .filter(func -> "return_only".equals(func.name()))
                    .findFirst();
                assertTrue(returnOnlyDef.isPresent(), "return_only should be present");

                PythonMethodElement returnOnlyMethod = new PythonMethodElement(returnOnlyDef.get(), processingEnvironment, testClass, processingEnvironment.metadataFactory());
                assertEquals("int", returnOnlyMethod.getReturnType().getName(), "return type should be int");
                assertEquals(0, returnOnlyMethod.getParameters().length, "should have no parameters");
            }
        }
    }

    @Test
    void testPythonEnumParsing() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        try (PythonEnvironment environment = pythonProcessor.parse("""
            from enum import Enum

            class Color(Enum):
                RED = 1
                GREEN = 2
                BLUE = 3

            class Status(Enum):
                ACTIVE = "active"
                INACTIVE = "inactive"
                PENDING = "pending"
            """)) {

            try (PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment)) {
                // Verify parsing completes successfully
                assertNotNull(environment);
                assertEquals(2, environment.classes().size());

                // Test Color enum
                ClassDef colorClassDef = environment.classes().get("Color");
                assertNotNull(colorClassDef);
                assertTrue(colorClassDef.isEnum(), "Color should be identified as an enum");
                assertEquals(List.of("RED", "GREEN", "BLUE"), colorClassDef.values(), "Color should have correct enum values");

                // Test Status enum
                ClassDef statusClassDef = environment.classes().get("Status");
                assertNotNull(statusClassDef);
                assertTrue(statusClassDef.isEnum(), "Status should be identified as an enum");
                assertEquals(List.of("ACTIVE", "INACTIVE", "PENDING"), statusClassDef.values(), "Status should have correct enum values");

                // Test that processing environment creates PythonEnumElement for enums
                Map<String, ClassElement> classes = processingEnvironment.classes();
                assertEquals(2, classes.size());

                ClassElement colorElement = classes.get("Color");
                assertNotNull(colorElement);
                assertTrue(colorElement instanceof PythonEnumElement, "Color should be a PythonEnumElement");

                PythonEnumElement colorEnum = (PythonEnumElement) colorElement;
                assertEquals("Color", colorEnum.getName());
                assertEquals(List.of("RED", "GREEN", "BLUE"), colorEnum.values());

                ClassElement statusElement = classes.get("Status");
                assertNotNull(statusElement);
                assertTrue(statusElement instanceof PythonEnumElement, "Status should be a PythonEnumElement");

                PythonEnumElement statusEnum = (PythonEnumElement) statusElement;
                assertEquals("Status", statusEnum.getName());
                assertEquals(List.of("ACTIVE", "INACTIVE", "PENDING"), statusEnum.values());
            }
        }
    }

    @Test
    void testConstructorParsing() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        try (PythonEnvironment environment = pythonProcessor.parse("""
            class TestConstructor:
                def __init__(self, name: str, age: int = 25):
                    self.name = name
                    self.age = age

                def method(self):
                    pass
            """)) {

            try (PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment)) {
                // Verify parsing completes successfully
                assertNotNull(environment);
                assertEquals(1, environment.classes().size());

                ClassDef testClassDef = environment.classes().get("TestConstructor");
                assertNotNull(testClassDef);
                assertEquals("TestConstructor", testClassDef.name());

                // Verify constructor is parsed
                FunctionDef constructor = testClassDef.constructor();
                assertNotNull(constructor, "Constructor should be parsed");
                assertEquals("__init__", constructor.name(), "Constructor name should be __init__");

                // Verify constructor arguments
                ArgumentsDef args = constructor.arguments();
                assertNotNull(args, "Constructor should have arguments");
                assertEquals(2, args.arguments().size(), "Constructor should have 2 arguments");

                ArgumentDef nameArg = args.arguments().get(0);
                assertEquals("name", nameArg.name(), "First argument should be name");
                assertEquals("str", nameArg.typeAnnotation(), "Name should have str type");

                ArgumentDef ageArg = args.arguments().get(1);
                assertEquals("age", ageArg.name(), "Second argument should be age");
                assertEquals("int", ageArg.typeAnnotation(), "Age should have int type");
                assertEquals(25, ageArg.defaultValue(), "Age should have default value 25");

                // Test getPrimaryConstructor
                ClassElement classElement = processingEnvironment.classes().get("TestConstructor");
                assertNotNull(classElement);
                assertTrue(classElement instanceof PythonClassElement, "Should be PythonClassElement");

                PythonClassElement pythonClass = (PythonClassElement) classElement;
                Optional<MethodElement> primaryConstructor = pythonClass.getPrimaryConstructor();
                assertTrue(primaryConstructor.isPresent(), "Should have primary constructor");

                MethodElement constructorElement = primaryConstructor.get();
                assertTrue(constructorElement instanceof PythonMethodElement, "Constructor should be PythonMethodElement");

                // Verify constructor method details
                assertEquals("__init__", constructorElement.getName());
                ParameterElement[] params = constructorElement.getParameters();
                assertEquals(2, params.length, "Constructor should have 2 parameters");
                assertEquals("name", params[0].getName());
                assertEquals("age", params[1].getName());
            }
        }
    }

    private static PythonEnvironment buildEnv() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        PythonEnvironment environment = pythonProcessor.parse("""
            @scope
            @micronaut_annotation("jakarta.inject.Singleton")
            def singleton(type):
                return type

            @micronaut_annotation("jakarta.inject.Scope")
            def scope(func):
                return func

            @micronaut_annotation("jakarta.inject.Named")
            def named(name = ""):
                def decorator_named(func):
                    return func
                return decorator_named

            def micronaut_annotation(func, name):
                return func

            @singleton
            @named("myName")
            class MyClass:
                ""\"A simple example class""\"
                i = 12345

                def f(self):
                    return 'hello world'


            """);
        return environment;
    }
}
