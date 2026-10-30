package io.micronaut.python.compiler;

import io.micronaut.python.processing.diagnostic.PythonDiagnostic;
import io.micronaut.python.processing.typecheck.TypeCheckMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PyronautCompilerTypeCheckTest {

    @Test
    void theTypeCheckModeIsPassedAsAProcessorOptionAndDiagnosticsReachTheCallback() {
        List<PythonDiagnostic> diagnostics = new ArrayList<>();

        PyronautCompiler compiler = PyronautCompiler.builder()
            .pythonCode("""
                from jakarta.inject import Singleton
                from java.util import NoSuchList

                @Singleton
                class Service:
                    def run(self) -> str:
                        return "x"
                """)
            .typeCheck(TypeCheckMode.ERROR)
            .pythonDiagnosticCallback(diagnostics::add)
            .build();
        RuntimeException exception = assertThrows(RuntimeException.class, compiler::buildClassLoader);

        assertTrue(exception.getMessage().contains("[python:unresolved-import]"), exception.getMessage());
        assertEquals(1, diagnostics.size());
        PythonDiagnostic diagnostic = diagnostics.get(0);
        assertEquals("unresolved-import", diagnostic.rule());
        assertEquals(PythonDiagnostic.Kind.ERROR, diagnostic.kind());
        assertNotNull(diagnostic.span());
        assertEquals(2, diagnostic.span().line());
    }

    @Test
    void checkedSourcesWithoutProblemsCompileInEveryMode() {
        for (TypeCheckMode mode : TypeCheckMode.values()) {
            ClassLoader classLoader = PyronautCompiler.builder()
                .pythonCode("""
                    from jakarta.inject import Singleton
                    from micronaut.context.python.annotation import TypeChecked

                    @Singleton
                    @TypeChecked
                    class Service:
                        def run(self) -> str:
                            return "x"

                        @TypeChecked(False)
                        def legacy(self) -> str:
                            return "y"
                    """)
                .typeCheck(mode)
                .build()
                .buildClassLoader();
            assertNotNull(classLoader, mode.name());
        }
    }

    @Test
    void theSwitchImportedFromPyronautBuildPlainOrAliasedChecksTheFunction() {
        List<PythonDiagnostic> diagnostics = new ArrayList<>();
        PyronautCompiler compiler = PyronautCompiler.builder()
            .pythonCode("""
                from jakarta.inject import Singleton
                from java.util import ArrayList
                from pyronaut.build import TypeChecked
                from pyronaut.build import TypeChecked as Checked

                @Singleton
                class Service:
                    @TypeChecked
                    def plain(self) -> int:
                        values = ArrayList()
                        values.plainMissing()
                        return values.size()

                    @Checked
                    def aliased(self) -> int:
                        values = ArrayList()
                        values.aliasedMissing()
                        return values.size()

                    def unchecked(self) -> int:
                        values = ArrayList()
                        values.uncheckedMissing()
                        return values.size()
                """)
            .typeCheck(TypeCheckMode.OFF)
            .pythonDiagnosticCallback(diagnostics::add)
            .build();
        // a switch under mode off checks at error severity
        assertThrows(RuntimeException.class, compiler::buildClassLoader);

        String messages = diagnostics.stream().map(PythonDiagnostic::message).toList().toString();
        assertEquals(2, diagnostics.size(), messages);
        assertTrue(messages.contains("[plainMissing]"), messages);
        assertTrue(messages.contains("[aliasedMissing]"), messages);
    }

    @Test
    void anUnknownModeIsRejectedWithAClearMessage() {
        PyronautCompiler compiler = PyronautCompiler.builder()
            .pythonCode("answer = 42")
            .options(List.of("-A" + TypeCheckMode.OPTION + "=strict"))
            .build();
        RuntimeException exception = assertThrows(RuntimeException.class, compiler::buildClassLoader);

        assertTrue(exception.getMessage().contains("Unknown value [strict] of the option micronaut.python.typecheck"), exception.getMessage());
    }
}
