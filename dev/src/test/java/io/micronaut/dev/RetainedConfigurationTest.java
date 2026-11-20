package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A bean a module annotated with {@code @Retain(invalidatedBy = ...)} that received a configuration bean under that
 * prefix is retained across a restart, the configuration bean being bound again by each generation, and released by a
 * change under the prefix; one that received configuration under another prefix is retained too, and released by a
 * change under that prefix, which it does not have to name.
 */
class RetainedConfigurationTest {

    @TempDir
    Path project;

    @BeforeEach
    void reset() {
        SettingsPools.Covered.CREATED.set(0);
        SettingsPools.Uncovered.CREATED.set(0);
    }

    @Test
    void aRetainedBeanOfConfigurationItsRetentionCoversIsRetainedUntilThatConfigurationChanges() throws Exception {
        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifest()), new String[0]);
        try {
            ApplicationContext first = runtime.context().orElseThrow();
            SettingsPools.Covered covered = first.getBean(SettingsPools.Covered.class);
            SettingsPools.Uncovered uncovered = first.getBean(SettingsPools.Uncovered.class);
            PoolSettings settings = first.getBean(PoolSettings.class);
            assertEquals(1, covered.size);

            runtime.restart();
            ApplicationContext second = runtime.awaitGeneration(2, Duration.ofMinutes(2));
            assertSame(covered, second.getBean(SettingsPools.Covered.class));
            assertNotSame(settings, second.getBean(PoolSettings.class));
            assertSame(uncovered, second.getBean(SettingsPools.Uncovered.class));
            assertEquals(1, SettingsPools.Covered.CREATED.get());
            assertEquals(1, SettingsPools.Uncovered.CREATED.get());

            Files.writeString(project.resolve("src/main/resources/application.properties"), "my.settings.size=2\nother.settings.size=1\n");
            ApplicationContext third = runtime.awaitGeneration(3, Duration.ofMinutes(2));
            SettingsPools.Covered recreated = third.getBean(SettingsPools.Covered.class);
            assertNotSame(covered, recreated);
            assertEquals(2, recreated.size);
            assertEquals(2, SettingsPools.Covered.CREATED.get());
            // the uncovered pool names my.settings too, so it is released with the covered one
            SettingsPools.Uncovered uncoveredThird = third.getBean(SettingsPools.Uncovered.class);
            assertNotSame(uncovered, uncoveredThird);
            assertEquals(2, SettingsPools.Uncovered.CREATED.get());

            // a change under the prefix of the configuration it received, which it does not name, releases it alone
            Files.writeString(project.resolve("src/main/resources/application.properties"), "my.settings.size=2\nother.settings.size=3\n");
            ApplicationContext fourth = runtime.awaitGeneration(4, Duration.ofMinutes(2));
            SettingsPools.Uncovered remade = fourth.getBean(SettingsPools.Uncovered.class);
            assertNotSame(uncoveredThird, remade);
            assertEquals(3, remade.size);
            assertEquals(3, SettingsPools.Uncovered.CREATED.get());
            assertSame(recreated, fourth.getBean(SettingsPools.Covered.class));
            assertEquals(2, SettingsPools.Covered.CREATED.get());
        } finally {
            runtime.close();
        }
    }

    private Path manifest() throws Exception {
        Path src = Files.createDirectories(project.resolve("src/main/java/app"));
        Path resources = Files.createDirectories(project.resolve("src/main/resources"));
        Files.writeString(resources.resolve("application.properties"), "my.settings.size=1\nother.settings.size=1\n");
        Files.writeString(src.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args)
                        .properties(java.util.Map.of("spec.name", "RetainedConfigurationTest", "micronaut.server.port", -1))
                        .mainClass(Application.class)
                        .start();
                }
            }
            """);
        Files.write(project.resolve("cp.argfile"), List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
        Path manifestFile = project.resolve("dev.properties");
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.reloadable=build/classes
            micronaut.dev.resources.config=src/main/resources
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.processor-path=@cp.argfile
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.java.output=build/classes
            """);
        return manifestFile;
    }
}
