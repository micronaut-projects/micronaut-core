package io.micronaut.python.processing;

import io.micronaut.python.processing.model.AttributeDef;
import io.micronaut.python.processing.model.ClassDef;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Attribute initialisers are resolved as literals; the annotation processor never executes user code.
 */
final class PythonAstParserAttributeValueTest {

    public static final class Probe {
        public static volatile boolean executed;

        public static void mark() {
            executed = true;
        }
    }

    @Test
    void attributeInitialisersAreLiteralsAndAreNotExecuted() {
        Probe.executed = false;
        PythonAstParser parser = new PythonAstParser();
        try (PythonEnvironment environment = parser.parse("""
            class Config:
                marker = java.type('io.micronaut.python.processing.PythonAstParserAttributeValueTest$Probe').mark()
                count = 1
                name: str = "demo"
                flags = [True, False]

            counter = java.type('io.micronaut.python.processing.PythonAstParserAttributeValueTest$Probe').mark()
            """)) {
            ClassDef config = environment.classes().get("Config");
            assertNotNull(config);
            Map<String, AttributeDef> attributes = config.attributes().stream()
                .collect(Collectors.toMap(AttributeDef::name, Function.identity()));

            assertFalse(Probe.executed, "attribute initialiser was executed by the annotation processor");
            assertNull(attributes.get("marker").value());
            assertEquals(1, attributes.get("count").value());
            assertEquals("demo", attributes.get("name").value());
            assertEquals(2, ((java.util.List<?>) attributes.get("flags").value()).size());
        } finally {
            parser.close();
        }
    }
}
