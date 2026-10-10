package io.micronaut.python.processing.diagnostic;

import io.micronaut.python.processing.model.SourceSpan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonDiagnosticsTest {

    private static final String SOURCE = """
        from jakarta.inject import Singleton

        @Singleton
        class Greeter:
            def greet(self, name: str) -> str:
                return values.ad(name)
        """;

    @Test
    void rendersTheMessageWithItsLocationAndAnExcerpt() {
        PythonDiagnostic diagnostic = PythonDiagnostic.error(
            "unknown-method",
            "Java type [java.util.ArrayList] has no method named [ad]",
            new SourceSpan("/src/greeter.py", 6, 23, 6, 25)
        );

        String rendered = PythonDiagnostics.render(diagnostic, Map.of("/src/greeter.py", SOURCE)::get);

        assertEquals(String.join(System.lineSeparator(),
            "[python:unknown-method] Java type [java.util.ArrayList] has no method named [ad]",
            "  --> /src/greeter.py:6:23",
            "  |",
            " 6 |         return values.ad(name)",
            "  |                       ^^"
        ), rendered);
        assertTrue(PythonDiagnostics.isLocated(rendered));
        assertTrue(PythonDiagnostics.isExcerptLine(" 6 |         return values.ad(name)"));
        assertTrue(PythonDiagnostics.isExcerptLine("  |                       ^^"));
        assertFalse(PythonDiagnostics.isExcerptLine("  --> /src/greeter.py:6:23"));
    }

    @Test
    void underlinesToTheEndOfTheFirstLineOfAMultiLineSpan() {
        PythonDiagnostic diagnostic = PythonDiagnostic.warning("unused", "unused function", new SourceSpan("greeter.py", 5, 5, 6, 31));

        String rendered = PythonDiagnostics.render(diagnostic, path -> SOURCE);

        assertTrue(rendered.endsWith(String.join(System.lineSeparator(),
            "  |",
            " 5 |     def greet(self, name: str) -> str:",
            "  |     ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^"
        )), rendered);
    }

    @Test
    void keepsTheTabsOfTheSourceLineInFrontOfTheCaret() {
        PythonDiagnostic diagnostic = PythonDiagnostic.error("unknown-method", "no such method", new SourceSpan("tabs.py", 3, 17, 3, 19));

        String rendered = PythonDiagnostics.render(diagnostic, path -> "class A:\n\tdef f(self):\n\t\treturn values.ad(1)\n");

        assertTrue(rendered.endsWith(String.join(System.lineSeparator(),
            "  |",
            " 3 | \t\treturn values.ad(1)",
            "  | \t\t              ^^"
        )), rendered);
    }

    @Test
    void rendersWithoutAnExcerptWhenTheSourceOrThePositionIsUnknown() {
        PythonDiagnostic located = PythonDiagnostic.error("unresolved-import", "Cannot import [X]", SourceSpan.at("app.py", 3, 1));
        PythonDiagnostic unlocated = PythonDiagnostic.error("unresolved-import", "Cannot import [X]", null);

        assertEquals("[python:unresolved-import] Cannot import [X]" + System.lineSeparator() + "  --> app.py:3:1", PythonDiagnostics.render(located));
        assertEquals("[python:unresolved-import] Cannot import [X]", PythonDiagnostics.render(unlocated, path -> SOURCE));
        assertFalse(PythonDiagnostics.isLocated(PythonDiagnostics.render(unlocated)));
        assertEquals("error: [python:unresolved-import] Cannot import [X] (app.py:3:1)", located.toString());
        assertEquals(List.of("Y"), located.withSuggestions(List.of("Y")).suggestions());
    }
}
