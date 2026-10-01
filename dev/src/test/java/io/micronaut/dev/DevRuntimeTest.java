package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.env.Environment;
import io.micronaut.context.reload.ResourceKind;
import io.micronaut.context.watch.ResourceChange;
import io.micronaut.context.watch.ResourceSelector;
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
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
                        .properties(java.util.Map.of("spec.name", "DevRuntimeTest", "greeting.suffix", args.length > 0 ? args[0] : "none"))
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
            assertEquals("one", greet(runtime, first));
            RetainedPool pool = first.getBean(RetainedPool.class);
            assertEquals(1, RetainedPool.CREATED.get());
            assertSame(runtime, first.getBean(DevRuntime.class));
            assertSame(callerLoader, Thread.currentThread().getContextClassLoader());
            assertEquals("!", first.getEnvironment().getProperty("greeting.suffix", String.class).orElse(null));
            assertEquals("alpha", first.getEnvironment().getProperty("app.label", String.class).orElse(null));

            // a resource watch of the running context starts from the static files the launcher reported
            List<ResourceChange> css = new CopyOnWriteArrayList<>();
            ((DefaultBeanContext) first).watchResources(ResourceSelector.of(ResourceKind.STATIC, "**/*.css"), css::add);
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
            assertEquals("two", greet(runtime, second));
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
            assertEquals("three", greet(runtime, third));
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
                    io.micronaut.runtime.Micronaut.build(args).properties(java.util.Map.of("spec.name", "DevRuntimeTest")).mainClass(Application.class).start();
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
            assertEquals("three", greet(runtime, second));
            assertEquals(1, runtime.redefinitions());
        } finally {
            runtime.close();
        }
    }

    private static String greeter(String greeting) {
        return "package app; @jakarta.inject.Singleton public class Greeter { public String greet() { return \"" + greeting + "\"; } }";
    }

    private static String greet(DevRuntime runtime, ApplicationContext context) throws Exception {
        Class<?> type = context.getClassLoader().loadClass("app.Greeter");
        Object bean = context.getBean(type);
        return (String) type.getMethod("greet").invoke(bean);
    }
}
