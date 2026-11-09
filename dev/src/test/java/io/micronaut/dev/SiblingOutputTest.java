package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.compile.CompilationRequest;
import io.micronaut.dev.compile.JavacSourceCompiler;
import io.micronaut.dev.compile.SiblingOutputs;
import io.micronaut.dev.compile.SourceKind;
import io.micronaut.dev.compile.SourceRoot;
import io.micronaut.dev.manifest.DevManifest;
import io.micronaut.dev.test.TestRunSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An application and a module it depends on, built by the build tool into outputs of their own, under a running launcher.
 */
class SiblingOutputTest {

    @TempDir
    Path project;

    private Path libSrc;
    private Path appBuild;
    private Path libBuild;
    private Path greeter;
    private Set<String> applicationBuild;
    private Set<String> libraryBuild;

    @BeforeEach
    void build() throws Exception {
        libSrc = Files.createDirectories(project.resolve("lib/src/lib"));
        Path appSrc = Files.createDirectories(project.resolve("app/src/app"));
        appBuild = project.resolve("app/build/classes");
        libBuild = project.resolve("lib/build/classes");
        greeter = libSrc.resolve("Greeter.java");
        Files.writeString(greeter, greeter("one"));
        Files.writeString(appSrc.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args).properties(java.util.Map.of("spec.name", "SiblingOutputTest", "micronaut.server.port", "-1")).mainClass(Application.class).start();
                }
            }
            """);
        Files.writeString(appSrc.resolve("Caller.java"), """
            package app;
            @jakarta.inject.Singleton
            public class Caller {
                private final lib.Greeter greeter;
                public Caller(lib.Greeter greeter) {
                    this.greeter = greeter;
                }
                public String call() {
                    return greeter.greet();
                }
            }
            """);
        // as the build tool does before it launches: each module into its own output
        compile(project.resolve("lib/src"), libBuild, List.of());
        compile(project.resolve("app/src"), appBuild, List.of(libBuild));
        applicationBuild = files(appBuild);
        libraryBuild = files(libBuild);
        Files.write(project.resolve("cp.argfile"), classpath(libBuild).stream().map(Path::toString).toList());
    }

    @Test
    void aRestartLoadsASiblingsCompiledClassesFromTheSiblingOutputAndTheApplicationsOutputHoldsNothingOfThem() throws Exception {
        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifest("restart")), new String[0]);
        try {
            ApplicationContext first = runtime.context().orElseThrow();
            assertEquals("one", call(first));

            // a change of the sibling's class: the generation loads it ahead of the sibling's own build output
            Files.writeString(greeter, greeter("two"));
            runtime.reload();
            ApplicationContext second = runtime.awaitGeneration(2, Duration.ofMinutes(2));
            assertEquals("two", call(second));
            assertEquals(libraryBuild, files(libBuild));
            assertEquals(applicationBuild, files(appBuild));
            assertTrue(files(siblings()).contains("lib/Greeter.class"));

            // a class added to the sibling, a bean the generation finds through the index in the sibling output
            Path added = libSrc.resolve("Added.java");
            Files.writeString(added, "package lib; @jakarta.inject.Singleton public class Added { }");
            runtime.reload();
            ApplicationContext third = runtime.awaitGeneration(3, Duration.ofMinutes(2));
            assertTrue(third.containsBean(third.getClassLoader().loadClass("lib.Added")));
            assertEquals(applicationBuild, files(appBuild));
            assertTrue(files(siblings()).stream().anyMatch(file -> file.startsWith("META-INF/micronaut/") && file.contains("lib.$Added$Definition")));

            // and deleted: the bean goes with its files in the sibling output
            Files.delete(added);
            runtime.changed(List.of(), List.of(added));
            runtime.awaitGeneration(4, Duration.ofMinutes(2));
            assertFalse(files(siblings()).stream().anyMatch(file -> file.contains("Added")));
            assertEquals(applicationBuild, files(appBuild));
        } finally {
            runtime.close();
        }
    }

    @Test
    void aBodyEditInASiblingHotSwaps() throws Exception {
        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifest("auto")), new String[0]);
        try {
            ApplicationContext first = runtime.context().orElseThrow();
            Class<?> type = first.getClassLoader().loadClass("app.Caller");
            Object caller = first.getBean(type);
            assertEquals("one", type.getMethod("call").invoke(caller));

            Files.writeString(greeter, greeter("two"));
            runtime.reload();

            assertEquals(1, runtime.redefinitions());
            assertEquals(1, runtime.generation());
            assertSame(first, runtime.context().orElseThrow());
            assertEquals("two", type.getMethod("call").invoke(caller));
            assertEquals(applicationBuild, files(appBuild));
            assertEquals(libraryBuild, files(libBuild));

            // and again, from the sibling output this time
            Files.writeString(greeter, greeter("three"));
            runtime.reload();
            assertEquals(2, runtime.redefinitions());
            assertEquals("three", type.getMethod("call").invoke(caller));

            // a new generation sees the last body
            Files.writeString(greeter, "package lib; @jakarta.inject.Singleton public class Greeter { public String greet() { return \"four\"; } public int extra() { return 4; } }");
            runtime.reload();
            ApplicationContext second = runtime.awaitGeneration(2, Duration.ofMinutes(2));
            assertNotSame(first, second);
            assertEquals("four", call(second));
        } finally {
            runtime.close();
        }
    }

    @Test
    void theRelaunchedProcessStartsFromWhatTheProcessBeforeItCompiledInTheSiblingOutput() throws Exception {
        Path manifest = manifest("restart");
        Files.writeString(manifest, Files.readString(manifest) + "micronaut.dev.max-generations=2\n");
        CompletableFuture<DevRuntime> relaunched = new CompletableFuture<>();
        MicronautDevMain launcher = new MicronautDevMain() {
            @Override
            protected void relaunch(DevRuntime runtime) {
                relaunched.complete(runtime);
            }
        };
        DevManifest loaded = DevManifest.load(manifest);
        DevRuntime runtime = launcher.launch(loaded, new String[0]);
        try {
            Files.writeString(greeter, greeter("two"));
            runtime.reload();
            runtime.awaitGeneration(2, Duration.ofMinutes(2));
            Files.writeString(greeter, greeter("three"));
            runtime.reload();
            relaunched.get(2, TimeUnit.MINUTES);
        } finally {
            runtime.close();
            // the process ends: its share of the directory goes with it, as it would for a relaunch in a new JVM
            SiblingOutputs.release(loaded.siblingOutput());
        }
        assertTrue(Files.exists(project.resolve("app/build/micronaut-dev/siblings.relaunch")));

        DevManifest relaunchedManifest = DevManifest.load(manifest);
        DevRuntime next = new MicronautDevMain().launch(relaunchedManifest, new String[0]);
        try {
            assertEquals("three", call(next.context().orElseThrow()));
            assertFalse(Files.exists(project.resolve("app/build/micronaut-dev/siblings.relaunch")));
        } finally {
            next.close();
            SiblingOutputs.release(relaunchedManifest.siblingOutput());
        }

        // a launch that is no relaunch starts a session of its own, from what the build tool compiled
        DevManifest newSession = DevManifest.load(manifest);
        DevRuntime fresh = new MicronautDevMain().launch(newSession, new String[0]);
        try {
            assertEquals("one", call(fresh.context().orElseThrow()));
        } finally {
            fresh.close();
            SiblingOutputs.release(newSession.siblingOutput());
        }
    }

    @Test
    void aChangeInASiblingRunsTheTestsThatDependOnItAndLeavesTheApplicationsOutputAlone() throws Exception {
        Path tests = Files.createDirectories(project.resolve("app/test/app"));
        Files.writeString(tests.resolve("CallerTest.java"), callerTest("one"));
        Files.writeString(tests.resolve("OtherTest.java"), """
            package app;
            import org.junit.jupiter.api.Test;
            public class OtherTest {
                @Test
                void runs() {
                }
            }
            """);
        Path manifest = manifest("restart");
        Files.writeString(manifest, Files.readString(manifest) + """
            micronaut.dev.mode=test
            micronaut.dev.test.sources.java=app/test
            micronaut.dev.test.compile.java.output=app/build/test-classes
            micronaut.dev.test.reports=app/build/test-results
            micronaut.dev.test.html-report=app/build/test-report
            """);
        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifest), new String[0]);
        try {
            assertEquals(2, runtime.lastTestRun().orElseThrow().passed());

            Files.writeString(greeter, greeter("two"));
            runtime.changed(List.of(greeter), List.of());
            TestRunSummary second = runtime.awaitTestRun(2, Duration.ofMinutes(2));

            // the test that reaches the changed class through the application's class, and sees its new body
            assertEquals(1, second.total());
            assertEquals(1, second.failed());
            assertEquals(Set.of("app.CallerTest"), runtime.failedTestClasses());
            assertTrue(files(siblings()).contains("lib/Greeter.class"));
            assertEquals(applicationBuild, files(appBuild));
        } finally {
            runtime.close();
        }
    }

    private static String callerTest(String expected) {
        return """
            package app;
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertEquals;
            public class CallerTest {
                @Test
                void calls() {
                    assertEquals("%s", new Caller(new lib.Greeter()).call());
                }
            }
            """.formatted(expected);
    }

    private Path siblings() {
        return project.resolve("app/build/micronaut-dev/siblings/classes");
    }

    private Path manifest(String strategy) throws Exception {
        Path manifest = project.resolve("dev.properties");
        Files.writeString(manifest, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.project-dir=app
            micronaut.dev.strategy=%s
            micronaut.dev.reloadable=app/build/classes,lib/build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.processor-path=@cp.argfile
            micronaut.dev.sources.java=app/src,lib/src
            micronaut.dev.compile.java.output=app/build/classes
            micronaut.dev.compile.java.generated-sources=app/build/generated
            micronaut.dev.compile.sibling-sources=lib/src
            micronaut.dev.compile.sibling-output=app/build/micronaut-dev/siblings
            """.formatted(strategy));
        return manifest;
    }

    private void compile(Path sources, Path output, List<Path> dependencies) {
        List<Path> classpath = classpath(dependencies.toArray(new Path[0]));
        CompilationRequest request = new CompilationRequest(SourceKind.JAVA, List.of(new SourceRoot(SourceKind.JAVA, sources)), Set.of(), Set.of(), true, classpath, classpath, output, output.resolveSibling("generated"), List.of());
        assertTrue(new JavacSourceCompiler().compile(request).isSuccess());
    }

    private static List<Path> classpath(Path... first) {
        Stream<Path> launch = Stream.of(System.getProperty("java.class.path").split(File.pathSeparator)).map(Path::of);
        return Stream.concat(Stream.of(first), launch).toList();
    }

    private static String greeter(String greeting) {
        return "package lib; @jakarta.inject.Singleton public class Greeter { public String greet() { return \"" + greeting + "\"; } }";
    }

    private static String call(ApplicationContext context) throws Exception {
        Class<?> type = context.getClassLoader().loadClass("app.Caller");
        return (String) type.getMethod("call").invoke(context.getBean(type));
    }

    private static Set<String> files(Path directory) throws Exception {
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile).map(file -> directory.relativize(file).toString()).collect(Collectors.toCollection(TreeSet::new));
        }
    }
}
