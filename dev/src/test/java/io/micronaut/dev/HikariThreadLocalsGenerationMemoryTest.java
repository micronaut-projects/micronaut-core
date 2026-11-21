package io.micronaut.dev;

import com.zaxxer.hikari.HikariDataSource;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A Hikari pool closed while a thread that used it lives on, as a change to the data sources closes a pool while the
 * event loops retained across restarts live on, is collected in development mode: Hikari holds weakly, in a thread local
 * of each thread that returned a connection, the connections of the pool. Hikari is on the system class loader here, as
 * in development mode, where it would otherwise hold them strongly, and the thread local would keep the pool.
 * <p>The application runs in a JVM of its own: the runtime sets a system property that Hikari reads.</p>
 */
class HikariThreadLocalsGenerationMemoryTest {

    private static final String PASSED = "the closed Hikari pool was collected while the thread that used it lives on";

    @TempDir
    Path project;

    @Test
    void aClosedPoolIsCollectedWhileTheThreadThatUsedItLivesOn() throws Exception {
        ProbeJvm.assertPasses(Probe.class, project, PASSED);
    }

    /**
     * Runs an application in development mode, then uses a Hikari pool of H2 on a thread that lives on, closes the pool
     * and reports whether it was collected.
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
                            .properties(java.util.Map.of("spec.name", "HikariThreadLocalsGenerationMemoryTest", "micronaut.server.port", -1))
                            .mainClass(Application.class)
                            .start();
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
            if (HikariDataSource.class.getClassLoader() != ClassLoader.getSystemClassLoader()) {
                System.out.println("Hikari is not on the system class loader: it holds its connections weakly on its own");
                return 1;
            }
            DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[0]);
            // a platform thread that outlives the pool, as an event loop retained across restarts does
            ExecutorService thread = Executors.newSingleThreadExecutor();
            try {
                WeakReference<Object> pool = closePool(thread);
                if (!ProbeJvm.collected(pool)) {
                    System.out.println("the closed pool is still reachable");
                    return 1;
                }
            } finally {
                thread.shutdown();
                runtime.close();
            }
            System.out.println(PASSED);
            return 0;
        }

        /**
         * Has the thread use a pool, then closes the pool on this thread, as the restart thread closes the pool of a
         * data source that changed, in a frame of its own so that no local keeps it. Hikari forgets a closed connection
         * in the thread local of the thread that closes the pool only.
         */
        private static WeakReference<Object> closePool(ExecutorService thread) throws Exception {
            HikariDataSource dataSource = thread.submit(Probe::usePool).get();
            WeakReference<Object> pool = new WeakReference<>(dataSource.getHikariPoolMXBean());
            dataSource.close();
            return pool;
        }

        /**
         * Borrows a connection and returns it on this thread, which keeps it in Hikari's thread local.
         */
        private static HikariDataSource usePool() throws Exception {
            HikariDataSource dataSource = new HikariDataSource();
            dataSource.setJdbcUrl("jdbc:h2:mem:hikari-thread-locals");
            dataSource.setMaximumPoolSize(1);
            try (Connection connection = dataSource.getConnection()) {
                connection.isValid(1);
            }
            return dataSource;
        }
    }
}
