package io.micronaut.python.processing;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.visitor.VisitorContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PythonReflectionGateTest {

    @Test
    void noPatternsAllowNothing() {
        assertTrue(PythonReflectionGate.of((String) null).isEmpty());
        assertTrue(PythonReflectionGate.of("").isEmpty());
        assertTrue(PythonReflectionGate.of(" , ").isEmpty());
        assertFalse(PythonReflectionGate.of("").allows("com.example.Order"));
    }

    @Test
    void packagePatternMatchesThePackageAndItsSubPackages() {
        PythonReflectionGate gate = PythonReflectionGate.of("com.example.model.*");
        assertTrue(gate.allows("com.example.model.Order"));
        assertTrue(gate.allows("com.example.model.nested.Line"));
        assertTrue(gate.allows("com.example.model.Order$Line"));
        assertFalse(gate.allows("com.example.modelling.Order"));
        assertFalse(gate.allows("com.example.Order"));
        assertFalse(gate.isEmpty());
    }

    @Test
    void classPatternMatchesTheWholeName() {
        PythonReflectionGate gate = PythonReflectionGate.of("com.example.Order");
        assertTrue(gate.allows("com.example.Order"));
        assertFalse(gate.allows("com.example.OrderLine"));
        assertFalse(gate.allows("com.example.Order$Line"));
        assertFalse(gate.allows("org.com.example.Order"));
    }

    @Test
    void leadingAndInnerStarsAreWildcards() {
        assertTrue(PythonReflectionGate.of("*.Order").allows("com.example.Order"));
        assertFalse(PythonReflectionGate.of("*.Order").allows("Order"));
        PythonReflectionGate gate = PythonReflectionGate.of("com.*.model.*Entity");
        assertTrue(gate.allows("com.example.model.BookEntity"));
        assertTrue(gate.allows("com.a.b.model.Entity"));
        assertFalse(gate.allows("com.example.model.Book"));
    }

    @Test
    void dotsOfAPatternAreLiterals() {
        assertFalse(PythonReflectionGate.of("com.example.Order").allows("comXexampleXOrder"));
    }

    @Test
    void starAllowsEverything() {
        assertTrue(PythonReflectionGate.of("*").allows("com.example.Order"));
        assertTrue(PythonReflectionGate.of("com.example.Order, *").allows("anything"));
        assertTrue(PythonReflectionGate.of("**").allows("anything"));
        assertFalse(PythonReflectionGate.of("*").isEmpty());
    }

    @Test
    void severalPatternsAreSeparatedByCommasAndTrimmed() {
        PythonReflectionGate gate = PythonReflectionGate.of(" com.example.model.* , org.acme.Book ,, ");
        assertTrue(gate.allows("com.example.model.Order"));
        assertTrue(gate.allows("org.acme.Book"));
        assertFalse(gate.allows("org.acme.Author"));
    }

    @Test
    void theGateReadsTheProcessorOptionAndTheSystemPropertyOfTheCompilation() {
        assertEquals("micronaut.introspection.allowReflection", PythonReflectionGate.OPTION);
        assertEquals("micronaut.introspection.allow-reflection", PythonReflectionGate.PROPERTY);

        VisitorContext context = visitorContext(Map.of(PythonReflectionGate.OPTION, "com.example.model.*"), new ArrayList<>());
        assertTrue(PythonReflectionGate.of(context).allows("com.example.model.Order"));
        assertFalse(PythonReflectionGate.of(context).allows("com.example.Order"));

        context = visitorContext(Map.of(PythonReflectionGate.PROPERTY, "com.example.Order"), new ArrayList<>());
        assertTrue(PythonReflectionGate.of(context).allows("com.example.Order"));
        assertFalse(PythonReflectionGate.of(context).allows("com.example.model.Order"));

        context = visitorContext(Map.of(PythonReflectionGate.OPTION, "com.example.model.*", PythonReflectionGate.PROPERTY, "com.example.Order"), new ArrayList<>());
        assertTrue(PythonReflectionGate.of(context).allows("com.example.model.Order"));
        assertTrue(PythonReflectionGate.of(context).allows("com.example.Order"));

        assertTrue(PythonReflectionGate.of(visitorContext(Map.of("micronaut.other", "value"), new ArrayList<>())).isEmpty());
        assertTrue(PythonReflectionGate.of(visitorContext(Map.of(PythonReflectionGate.OPTION, ""), new ArrayList<>())).isEmpty());
    }

    @Test
    void aRefusedTypeIsReportedOnceNamingTheOption() {
        List<String> messages = new ArrayList<>();
        VisitorContext context = visitorContext(Map.of(), messages);
        Element element = ClassElement.of(String.class);
        PythonReflectionGate gate = PythonReflectionGate.of("com.example.model.*");

        assertFalse(gate.allows("com.example.Order", "jakarta.persistence.Entity", element, context));
        assertFalse(gate.allows("com.example.Order", "jakarta.persistence.Table", element, context));
        assertFalse(gate.allows("com.example.Author", "jakarta.persistence.Entity", element, context));
        assertTrue(gate.allows("com.example.model.Book", "jakarta.persistence.Entity", element, context));

        assertEquals(2, messages.size());
        assertTrue(messages.get(0).contains("[com.example.Order]"));
        assertTrue(messages.get(0).contains("@jakarta.persistence.Entity"));
        assertTrue(messages.get(0).contains("-A" + PythonReflectionGate.OPTION + "=com.example.Order"));
        assertTrue(messages.get(1).contains("[com.example.Author]"));
    }

    @Test
    void beanValidationConstraintsAreLeftOffWithoutANote() {
        List<String> messages = new ArrayList<>();
        VisitorContext context = visitorContext(Map.of(), messages);
        Element element = ClassElement.of(String.class);
        PythonReflectionGate gate = PythonReflectionGate.of("");

        assertFalse(gate.allows("com.example.Person", "jakarta.validation.constraints.NotBlank$List", element, context));
        assertFalse(gate.allows("com.example.Person", "javax.validation.constraints.NotNull", element, context));
        assertTrue(messages.isEmpty());

        assertFalse(gate.allows("com.example.Person", "jakarta.persistence.Entity", element, context));
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("@jakarta.persistence.Entity"));
    }

    /**
     * A visitor context answering the given options and recording the messages reported through
     * {@code info(String, Element)}.
     */
    private static VisitorContext visitorContext(Map<String, String> options, List<String> messages) {
        return (VisitorContext) Proxy.newProxyInstance(
            PythonReflectionGateTest.class.getClassLoader(),
            new Class<?>[] {VisitorContext.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getOptions" -> options;
                case "info" -> messages.add((String) args[0]);
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
    }
}
