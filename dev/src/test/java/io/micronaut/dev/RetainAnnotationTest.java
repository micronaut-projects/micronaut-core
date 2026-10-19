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
 * A bean its module annotated with {@code @Retain} survives a restart without the manifest naming it, unless the
 * manifest turns annotation retention off.
 */
class RetainAnnotationTest {

    @TempDir
    Path project;

    @BeforeEach
    void reset() {
        AnnotatedPool.CREATED.set(0);
        AnnotatedPool.DESTROYED.set(0);
    }

    @Test
    void anAnnotatedBeanIsRetainedWithoutBeingNamed() throws Exception {
        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifest("")), new String[0]);
        try {
            AnnotatedPool pool = runtime.context().orElseThrow().getBean(AnnotatedPool.class);
            assertEquals("a", pool.url);
            runtime.restart();
            ApplicationContext second = runtime.awaitGeneration(2, Duration.ofMinutes(2));
            assertSame(pool, second.getBean(AnnotatedPool.class));
            assertEquals(1, AnnotatedPool.CREATED.get());
            assertEquals(0, AnnotatedPool.DESTROYED.get());
        } finally {
            runtime.close();
        }
        assertEquals(1, AnnotatedPool.DESTROYED.get());
    }

    @Test
    void anAnnotatedBeanIsReleasedWhenTheConfigurationItNamesChanges() throws Exception {
        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifest("")), new String[0]);
        try {
            AnnotatedPool pool = runtime.context().orElseThrow().getBean(AnnotatedPool.class);
            runtime.restart();
            ApplicationContext second = runtime.awaitGeneration(2, Duration.ofMinutes(2));
            assertSame(pool, second.getBean(AnnotatedPool.class));

            // the pool injects my.pool.url, so its edit restarts; my.pool is what the annotation says releases it
            Files.writeString(project.resolve("src/main/resources/application.properties"), "my.pool.url=b\n");
            ApplicationContext third = runtime.awaitGeneration(3, Duration.ofMinutes(2));
            AnnotatedPool recreated = third.getBean(AnnotatedPool.class);
            assertNotSame(pool, recreated);
            assertEquals("b", recreated.url);
            assertEquals(2, AnnotatedPool.CREATED.get());
            assertEquals(1, AnnotatedPool.DESTROYED.get());
        } finally {
            runtime.close();
        }
    }

    @Test
    void annotationRetentionCanBeTurnedOff() throws Exception {
        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifest("micronaut.dev.retain-annotated=false\n")), new String[0]);
        try {
            AnnotatedPool pool = runtime.context().orElseThrow().getBean(AnnotatedPool.class);
            runtime.restart();
            ApplicationContext second = runtime.awaitGeneration(2, Duration.ofMinutes(2));
            assertNotSame(pool, second.getBean(AnnotatedPool.class));
            assertEquals(2, AnnotatedPool.CREATED.get());
            assertEquals(1, AnnotatedPool.DESTROYED.get());
        } finally {
            runtime.close();
        }
    }

    private Path manifest(String extra) throws Exception {
        Path src = Files.createDirectories(project.resolve("src/main/java/app"));
        Path resources = Files.createDirectories(project.resolve("src/main/resources"));
        Files.writeString(resources.resolve("application.properties"), "my.pool.url=a\n");
        Files.writeString(src.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args)
                        .properties(java.util.Map.of("spec.name", "RetainAnnotationTest", "micronaut.server.port", -1))
                        .mainClass(Application.class)
                        .start();
                }
            }
            """);
        Files.write(project.resolve("cp.argfile"), List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
        Path manifestFile = project.resolve("dev.properties");
        // no micronaut.dev.retain: the annotation alone retains the pool
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.reloadable=build/classes
            micronaut.dev.resources.config=src/main/resources
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.processor-path=@cp.argfile
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.java.output=build/classes
            """ + extra);
        return manifestFile;
    }
}
