package io.micronaut.python.processing;

import io.micronaut.python.processing.visitor.ClassDef;
import io.micronaut.python.processing.visitor.PythonClassElement;
import jakarta.inject.Named;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PythonAstParserTest {

    @Test
    void testParse() {
        PythonEnvironment environment = buildEnv();

        ClassDef myClass = environment.classes().get("MyClass");

        assertNotNull(myClass);
        assertEquals(1, myClass.functions().size());
        assertEquals("f", myClass.functions().get(0).name());

        assertEquals(3, environment.decorators().size());

        assertTrue(environment.decorators().containsKey(Singleton.class.getName()));
        assertTrue(environment.decorators().containsKey(Scope.class.getName()));
        assertTrue(environment.decorators().containsKey(Named.class.getName()));
    }

    @Test
    void testProcessingEnvironment() {
        PythonEnvironment environment = buildEnv();

        PythonProcessingEnvironment processingEnvironment = new PythonProcessingEnvironment(environment);


        Map<String, PythonClassElement> classes = processingEnvironment.classes();
        assertNotNull(classes);
        assertEquals(1, classes.size());

        PythonClassElement myClass = classes.get("MyClass");

        assertNotNull(myClass);
        assertEquals("MyClass", myClass.getName());
    }

    @Test
    void testAttributeParsing() {
        PythonAstParser pythonProcessor = new PythonAstParser();
        PythonEnvironment environment = pythonProcessor.parse("""
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
            """);

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
        assertEquals(42, simpleAttr.get().value(), "simple_attr should have value 42");
        assertNull(simpleAttr.get().annotation(), "simple_attr should have no annotation");

        var nameAttr = testClass.attributes().stream()
            .filter(attr -> "name".equals(attr.name()))
            .findFirst();
        assertTrue(nameAttr.isPresent(), "name attribute should be parsed");
        assertEquals("test", nameAttr.get().value(), "name should have value 'test'");

        var annotatedAttr = testClass.attributes().stream()
            .filter(attr -> "annotated_attr".equals(attr.name()))
            .findFirst();
        assertTrue(annotatedAttr.isPresent(), "annotated_attr should be parsed");
        assertEquals("int", annotatedAttr.get().annotation(), "annotated_attr should have int annotation");
        assertEquals(100, annotatedAttr.get().value(), "annotated_attr should have value 100");

        var finalAttr = testClass.attributes().stream()
            .filter(attr -> "final_attr".equals(attr.name()))
            .findFirst();
        assertTrue(finalAttr.isPresent(), "final_attr should be parsed");
        assertTrue(finalAttr.get().annotation().contains("Final"), "final_attr should have Final annotation");

        var complexAttr = testClass.attributes().stream()
            .filter(attr -> "complex_attr".equals(attr.name()))
            .findFirst();
        assertTrue(complexAttr.isPresent(), "complex_attr should be parsed");
        assertTrue(complexAttr.get().annotation().contains("Annotated"), "complex_attr should have Annotated annotation");

        // Should still parse the regular method (properties are ignored)
        assertEquals(1, testClass.functions().size());
        assertEquals("regular_method", testClass.functions().get(0).name());
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
