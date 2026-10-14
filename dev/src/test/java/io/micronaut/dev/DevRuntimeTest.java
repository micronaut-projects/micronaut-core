package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.reload.ResourceKind;
import io.micronaut.context.watch.ResourceChange;
import io.micronaut.dev.compile.SourceKind;
import io.micronaut.dev.loader.GenerationClassLoader;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
            assertEquals(io.micronaut.dev.manifest.DevReloadStrategy.AUTO, runtime.strategy(), "byte-buddy-agent attaches to the test JVM");

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
    void aRedefinitionTheJvmRefusesRestartsWithEveryChangedClassAndLeavesTheRetiredGenerationAsItWas() throws Exception {
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
        Path other = src.resolve("Other.java");
        Files.writeString(other, other("one"));
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
            assertEquals("one", greet(first));
            assertEquals(io.micronaut.dev.manifest.DevReloadStrategy.AUTO, runtime.strategy(), "byte-buddy-agent attaches to the test JVM");
            refuseRedefinitions(runtime);
            GenerationClassLoader generation = runtime.classLoader().current();
            byte[] greeterBefore = InPlaceUpdates.classFile(generation.roots(), "app.Greeter");
            byte[] otherBefore = InPlaceUpdates.classFile(generation.roots(), "app.Other");

            // two body-only edits the JVM refuses to redefine: the restart applies both
            Files.writeString(greeter, greeter("two"));
            Files.writeString(other, other("two"));
            runtime.reload();
            ApplicationContext second = runtime.awaitGeneration(2, Duration.ofMinutes(2));
            assertNotSame(first, second);
            assertEquals(0, runtime.redefinitions());
            assertEquals("two", greet(second));
            Class<?> otherType = second.getClassLoader().loadClass("app.Other");
            assertEquals("two", otherType.getMethod("value").invoke(null));
            // the retired generation's snapshot holds the class files it ran with, not the refused ones
            assertArrayEquals(greeterBefore, InPlaceUpdates.classFile(generation.roots(), "app.Greeter"));
            assertArrayEquals(otherBefore, InPlaceUpdates.classFile(generation.roots(), "app.Other"));
        } finally {
            runtime.close();
        }
    }

    /**
     * Gives the runtime an agent that refuses every redefinition, as the JVM does a change it cannot apply.
     */
    private static void refuseRedefinitions(DevRuntime runtime) throws ReflectiveOperationException {
        Field field = DevRuntime.class.getDeclaredField("instrumentation");
        field.setAccessible(true);
        Instrumentation agent = (Instrumentation) field.get(runtime);
        field.set(runtime, Proxy.newProxyInstance(DevRuntimeTest.class.getClassLoader(), new Class<?>[] {Instrumentation.class}, (proxy, method, args) -> {
            if (method.getName().equals("redefineClasses")) {
                throw new UnmodifiableClassException("refused by the test");
            }
            try {
                return method.invoke(agent, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }));
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
            awaitTrue(() -> schemas.stream().anyMatch(change -> !change.initial() && change.changed().contains(schema.toAbsolutePath())), schemas);
            assertTrue(configuration.isEmpty(), configuration.toString());
            assertEquals(1, runtime.generation());
            assertTrue(first.isRunning());

            // a configuration file refreshes the configuration in place, and a watch of the configuration root is told
            Files.writeString(config, "app.label=beta\n");
            awaitTrue(() -> "beta".equals(first.getEnvironment().getProperty("app.label", String.class).orElse(null)), configuration);
            awaitTrue(() -> configuration.stream().anyMatch(change -> change.changed().contains(config.toAbsolutePath())), configuration);
            assertEquals(1, runtime.generation());

            // a deleted schema is reported removed
            schemas.clear();
            Files.delete(schema);
            awaitTrue(() -> schemas.stream().anyMatch(change -> change.removed().contains(schema.toAbsolutePath())), schemas);
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
        // a file micronaut.config.import names with classpath*:
        assertTrue(ResourceNotifier.isPropertySource(project.resolve("shared.yml"), roots, Set.of("classpath*:shared.yml")));
        // a file micronaut.config.files names relative to the working directory
        Path relative = Path.of("").toAbsolutePath().relativize(project.resolve("relative.yml").toAbsolutePath());
        assertTrue(ResourceNotifier.isPropertySource(project.resolve("relative.yml"), roots, Set.of("file:" + relative)));
    }

    @Test
    void theConfigurationFilesIncludeThoseOfOverriddenLocationsAndOfImportsNotLoadedYet() {
        List<Path> roots = List.of(project);
        Set<String> names = ResourceNotifier.names();
        Set<String> extensions = Set.of("properties", "yml", "yaml");
        // overrideConfigLocations("classpath:config/"): the environment records only application.yml as the origin
        List<String> locations = List.of("classpath:config/", "file:" + project.resolve("external"));
        assertTrue(ResourceNotifier.isConfigurationFile(project.resolve("config/application.yml"), roots, locations, names, extensions));
        assertTrue(ResourceNotifier.isConfigurationFile(project.resolve("external/application-dev.yml"), roots, locations, names, extensions));
        assertFalse(ResourceNotifier.isConfigurationFile(project.resolve("application.yml"), roots, locations, names, extensions));
        assertFalse(ResourceNotifier.isConfigurationFile(project.resolve("config/schema.graphqls"), roots, locations, names, extensions));
        assertFalse(ResourceNotifier.isConfigurationFile(project.resolve("config/application.yml"), roots, names, extensions));

        // imports declared whether or not their files existed when the context started
        Set<String> imports = ResourceNotifier.imports(List.of(
            PropertySource.of("application", Map.of(
                "micronaut.config.import", List.of("optional:classpath://feature", "optional:classpath://late.yml", "optional:file://" + project.resolve("abs.yml").toAbsolutePath()))),
            PropertySource.of("indexed", Map.of("micronaut.config.import[0]", "optional:classpath://indexed/one.yml")),
            PropertySource.of("structured", Map.of(
                "micronaut.config.import[0].provider", "classpath",
                "micronaut.config.import[0].resource-path", "nested/structured.yml",
                "micronaut.config.import[0].optional", true)),
            PropertySource.of("nested", Map.of("micronaut.config.import", "optional:classpath://sibling.yml"), PropertySource.Origin.of("classpath:config/app.yml")),
            PropertySource.of("structured-nested", Map.of(
                "micronaut.config.import[0].provider", "classpath",
                "micronaut.config.import[0].resource-path", "shared.yml"), PropertySource.Origin.of("classpath:config/other.yml")),
            PropertySource.of("other", Map.of("micronaut.config.importance", "classpath://unrelated.yml", "app.name", "test"))));
        assertTrue(ResourceNotifier.isImport(project.resolve("late.yml"), roots, imports, extensions));
        assertTrue(ResourceNotifier.isImport(project.resolve("abs.yml"), roots, imports, extensions));
        assertTrue(ResourceNotifier.isImport(project.resolve("indexed/one.yml"), roots, imports, extensions));
        assertTrue(ResourceNotifier.isImport(project.resolve("nested/structured.yml"), roots, imports, extensions));
        // an import resolves against the location of the property source that declares it, and names that file only
        assertTrue(ResourceNotifier.isImport(project.resolve("config/sibling.yml"), roots, imports, extensions));
        assertFalse(ResourceNotifier.isImport(project.resolve("sibling.yml"), roots, imports, extensions));
        assertFalse(ResourceNotifier.isImport(project.resolve("config/late.yml"), roots, imports, extensions));
        // a structured import resolves from the class path root, wherever it is declared
        assertTrue(ResourceNotifier.isImport(project.resolve("shared.yml"), roots, imports, extensions));
        assertFalse(ResourceNotifier.isImport(project.resolve("config/shared.yml"), roots, imports, extensions));
        assertFalse(ResourceNotifier.isImport(project.resolve("unrelated.yml"), roots, imports, extensions));
        assertFalse(ResourceNotifier.isImport(project.resolve("schema.graphqls"), roots, imports, extensions));
        assertFalse(ResourceNotifier.isImport(project.resolve("notlate.yml"), roots, imports, extensions));
        // an import without an extension, of a file in any format a loader reads
        assertTrue(ResourceNotifier.isImport(project.resolve("feature.yml"), roots, imports, extensions));
        assertFalse(ResourceNotifier.isImport(project.resolve("feature.graphqls"), roots, imports, extensions));
    }

    /**
     * Waits for the file watcher's batch to be handled, which this branch has no direct way to report.
     */
    private static void awaitTrue(BooleanSupplier condition, Object state) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out: " + state);
            }
            Thread.sleep(50);
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
