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
import java.util.Map;
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
            assertEquals("one", greet(runtime, first));
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
            assertEquals("two", greet(runtime, second));
            assertEquals("!", second.getEnvironment().getProperty("greeting.suffix", String.class).orElse(null));
            assertSame(pool, second.getBean(RetainedPool.class));
            assertEquals(1, RetainedPool.CREATED.get());
            assertEquals(0, RetainedPool.DESTROYED.get());
            // the pool, and the HTTP server's event loop groups
            assertEquals(2, runtime.retainedCount());
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
            assertEquals("three", greet(runtime, second));
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

    @Test
    void anEditThatFlipsARequirementRestartsSoThatTheBeanAppearsOrDisappears() throws Exception {
        Path src = Files.createDirectories(project.resolve("src/main/java/app"));
        Files.writeString(src.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args).properties(java.util.Map.of("spec.name", "DevRuntimeTest", "micronaut.server.port", "-1")).mainClass(Application.class).start();
                }
            }
            """);
        Files.writeString(src.resolve("Toggle.java"), "package app; @jakarta.inject.Singleton @io.micronaut.context.annotation.Requires(missingProperty = \"app.off\") public class Toggle { }");
        Files.writeString(src.resolve("Gated.java"), "package app; @jakarta.inject.Singleton @io.micronaut.context.annotation.Requires(property = \"app.on\") public class Gated { }");
        Path config = project.resolve("src/main/resources/application.properties");
        Files.createDirectories(config.getParent());
        Files.writeString(config, "app.label=alpha\n");
        Path manifestFile = project.resolve("dev.properties");
        Files.write(project.resolve("cp.argfile"), List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.strategy=restart
            micronaut.dev.reloadable=build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.processor-path=@cp.argfile
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.resources.config=src/main/resources
            micronaut.dev.compile.java.output=build/classes
            """);

        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[0]);
        try {
            ApplicationContext first = runtime.context().orElseThrow();
            assertTrue(first.containsBean(first.getClassLoader().loadClass("app.Toggle")));
            // asked for while running, the disabled reference is forgotten by the context
            assertFalse(first.containsBean(first.getClassLoader().loadClass("app.Gated")));

            // a key no requirement reads refreshes in place
            Files.writeString(config, "app.label=beta\n");
            runtime.changed(List.of(config), List.of());
            assertEquals("beta", first.getEnvironment().getProperty("app.label", String.class).orElse(null));
            assertEquals(1, runtime.generation());
            assertTrue(first.isRunning());

            // the key a disabled bean requires appears: the bean appears with a restart
            Files.writeString(config, "app.label=beta\napp.on=true\n");
            runtime.changed(List.of(config), List.of());
            ApplicationContext second = runtime.awaitGeneration(2, Duration.ofMinutes(2));
            assertFalse(first.isRunning());
            assertTrue(second.containsBean(second.getClassLoader().loadClass("app.Gated")));
            assertTrue(second.containsBean(second.getClassLoader().loadClass("app.Toggle")));

            // the key a bean requires to be missing appears: the bean disappears with a restart
            Files.writeString(config, "app.label=beta\napp.on=true\napp.off=true\n");
            runtime.changed(List.of(config), List.of());
            ApplicationContext third = runtime.awaitGeneration(3, Duration.ofMinutes(2));
            assertFalse(second.isRunning());
            assertFalse(third.containsBean(third.getClassLoader().loadClass("app.Toggle")));
            assertTrue(third.containsBean(third.getClassLoader().loadClass("app.Gated")));
        } finally {
            runtime.close();
        }
    }

    @Test
    void aResourceThatIsNotConfigurationUnderTheConfigurationRootReachesTheResourceWatches() throws Exception {
        Path src = Files.createDirectories(project.resolve("src/main/java/app"));
        Files.writeString(src.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args).properties(java.util.Map.of("spec.name", "DevRuntimeTest", "micronaut.server.port", "-1")).mainClass(Application.class).start();
                }
            }
            """);
        Path resources = Files.createDirectories(project.resolve("src/main/resources"));
        Path schema = Files.createDirectories(resources.resolve("graphql")).resolve("schema.graphqls");
        Files.writeString(schema, "type Query { hello: String }");
        Path config = resources.resolve("application.properties");
        Files.writeString(config, "app.label=alpha\n");
        Path manifestFile = project.resolve("dev.properties");
        Files.write(project.resolve("cp.argfile"), List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.strategy=restart
            micronaut.dev.reloadable=build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.processor-path=@cp.argfile
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.resources.config=src/main/resources
            micronaut.dev.compile.java.output=build/classes
            """);

        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[0]);
        try {
            ApplicationContext first = runtime.context().orElseThrow();
            List<ResourceChange> schemas = new CopyOnWriteArrayList<>();
            List<ResourceChange> configuration = new CopyOnWriteArrayList<>();
            ((DefaultBeanContext) first).resources(ResourceKind.CONFIG).include("**/*.graphqls").watch(schemas::add);
            ((DefaultBeanContext) first).resources(ResourceKind.CONFIG).include("*.properties").watch(configuration::add);
            assertTrue(schemas.get(0).initial());
            schemas.clear();
            configuration.clear();

            // the schema is edited: the watch is told, nothing restarts and the configuration is not what changed
            Files.writeString(schema, "type Query { hello: String, bye: String }");
            runtime.changed(List.of(schema), List.of());
            assertTrue(schemas.stream().anyMatch(change -> !change.initial() && change.changed().contains(schema.toAbsolutePath())), schemas.toString());
            assertTrue(configuration.isEmpty(), configuration.toString());
            assertEquals(1, runtime.generation());
            assertTrue(first.isRunning());

            // a configuration file refreshes the configuration in place, and a watch of the configuration root is told
            Files.writeString(config, "app.label=beta\n");
            runtime.changed(List.of(config), List.of());
            assertEquals("beta", first.getEnvironment().getProperty("app.label", String.class).orElse(null));
            assertTrue(configuration.stream().anyMatch(change -> change.changed().contains(config.toAbsolutePath())), configuration.toString());
            assertEquals(1, runtime.generation());

            // a deleted schema is reported removed
            Files.delete(schema);
            schemas.clear();
            runtime.changed(List.of(), List.of(schema));
            assertTrue(removedIn(schemas).contains(schema.toAbsolutePath()), schemas.toString());
            assertEquals(1, runtime.generation());
        } finally {
            runtime.close();
        }
    }

    @Test
    void theConfigurationFilesAreTheApplicationAndBootstrapFilesAtTheRootAndThePropertySourcesTheEnvironmentRead() {
        List<Path> roots = List.of(project);
        Set<String> names = ResourceNotifier.names();
        Set<String> extensions = Set.of("properties", "yml", "yaml");
        assertTrue(ResourceNotifier.isConfigurationFile(project.resolve("application.yml"), roots, names, extensions));
        assertTrue(ResourceNotifier.isConfigurationFile(project.resolve("application-dev.properties"), roots, names, extensions));
        assertTrue(ResourceNotifier.isConfigurationFile(project.resolve("bootstrap.yaml"), roots, names, extensions));
        assertFalse(ResourceNotifier.isConfigurationFile(project.resolve("schema.graphqls"), roots, names, extensions));
        assertFalse(ResourceNotifier.isConfigurationFile(project.resolve("logback.xml"), roots, names, extensions));
        assertFalse(ResourceNotifier.isConfigurationFile(project.resolve("applications.yml"), roots, names, extensions));
        assertFalse(ResourceNotifier.isConfigurationFile(project.resolve("graphql/application.yml"), roots, names, extensions));
        assertFalse(ResourceNotifier.isConfigurationFile(project.resolve("messages.properties"), roots, names, extensions));
        // a property source micronaut.config.files names
        Set<String> origins = Set.of("classpath:custom.yml", "file:" + project.resolve("other/extra.yml").toAbsolutePath());
        assertTrue(ResourceNotifier.isPropertySource(project.resolve("custom.yml"), roots, origins));
        assertTrue(ResourceNotifier.isPropertySource(project.resolve("other/extra.yml"), roots, origins));
        assertFalse(ResourceNotifier.isPropertySource(project.resolve("schema.graphqls"), roots, origins));
    }

    @Test
    void aLateReportOfAWriteOfADeletedStaticFileIsSettledAsADeletion() throws Exception {
        Path src = Files.createDirectories(project.resolve("src/main/java/app"));
        Files.writeString(src.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args).properties(java.util.Map.of("spec.name", "DevRuntimeTest", "micronaut.server.port", "-1")).mainClass(Application.class).start();
                }
            }
            """);
        Path staticRoot = Files.createDirectories(project.resolve("src/main/resources/static"));
        Path css = Files.writeString(staticRoot.resolve("app.css"), "body {}").toAbsolutePath();
        Path kept = Files.writeString(staticRoot.resolve("kept.css"), "p {}").toAbsolutePath();
        Path manifestFile = project.resolve("dev.properties");
        Files.write(project.resolve("cp.argfile"), List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.strategy=restart
            micronaut.dev.reloadable=build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.processor-path=@cp.argfile
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.resources.static=src/main/resources/static
            micronaut.dev.compile.java.output=build/classes
            """);

        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[0]);
        try {
            ApplicationContext first = runtime.context().orElseThrow();
            List<ResourceChange> changes = new CopyOnWriteArrayList<>();
            ((DefaultBeanContext) first).resources(ResourceKind.STATIC).include("**/*.css").watch(changes::add);
            changes.clear();

            // the stylesheet is deleted and another written; the watcher's own reports of that are handled first
            Files.delete(css);
            Files.writeString(kept, "p { color: red }");
            long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
            while (!(removedIn(changes).contains(css) && changes.stream().anyMatch(change -> change.changed().contains(kept))) && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertTrue(removedIn(changes).contains(css), changes.toString());
            runtime.awaitBatch(runtime.enqueue(new Pending(Map.of(), Map.of(ResourceKind.STATIC, new SourceChanges(Set.of(), Set.of(css))), false)));
            changes.clear();

            // then the harness reports the deletion and the write, and a watcher's late reports of the stylesheet's
            // earlier write and of a deletion before the other was written again arrive after them: the batches merge
            // in that order, the later winning, as the reload thread merges those that arrive together
            Pending harness = new Pending(Map.of(), Map.of(ResourceKind.STATIC, new SourceChanges(Set.of(kept), Set.of(css))), false);
            Pending late = new Pending(Map.of(), Map.of(ResourceKind.STATIC, new SourceChanges(Set.of(css), Set.of(kept))), false);
            runtime.awaitBatch(runtime.enqueue(Pending.merge(List.of(harness, late))));

            // the file system settles it: the stylesheet is gone, the other is there
            deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (changes.isEmpty() && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            List<ResourceChange> reported = List.copyOf(changes);
            assertFalse(reported.isEmpty());
            assertTrue(removedIn(reported).contains(css), reported.toString());
            assertTrue(reported.stream().noneMatch(change -> change.changed().contains(css)), reported.toString());
            assertTrue(reported.stream().noneMatch(change -> change.removed().contains(kept)), reported.toString());
            assertTrue(reported.stream().anyMatch(change -> change.changed().contains(kept)), reported.toString());
            // and the stylesheet still belongs to the live root, its build copy hidden
            assertTrue(runtime.classLoader().liveResources().belongs("app.css"));
            assertEquals(1, runtime.generation());
        } finally {
            runtime.close();
        }
    }

    @Test
    void aLateReportOfAWriteOfADeletedDirectoryRemovesTheFilesKnownUnderIt() throws Exception {
        Path root = Files.createDirectories(project.resolve("static"));
        Path nested = Files.createDirectories(root.resolve("css"));
        Path one = Files.writeString(nested.resolve("one.css"), "a {}").toAbsolutePath();
        Path two = Files.writeString(nested.resolve("two.css"), "b {}").toAbsolutePath();
        Path here = Files.writeString(root.resolve("here.css"), "c {}").toAbsolutePath();
        io.micronaut.dev.loader.LiveResources live = new io.micronaut.dev.loader.LiveResources(List.of(root));
        Files.delete(one);
        Files.delete(two);
        Files.delete(nested);
        Path absoluteNested = nested.toAbsolutePath();
        java.util.function.Function<Path, Set<Path>> removed = path -> {
            Set<Path> files = new java.util.LinkedHashSet<>();
            files.add(path);
            files.addAll(live.knownUnder(path));
            return files;
        };
        Map<ResourceKind, SourceChanges> merged = Map.of(ResourceKind.STATIC, new SourceChanges(Set.of(absoluteNested), Set.of(here)));
        Map<ResourceKind, SourceChanges> settled = ResourceNotifier.settle(merged, removed, path -> ResourceKind.STATIC);
        assertEquals(Set.of(here), settled.get(ResourceKind.STATIC).changed());
        assertEquals(Set.of(absoluteNested, one, two), settled.get(ResourceKind.STATIC).deleted());
        // changes the file system agrees with are kept as they are
        Map<ResourceKind, SourceChanges> agreed = Map.of(ResourceKind.STATIC, new SourceChanges(Set.of(here), Set.of(one)));
        assertSame(agreed, ResourceNotifier.settle(agreed, removed, path -> ResourceKind.STATIC));
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
            assertEquals("one", greet(runtime, restarted));
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
            assertEquals("three", greet(runtime, fixed));
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

    private static String greet(DevRuntime runtime, ApplicationContext context) throws Exception {
        Class<?> type = context.getClassLoader().loadClass("app.Greeter");
        Object bean = context.getBean(type);
        return (String) type.getMethod("greet").invoke(bean);
    }
}
