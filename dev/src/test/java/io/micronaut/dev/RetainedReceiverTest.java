package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bean a module annotated with {@code @Retain} that received a bean of the application is not retained: it would
 * keep the retired generation's bean, and with it the generation, and run its old code.
 */
class RetainedReceiverTest {

    @TempDir
    Path project;

    @BeforeEach
    void reset() {
        AuthenticatedClient.CREATED.set(0);
        AuthenticatedClient.DESTROYED.set(0);
    }

    @Test
    void aRetainedBeanThatReceivedAnApplicationBeanIsCreatedAgain() throws Exception {
        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifest()), new String[0]);
        try {
            ApplicationContext first = runtime.context().orElseThrow();
            AuthenticatedClient client = first.getBean(AuthenticatedClient.class);
            WeakReference<ClientCredentials> firstCredentials = new WeakReference<>(client.credentials);
            assertSame(first.getClassLoader(), client.credentials.getClass().getClassLoader());
            first = null;
            runtime.restart();
            ApplicationContext second = runtime.awaitGeneration(2, Duration.ofMinutes(2));
            AuthenticatedClient recreated = second.getBean(AuthenticatedClient.class);
            assertNotSame(client, recreated);
            assertSame(second.getClassLoader(), recreated.credentials.getClass().getClassLoader());
            assertEquals(2, AuthenticatedClient.CREATED.get());
            assertEquals(1, AuthenticatedClient.DESTROYED.get());
            client = null;
            assertTrue(collected(firstCredentials), "the first generation's credentials are still reachable");
        } finally {
            runtime.close();
        }
    }

    private static boolean collected(WeakReference<?> reference) throws InterruptedException {
        for (int i = 0; i < 50 && reference.get() != null; i++) {
            System.gc();
            Thread.sleep(100);
        }
        return reference.get() == null;
    }

    private Path manifest() throws Exception {
        Path src = Files.createDirectories(project.resolve("src/main/java/app"));
        Files.writeString(src.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args)
                        .properties(java.util.Map.of("spec.name", "RetainedReceiverTest", "micronaut.server.port", -1))
                        .mainClass(Application.class)
                        .start();
                }
            }
            """);
        Files.writeString(src.resolve("AppCredentials.java"), """
            package app;
            // a prototype, as a bean without a scope is: the client owns its instance, which no singleton registration
            // of the retired generation holds, so nothing but the class tells it apart
            @io.micronaut.context.annotation.Prototype
            public class AppCredentials implements io.micronaut.dev.ClientCredentials {
                @Override
                public String token() {
                    return "app";
                }
            }
            """);
        Files.write(project.resolve("cp.argfile"), List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
        Path manifestFile = project.resolve("dev.properties");
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.reloadable=build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.processor-path=@cp.argfile
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.java.output=build/classes
            """);
        return manifestFile;
    }
}
