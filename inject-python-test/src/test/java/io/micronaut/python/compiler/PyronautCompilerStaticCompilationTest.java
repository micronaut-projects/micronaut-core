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
package io.micronaut.python.compiler;

import io.micronaut.python.processing.diagnostic.PythonDiagnostic;
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision;
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision.Outcome;
import io.micronaut.python.processing.staticcompile.StaticCompilationMode;
import io.micronaut.python.processing.staticcompile.StaticCompilationReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PyronautCompilerStaticCompilationTest {

    private static final String SOURCE = """
        from jakarta.inject import Singleton
        from micronaut.context.python.annotation import CompileStatic
        from java.util import ArrayList

        @Singleton
        class PricingService:
            def __init__(self, rate: float):
                self.rate = rate

            def total(self, quantity: int, unit_price: float) -> float:
                return quantity * unit_price * self.rate

            def names(self, values: ArrayList) -> str:
                return ",".join([str(value) for value in values])

            @CompileStatic
            def describe(self, *parts: str) -> str:
                return ",".join(parts)

            @CompileStatic(False)
            def legacy(self, payload) -> str:
                return str(payload)
        """;

    @Test
    void everyFunctionGetsADecisionAndTheReportIsWritten(@TempDir Path directory) throws IOException {
        List<StaticCompilationDecision> decisions = new ArrayList<>();
        List<PythonDiagnostic> diagnostics = new ArrayList<>();

        ClassLoader classLoader = PyronautCompiler.builder()
            .pythonCode(SOURCE)
            .staticCompilation(StaticCompilationMode.ALL)
            .staticCompilationReport(directory.toFile())
            .staticCompilationDecisionCallback(decisions::add)
            .pythonDiagnosticCallback(diagnostics::add)
            .build()
            .buildClassLoader();

        assertNotNull(classLoader);
        Map<String, StaticCompilationDecision> byName = decisions.stream().collect(Collectors.toMap(StaticCompilationDecision::qualifiedName, Function.identity()));
        assertEquals(Outcome.NOT_CANDIDATE, byName.get("PricingService.__init__").outcome());
        assertEquals(Outcome.COMPILED, byName.get("PricingService.total").outcome());
        assertEquals(Outcome.SKIPPED, byName.get("PricingService.names").outcome());
        assertEquals("unsupported-expression", byName.get("PricingService.names").reasons().get(0).rule());
        assertEquals(Outcome.SKIPPED, byName.get("PricingService.describe").outcome());
        assertEquals(Outcome.EXCLUDED, byName.get("PricingService.legacy").outcome());
        assertNotNull(byName.get("PricingService.total").span());
        assertEquals(10, byName.get("PricingService.total").span().line());

        // the explicit switch on describe cannot be honoured: a warning, since the build succeeded
        assertEquals(1, diagnostics.size(), diagnostics.toString());
        assertEquals("compile-static", diagnostics.get(0).rule());
        assertEquals(PythonDiagnostic.Kind.WARNING, diagnostics.get(0).kind());
        assertTrue(diagnostics.get(0).message().startsWith("[PricingService.describe] cannot be compiled statically: [varargs-signature]"), diagnostics.get(0).message());

        List<String> lines = Files.readAllLines(directory.resolve(StaticCompilationReport.DECISIONS_FILE));
        assertEquals("{\"record\":\"plan\",\"mode\":\"all\",\"coverage\":\"full\"}", lines.get(0));
        assertEquals(decisions.size() + 1, lines.size());
        assertTrue(lines.get(1).startsWith("{\"record\":\"decision\",\"name\":\"PricingService.__init__\""), lines.get(1));
        String summary = Files.readString(directory.resolve(StaticCompilationReport.SUMMARY_FILE));
        assertTrue(summary.contains("COMPILED      PricingService.total"), summary);
        assertTrue(summary.contains("[unsupported-expression]"), summary);
        assertTrue(summary.contains("EXCLUDED      1"), summary);
    }

    @Test
    void aCompiledBodyIsGeneratedAsJavaInsideTheStub(@TempDir Path directory) throws IOException {
        Path output = Files.createDirectories(directory.resolve("output"));
        PyronautCompiler.builder()
            .pythonCode(SOURCE)
            .staticCompilation(StaticCompilationMode.ALL)
            .targetDir(output.toFile())
            .build()
            .compile();

        String generated;
        try (var paths = Files.walk(output)) {
            generated = Files.readString(paths.filter(path -> path.getFileName().toString().equals("PricingService.java")).findFirst().orElseThrow());
        }
        String total = generated.substring(generated.indexOf("Compiled from"), generated.indexOf("public String names("));
        assertTrue(total.contains("public double total(int quantity, double unit_price)"), total);
        assertTrue(total.contains("this.asPolyglotValue().getMember(\"rate\").asDouble()"), total);
        assertTrue(!total.contains("invokePythonMethod"), total);
        // the method that is not compiled keeps its bridge
        assertTrue(generated.substring(generated.indexOf("public String names(")).contains("invokePythonMethod"), generated);
    }

    @Test
    void aStubWithCompiledBodiesBindsItsPythonObjectAndTracesWhenAsked(@TempDir Path directory) throws IOException {
        Path output = Files.createDirectories(directory.resolve("output"));
        PyronautCompiler.builder()
            .pythonCode(SOURCE)
            .staticCompilation(StaticCompilationMode.ALL)
            .options(List.of("-A" + StaticCompilationMode.TRACE_OPTION + "=true"))
            .targetDir(output.toFile())
            .build()
            .compile();

        String generated;
        try (var paths = Files.walk(output)) {
            generated = Files.readString(paths.filter(path -> path.getFileName().toString().equals("PricingService.java")).findFirst().orElseThrow());
        }
        assertTrue(generated.contains("PythonStatic.entered(\"python.PricingService#total\")"), generated);
        assertTrue(generated.contains("PythonStatic.bindCompiled(value, new python.PricingService.PyronautCompiled(this, value))"), generated);
        assertTrue(generated.contains("public static final class PyronautCompiled"), generated);
        assertTrue(generated.contains("private final Value self;"), generated);
        assertEquals(2, generated.split("Compiled from ", -1).length - 1, generated);
    }

    @Test
    void strictModeFailsTheBuildForAnExplicitSwitchThatCannotBeHonoured() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> PyronautCompiler.builder()
            .pythonCode(SOURCE)
            .staticCompilation(StaticCompilationMode.ANNOTATED)
            .options(List.of("-A" + StaticCompilationMode.STRICT_OPTION + "=true"))
            .build()
            .buildClassLoader());

        assertTrue(exception.getMessage().contains("[python:compile-static] [PricingService.describe] cannot be compiled statically"), exception.getMessage());
    }

    @Test
    void nothingIsPlannedWhenTheModeIsOffAndNoSwitchIsMentioned() {
        List<StaticCompilationDecision> decisions = new ArrayList<>();
        PyronautCompiler.builder()
            .pythonCode("""
                from jakarta.inject import Singleton

                @Singleton
                class Service:
                    def run(self, value: int) -> int:
                        return value
                """)
            .staticCompilationDecisionCallback(decisions::add)
            .build()
            .buildClassLoader();
        assertTrue(decisions.isEmpty());
    }

    @Test
    void anUnknownModeIsRejectedWithAClearMessage() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> PyronautCompiler.builder()
            .pythonCode("answer = 42")
            .options(List.of("-A" + StaticCompilationMode.OPTION + "=fast"))
            .build()
            .buildClassLoader());

        assertTrue(exception.getMessage().contains("Unknown value [fast] of the option micronaut.python.compile.static"), exception.getMessage());
    }
}
