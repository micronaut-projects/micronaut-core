package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * An application whose first generation opens an H2 database with {@code DB_CLOSE_ON_EXIT=TRUE} has its first generation
 * collected: the shutdown hook H2 registers then, a thread created as {@code OnExitDatabaseCloser} initializes and never
 * started until the JVM exits, is created outside any generation, with the parent tier's loader as its context class
 * loader.
 * <p>The application runs in a JVM of its own: H2 must not have been initialized there by any other test.</p>
 */
class H2ShutdownHookGenerationMemoryTest {

    private static final String PASSED = "H2 registered its shutdown hook and generation 1 was collected";

    @TempDir
    Path project;

    @Test
    void theFirstGenerationIsCollectedAfterItOpenedADatabaseClosedOnExit() throws Exception {
        ProbeJvm.assertPasses(Probe.class, project, PASSED);
    }

    /**
     * Runs an application that opens an in-memory H2 database closed on exit in development mode, restarts it twice and
     * reports whether H2 registered its shutdown hook and whether the first generation was collected.
     */
    public static final class Probe {

        public static void main(String[] args) throws Exception {
            int status;
            try {
                status = run(Path.of(args[0]));
            } catch (Throwable e) {
                e.printStackTrace(System.out);
                status = 2;
            }
            System.out.flush();
            System.exit(status);
        }

        private static int run(Path project) throws Exception {
            Path src = Files.createDirectories(project.resolve("src/main/java/app"));
            Files.writeString(src.resolve("Application.java"), """
                package app;
                public class Application {
                    public static void main(String[] args) {
                        io.micronaut.runtime.Micronaut.build(args)
                            .properties(java.util.Map.of("spec.name", "H2ShutdownHookGenerationMemoryTest", "micronaut.server.port", -1))
                            .mainClass(Application.class)
                            .start();
                    }
                }
                """);
            // opened on the generation's thread, kept open by DB_CLOSE_DELAY, as an application's in-memory database is
            Files.writeString(src.resolve("Store.java"), """
                package app;
                @io.micronaut.context.annotation.Context
                public class Store {
                    public Store() throws java.sql.SQLException {
                        try (java.sql.Connection connection = java.sql.DriverManager.getConnection("jdbc:h2:mem:closed-on-exit;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=TRUE");
                             java.sql.Statement statement = connection.createStatement()) {
                            statement.execute("create table if not exists visit (id int auto_increment primary key)");
                            statement.execute("insert into visit default values");
                        }
                    }
                }
                """);
            Files.write(project.resolve("cp.argfile"), List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
            Path manifestFile = project.resolve("dev.properties");
            Files.writeString(manifestFile, """
                micronaut.dev.main-class=app.Application
                micronaut.dev.strategy=restart
                micronaut.dev.reloadable=build/classes
                micronaut.dev.compile-classpath=@cp.argfile
                micronaut.dev.processor-path=@cp.argfile
                micronaut.dev.sources.java=src/main/java
                micronaut.dev.compile.java.output=build/classes
                micronaut.dev.patch-in-place=false
                """);
            DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[0]);
            try {
                WeakReference<ClassLoader> first = firstLoader(runtime);
                runtime.restart();
                runtime.awaitGeneration(2, Duration.ofMinutes(2));
                runtime.restart();
                runtime.awaitGeneration(3, Duration.ofMinutes(2));
                // what the test is about: without the hook, nothing of H2 would keep the first generation
                Class<?> closer = Class.forName("org.h2.engine.OnExitDatabaseCloser");
                Field registered = closer.getDeclaredField("registered");
                registered.setAccessible(true);
                if (!registered.getBoolean(null)) {
                    System.out.println("H2 registered no shutdown hook");
                    return 1;
                }
                Field instance = closer.getDeclaredField("INSTANCE");
                instance.setAccessible(true);
                ClassLoader hookLoader = ((Thread) instance.get(null)).getContextClassLoader();
                if (hookLoader instanceof io.micronaut.dev.loader.GenerationClassLoader) {
                    System.out.println("H2's shutdown hook has a generation's loader as its context class loader: " + hookLoader);
                    return 1;
                }
                if (!ProbeJvm.collected(first)) {
                    System.out.println("generation 1 is still reachable");
                    return 1;
                }
            } finally {
                runtime.close();
            }
            System.out.println(PASSED);
            return 0;
        }

        /**
         * A weak reference to the first generation's loader, taken in a frame of its own so that no local keeps it.
         */
        private static WeakReference<ClassLoader> firstLoader(DevRuntime runtime) {
            ApplicationContext context = runtime.context().orElseThrow();
            if (!context.isRunning()) {
                throw new IllegalStateException("the first generation did not start");
            }
            return new WeakReference<>(context.getClassLoader());
        }
    }
}
