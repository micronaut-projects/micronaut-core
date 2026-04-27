package io.micronaut.python.processing;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.micronaut.context.annotation.BeanProperties;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.PropertyElementQuery;
import io.micronaut.python.processing.visitor.ArgumentDef;
import io.micronaut.python.processing.visitor.ArgumentsDef;
import io.micronaut.python.processing.visitor.ClassDef;
import io.micronaut.python.processing.visitor.DecoratorDef;
import io.micronaut.python.processing.visitor.FunctionDef;
import io.micronaut.python.processing.visitor.PythonClassElement;
import io.micronaut.python.processing.visitor.PythonConstructorElement;
import io.micronaut.python.processing.visitor.PythonEnumElement;
import io.micronaut.python.processing.visitor.PythonFieldElement;
import io.micronaut.python.processing.visitor.PythonMethodElement;
import io.micronaut.python.processing.visitor.PythonParameterElement;
import jakarta.inject.Named;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;
import org.graalvm.polyglot.Source;

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
    void testParseDecorators() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        try (PythonEnvironment environment = pythonProcessor.parse("""
from jakarta.inject import Singleton

def micronaut_annotation(name, repeated=None):
    def decorator(func):
        func._micronaut_annotation_name = name
        if repeated:
            func._micronaut_repeatable_container = repeated
        return func
    return decorator

@micronaut_annotation('jakarta.inject.Scope')
def Scope(*args, **kwargs):
    def decorator(func):
        if not hasattr(func, '_micronaut_annotations'):
            func._micronaut_annotations = []
        annotation_data = {'name': 'jakarta.inject.Scope'}
        annotation_data['args'] = args
        annotation_data['kwargs'] = kwargs
        func._micronaut_annotations.append(annotation_data)
        return func
    return decorator

@micronaut_annotation('jakarta.inject.Singleton')
def Singleton(*args, **kwargs):
    def decorator(func):
        if not hasattr(func, '_micronaut_annotations'):
            func._micronaut_annotations = []
        annotation_data = {'name': 'jakarta.inject.Singleton'}
        annotation_data['args'] = args
        annotation_data['kwargs'] = kwargs
        func._micronaut_annotations.append(annotation_data)
        return func
    return decorator

@Singleton
class MySingletonService:
    pass


            """)) {
            ClassDef myClass = environment.classes().get("MySingletonService");

            assertNotNull(myClass);
            assertEquals(1, myClass.decorators().size());

            assertEquals(2, environment.decorators().size());

            assertTrue(environment.decorators().containsKey(Singleton.class.getName()));
            assertTrue(environment.decorators().containsKey(Scope.class.getName()));
        }
    }

    @Test
    void testProcessingEnvironment() {
        try (PythonEnvironment environment = buildEnv();
             PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment, null)) {

            Map<String, ClassElement> classes = processingEnvironment.classes();
            assertNotNull(classes);
            assertEquals(1, classes.size());

            ClassElement myClass = classes.get("MyClass");

            assertNotNull(myClass);
            assertEquals("MyClass", myClass.getSimpleName());
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

            try (PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment, null)) {
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
                PythonFieldElement intField = new PythonFieldElement(intAttr.get(), processingEnvironment, testClass, testClass, processingEnvironment.metadataFactory());
                assertEquals("int", intField.getType().getName(), "int field should resolve to primitive int");

                PythonFieldElement floatField = new PythonFieldElement(floatAttr.get(), processingEnvironment, testClass, testClass, processingEnvironment.metadataFactory());
                assertEquals("double", floatField.getType().getName(), "float field should resolve to primitive double");

                PythonFieldElement strField = new PythonFieldElement(strAttr.get(), processingEnvironment, testClass, testClass, processingEnvironment.metadataFactory());
                assertEquals("java.lang.String", strField.getType().getName(), "str field should resolve to String");

                PythonFieldElement boolField = new PythonFieldElement(boolAttr.get(), processingEnvironment, testClass, testClass, processingEnvironment.metadataFactory());
                assertEquals("boolean", boolField.getType().getName(), "bool field should resolve to primitive boolean");

                PythonFieldElement complexField = new PythonFieldElement(complexAttr.get(), processingEnvironment, testClass, testClass, processingEnvironment.metadataFactory());
                assertEquals("java.util.List", complexField.getType().getName(), "complex field should fall back to Object");
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

            try (PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment, null)) {
                // Get the class definition
                ClassDef testClassDef = environment.classes().get("TestMethods");
                PythonClassElement testClass = new PythonClassElement(testClassDef, processingEnvironment);
                assertNotNull(testClassDef);

                // Test that we can create method elements directly from function definitions
                var publicMethodDef = testClassDef.functions().stream()
                    .filter(func -> "public_method".equals(func.name()))
                    .findFirst();
                assertTrue(publicMethodDef.isPresent(), "public_method should be present");

                PythonMethodElement publicMethod = new PythonMethodElement(publicMethodDef.get(), processingEnvironment, testClass, testClass, processingEnvironment.metadataFactory());

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

                PythonMethodElement privateMethod = new PythonMethodElement(privateMethodDef.get(), processingEnvironment, testClass, testClass, processingEnvironment.metadataFactory());
                assertFalse(privateMethod.isPublic(), "_private_method should not be public");
                assertTrue(privateMethod.isPrivate(), "_private_method should be private");

                // Test method with no annotations
                var noAnnotationsDef = testClassDef.functions().stream()
                    .filter(func -> "no_annotations".equals(func.name()))
                    .findFirst();
                assertTrue(noAnnotationsDef.isPresent(), "no_annotations should be present");

                PythonMethodElement noAnnotationsMethod = new PythonMethodElement(noAnnotationsDef.get(), processingEnvironment, testClass, testClass, processingEnvironment.metadataFactory());
                assertEquals(2, noAnnotationsMethod.getParameters().length, "should have 2 parameters");
                assertEquals("void", noAnnotationsMethod.getReturnType().getName(), "return type should be Object");

                // Test return-only method
                var returnOnlyDef = testClassDef.functions().stream()
                    .filter(func -> "return_only".equals(func.name()))
                    .findFirst();
                assertTrue(returnOnlyDef.isPresent(), "return_only should be present");

                PythonMethodElement returnOnlyMethod = new PythonMethodElement(returnOnlyDef.get(), processingEnvironment, testClass, testClass, processingEnvironment.metadataFactory());
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

            try (PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment, null)) {
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
                assertEquals("Color", colorEnum.getSimpleName());
                assertEquals(List.of("RED", "GREEN", "BLUE"), colorEnum.values());

                ClassElement statusElement = classes.get("Status");
                assertNotNull(statusElement);
                assertTrue(statusElement instanceof PythonEnumElement, "Status should be a PythonEnumElement");

                PythonEnumElement statusEnum = (PythonEnumElement) statusElement;
                assertEquals("Status", statusEnum.getSimpleName());
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

            try (PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment, null)) {
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
                assertEquals("str", nameArg.typeAnnotation().name(), "Name should have str type");

                ArgumentDef ageArg = args.arguments().get(1);
                assertEquals("age", ageArg.name(), "Second argument should be age");
                assertEquals("int", ageArg.typeAnnotation().name(), "Age should have int type");
                assertEquals(25, ageArg.defaultValue(), "Age should have default value 25");

                // Test getPrimaryConstructor
                ClassElement classElement = processingEnvironment.classes().get("TestConstructor");
                assertNotNull(classElement);
                assertInstanceOf(PythonClassElement.class, classElement, "Should be PythonClassElement");

                PythonClassElement pythonClass = (PythonClassElement) classElement;
                Optional<MethodElement> primaryConstructor = pythonClass.getPrimaryConstructor();
                assertTrue(primaryConstructor.isPresent(), "Should have primary constructor");

                MethodElement constructorElement = primaryConstructor.get();
                assertInstanceOf(PythonConstructorElement.class, constructorElement, "Constructor should be PythonMethodElement");

                // Verify constructor method details
                assertEquals("__init__", constructorElement.getName());
                ParameterElement[] params = constructorElement.getParameters();
                assertEquals(2, params.length, "Constructor should have 2 parameters");
                assertEquals("name", params[0].getName());
                assertEquals("age", params[1].getName());
            }
        }
    }

    @Test
    void testPackageTranslation() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        try (PythonEnvironment environment = pythonProcessor.parse("""
            class TestClass:
                def method(self):
                    pass
            """, "com.example.mypackage")) {

            try (PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment, null)) {
                // Verify parsing completes successfully
                assertNotNull(environment);
                assertEquals(1, environment.classes().size());

                ClassDef testClassDef = environment.classes().get("com.example.mypackage.TestClass");
                assertNotNull(testClassDef);
                assertEquals("TestClass", testClassDef.name());
                assertEquals("com.example.mypackage", testClassDef.packageName());

                // Test that processing environment creates PythonClassElement with correct package
                Map<String, ClassElement> classes = processingEnvironment.classes();
                assertEquals(1, classes.size());

                ClassElement classElement = classes.get("com.example.mypackage.TestClass");
                assertNotNull(classElement);
                assertTrue(classElement instanceof PythonClassElement, "Should be PythonClassElement");

                PythonClassElement pythonClass = (PythonClassElement) classElement;
                assertEquals("com.example.mypackage.TestClass", pythonClass.getName(), "Full qualified name should include package");
                assertEquals("TestClass", pythonClass.getSimpleName(), "Simple name should not include the package");
                assertEquals("com.example.mypackage", pythonClass.getPackageName(), "Package name should be correctly translated");

            }
        }
    }

    @Test
    void testRootModuleImportsResolveToDefaultPackage(@TempDir Path tempDir) throws IOException {
        Path dependency = tempDir.resolve("Dependency.py");
        Path main = tempDir.resolve("Main.py");
        Files.writeString(dependency, """
            class Dependency:
                pass
            """);
        Files.writeString(main, """
            from Dependency import Dependency

            class Main:
                def __init__(self, dependency: Dependency):
                    self.dependency = dependency
            """);

        PythonAstParser pythonProcessor = new PythonAstParser();
        List<Source> sources = List.of(
            Source.newBuilder("python", dependency.toFile()).build(),
            Source.newBuilder("python", main.toFile()).build()
        );

        try (PythonEnvironment environment = pythonProcessor.parse(sources, List.of(tempDir.toString()), null);
             PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment, null)) {
            ClassElement mainClass = processingEnvironment.classes().get("python.Main");
            assertNotNull(mainClass);

            MethodElement constructor = mainClass.getPrimaryConstructor().orElseThrow();
            ParameterElement dependencyParameter = constructor.getParameters()[0];
            assertEquals("python.Dependency", dependencyParameter.getType().getName());
        }
    }

    @Test
    void testAbstractMethodDetection() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        try (PythonEnvironment environment = pythonProcessor.parse("""
            from abc import ABC, abstractmethod

            class AbstractClass(ABC):
                @abstractmethod
                def abstract_method(self, x: int) -> str:
                    \"\"\"This is an abstract method.\"\"\"
                    pass

                def concrete_method(self, y: float) -> bool:
                    \"\"\"This is a concrete method.\"\"\"
                    return True

                @abstractmethod
                def another_abstract_method(self) -> None:
                    pass
            """)) {

            try (PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment, null)) {
                // Verify parsing completes successfully
                assertNotNull(environment);
                assertEquals(1, environment.classes().size());

                ClassDef abstractClassDef = environment.classes().get("AbstractClass");
                assertNotNull(abstractClassDef);
                assertEquals("AbstractClass", abstractClassDef.name());

                // Verify abstract methods are detected
                var abstractMethodDef = abstractClassDef.functions().stream()
                    .filter(func -> "abstract_method".equals(func.name()))
                    .findFirst();
                assertTrue(abstractMethodDef.isPresent(), "abstract_method should be present");
                assertTrue(abstractMethodDef.get().isAbstract(), "abstract_method should be marked as abstract");

                var anotherAbstractMethodDef = abstractClassDef.functions().stream()
                    .filter(func -> "another_abstract_method".equals(func.name()))
                    .findFirst();
                assertTrue(anotherAbstractMethodDef.isPresent(), "another_abstract_method should be present");
                assertTrue(anotherAbstractMethodDef.get().isAbstract(), "another_abstract_method should be marked as abstract");

                // Verify concrete method is not abstract
                var concreteMethodDef = abstractClassDef.functions().stream()
                    .filter(func -> "concrete_method".equals(func.name()))
                    .findFirst();
                assertTrue(concreteMethodDef.isPresent(), "concrete_method should be present");
                assertFalse(concreteMethodDef.get().isAbstract(), "concrete_method should not be marked as abstract");
            }
        }
    }

    private static PythonEnvironment buildEnv() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        PythonEnvironment environment = pythonProcessor.parse("""
            @micronaut_annotation("jakarta.inject.Singleton")
            def Singleton(*args, **kwargs):
                def decorator(func):
                    if not hasattr(func, '_micronaut_annotations'):
                        func._micronaut_annotations = []
                    annotation_data = {'name': 'jakarta.inject.Singleton'}
                    annotation_data['args'] = args
                    annotation_data['kwargs'] = kwargs
                    func._micronaut_annotations.append(annotation_data)
                    return func
                return decorator

            @micronaut_annotation("jakarta.inject.Scope")
            def Scope(*args, **kwargs):
                def decorator(func):
                    if not hasattr(func, '_micronaut_annotations'):
                        func._micronaut_annotations = []
                    annotation_data = {'name': 'jakarta.inject.Scope'}
                    annotation_data['args'] = args
                    annotation_data['kwargs'] = kwargs
                    func._micronaut_annotations.append(annotation_data)
                    return func
                return decorator

            @micronaut_annotation("jakarta.inject.Named")
            def Named(*args, **kwargs):
                def decorator(func):
                    if not hasattr(func, '_micronaut_annotations'):
                        func._micronaut_annotations = []
                    annotation_data = {'name': 'jakarta.inject.Named'}
                    annotation_data['args'] = args
                    annotation_data['kwargs'] = kwargs
                    func._micronaut_annotations.append(annotation_data)
                    return func
                return decorator

            def micronaut_annotation(name):
                return lambda func: func

            @Singleton
            @Named("myName")
            class MyClass:
                ""\"A simple example class""\"
                i = 12345

                def f(self):
                    return 'hello world'


            """);
        return environment;
    }

    @Test
    void testDocumentationParsing() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        try (PythonEnvironment environment = pythonProcessor.parse("""
            class DocumentedClass:
                \"\"\"This is a class with documentation.

                This class demonstrates various documentation features.
                \"\"\"
                def __init__(self, name: str, age: int = 25):
                    \"\"\"Initialize the documented class.

                    Args:
                        name (str): The name of the instance
                        age (int): The age of the instance, defaults to 25

                    Returns:
                        None
                    \"\"\"
                    self.name = name
                    self.age = age

                def documented_method(self, param1: str, param2: int = 10) -> bool:
                    \"\"\"A method with parameter documentation.

                    This method demonstrates parameter documentation extraction.

                    Parameters:
                        param1 (str): The first parameter description
                        param2 (int): The second parameter with default

                    Returns:
                        bool: Always returns True
                    \"\"\"
                    return True

                undocumented_field = "no docs"

                documented_field: str = "has docs"
                \"\"\"This field has documentation.\"\"\"
            """)) {

            try (PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment, null)) {
                // Test class documentation
                ClassElement classElement = processingEnvironment.classes().get("DocumentedClass");
                assertNotNull(classElement);
                assertTrue(classElement instanceof PythonClassElement);

                PythonClassElement pythonClass = (PythonClassElement) classElement;

                // Test raw class documentation
                Optional<String> rawClassDoc = pythonClass.getDocumentation(false);
                assertTrue(rawClassDoc.isPresent());
                assertTrue(rawClassDoc.get().contains("This is a class with documentation"));

                // Test parsed class documentation (should exclude structured sections)
                Optional<String> parsedClassDoc = pythonClass.getDocumentation(true);
                assertTrue(parsedClassDoc.isPresent());
                String parsedClass = parsedClassDoc.get();
                assertTrue(parsedClass.contains("This is a class with documentation"));
                assertTrue(parsedClass.contains("This class demonstrates various documentation features"));
                // Should not contain the closing quotes
                assertFalse(parsedClass.contains("\"\"\""));

                // Test method documentation
                Optional<MethodElement> methodOpt = pythonClass.getEnclosedElements(ElementQuery.ALL_METHODS)
                    .stream()
                    .filter(m -> "documented_method".equals(m.getName()))
                    .findFirst();
                assertTrue(methodOpt.isPresent());
                PythonMethodElement method = (PythonMethodElement) methodOpt.get();

                // Test raw method documentation
                Optional<String> rawMethodDoc = method.getDocumentation(false);
                assertTrue(rawMethodDoc.isPresent());
                assertTrue(rawMethodDoc.get().contains("A method with parameter documentation"));

                // Test parsed method documentation
                Optional<String> parsedMethodDoc = method.getDocumentation(true);
                assertTrue(parsedMethodDoc.isPresent());
                String parsedMethod = parsedMethodDoc.get();
                assertTrue(parsedMethod.contains("A method with parameter documentation"));
                assertTrue(parsedMethod.contains("This method demonstrates parameter documentation extraction"));
                // Should stop before Parameters section
                assertFalse(parsedMethod.contains("Parameters:"));

                // Test parameter documentation
                ParameterElement[] params = method.getParameters();
                assertEquals(2, params.length);

                // Test param1 documentation
                ParameterElement param1 = params[0];
                Optional<String> param1Doc = param1.getDocumentation(false);
                assertTrue(param1Doc.isPresent());
                assertEquals("The first parameter description", param1Doc.get().trim());

                // Test param2 documentation
                ParameterElement param2 = params[1];
                Optional<String> param2Doc = param2.getDocumentation(false);
                assertTrue(param2Doc.isPresent());
                assertEquals("The second parameter with default", param2Doc.get().trim());

                // Test constructor parameter documentation
                Optional<MethodElement> constructorOpt = pythonClass.getPrimaryConstructor();
                assertTrue(constructorOpt.isPresent());
                MethodElement constructorElement = constructorOpt.get();

                ParameterElement[] constructorParams = constructorElement.getParameters();
                assertEquals(2, constructorParams.length);

                // Test constructor param documentation
                ParameterElement nameParam = constructorParams[0];
                Optional<String> nameParamDoc = nameParam.getDocumentation(false);
                assertTrue(nameParamDoc.isPresent());
                assertEquals("The name of the instance", nameParamDoc.get().trim());

                ParameterElement ageParam = constructorParams[1];
                Optional<String> ageParamDoc = ageParam.getDocumentation(false);
                assertTrue(ageParamDoc.isPresent());
                assertEquals("The age of the instance, defaults to 25", ageParamDoc.get().trim());

                // Test field documentation
                Optional<FieldElement> fieldOpt = pythonClass.getFields()
                    .stream()
                    .filter(f -> "documented_field".equals(f.getName()))
                    .findFirst();
                assertTrue(fieldOpt.isPresent());
                PythonFieldElement field = (PythonFieldElement) fieldOpt.get();

                // Test raw field documentation
                Optional<String> rawFieldDoc = field.getDocumentation(false);
                assertTrue(rawFieldDoc.isPresent());
                assertEquals("This field has documentation.", rawFieldDoc.get().trim());

                // Test parsed field documentation (should be same for fields)
                Optional<String> parsedFieldDoc = field.getDocumentation(true);
                assertTrue(parsedFieldDoc.isPresent());
                assertEquals("This field has documentation.", parsedFieldDoc.get().trim());

                // Test field without documentation
                Optional<FieldElement> undocumentedFieldOpt = pythonClass.getFields()
                    .stream()
                    .filter(f -> "undocumented_field".equals(f.getName()))
                    .findFirst();
                assertTrue(undocumentedFieldOpt.isPresent());
                PythonFieldElement undocumentedField = (PythonFieldElement) undocumentedFieldOpt.get();

                Optional<String> noDoc = undocumentedField.getDocumentation(false);
                assertFalse(noDoc.isPresent());
            }
        }
    }

    @Test
    void testElementQueryAPI() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        try (PythonEnvironment environment = pythonProcessor.parse("""
            class TestElementQuery:
                class_field = "value"

                instance_field: str = "instance"

                def public_method(self) -> str:
                    return "public"

                def _private_method(self) -> int:
                    return 42

                @staticmethod
                def static_method() -> bool:
                    return True

                def abstract_method(self):
                    pass
            """)) {

            try (PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment, null)) {
                ClassElement classElement = processingEnvironment.classes().get("TestElementQuery");
                assertNotNull(classElement);
                assertTrue(classElement instanceof PythonClassElement);

                PythonClassElement pythonClass = (PythonClassElement) classElement;

                // Test ALL_METHODS query
                List<MethodElement> allMethods = pythonClass.getEnclosedElements(ElementQuery.ALL_METHODS);
                assertEquals(4, allMethods.size(), "Should have 4 methods");

                // Test ALL_FIELDS query
                List<FieldElement> allFields = pythonClass.getFields();
                assertEquals(2, allFields.size(), "Should have 2 fields");

                // Test filtering by name
                List<MethodElement> publicMethods = pythonClass.getEnclosedElements(
                    ElementQuery.ALL_METHODS.named("public_method")
                );
                assertEquals(1, publicMethods.size(), "Should find public_method");
                assertEquals("public_method", publicMethods.get(0).getName());

                // Test filtering by modifier (only public)
                List<MethodElement> onlyPublicMethods = pythonClass.getEnclosedElements(
                    ElementQuery.ALL_METHODS.onlyAccessible()
                );
                // Should exclude _private_method
                assertTrue(onlyPublicMethods.stream().noneMatch(m -> m.getName().startsWith("_")),
                    "Should not include private methods");

                // Test only declared (no inherited)
                List<MethodElement> declaredMethods = pythonClass.getEnclosedElements(
                    ElementQuery.ALL_METHODS.onlyDeclared()
                );
                assertEquals(4, declaredMethods.size(), "Should have all declared methods");

                // Test typed filtering (methods returning string)
                List<MethodElement> stringMethods = pythonClass.getEnclosedElements(
                    ElementQuery.ALL_METHODS.typed(type -> "java.lang.String".equals(type.getName()))
                );
                assertEquals(1, stringMethods.size(), "Should find method returning string");
                assertEquals("public_method", stringMethods.get(0).getName());
            }
        }
    }

    @Test
    void testAnnotatedAttributeParsing() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        try (PythonEnvironment environment = pythonProcessor.parse("""
            from typing import Annotated

            class Fruit:
                name: str = "apple"
                weight: Annotated[float, Gt(0)] = 1.5
                count: Annotated[int, Min(1), Max(100)] = 10
                validated_name: Annotated[str, NotBlank] = "apple"

            # Define Gt, Min, Max, NotBlank as micronaut annotations for testing
            @micronaut_annotation("Gt")
            def Gt(value):
                return lambda: None

            @micronaut_annotation("Min")
            def Min(value):
                return lambda: None

            @micronaut_annotation("Max")
            def Max(value):
                return lambda: None

            @micronaut_annotation("NotBlank")
            def NotBlank():
                return lambda: None

            def micronaut_annotation(name):
                return lambda func: func
            """)) {

            try (PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment)) {
                // Verify parsing completes successfully
                assertNotNull(environment);
                assertEquals(1, environment.classes().size());

                ClassDef fruitClass = environment.classes().get("Fruit");
                assertNotNull(fruitClass);
                assertEquals("Fruit", fruitClass.name());

                // Verify attributes are parsed
                assertEquals(4, fruitClass.attributes().size(), "Should parse 4 attributes");

                // Check weight attribute - should have full Annotated type and Gt decorator
                var weightAttr = fruitClass.attributes().stream()
                    .filter(attr -> "weight".equals(attr.name()))
                    .findFirst();
                assertTrue(weightAttr.isPresent(), "weight attribute should be parsed");
                assertEquals("Annotated[float, Gt(0)]", weightAttr.get().annotation(), "weight should have full annotation string");
                assertEquals("float", weightAttr.get().typeName().name(), "weight should have full annotation as typeName for now");
                assertEquals(1.5, weightAttr.get().value().asDouble(), 0.01, "weight should have value 1.5");

                // Check that weight has Gt decorator
                List<DecoratorDef> weightDecorators = weightAttr.get().decorators();
                assertEquals(1, weightDecorators.size(), "weight should have 1 decorator");
                DecoratorDef gtDecorator = weightDecorators.get(0);
                assertEquals("Gt", gtDecorator.name(), "decorator should be Gt");
                assertEquals("Gt", gtDecorator.annotationName(), "annotation name should be Gt");
                assertTrue(gtDecorator.members().containsKey("value"), "Gt should have value member");
                var gtMemberValue = gtDecorator.members().get("value");
                assertTrue(gtMemberValue instanceof org.graalvm.polyglot.Value, "Gt value should be a GraalVM Value");
                assertEquals(0, gtMemberValue.asInt(), "Gt value should be 0");

                // Check count attribute - should have extracted int type and Min/Max decorators
                var countAttr = fruitClass.attributes().stream()
                    .filter(attr -> "count".equals(attr.name()))
                    .findFirst();
                assertTrue(countAttr.isPresent(), "count attribute should be parsed");
                assertEquals("Annotated[int, Min(1), Max(100)]", countAttr.get().annotation(), "count should have full annotation string");
                assertEquals("int", countAttr.get().typeName().name(), "count should have full annotation as typeName for now");
                assertEquals(10, countAttr.get().value().asInt(), "count should have value 10");

                // Check that count has Min and Max decorators
                List<DecoratorDef> countDecorators = countAttr.get().decorators();
                assertEquals(2, countDecorators.size(), "count should have 2 decorators");

                // Find Min and Max decorators
                var minDecorator = countDecorators.stream()
                    .filter(d -> "Min".equals(d.name()))
                    .findFirst();
                assertTrue(minDecorator.isPresent(), "count should have Min decorator");
                assertEquals(1, minDecorator.get().members().get("value").asInt(), "Min value should be 1");

                var maxDecorator = countDecorators.stream()
                    .filter(d -> "Max".equals(d.name()))
                    .findFirst();
                assertTrue(maxDecorator.isPresent(), "count should have Max decorator");
                assertEquals(100, maxDecorator.get().members().get("value").asInt(), "Max value should be 100");

                // Test that PythonFieldElement creates correct annotation metadata
                ClassElement fruitElement = processingEnvironment.classes().get("Fruit");
                assertNotNull(fruitElement);
                assertInstanceOf(PythonClassElement.class, fruitElement);

                PythonClassElement fruitPythonClass = (PythonClassElement) fruitElement;

                // Get weight field
                FieldElement weightField = fruitPythonClass.getFields()
                    .stream()
                    .filter(f -> "weight".equals(f.getName()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("weight field should be present"));

                assertInstanceOf(PythonFieldElement.class, weightField);
                PythonFieldElement weightPythonField = (PythonFieldElement) weightField;

                // Verify type resolution
                assertEquals("double", weightPythonField.getType().getName(), "weight field should resolve to double");

                // Verify annotation metadata is populated
                var annotationMetadata = weightField.getAnnotationMetadata();
                assertTrue(annotationMetadata.hasAnnotation("Gt"), "weight field should have Gt annotation");
                var gtValue = annotationMetadata.intValue("Gt", "value");
                assertTrue(gtValue.isPresent(), "Gt annotation should have value");
                assertEquals(0, gtValue.getAsInt(), "Gt annotation should have value 0");

                // Test count field with multiple annotations
                FieldElement countField = fruitPythonClass.getFields()
                    .stream()
                    .filter(f -> "count".equals(f.getName()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("count field should be present"));

                var countAnnotationMetadata = countField.getAnnotationMetadata();
                assertTrue(countAnnotationMetadata.hasAnnotation("Min"), "count field should have Min annotation");
                assertTrue(countAnnotationMetadata.hasAnnotation("Max"), "count field should have Max annotation");

                var minValue = countAnnotationMetadata.intValue("Min", "value");
                assertTrue(minValue.isPresent(), "Min annotation should have value");
                assertEquals(1, minValue.getAsInt(), "Min annotation should have value 1");

                var maxValue = countAnnotationMetadata.intValue("Max", "value");
                assertTrue(maxValue.isPresent(), "Max annotation should have value");
                assertEquals(100, maxValue.getAsInt(), "Max annotation should have value 100");

                // Check validated_name attribute - should have NotBlank decorator (constraint-only)
                var validatedNameAttr = fruitClass.attributes().stream()
                    .filter(attr -> "validated_name".equals(attr.name()))
                    .findFirst();
                assertTrue(validatedNameAttr.isPresent(), "validated_name attribute should be parsed");
                assertEquals("Annotated[str, NotBlank]", validatedNameAttr.get().annotation(), "validated_name should have full annotation string");
                assertEquals("str", validatedNameAttr.get().typeName().name(), "validated_name should have full annotation as typeName");
                assertEquals("apple", validatedNameAttr.get().value().asString(), "validated_name should have value 'apple'");

                // Check that validated_name has NotBlank decorator
                List<DecoratorDef> validatedNameDecorators = validatedNameAttr.get().decorators();
                assertEquals(1, validatedNameDecorators.size(), "validated_name should have 1 decorator");
                DecoratorDef notBlankDecorator = validatedNameDecorators.get(0);
                assertEquals("NotBlank", notBlankDecorator.name(), "decorator should be NotBlank");
                assertEquals("NotBlank", notBlankDecorator.annotationName(), "annotation name should be NotBlank");
                // NotBlank has no parameters, so members should be empty
                assertTrue(notBlankDecorator.members().isEmpty(), "NotBlank should have no members");

                // Test that PythonFieldElement creates correct annotation metadata for NotBlank
                FieldElement validatedNameField = fruitPythonClass.getFields()
                    .stream()
                    .filter(f -> "validated_name".equals(f.getName()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("validated_name field should be present"));

                var validatedNameAnnotationMetadata = validatedNameField.getAnnotationMetadata();
                assertTrue(validatedNameAnnotationMetadata.hasAnnotation("NotBlank"), "validated_name field should have NotBlank annotation");
            }
        }
    }

    @Test
    void testAnnotatedFunctionArgumentParsing() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        try (PythonEnvironment environment = pythonProcessor.parse("""
            from typing import Annotated

            class FruitService:
                def process_fruit(self, name: str, weight: Annotated[float, Gt(0)], count: Annotated[int, Min(1), Max(100)] = 10) -> str:
                    return f"Processed {count} {name}(s) with total weight {weight}"

            # Define Gt, Min, Max as micronaut annotations for testing
            @micronaut_annotation("Gt")
            def Gt(value):
                return lambda: None

            @micronaut_annotation("Min")
            def Min(value):
                return lambda: None

            @micronaut_annotation("Max")
            def Max(value):
                return lambda: None

            def micronaut_annotation(name):
                return lambda func: func
            """)) {

            try (PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment)) {
                // Verify parsing completes successfully
                assertNotNull(environment);
                assertEquals(1, environment.classes().size());

                ClassDef fruitServiceClass = environment.classes().get("FruitService");
                assertNotNull(fruitServiceClass);
                assertEquals("FruitService", fruitServiceClass.name());

                // Verify function is parsed
                assertEquals(1, fruitServiceClass.functions().size());
                FunctionDef processFruit = fruitServiceClass.functions().get(0);
                assertEquals("process_fruit", processFruit.name());

                // Verify function arguments
                ArgumentsDef args = processFruit.arguments();
                assertNotNull(args);
                assertEquals(3, args.arguments().size(), "Should have 3 arguments");

                // Check name argument - simple type
                ArgumentDef nameArg = args.arguments().get(0);
                assertEquals("name", nameArg.name());
                assertEquals("str", nameArg.annotation());
                assertEquals("str", nameArg.typeAnnotation().name());
                assertEquals(0, nameArg.decorators().size(), "name should have no decorators");

                // Check weight argument - should have full Annotated type and Gt decorator
                ArgumentDef weightArg = args.arguments().get(1);
                assertEquals("weight", weightArg.name());
                assertEquals("Annotated[float, Gt(0)]", weightArg.annotation(), "weight should have full annotation string");
                assertEquals("float", weightArg.typeAnnotation().name(), "weight should have extracted float type");

                // Check that weight has Gt decorator
                List<DecoratorDef> weightDecorators = weightArg.decorators();
                assertEquals(1, weightDecorators.size(), "weight should have 1 decorator");
                DecoratorDef gtDecorator = weightDecorators.get(0);
                assertEquals("Gt", gtDecorator.name(), "decorator should be Gt");
                assertEquals("Gt", gtDecorator.annotationName(), "annotation name should be Gt");
                assertTrue(gtDecorator.members().containsKey("value"), "Gt should have value member");
                var gtMemberValue = gtDecorator.members().get("value");
                assertTrue(gtMemberValue instanceof org.graalvm.polyglot.Value, "Gt value should be a GraalVM Value");
                assertEquals(0, ((org.graalvm.polyglot.Value) gtMemberValue).asInt(), "Gt value should be 0");

                // Check count argument - should have extracted int type and Min/Max decorators
                ArgumentDef countArg = args.arguments().get(2);
                assertEquals("count", countArg.name());
                assertEquals("Annotated[int, Min(1), Max(100)]", countArg.annotation(), "count should have full annotation string");
                assertEquals("int", countArg.typeAnnotation().name(), "count should have extracted int type");
                assertEquals(10, countArg.defaultValue(), "count should have default value 10");

                // Check that count has Min and Max decorators
                List<DecoratorDef> countDecorators = countArg.decorators();
                assertEquals(2, countDecorators.size(), "count should have 2 decorators");

                // Find Min and Max decorators
                var minDecorator = countDecorators.stream()
                    .filter(d -> "Min".equals(d.name()))
                    .findFirst();
                assertTrue(minDecorator.isPresent(), "count should have Min decorator");
                assertEquals(1, minDecorator.get().members().get("value").asInt(), "Min value should be 1");

                var maxDecorator = countDecorators.stream()
                    .filter(d -> "Max".equals(d.name()))
                    .findFirst();
                assertTrue(maxDecorator.isPresent(), "count should have Max decorator");
                assertEquals(100, maxDecorator.get().members().get("value").asInt(), "Max value should be 100");

                // Test that PythonParameterElement creates correct annotation metadata
                ClassElement fruitServiceElement = processingEnvironment.classes().get("FruitService");
                assertNotNull(fruitServiceElement);
                assertInstanceOf(PythonClassElement.class, fruitServiceElement);

                PythonClassElement fruitServicePythonClass = (PythonClassElement) fruitServiceElement;

                // Get the method
                MethodElement processFruitMethod = fruitServicePythonClass.getEnclosedElements(ElementQuery.ALL_METHODS)
                    .stream()
                    .filter(m -> "process_fruit".equals(m.getName()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("process_fruit method should be present"));

                assertInstanceOf(PythonMethodElement.class, processFruitMethod);
                PythonMethodElement pythonMethod = (PythonMethodElement) processFruitMethod;

                // Verify method parameters have annotation metadata
                ParameterElement[] params = pythonMethod.getParameters();
                assertEquals(3, params.length, "Should have 3 parameters");

                // Test weight parameter annotations
                ParameterElement weightParam = params[1];
                assertInstanceOf(PythonParameterElement.class, weightParam);
                PythonParameterElement weightPythonParam = (PythonParameterElement) weightParam;

                // Verify type resolution
                assertEquals("double", weightPythonParam.getType().getName(), "weight parameter should resolve to double");

                // Verify annotation metadata is populated
                var weightAnnotationMetadata = weightParam.getAnnotationMetadata();
                assertTrue(weightAnnotationMetadata.hasAnnotation("Gt"), "weight parameter should have Gt annotation");
                var weightGtValue = weightAnnotationMetadata.intValue("Gt", "value");
                assertTrue(weightGtValue.isPresent(), "Gt annotation should have value");
                assertEquals(0, weightGtValue.getAsInt(), "Gt annotation should have value 0");

                // Test count parameter with multiple annotations
                ParameterElement countParam = params[2];
                var countAnnotationMetadata = countParam.getAnnotationMetadata();
                assertTrue(countAnnotationMetadata.hasAnnotation("Min"), "count parameter should have Min annotation");
                assertTrue(countAnnotationMetadata.hasAnnotation("Max"), "count parameter should have Max annotation");

                var countMinValue = countAnnotationMetadata.intValue("Min", "value");
                assertTrue(countMinValue.isPresent(), "Min annotation should have value");
                assertEquals(1, countMinValue.getAsInt(), "Min annotation should have value 1");

                var countMaxValue = countAnnotationMetadata.intValue("Max", "value");
                assertTrue(countMaxValue.isPresent(), "Max annotation should have value");
                assertEquals(100, countMaxValue.getAsInt(), "Max annotation should have value 100");
            }
        }
    }

    @Test
    void testFieldBasedPropertySupport() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        try (PythonEnvironment environment = pythonProcessor.parse("""
            class TestFieldProperties:
                # Regular field properties
                name: str = "test"
                age: int = 25
                active: bool = True

                # Method (should not be a property)
                def method(self):
                    return "not a property"
            """)) {

            try (PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment)) {
                ClassElement classElement = processingEnvironment.classes().get("TestFieldProperties");
                assertNotNull(classElement);
                assertInstanceOf(PythonClassElement.class, classElement);

                PythonClassElement pythonClass = (PythonClassElement) classElement;

                // Test getBeanProperties
                List<PropertyElement> properties = pythonClass.getBeanProperties();
                assertNotNull(properties, "getBeanProperties should return a list");

                // Debug: print what we found
                System.out.println("Found " + properties.size() + " properties:");
                properties.forEach(p -> System.out.println("  - " + p.getName() + " (" + p.getType().getName() + ")"));

                // Debug: check what's in the class definition
                ClassDef testClassDef = environment.classes().get("TestFieldProperties");
                System.out.println("ClassDef attributes: " + testClassDef.attributes().size());
                testClassDef.attributes().forEach(attr -> System.out.println("  - attr: " + attr.name() + " (" + attr.annotation() + ")"));
                System.out.println("ClassDef properties: " + testClassDef.properties().size());
                testClassDef.properties().forEach(prop -> System.out.println("  - prop: " + prop.name()));

                // Should find the field-based properties
                assertTrue(properties.size() >= 3, "Should find at least 3 field-based properties. Found: " + properties.size());

                // Find the name property
                PropertyElement nameProperty = properties.stream()
                    .filter(p -> "name".equals(p.getName()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("name property should be found"));

                assertEquals("java.lang.String", nameProperty.getType().getName(), "name property should have String type");
                assertEquals(PropertyElement.AccessKind.METHOD, nameProperty.getReadAccessKind(), "name should be METHOD access");
                assertEquals(PropertyElement.AccessKind.METHOD, nameProperty.getWriteAccessKind(), "name should be METHOD write access");

                // Should have a field and synthetic read/write methods
                assertFalse(nameProperty.getField().isPresent(), "fields are inaccessible, accessible through synthetic methods");
                assertTrue(nameProperty.getReadMethod().isPresent(), "name property should have synthetic read method");
                assertTrue(nameProperty.getWriteMethod().isPresent(), "name property should have synthetic write method");

                // Find the age property
                PropertyElement ageProperty = properties.stream()
                    .filter(p -> "age".equals(p.getName()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("age property should be found"));

                assertEquals("int", ageProperty.getType().getName(), "age property should have int type");

                // Find the active property
                PropertyElement activeProperty = properties.stream()
                    .filter(p -> "active".equals(p.getName()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("active property should be found"));

                assertEquals("boolean", activeProperty.getType().getName(), "active property should have boolean type");
            }
        }
    }

    @Test
    void testPropertyDecoratorSupport() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        try (PythonEnvironment environment = pythonProcessor.parse("""
            class TestPropertyDecorators:
                _price = 0.0  # backing field

                @property
                def price(self) -> float:
                    '''Get the current price.'''
                    return self._price

                @price.setter
                def price(self, value: float):
                    '''Set the price.'''
                    if value >= 0:
                        self._price = value

                @property
                def read_only_name(self) -> str:
                    '''Read-only name property.'''
                    return "readonly"

                # Regular field
                description: str = "test"
            """)) {

            try (PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment)) {
                ClassElement classElement = processingEnvironment.classes().get("TestPropertyDecorators");
                assertNotNull(classElement);
                assertInstanceOf(PythonClassElement.class, classElement);

                PythonClassElement pythonClass = (PythonClassElement) classElement;

                // Test getBeanProperties
                List<PropertyElement> properties = pythonClass.getBeanProperties();
                assertNotNull(properties, "getBeanProperties should return a list");

                // Should find properties
                assertTrue(properties.size() >= 3, "Should find at least 3 properties");

                // Test price property (has both getter and setter)
                PropertyElement priceProperty = properties.stream()
                    .filter(p -> "price".equals(p.getName()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("price property should be found"));

                assertEquals("double", priceProperty.getType().getName(), "price property should have double type");
                assertEquals(PropertyElement.AccessKind.METHOD, priceProperty.getReadAccessKind(), "price read should be METHOD access");
                assertEquals(PropertyElement.AccessKind.METHOD, priceProperty.getWriteAccessKind(), "price write should be METHOD access");

                // Should have both read and write methods
                assertTrue(priceProperty.getReadMethod().isPresent(), "price property should have read method");
                assertTrue(priceProperty.getWriteMethod().isPresent(), "price property should have write method");

                // Test read method details
                MethodElement readMethod = priceProperty.getReadMethod().get();
                assertEquals("price", readMethod.getName(), "read method should be named price");
                assertEquals("double", readMethod.getReturnType().getName(), "read method should return double");

                // Test write method details
                MethodElement writeMethod = priceProperty.getWriteMethod().get();
                assertEquals("price", writeMethod.getName(), "write method should be named price");
                assertEquals(1, writeMethod.getParameters().length, "write method should have 1 parameter");
                assertEquals("double", writeMethod.getParameters()[0].getType().getName(), "write method parameter should be double");

                // Test read-only property
                PropertyElement nameProperty = properties.stream()
                    .filter(p -> "read_only_name".equals(p.getName()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("read_only_name property should be found"));

                assertEquals("java.lang.String", nameProperty.getType().getName(), "read_only_name property should have String type");
                assertEquals(PropertyElement.AccessKind.METHOD, nameProperty.getReadAccessKind(), "read_only_name read should be METHOD access");
                assertNull(nameProperty.getWriteAccessKind(), "read_only_name should not have write access");

                assertTrue(nameProperty.getReadMethod().isPresent(), "read_only_name property should have read method");
                assertFalse(nameProperty.getWriteMethod().isPresent(), "read_only_name property should not have write method");
                assertFalse(nameProperty.getField().isPresent(), "read_only_name property should not have field");

                assertTrue(nameProperty.isReadOnly(), "read_only_name should be read-only");
                assertFalse(nameProperty.isWriteOnly(), "read_only_name should not be write-only");

                // Test documentation
                Optional<String> nameDoc = nameProperty.getDocumentation(true);
                assertTrue(nameDoc.isPresent(), "read_only_name should have documentation");
                assertEquals("Read-only name property.", nameDoc.get().trim());

                // Test field-based property
                PropertyElement descProperty = properties.stream()
                    .filter(p -> "description".equals(p.getName()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("description property should be found"));

                assertEquals("java.lang.String", descProperty.getType().getName(), "description property should have String type");
                assertEquals(PropertyElement.AccessKind.METHOD, descProperty.getReadAccessKind(), "description should be METHOD access");
                assertFalse(descProperty.getField().isPresent(), "skip fields because they are inaccessible");
                assertTrue(descProperty.getReadMethod().isPresent(), "description should have synthetic read method");
            }
        }
    }

    @Test
    void testPropertyElementWithQuery() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        try (PythonEnvironment environment = pythonProcessor.parse("""
            class TestPropertyQuery:
                # Regular field
                name: str = "test"

                # Property with getter and setter
                @property
                def age(self) -> int:
                    return 25

                @age.setter
                def age(self, value: int):
                    pass

                # Read-only property
                @property
                def readonly(self) -> str:
                    return "readonly"
            """)) {

            try (PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment)) {
                ClassElement classElement = processingEnvironment.classes().get("TestPropertyQuery");
                assertNotNull(classElement);
                assertInstanceOf(PythonClassElement.class, classElement);

                PythonClassElement pythonClass = (PythonClassElement) classElement;

                // Test getBeanProperties with default query
                List<PropertyElement> allProperties = pythonClass.getBeanProperties();
                assertNotNull(allProperties);
                assertTrue(allProperties.size() >= 1, "Should find at least 1 property");

                PropertyElementQuery allPropertiesQuery = PropertyElementQuery.of(pythonClass.getAnnotationMetadata())
                    .accessKinds(Set.of(BeanProperties.AccessKind.FIELD, BeanProperties.AccessKind.METHOD));
                List<PropertyElement> queriedProperties = pythonClass.getBeanProperties(allPropertiesQuery);

                assertNotNull(queriedProperties);
                assertEquals(allProperties.size(), queriedProperties.size(), "Query results should match default");

                // Test PropertyElementQuery filtering
                testPropertyElementQueryFiltering(pythonClass);

                // Results should be the same
                assertEquals(allProperties.size(), queriedProperties.size(), "Query results should match default");

                // Test property details
                PropertyElement ageProperty = allProperties.stream()
                    .filter(p -> "age".equals(p.getName()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("age property should be found"));

                assertFalse(ageProperty.isReadOnly(), "age should not be read-only");
                assertFalse(ageProperty.isWriteOnly(), "age should not be write-only");

                // Test read/write type information
                Optional<ClassElement> readType = ageProperty.getReadType();
                assertTrue(readType.isPresent(), "age should have read type");
                assertEquals("int", readType.get().getName());

                Optional<ClassElement> writeType = ageProperty.getWriteType();
                assertTrue(writeType.isPresent(), "age should have write type");
                assertEquals("int", writeType.get().getName());

                // Test member access
                Optional<? extends MemberElement> readMember = ageProperty.getReadMember();
                assertTrue(readMember.isPresent(), "age should have read member");
                assertTrue(readMember.get() instanceof MethodElement, "read member should be a method");

                Optional<? extends MemberElement> writeMember = ageProperty.getWriteMember();
                assertTrue(writeMember.isPresent(), "age should have write member");
                assertTrue(writeMember.get() instanceof MethodElement, "write member should be a method");

                // Test readonly property
                PropertyElement readonlyProperty = allProperties.stream()
                    .filter(p -> "readonly".equals(p.getName()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("readonly property should be found"));

                assertTrue(readonlyProperty.isReadOnly(), "readonly should be read-only");
                assertFalse(readonlyProperty.isWriteOnly(), "readonly should not be write-only");

                Optional<? extends MemberElement> readonlyWriteMember = readonlyProperty.getWriteMember();
                assertFalse(readonlyWriteMember.isPresent(), "readonly should not have write member");
            }
        }
    }

    private static void testPropertyElementQueryFiltering(PythonClassElement pythonClass) {
        // First, get all properties to understand what we have
        List<PropertyElement> allProperties = pythonClass.getBeanProperties();
        System.out.println("All properties found:");
        allProperties.forEach(p -> {
            String accessKind = p.getReadAccessKind() + "/" + (p.getWriteAccessKind() != null ? p.getWriteAccessKind() : "null");
            System.out.println("  - " + p.getName() + " (" + accessKind + ")");
        });

        // Test includes filtering
        PropertyElementQuery includesQuery = PropertyElementQuery.of(pythonClass.getAnnotationMetadata())
            .includes(Set.of("name", "age"));
        List<PropertyElement> includedProperties = pythonClass.getBeanProperties(includesQuery);
        assertEquals(2, includedProperties.size(), "Should include only name and age");
        assertTrue(includedProperties.stream().anyMatch(p -> "name".equals(p.getName())), "Should include name");
        assertTrue(includedProperties.stream().anyMatch(p -> "age".equals(p.getName())), "Should include age");

        // Test excludes filtering
        PropertyElementQuery excludesQuery = PropertyElementQuery.of(pythonClass.getAnnotationMetadata())
            .excludes(Set.of("readonly"));
        List<PropertyElement> excludedProperties = pythonClass.getBeanProperties(excludesQuery);
        assertFalse(excludedProperties.stream().anyMatch(p -> "readonly".equals(p.getName())), "Should exclude readonly");

        // Test static properties filtering - should exclude static properties
        PropertyElementQuery noStaticQuery = PropertyElementQuery.of(pythonClass.getAnnotationMetadata())
            .allowStaticProperties(false);
        List<PropertyElement> noStaticProperties = pythonClass.getBeanProperties(noStaticQuery);
        // All our test properties are non-static, so size should remain the same
        assertEquals(allProperties.size(), noStaticProperties.size(), "Should include all non-static properties");
    }
}
