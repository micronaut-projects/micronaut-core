package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.env.Environment;
import io.micronaut.context.reload.ResourceKind;
import io.micronaut.context.watch.ResourceChange;
import io.micronaut.dev.compile.SourceKind;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevRuntimeTest {

    @TempDir
    Path project;

    @Test
    void theLauncherRestartsOnAClassChangeKeepsTheRetainedBeanAndSurvivesABrokenEdit() throws Exception {
        Path src = Files.createDirectories(project.resolve("src/main/java/app"));
        Path staticRoot = Files.createDirectories(project.resolve("src/main/resources/static"));
        Files.writeString(staticRoot.resolve("app.css"), "body {}");
        Path config = project.resolve("src/main/resources/application.properties");
        Files.writeString(config, "app.label=alpha\n");
        Files.writeString(src.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args)
                        .properties(java.util.Map.of("spec.name", "DevRuntimeTest", "greeting.suffix", args.length > 0 ? args[0] : "none", "micronaut.server.port", "-1"))
                        .mainClass(Application.class)
                        .start();
                }
            }
            """);
        Path greeter = src.resolve("Greeter.java");
        Files.writeString(greeter, greeter("one"));
        Path manifestFile = project.resolve("dev.properties");
        List<String> classpath = List.of(System.getProperty("java.class.path").split(File.pathSeparator));
        Files.write(project.resolve("cp.argfile"), classpath);
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.strategy=restart
            micronaut.dev.reloadable=build/classes
            micronaut.dev.resources.config=src/main/resources
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.processor-path=@cp.argfile
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.resources.static=src/main/resources/static
            micronaut.dev.compile.java.output=build/classes
            micronaut.dev.retain=io.micronaut.dev.RetainedPool
            """);
        RetainedPool.CREATED.set(0);
        RetainedPool.DESTROYED.set(0);

        ClassLoader callerLoader = Thread.currentThread().getContextClassLoader();
        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[] {"!"});
        try {
            // the first generation: compiled from a clean checkout, running behind the reloadable loader in the dev environment
            ApplicationContext first = runtime.context().orElseThrow();
            assertTrue(first.isRunning());
            assertEquals(1, runtime.generation());
            assertTrue(first.getEnvironment().getActiveNames().contains(Environment.DEVELOPMENT));
            assertSame(runtime.classLoader().current(), first.getClassLoader());
            assertEquals("one", greet(first));
            RetainedPool pool = first.getBean(RetainedPool.class);
            assertEquals(1, RetainedPool.CREATED.get());
            assertSame(runtime, first.getBean(DevRuntime.class));
            assertSame(callerLoader, Thread.currentThread().getContextClassLoader());
            assertEquals("!", first.getEnvironment().getProperty("greeting.suffix", String.class).orElse(null));
            assertEquals("alpha", first.getEnvironment().getProperty("app.label", String.class).orElse(null));

            // a resource watch of the running context starts from the static files the launcher reported
            List<ResourceChange> css = new CopyOnWriteArrayList<>();
            ((DefaultBeanContext) first).resources(ResourceKind.STATIC).include("**/*.css").watch(css::add);
            assertEquals(1, css.size());
            assertTrue(css.get(0).initial());
            assertEquals(List.of(staticRoot.resolve("app.css").toAbsolutePath()), css.get(0).changed());

            // an edit: the class changes, the application restarts on generation two, the pool survives
            Files.writeString(greeter, greeter("two"));
            runtime.reload();
            ApplicationContext second = runtime.awaitGeneration(2, Duration.ofMinutes(2));
            assertNotSame(first, second);
            assertFalse(first.isRunning());
            assertTrue(second.isRunning());
            assertEquals("two", greet(second));
            assertEquals("!", second.getEnvironment().getProperty("greeting.suffix", String.class).orElse(null));
            assertSame(pool, second.getBean(RetainedPool.class));
            assertEquals(1, RetainedPool.CREATED.get());
            assertEquals(0, RetainedPool.DESTROYED.get());
            assertEquals(1, runtime.retainedCount());
            assertTrue(runtime.lastFailure().isEmpty());

            // a broken edit: the compilation fails, generation two keeps running, the failure is reported
            Files.writeString(greeter, "package app; @jakarta.inject.Singleton public class Greeter { public String greet() { return 1; } }");
            runtime.reload();
            assertTrue(runtime.lastFailure().isPresent());
            assertEquals(2, runtime.generation());
            assertTrue(second.isRunning());
            assertTrue(runtime.lastFailure().get().describe().contains("incompatible types"));

            // the fix: generation three, the failure cleared
            Files.writeString(greeter, greeter("three"));
            runtime.reload();
            ApplicationContext third = runtime.awaitGeneration(3, Duration.ofMinutes(2));
            assertTrue(runtime.lastFailure().isEmpty());
            assertEquals("three", greet(third));
            assertSame(pool, third.getBean(RetainedPool.class));

            // an edit that changes no class does not restart
            Files.writeString(greeter, greeter("three") + "\n// a comment\n");
            runtime.reload();
            assertEquals(3, runtime.generation());
            assertTrue(third.isRunning());

            // an edited configuration file: the watcher notices it and the running context refreshes in place,
            // no restart, the generation and the pool untouched
            Files.writeString(config, "app.label=beta\n");
            long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
            while (!"beta".equals(third.getEnvironment().getProperty("app.label", String.class).orElse(null)) && System.nanoTime() < deadline) {
                Thread.sleep(200);
            }
            assertEquals("beta", third.getEnvironment().getProperty("app.label", String.class).orElse(null));
            assertTrue(third.isRunning());
            assertEquals(3, runtime.generation());
            assertSame(pool, third.getBean(RetainedPool.class));

            // a forced restart without a change, retaining the pool again; the configuration file, read live
            // from the resource root ahead of the build output, is what the new generation sees too
            Files.writeString(config, "app.label=gamma\n");
            Files.writeString(staticRoot.resolve("app.css"), "body { color: red }");
            assertEquals("body { color: red }", new String(third.getClassLoader().getResourceAsStream("app.css").readAllBytes()));
            runtime.restart();
            ApplicationContext fourth = runtime.awaitGeneration(4, Duration.ofMinutes(2));
            assertFalse(third.isRunning());
            assertSame(pool, fourth.getBean(RetainedPool.class));
            assertEquals("gamma", fourth.getEnvironment().getProperty("app.label", String.class).orElse(null));

            // a bean that injects the property directly appears: a class change restarts, and from then on an edit
            // of that property restarts too, since no refresh reaches a @Value field, with the pool retained
            Files.writeString(src.resolve("Labelled.java"), "package app; @jakarta.inject.Singleton public class Labelled { @io.micronaut.context.annotation.Value(\"${app.label}\") public String label; }");
            runtime.reload();
            ApplicationContext fifth = runtime.awaitGeneration(5, Duration.ofMinutes(2));
            Class<?> labelled = fifth.getClassLoader().loadClass("app.Labelled");
            assertEquals("gamma", labelled.getField("label").get(fifth.getBean(labelled)));
            Files.writeString(config, "app.label=delta\n");
            ApplicationContext sixth = runtime.awaitGeneration(6, Duration.ofMinutes(2));
            assertFalse(fifth.isRunning());
            assertSame(pool, sixth.getBean(RetainedPool.class));
            labelled = sixth.getClassLoader().loadClass("app.Labelled");
            assertEquals("delta", labelled.getField("label").get(sixth.getBean(labelled)));
        } finally {
            runtime.close();
        }
        assertTrue(runtime.context().map(context -> !context.isRunning()).orElse(true));
        assertEquals(1, RetainedPool.DESTROYED.get());
    }

    @Test
    void aBodyOnlyEditIsAppliedInPlaceWhenTheManifestAllowsItAndAnAgentIsThere() throws Exception {
        Path src = Files.createDirectories(project.resolve("src/main/java/app"));
        Files.writeString(src.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args).properties(java.util.Map.of("spec.name", "DevRuntimeTest", "micronaut.server.port", "-1")).mainClass(Application.class).start();
                }
            }
            """);
        Path greeter = src.resolve("Greeter.java");
        Files.writeString(greeter, greeter("one"));
        Path manifestFile = project.resolve("dev.properties");
        Files.write(project.resolve("cp.argfile"), List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.strategy=auto
            micronaut.dev.reloadable=build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.processor-path=@cp.argfile
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.java.output=build/classes
            """);

        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[0]);
        try {
            ApplicationContext first = runtime.context().orElseThrow();
            Class<?> type = first.getClassLoader().loadClass("app.Greeter");
            Object bean = first.getBean(type);
            assertEquals("one", type.getMethod("greet").invoke(bean));
            assertEquals(io.micronaut.context.reload.ReloadStrategy.AUTO, runtime.strategy(), "byte-buddy-agent attaches to the test JVM");

            // a body-only edit: the same generation, the same context, the same bean, a new body
            Files.writeString(greeter, greeter("two"));
            runtime.reload();
            assertEquals(1, runtime.redefinitions());
            assertEquals(1, runtime.generation());
            assertSame(first, runtime.context().orElseThrow());
            assertEquals("two", type.getMethod("greet").invoke(bean));

            // a structural edit still restarts
            Files.writeString(greeter, "package app; @jakarta.inject.Singleton public class Greeter { public String greet() { return \"three\"; } public int extra() { return 3; } }");
            runtime.reload();
            ApplicationContext second = runtime.awaitGeneration(2, Duration.ofMinutes(2));
            assertNotSame(first, second);
            assertEquals("three", greet(second));
            assertEquals(1, runtime.redefinitions());
        } finally {
            runtime.close();
        }
    }

    @Test
    void aViewDeletedFromItsRootIsGoneRatherThanServedFromTheBuildCopyAndTheWatchIsToldEachFile() throws Exception {
        Path src = Files.createDirectories(project.resolve("src/main/java/app"));
        Files.writeString(src.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args).properties(java.util.Map.of("spec.name", "DevRuntimeTest", "micronaut.server.port", "-1")).mainClass(Application.class).start();
                }
            }
            """);
        Path views = Files.createDirectories(project.resolve("src/main/resources/views"));
        Path index = views.resolve("index.html");
        Files.writeString(index, "<p>live</p>");
        Path partials = Files.createDirectories(views.resolve("partials"));
        Files.writeString(partials.resolve("a.html"), "a");
        Files.writeString(partials.resolve("b.html"), "b");
        // the build copied the resource root into its output, and a processor generated a resource no root holds
        Path output = Files.createDirectories(project.resolve("build/resources/main"));
        Files.createDirectories(output.resolve("views/partials"));
        Files.writeString(output.resolve("views/index.html"), "<p>stale</p>");
        Files.writeString(output.resolve("views/partials/a.html"), "stale a");
        Files.writeString(output.resolve("views/partials/b.html"), "stale b");
        Files.writeString(output.resolve("views/generated.html"), "generated");
        Path manifestFile = project.resolve("dev.properties");
        Files.write(project.resolve("cp.argfile"), List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.strategy=restart
            micronaut.dev.reloadable=build/classes,build/resources/main
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.processor-path=@cp.argfile
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.resources.config=src/main/resources
            micronaut.dev.resources.views=src/main/resources/views
            micronaut.dev.compile.java.output=build/classes
            """);

        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[0]);
        try {
            ApplicationContext first = runtime.context().orElseThrow();
            ClassLoader loader = first.getClassLoader();
            assertEquals("<p>live</p>", read(loader, "views/index.html"));
            List<ResourceChange> changes = new CopyOnWriteArrayList<>();
            ((DefaultBeanContext) first).resources(ResourceKind.VIEWS).include("**/*.html").watch(changes::add);
            changes.clear();

            // the template is deleted: the watch is told, and the build's stale copy is not served in its place
            Files.delete(index);
            runtime.changed(List.of(), List.of(index));
            assertTrue(removedIn(changes).contains(index.toAbsolutePath()), changes.toString());
            assertNull(loader.getResource("views/index.html"));
            assertFalse(loader.getResources("views/index.html").hasMoreElements());
            // a resource only the build output holds is still served
            assertEquals("generated", read(loader, "views/generated.html"));

            // a directory deleted as a whole: the watch is told of each file it held
            Files.delete(partials.resolve("a.html"));
            Files.delete(partials.resolve("b.html"));
            Files.delete(partials);
            changes.clear();
            runtime.changed(List.of(), List.of(partials));
            List<Path> removed = removedIn(changes);
            assertTrue(removed.contains(partials.resolve("a.html").toAbsolutePath()), removed.toString());
            assertTrue(removed.contains(partials.resolve("b.html").toAbsolutePath()), removed.toString());
            assertNull(loader.getResource("views/partials/a.html"));

            // a template created, read, and deleted after startup is gone too, and a new generation keeps it gone
            Path late = views.resolve("late.html");
            Files.writeString(late, "late");
            Files.writeString(output.resolve("views/late.html"), "stale late");
            assertEquals("late", read(loader, "views/late.html"));
            Files.delete(late);
            assertNull(loader.getResource("views/late.html"));
            runtime.restart();
            ApplicationContext second = runtime.awaitGeneration(2, Duration.ofMinutes(2));
            assertNull(second.getClassLoader().getResource("views/index.html"));
            assertNull(second.getClassLoader().getResource("views/late.html"));
            assertEquals("generated", read(second.getClassLoader(), "views/generated.html"));

            // written again, it is served live
            Files.writeString(index, "<p>back</p>");
            runtime.changed(List.of(index), List.of());
            assertEquals("<p>back</p>", read(second.getClassLoader(), "views/index.html"));
        } finally {
            runtime.close();
        }
    }

    private static List<Path> removedIn(List<ResourceChange> changes) {
        return changes.stream().flatMap(change -> change.removed().stream()).toList();
    }

    private static String read(ClassLoader loader, String name) throws IOException {
        try (var in = loader.getResourceAsStream(name)) {
            assertNotNull(in, name);
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @Test
    void theSourcesOfAFailedCompilationCompileAgainWithTheNextEdit() throws Exception {
        Path src = Files.createDirectories(project.resolve("src/main/java/app"));
        Files.writeString(src.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args)
                        .properties(java.util.Map.of("spec.name", "DevRuntimeTest", "micronaut.server.port", "-1"))
                        .mainClass(Application.class)
                        .start();
                }
            }
            """);
        Path greeter = src.resolve("Greeter.java");
        Files.writeString(greeter, greeter("one"));
        Path other = src.resolve("Other.java");
        Files.writeString(other, other("one"));
        Path manifestFile = project.resolve("dev.properties");
        List<String> classpath = List.of(System.getProperty("java.class.path").split(File.pathSeparator));
        Files.write(project.resolve("cp.argfile"), classpath);
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.reloadable=build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.processor-path=@cp.argfile
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.java.output=build/classes
            """);

        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[0]);
        try {
            assertEquals(1, runtime.generation());

            // a broken edit of one file fails
            Files.writeString(greeter, "package app; @jakarta.inject.Singleton public class Greeter { public String greet() { return 1; } }");
            runtime.sourcesChanged(SourceKind.JAVA, Set.of(greeter), Set.of());
            assertTrue(runtime.lastFailure().isPresent());

            // a restart compiles nothing: it runs the output that compiled last
            runtime.restart();
            ApplicationContext restarted = runtime.awaitGeneration(2, Duration.ofMinutes(2));
            assertEquals("one", greet(restarted));
            assertTrue(runtime.lastFailure().isPresent());

            // a valid edit of an unrelated file compiles the broken one with it: the failure stays, nothing reloads
            Files.writeString(other, other("two"));
            runtime.sourcesChanged(SourceKind.JAVA, Set.of(other), Set.of());
            assertTrue(runtime.lastFailure().isPresent());
            assertTrue(runtime.lastFailure().get().describe().contains("incompatible types"));
            assertEquals(2, runtime.generation());

            // the fix applies both edits, by a restart or, body only, in place
            Files.writeString(greeter, greeter("three"));
            runtime.sourcesChanged(SourceKind.JAVA, Set.of(greeter), Set.of());
            ApplicationContext fixed = runtime.context().orElseThrow();
            assertTrue(runtime.lastFailure().isEmpty());
            assertEquals("three", greet(fixed));
            Class<?> otherType = fixed.getClassLoader().loadClass("app.Other");
            assertEquals("two", otherType.getMethod("value").invoke(null));
        } finally {
            runtime.close();
        }
    }

    private static String other(String value) {
        return "package app; public class Other { public static String value() { return \"" + value + "\"; } }";
    }

    private static String greeter(String greeting) {
        return "package app; @jakarta.inject.Singleton public class Greeter { public String greet() { return \"" + greeting + "\"; } }";
    }

    private static String greet(ApplicationContext context) throws Exception {
        Class<?> type = context.getClassLoader().loadClass("app.Greeter");
        Object bean = context.getBean(type);
        return (String) type.getMethod("greet").invoke(bean);
    }
}
