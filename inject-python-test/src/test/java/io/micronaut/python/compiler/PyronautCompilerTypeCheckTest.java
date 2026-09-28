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

        RuntimeException exception = assertThrows(RuntimeException.class, () -> PyronautCompiler.builder()
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
            .build()
            .buildClassLoader());

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
    void anUnknownModeIsRejectedWithAClearMessage() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> PyronautCompiler.builder()
            .pythonCode("answer = 42")
            .options(List.of("-A" + TypeCheckMode.OPTION + "=strict"))
            .build()
            .buildClassLoader());

        assertTrue(exception.getMessage().contains("Unknown value [strict] of the option micronaut.python.typecheck"), exception.getMessage());
    }
}
