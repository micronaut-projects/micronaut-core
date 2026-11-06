package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationBudgetTest {

    @TempDir
    Path project;

    @Test
    void aReloadBeyondTheBudgetClosesTheRuntimeForTheLauncherToRelaunchTheProcess() throws Exception {
        Path src = Files.createDirectories(project.resolve("src/main/java/app"));
        Files.writeString(src.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args).properties(java.util.Map.of("spec.name", "GenerationBudgetTest", "micronaut.server.port", "-1")).mainClass(Application.class).start();
                }
            }
            """);
        Path greeter = src.resolve("Greeter.java");
        Files.writeString(greeter, greeter("one"));
        Path manifestFile = project.resolve("dev.properties");
        Files.write(project.resolve("cp.argfile"), List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.strategy=restart
            micronaut.dev.max-generations=2
            micronaut.dev.reloadable=build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.processor-path=@cp.argfile
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.java.output=build/classes
            """);
        CompletableFuture<DevRuntime> relaunched = new CompletableFuture<>();
        MicronautDevMain launcher = new MicronautDevMain() {
            @Override
            protected void relaunch(DevRuntime runtime) {
                relaunched.complete(runtime);
            }
        };

        DevRuntime runtime = launcher.launch(DevManifest.load(manifestFile), new String[0]);
        try {
            assertEquals(2, runtime.manifest().maxGenerations());
            assertFalse(runtime.isGenerationBudgetSpent());

            // the second generation is within the budget
            Files.writeString(greeter, greeter("two"));
            runtime.reload();
            ApplicationContext second = runtime.awaitGeneration(2, Duration.ofMinutes(2));
            assertTrue(runtime.isGenerationBudgetSpent());
            assertFalse(runtime.isRelaunchRequested());

            // a third would exceed it: the change is compiled, then the runtime closes and asks for a relaunch
            Files.writeString(greeter, greeter("three"));
            runtime.reload();
            assertSame(runtime, relaunched.get(2, TimeUnit.MINUTES));
            assertTrue(runtime.isRelaunchRequested());
            assertEquals(2, runtime.generation());
            assertFalse(second.isRunning());
            assertNull(DevRuntime.current());
            // the relaunched process starts from the class the last change compiled
            String classFile = new String(Files.readAllBytes(project.resolve("build/classes/app/Greeter.class")), StandardCharsets.ISO_8859_1);
            assertTrue(classFile.contains("three"));
        } finally {
            runtime.close();
        }
    }

    @Test
    void theBudgetIsUnlimitedOnTheJvmUnlessTheManifestSetsOne() {
        assertEquals(0, manifest(null).maxGenerations());
        assertEquals(0, manifest("unlimited").maxGenerations());
        assertEquals(0, manifest("0").maxGenerations());
        assertEquals(7, manifest(" 7 ").maxGenerations());
        assertEquals(3, MicronautDevMain.RELAUNCH);
        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class, () -> manifest("-1"));
        assertTrue(negative.getMessage().contains(DevManifest.MAX_GENERATIONS));
        assertThrows(IllegalArgumentException.class, () -> manifest("many"));
    }

    private DevManifest manifest(String maxGenerations) {
        Properties properties = new Properties();
        properties.setProperty("micronaut.dev.main-class", "app.Application");
        properties.setProperty("micronaut.dev.reloadable", "build/classes");
        if (maxGenerations != null) {
            properties.setProperty(DevManifest.MAX_GENERATIONS, maxGenerations);
        }
        return DevManifest.of(project, properties);
    }

    private static String greeter(String greeting) {
        return "package app; @jakarta.inject.Singleton public class Greeter { public String greet() { return \"" + greeting + "\"; } }";
    }
}
