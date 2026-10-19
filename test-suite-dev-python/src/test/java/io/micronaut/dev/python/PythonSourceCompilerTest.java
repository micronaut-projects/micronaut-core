package io.micronaut.dev.python;

import io.micronaut.dev.DevRuntime;
import io.micronaut.dev.compile.CompilationRequest;
import io.micronaut.dev.compile.CompilationResult;
import io.micronaut.dev.compile.PythonSourceCompiler;
import io.micronaut.dev.compile.SourceKind;
import io.micronaut.dev.compile.SourceRoot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonSourceCompilerTest {

    @TempDir
    Path project;

    private final PythonSourceCompiler compiler = new PythonSourceCompiler();

    @AfterEach
    void close() {
        compiler.close();
    }

    @Test
    void theCompilerIsAvailableAndRegisteredForPythonWithJavaCompiledJointly() {
        assertTrue(compiler.isAvailable());
        assertEquals(Set.of(SourceKind.PYTHON), compiler.kinds());
        assertEquals(Set.of(SourceKind.JAVA), compiler.jointKinds());
        assertInstanceOf(PythonSourceCompiler.class, DevRuntime.availableCompilers().get(SourceKind.PYTHON));
    }

    @Test
    void aModuleIsCompiledWithItsJavaSourcesAndItsEditsReplaceTheOutputsTheyChange() throws Exception {
        PythonFixture fixture = PythonFixture.create(project);
        Path out = fixture.classOutput();

        CompilationResult full = compiler.compile(fixture.request(Set.of(), Set.of()).asFull());

        assertEquals(CompilationResult.Status.SUCCESS, full.status(), full.diagnostics().toString());
        assertTrue(Files.exists(out.resolve("base/Greeting.class")));
        assertTrue(Files.exists(out.resolve("app/PythonGreeter.class")));
        assertTrue(Files.exists(out.resolve("app/$PythonGreeter$Definition.class")));
        assertTrue(Files.exists(out.resolve("pyronaut_application/PyronautMain.class")));
        // the generated stubs are sources: they go to the generated sources directory, not the runtime classes
        assertTrue(Files.exists(project.resolve("build/generated/app/PythonGreeter.java")));
        assertFalse(Files.exists(out.resolve("app/PythonGreeter.java")));
        Path greeterSource = vfsFile(out, "app/greeter.py").orElseThrow();
        assertTrue(Files.readString(greeterSource).contains("\"one\""));
        assertEquals("Hello one", greet(out));

        // a Python edit: the module resource is replaced
        fixture.writeGreeter("two");
        CompilationResult python = compiler.compile(fixture.request(Set.of(fixture.greeter()), Set.of()));
        assertTrue(python.isSuccess(), python.diagnostics().toString());
        assertTrue(Files.readString(greeterSource).contains("\"two\""));

        // a Java edit compiled with the module: the class changes and is reported as changed
        fixture.writeGreeting("Hi ");
        CompilationResult java = compiler.compile(fixture.request(Set.of(fixture.greeting()), Set.of()));
        assertEquals(CompilationResult.Status.SUCCESS, java.status(), java.diagnostics().toString());
        assertTrue(java.compiledClasses().contains("base.Greeting"), java.compiledClasses().toString());
        assertEquals("Hi two", greet(out));

        // nothing changed: nothing to do, the output untouched
        CompilationResult none = compiler.compile(fixture.request(Set.of(), Set.of()));
        assertEquals(CompilationResult.Status.NOTHING_TO_DO, none.status(), none.diagnostics().toString());
    }

    @Test
    void aBrokenEditFailsAndLeavesTheOutputAsItWasUntilTheFix() throws Exception {
        PythonFixture fixture = PythonFixture.create(project);
        Path out = fixture.classOutput();
        assertTrue(compiler.compile(fixture.request(Set.of(), Set.of()).asFull()).isSuccess());
        byte[] definition = Files.readAllBytes(out.resolve("app/$PythonGreeter$Definition.class"));
        byte[] module = Files.readAllBytes(vfsFile(out, "app/greeter.py").orElseThrow());

        Files.writeString(fixture.greeting(), "package base; public abstract class Greeting { public String greet() { return 1; } }");
        CompilationResult broken = compiler.compile(fixture.request(Set.of(fixture.greeting()), Set.of()));

        assertEquals(CompilationResult.Status.FAILED, broken.status());
        assertFalse(broken.diagnostics().isEmpty());
        assertArrayEquals(definition, Files.readAllBytes(out.resolve("app/$PythonGreeter$Definition.class")));
        assertArrayEquals(module, Files.readAllBytes(vfsFile(out, "app/greeter.py").orElseThrow()));
        assertEquals("Hello one", greet(out));

        fixture.writeGreeting("Hey ");
        CompilationResult fixed = compiler.compile(fixture.request(Set.of(fixture.greeting()), Set.of()));
        assertTrue(fixed.isSuccess(), fixed.diagnostics().toString());
        assertEquals("Hey one", greet(out));
    }

    @Test
    void aDeletedModuleTakesItsOutputsWithIt() throws Exception {
        PythonFixture fixture = PythonFixture.create(project);
        Path out = fixture.classOutput();
        Path other = fixture.writePython("app/farewell.py", """
            from jakarta.inject import Singleton


            @Singleton
            class Farewell:
                def bye(self) -> str:
                    return "bye"
            """);
        assertTrue(compiler.compile(fixture.request(Set.of(), Set.of()).asFull()).isSuccess());
        assertTrue(Files.exists(out.resolve("app/Farewell.class")));
        assertTrue(vfsFile(out, "app/farewell.py").isPresent());

        Files.delete(other);
        CompilationResult result = compiler.compile(fixture.request(Set.of(), Set.of(other)));

        assertEquals(CompilationResult.Status.SUCCESS, result.status(), result.diagnostics().toString());
        assertFalse(Files.exists(out.resolve("app/Farewell.class")));
        assertFalse(Files.exists(out.resolve("app/$Farewell$Definition.class")));
        assertFalse(Files.exists(project.resolve("build/generated/app/Farewell.java")));
        assertTrue(vfsFile(out, "app/farewell.py").isEmpty());
        assertTrue(result.removedOutputs().contains(out.resolve("app/Farewell.class")));
        assertTrue(Files.exists(out.resolve("app/PythonGreeter.class")));
    }

    @Test
    void aModuleDeletedAfterABuildByPyronautTakesItsOutputsWithItOnTheFirstCompilation() throws Exception {
        PythonFixture fixture = PythonFixture.create(project);
        Path out = fixture.classOutput();
        Path farewell = fixture.writePython("app/farewell.py", """
            from jakarta.inject import Singleton


            @Singleton
            class Farewell:
                def bye(self) -> str:
                    return "bye"
            """);
        // the output pyronaut process builds, before development mode starts
        Files.createDirectories(out);
        io.micronaut.python.compiler.PyronautCompiler.builder()
            .pythonSrc(fixture.python().toString())
            .javaSrc(fixture.java().toString())
            .targetDir(out.toFile())
            .classpath(PythonFixture.testClasspath().stream().map(Path::toFile).toList())
            .build()
            .compile();
        Files.writeString(out.resolve("application.properties"), "a=b\n");
        assertTrue(Files.exists(out.resolve("app/Farewell.class")));

        Files.delete(farewell);
        CompilationResult result = compiler.compile(fixture.request(Set.of(), Set.of(farewell)));

        assertTrue(result.isSuccess(), result.diagnostics().toString());
        assertFalse(Files.exists(out.resolve("app/Farewell.class")));
        assertFalse(Files.exists(out.resolve("app/$Farewell$Definition.class")));
        assertTrue(vfsFile(out, "app/farewell.py").isEmpty());
        try (Stream<Path> index = Files.walk(out.resolve("META-INF/micronaut"))) {
            assertTrue(index.noneMatch(entry -> entry.getFileName().toString().contains("Farewell")));
        }
        assertTrue(Files.exists(out.resolve("app/PythonGreeter.class")));
        assertTrue(Files.exists(out.resolve("base/Greeting.class")));
        assertEquals("a=b\n", Files.readString(out.resolve("application.properties")));
        assertEquals("Hello one", greet(out));
    }

    @Test
    void aFileAnotherToolWroteIntoTheOutputIsKept() throws Exception {
        PythonFixture fixture = PythonFixture.create(project);
        Path out = fixture.classOutput();
        Path foreign = out.resolve("application.properties");
        Files.createDirectories(out);
        Files.writeString(foreign, "a=b\n");

        assertTrue(compiler.compile(fixture.request(Set.of(), Set.of()).asFull()).isSuccess());
        assertTrue(compiler.compile(fixture.request(Set.of(), Set.of()).asFull()).isSuccess());

        assertEquals("a=b\n", Files.readString(foreign));
    }

    @Test
    void aClassAnotherCompilerWroteIntoASharedOutputIsResolvedAndKept() throws Exception {
        PythonFixture fixture = PythonFixture.create(project);
        Path out = fixture.classOutput();
        // the Java base class is compiled by another tool into the shared output, and is not a source of the request
        Files.createDirectories(out);
        javax.tools.JavaCompiler javac = javax.tools.ToolProvider.getSystemJavaCompiler();
        assertEquals(0, javac.run(null, null, null, "-d", out.toString(), fixture.greeting().toString()));
        CompilationRequest pythonOnly = new CompilationRequest(SourceKind.PYTHON, List.of(new SourceRoot(SourceKind.PYTHON, fixture.python())), Set.of(), Set.of(), true,
            PythonFixture.testClasspath(), List.of(), out, project.resolve("build/generated"), List.of());
        byte[] greeting = Files.readAllBytes(out.resolve("base/Greeting.class"));

        CompilationResult result = compiler.compile(pythonOnly);

        assertEquals(CompilationResult.Status.SUCCESS, result.status(), result.diagnostics().toString());
        assertTrue(Files.exists(out.resolve("app/PythonGreeter.class")));
        assertArrayEquals(greeting, Files.readAllBytes(out.resolve("base/Greeting.class")));
        assertEquals("Hello one", greet(out));
    }

    @Test
    void moreThanOneJavaRootIsReported() throws Exception {
        PythonFixture fixture = PythonFixture.create(project);
        Path second = Files.createDirectories(project.resolve("src/other/java"));
        CompilationRequest request = fixture.request(Set.of(), Set.of());
        List<SourceRoot> roots = new java.util.ArrayList<>(request.sourceRoots());
        roots.add(new SourceRoot(SourceKind.JAVA, second));
        CompilationRequest twoRoots = new CompilationRequest(SourceKind.PYTHON, roots, Set.of(), Set.of(), true, request.compileClasspath(),
            request.processorPath(), request.classOutput(), request.generatedSources(), request.options());

        CompilationResult result = compiler.compile(twoRoots);

        assertEquals(CompilationResult.Status.FAILED, result.status());
        assertTrue(result.diagnostics().getFirst().message().contains("at most one Java source root"));
    }

    static Optional<Path> vfsFile(Path out, String module) throws Exception {
        Path vfs = out.resolve("META-INF/GRAALPY-VFS");
        if (!Files.isDirectory(vfs)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.walk(vfs)) {
            return files.filter(file -> file.toString().replace(File.separatorChar, '/').endsWith("/src/" + module)).findFirst();
        }
    }

    /**
     * Calls the Java base method of the Python bean's stub, which runs the Python override, the way
     * the application would: in a context started from the output.
     */
    private static String greet(Path out) throws Exception {
        try (URLClassLoader loader = new URLClassLoader(new URL[] {out.toUri().toURL()}, PythonSourceCompilerTest.class.getClassLoader());
             io.micronaut.context.ApplicationContext context = io.micronaut.context.ApplicationContext.builder()
                 .classLoader(loader)
                 .properties(java.util.Map.of("micronaut.python.pool.enabled", "false"))
                 .start()) {
            Class<?> type = loader.loadClass("app.PythonGreeter");
            Object bean = context.getBean(type);
            return (String) type.getMethod("greet").invoke(bean);
        }
    }
}
