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

import io.micronaut.context.ApplicationContext;
import io.micronaut.python.processing.diagnostic.PythonDiagnostic;
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision;
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision.Outcome;
import io.micronaut.python.processing.staticcompile.StaticCompilationMode;
import io.micronaut.python.processing.staticcompile.StaticCompilationReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.LineNumber;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static final String LINE_MAPPED_SOURCE = """
        from jakarta.inject import Singleton
        from java.lang import IllegalArgumentException


        @Singleton
        class Checks:
            def check(self, n: int) -> int:
                doubled = n * 2
                if doubled > 10:
                    raise IllegalArgumentException("too big: " + str(doubled))
                return doubled
        """;

    @Test
    void everyFunctionGetsADecisionAndTheReportIsWritten(@TempDir Path directory) throws IOException {
        List<StaticCompilationDecision> decisions = new ArrayList<>();
        List<PythonDiagnostic> diagnostics = new ArrayList<>();

        ByteArrayOutputStream console = new ByteArrayOutputStream();
        PrintStream err = System.err;
        ClassLoader classLoader;
        System.setErr(new PrintStream(console, true, StandardCharsets.UTF_8));
        try {
            classLoader = PyronautCompiler.builder()
                .pythonCode(SOURCE)
                .staticCompilation(StaticCompilationMode.ALL)
                .staticCompilationReport(directory.toFile())
                .staticCompilationDecisionCallback(decisions::add)
                .pythonDiagnosticCallback(diagnostics::add)
                .build()
                .buildClassLoader();
        } finally {
            System.setErr(err);
        }

        assertNotNull(classLoader);
        // the compilation says once that the feature is experimental, naming the mode and the report
        String output = console.toString(StandardCharsets.UTF_8);
        String note = "Static compilation of Python is experimental and in use (mode all)";
        assertTrue(output.contains("NOTE: " + note), output);
        assertEquals(output.indexOf(note), output.lastIndexOf(note), output);
        assertTrue(output.contains("Review the decisions in " + directory), output);
        Map<String, StaticCompilationDecision> byName = decisions.stream().collect(Collectors.toMap(StaticCompilationDecision::qualifiedName, Function.identity()));
        assertEquals(Outcome.NOT_CANDIDATE, byName.get("PricingService.__init__").outcome());
        assertEquals(Outcome.COMPILED, byName.get("PricingService.total").outcome());
        assertEquals(Outcome.SKIPPED, byName.get("PricingService.names").outcome());
        // the comprehension is lowered; the elements of a raw ArrayList have no type
        assertEquals("unknown-type", byName.get("PricingService.names").reasons().get(0).rule());
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
        assertTrue(summary.contains("[unknown-type]"), summary);
        assertTrue(summary.contains("              fix: "), summary);
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
        assertTrue(generated.contains("PythonStatic.bindCompiled(value, \"python.PricingService\", new python.PricingService.PyronautCompiled())"), generated);
        assertTrue(generated.contains("public static final class PyronautCompiled"), generated);
        // stateless: the delegate takes the Python object, so binding an object keeps nothing alive
        assertTrue(generated.contains("public double total(Value __mnSelf, int quantity, double unit_price)"), generated);
        assertTrue(generated.contains("throw PythonStatic.forPython(__mnSelf, "), generated);
        assertEquals(2, generated.split("Compiled from ", -1).length - 1, generated);
    }

    @Test
    void aConditionalOperandIsGroupedInTheGeneratedJava(@TempDir Path directory) throws IOException {
        Path output = Files.createDirectories(directory.resolve("output"));
        PyronautCompiler.builder()
            .pythonCode("""
                from jakarta.inject import Singleton

                @Singleton
                class Grouping:
                    def shifted(self, flag: bool, n: int) -> int:
                        return (n if flag else 0) + 1

                    def negated(self, flag: bool, n: int) -> int:
                        return -(n if flag else 0)
                """)
            .staticCompilation(StaticCompilationMode.ALL)
            .targetDir(output.toFile())
            .build()
            .compile();

        String generated;
        try (var paths = Files.walk(output)) {
            generated = Files.readString(paths.filter(path -> path.getFileName().toString().equals("Grouping.java")).findFirst().orElseThrow());
        }
        // without the grouping, Java would read flag ? n : 0 + 1
        String shifted = generated.substring(generated.indexOf("public int shifted("));
        assertTrue(shifted.substring(0, shifted.indexOf('}')).contains("Math.addExact(PythonStatic.group(flag ? "), shifted);
        String negated = generated.substring(generated.indexOf("public int negated("));
        assertTrue(negated.substring(0, negated.indexOf('}')).contains("Math.negateExact(PythonStatic.group(flag ? "), negated);
    }

    @Test
    void aCompiledJavaCallNamesTheOverloadItBindsTo(@TempDir Path directory) throws IOException {
        Path output = Files.createDirectories(directory.resolve("output"));
        PyronautCompiler.builder()
            .pythonCode("""
                from jakarta.inject import Singleton
                from io.micronaut.python.annotation.processing.test import StaticOverloads

                @Singleton
                class Overloaded:
                    def none(self) -> str:
                        return StaticOverloads.which(None)

                    def text(self, value: str) -> str:
                        return StaticOverloads.which(value)

                    def sequence(self, value: str) -> str:
                        return StaticOverloads.sequence(value)
                """)
            .staticCompilation(StaticCompilationMode.ALL)
            .targetDir(output.toFile())
            .build()
            .compile();

        String generated;
        try (var paths = Files.walk(output)) {
            generated = Files.readString(paths.filter(path -> path.getFileName().toString().equals("Overloaded.java")).findFirst().orElseThrow());
        }
        // a None of the parameter's type, an argument at its own type, an argument cast to the overload's parameter
        assertTrue(generated.contains("String none0 = null;"), generated);
        assertTrue(generated.contains("StaticOverloads.which(none0)"), generated);
        assertTrue(generated.contains("StaticOverloads.which(value)"), generated);
        assertTrue(generated.contains("StaticOverloads.sequence((CharSequence) value)"), generated);
    }

    @Test
    void aSkippedDecisionRecordCarriesEveryField(@TempDir Path directory) throws IOException {
        PyronautCompiler.builder()
            .pythonCode(SOURCE)
            .staticCompilation(StaticCompilationMode.ALL)
            .staticCompilationReport(directory.toFile())
            .build()
            .buildClassLoader();

        String line = Files.readAllLines(directory.resolve(StaticCompilationReport.DECISIONS_FILE)).stream()
            .filter(candidate -> candidate.contains("\"name\":\"PricingService.names\""))
            .findFirst()
            .orElseThrow();
        JsonNode record = new ObjectMapper().readTree(line);
        assertEquals(List.of("record", "name", "source", "line", "column", "outcome", "scope", "reasons", "stats"), propertyNames(record), line);
        assertEquals("decision", record.get("record").asString());
        assertEquals("PricingService.names", record.get("name").asString());
        String source = record.get("source").asString();
        assertFalse(source.isEmpty(), line);
        assertEquals(13, record.get("line").asInt());
        assertEquals(5, record.get("column").asInt());
        assertEquals("SKIPPED", record.get("outcome").asString());
        assertEquals("MODE", record.get("scope").asString());

        JsonNode reasons = record.get("reasons");
        assertEquals(1, reasons.size(), line);
        JsonNode reason = reasons.get(0);
        assertEquals(List.of("rule", "message", "location", "hint"), propertyNames(reason), line);
        // the comprehension is lowered; the elements of the raw ArrayList it iterates have no type
        assertEquals("unknown-type", reason.get("rule").asString());
        assertEquals("the elements of the [java.util.ArrayList] have no static type", reason.get("message").asString());
        assertEquals(source + ":14:50", reason.get("location").asString());
        assertTrue(reason.get("hint").asString().startsWith("Give the value a static type"), line);

        JsonNode stats = record.get("stats");
        assertEquals(List.of("statements", "javaCalls", "bridgeCalls", "helperCalls"), propertyNames(stats), line);
        assertEquals(1, stats.get("statements").asInt());
        assertEquals(0, stats.get("javaCalls").asInt());
        assertEquals(0, stats.get("bridgeCalls").asInt());
        assertEquals(0, stats.get("helperCalls").asInt());
    }

    private static List<String> propertyNames(JsonNode node) {
        return node.properties().stream().map(Map.Entry::getKey).toList();
    }

    @Test
    void strictModeFailsTheBuildForAnExplicitSwitchThatCannotBeHonoured() {
        PyronautCompiler compiler = PyronautCompiler.builder()
            .pythonCode(SOURCE)
            .staticCompilation(StaticCompilationMode.ANNOTATED)
            .options(List.of("-A" + StaticCompilationMode.STRICT_OPTION + "=true"))
            .build();
        RuntimeException exception = assertThrows(RuntimeException.class, compiler::buildClassLoader);

        assertTrue(exception.getMessage().contains("[python:compile-static] [PricingService.describe] cannot be compiled statically"), exception.getMessage());
    }

    @Test
    void theExperimentalNoteIsGivenOnlyWhenSomethingCompiles() {
        // in mode off, a module mentioning CompileStatic records its functions, the switched off one EXCLUDED
        String off = """
            from jakarta.inject import Singleton
            from micronaut.context.python.annotation import CompileStatic

            @Singleton
            class Service:
                @CompileStatic(False)
                def legacy(self, value: int) -> int:
                    return value
            """;
        String on = off.replace("@CompileStatic(False)", "@CompileStatic");
        for (String source : List.of(off, on)) {
            List<StaticCompilationDecision> decisions = new ArrayList<>();
            ByteArrayOutputStream console = new ByteArrayOutputStream();
            PrintStream err = System.err;
            System.setErr(new PrintStream(console, true, StandardCharsets.UTF_8));
            try {
                PyronautCompiler.builder()
                    .pythonCode(source)
                    .staticCompilationDecisionCallback(decisions::add)
                    .build()
                    .buildClassLoader();
            } finally {
                System.setErr(err);
            }
            String output = console.toString(StandardCharsets.UTF_8);
            Outcome outcome = decisions.stream().filter(decision -> decision.qualifiedName().equals("Service.legacy")).findFirst().orElseThrow().outcome();
            if (source.equals(off)) {
                assertEquals(Outcome.EXCLUDED, outcome);
                assertTrue(!output.contains("Static compilation of Python is experimental"), output);
            } else {
                assertEquals(Outcome.COMPILED, outcome);
                assertTrue(output.contains("Static compilation of Python is experimental and in use (switched on by CompileStatic)"), output);
            }
        }
    }

    @Test
    void theSwitchImportedFromPyronautBuildPlainOrAliasedIsHonoured() {
        List<StaticCompilationDecision> decisions = new ArrayList<>();
        List<PythonDiagnostic> diagnostics = new ArrayList<>();
        PyronautCompiler.builder()
            .pythonCode("""
                from jakarta.inject import Singleton
                from pyronaut.build import CompileStatic
                from pyronaut.build import CompileStatic as CS

                @Singleton
                class Switches:
                    @CompileStatic
                    def plain(self, n: int) -> int:
                        return n

                    @CS
                    def aliased(self, n: int) -> int:
                        return n

                    def unswitched(self, n: int) -> int:
                        return n

                @CS
                @Singleton
                class AliasedClass:
                    def run(self, n: int) -> int:
                        return n
                """)
            .staticCompilation(StaticCompilationMode.ANNOTATED)
            .staticCompilationDecisionCallback(decisions::add)
            .pythonDiagnosticCallback(diagnostics::add)
            .build()
            .buildClassLoader();

        Map<String, StaticCompilationDecision> byName = decisions.stream().collect(Collectors.toMap(StaticCompilationDecision::qualifiedName, Function.identity()));
        assertEquals(Outcome.COMPILED, byName.get("Switches.plain").outcome());
        assertEquals(StaticCompilationDecision.Scope.FUNCTION, byName.get("Switches.plain").scope());
        assertEquals(Outcome.COMPILED, byName.get("Switches.aliased").outcome());
        assertEquals(StaticCompilationDecision.Scope.FUNCTION, byName.get("Switches.aliased").scope());
        assertEquals(Outcome.EXCLUDED, byName.get("Switches.unswitched").outcome());
        assertEquals(StaticCompilationDecision.Scope.MODE, byName.get("Switches.unswitched").scope());
        assertEquals(Outcome.COMPILED, byName.get("AliasedClass.run").outcome());
        assertEquals(StaticCompilationDecision.Scope.CLASS, byName.get("AliasedClass.run").scope());
        assertTrue(diagnostics.isEmpty(), diagnostics.toString());
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
        PyronautCompiler compiler = PyronautCompiler.builder()
            .pythonCode("answer = 42")
            .options(List.of("-A" + StaticCompilationMode.OPTION + "=fast"))
            .build();
        RuntimeException exception = assertThrows(RuntimeException.class, compiler::buildClassLoader);

        assertTrue(exception.getMessage().contains("Unknown value [fast] of the option micronaut.python.compile.static"), exception.getMessage());
    }

    @Test
    void anUnwritableReportDirectoryFailsTheBuildNamingIt(@TempDir Path directory) throws IOException {
        Path notADirectory = Files.writeString(directory.resolve("report"), "a file");
        PyronautCompiler compiler = PyronautCompiler.builder()
            .pythonCode(SOURCE)
            .staticCompilation(StaticCompilationMode.ALL)
            .staticCompilationReport(notADirectory.toFile())
            .build();
        RuntimeException exception = assertThrows(RuntimeException.class, compiler::buildClassLoader);

        assertTrue(exception.getMessage().contains("Cannot write the static compilation report to the directory [" + notADirectory.toAbsolutePath().normalize() + "]"), exception.getMessage());
        assertFalse(exception.getMessage().contains("Failed Trace"), exception.getMessage());
    }

    @Test
    void anIncrementalBuildKeepsTheDecisionsOfUnchangedSourcesAndDropsRemovedFunctions(@TempDir Path directory) throws IOException {
        Path python = Files.createDirectories(directory.resolve("python"));
        Path java = Files.createDirectories(directory.resolve("java"));
        Path output = Files.createDirectories(directory.resolve("classes"));
        Path cache = directory.resolve("incremental");
        Path report = directory.resolve("report");
        Path orders = Files.writeString(python.resolve("orders.py"), """
            class Orders:
                def place(self, quantity: int) -> int:
                    return quantity

                def cancel(self, quantity: int) -> int:
                    return -quantity
            """);
        Files.writeString(python.resolve("pricing.py"), """
            class Pricing:
                def total(self, quantity: int) -> int:
                    return quantity * 2
            """);
        compileIncrementally(python, java, output, cache, report);
        // the decisions follow the order the processor visits the sources in, which differs between file systems
        assertEquals(Set.of("Orders.place", "Orders.cancel", "Pricing.total"), Set.copyOf(reportedNames(report)));
        assertEquals(3, reportedNames(report).size());

        Files.writeString(orders, """
            class Orders:
                def place(self, quantity: int) -> int:
                    return quantity + 1
            """);
        compileIncrementally(python, java, output, cache, report);

        List<String> lines = Files.readAllLines(report.resolve(StaticCompilationReport.DECISIONS_FILE));
        assertEquals("{\"record\":\"plan\",\"mode\":\"all\",\"coverage\":\"incremental\"}", lines.get(0));
        // the incremental build may plan more than the changed source; it never keeps a removed function
        assertEquals(Set.of("Pricing.total", "Orders.place"), Set.copyOf(reportedNames(report)));
        assertEquals(2, reportedNames(report).size());
    }

    @Test
    void aStackTraceOfACompiledBodyShowsThePythonFileAndLine(@TempDir Path directory) throws Exception {
        Path python = Files.createDirectories(directory.resolve("python/example"));
        Path output = Files.createDirectories(directory.resolve("classes"));
        Files.writeString(python.resolve("checks.py"), LINE_MAPPED_SOURCE);
        PyronautCompiler.builder()
            .pythonSrc(python.getParent().toString())
            .targetDir(output.toFile())
            .staticCompilation(StaticCompilationMode.ALL)
            .build()
            .compile();

        Path classFile;
        Path source;
        try (var paths = Files.walk(output)) {
            List<Path> files = paths.toList();
            classFile = files.stream().filter(path -> path.getFileName().toString().equals("Checks.class")).findFirst().orElseThrow();
            source = files.stream().filter(path -> path.getFileName().toString().equals("Checks.java")).findFirst().orElseThrow();
        }
        // the generated Java says which Python line each statement comes from
        String generated = Files.readString(source);
        assertTrue(generated.contains("// Python line 10"), generated);
        assertFalse(generated.contains("__mnPythonLine"), generated);
        assertPythonLines(Files.readAllBytes(classFile));

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{output.toUri().toURL()});
             ApplicationContext context = ApplicationContext.builder().classLoader(classLoader).build().start()) {
            Class<?> checks = classLoader.loadClass("example.Checks");
            Object bean = context.getBean(checks);
            Method check = checks.getMethod("check", int.class);
            assertEquals(8, check.invoke(bean, 4));
            InvocationTargetException failure = assertThrows(InvocationTargetException.class, () -> check.invoke(bean, 6));
            assertEquals(IllegalArgumentException.class, failure.getCause().getClass());
            StackTraceElement frame = failure.getCause().getStackTrace()[0];
            assertEquals("example.Checks", frame.getClassName(), frame.toString());
            assertEquals("checks.py", frame.getFileName(), frame.toString());
            assertEquals(10, frame.getLineNumber(), frame.toString());
        }

        // the classes of an in-memory compilation are mapped as well
        ClassLoader inMemory = PyronautCompiler.builder()
            .pythonSrc(python.getParent().toString())
            .staticCompilation(StaticCompilationMode.ALL)
            .build()
            .buildClassLoader();
        try (var stream = inMemory.getResourceAsStream("example/Checks.class")) {
            assertNotNull(stream);
            assertPythonLines(stream.readAllBytes());
        }
    }

    /**
     * The class file names the Python file, and the lines of the compiled method are the lines of its
     * Python body, the raise among them.
     */
    private static void assertPythonLines(byte[] bytes) {
        ClassModel model = ClassFile.of().parse(bytes);
        assertEquals("checks.py", model.findAttribute(Attributes.sourceFile()).orElseThrow().sourceFile().stringValue());
        MethodModel check = model.methods().stream()
            .filter(method -> method.methodName().equalsString("check") && method.methodTypeSymbol().parameterCount() == 1)
            .findFirst().orElseThrow();
        Set<Integer> lines = check.code().orElseThrow().elementStream()
            .filter(LineNumber.class::isInstance)
            .map(element -> ((LineNumber) element).line())
            .collect(Collectors.toSet());
        assertTrue(lines.contains(10), lines.toString());
        assertTrue(lines.stream().allMatch(line -> line >= 7 && line <= 11), lines.toString());
    }

    private static void compileIncrementally(Path python, Path java, Path output, Path cache, Path report) {
        PyronautCompiler.builder()
            .pythonSrc(python.toString())
            .javaSrc(java.toString())
            .targetDir(output.toFile())
            .incremental(true)
            .incrementalCacheDirectory(cache.toFile())
            .staticCompilation(StaticCompilationMode.ALL)
            .staticCompilationReport(report.toFile())
            .build()
            .compile();
    }

    private static List<String> reportedNames(Path report) {
        return StaticCompilationReport.readState(report.resolve(StaticCompilationReport.STATE_FILE)).stream()
            .map(StaticCompilationDecision::qualifiedName)
            .filter(name -> !name.endsWith(".__init__"))
            .toList();
    }
}
