package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * A JDBC data source survives the restarts of an application in development mode without the manifest naming it, since
 * the factory method that makes it is annotated with {@link io.micronaut.context.annotation.Retain}: the
 * new generation is served the same pool, behind the wrapper its own bean created listener makes, as Micronaut Data
 * wraps a data source, and the first generation is collected. H2, which preallocates exceptions as it is first used,
 * is initialized outside any generation.
 * <p>The application runs in a JVM of its own: H2 must not have been initialized there by any other test.</p>
 */
class DataSourceGenerationMemoryTest {

    private static final String PASSED = "the pool was retained and generation 1 collected";

    @TempDir
    Path project;

    @Test
    void theDataSourceIsRetainedRewrappedAndTheFirstGenerationCollected() throws Exception {
        ProbeJvm.run(Probe.class, project, PASSED);
    }

    /**
     * Runs an application with an H2 data source in development mode, restarts it twice and reports what was retained
     * and whether its first generation was collected.
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
                            .properties(java.util.Map.of("spec.name", "DataSourceGenerationMemoryTest", "micronaut.server.port", -1))
                            .mainClass(Application.class)
                            .start();
                    }
                }
                """);
            // the application's own use of the database: a row per generation, in a table the first one creates
            Files.writeString(src.resolve("Store.java"), """
                package app;
                @io.micronaut.context.annotation.Context
                public class Store {
                    public final int rows;
                    public Store(javax.sql.DataSource dataSource) throws java.sql.SQLException {
                        try (java.sql.Connection connection = dataSource.getConnection(); java.sql.Statement statement = connection.createStatement()) {
                            statement.execute("create table if not exists visit (id int auto_increment primary key)");
                            statement.execute("insert into visit default values");
                            try (java.sql.ResultSet result = statement.executeQuery("select count(*) from visit")) {
                                result.next();
                                rows = result.getInt(1);
                            }
                        }
                    }
                }
                """);
            Files.write(project.resolve("cp.argfile"), List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
            Path manifestFile = project.resolve("dev.properties");
            // no micronaut.dev.retain: the factory method that makes the data source is annotated with @Retain
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
            PooledDataSource pool;
            WeakReference<ClassLoader> first;
            try {
                first = firstLoader(runtime);
                pool = pool(runtime.context().orElseThrow(), 1);
                runtime.restart();
                runtime.awaitGeneration(2, Duration.ofMinutes(2));
                check(runtime, pool, 2);
                runtime.restart();
                runtime.awaitGeneration(3, Duration.ofMinutes(2));
                check(runtime, pool, 3);
                if (PooledDataSource.CREATED.get() != 1 || pool.isClosed()) {
                    System.out.println("the pool was created " + PooledDataSource.CREATED.get() + " time(s), closed: " + pool.isClosed());
                    return 1;
                }
                if (!ProbeJvm.collected(first)) {
                    System.out.println("generation 1 is still reachable");
                    return 1;
                }
            } finally {
                runtime.close();
            }
            if (!pool.isClosed()) {
                System.out.println("the pool was not closed with the last generation");
                return 1;
            }
            System.out.println(PASSED);
            return 0;
        }

        /**
         * The pool behind the generation's data source, after checking that the wrapper is bound to that generation's
         * context and that the application saw the rows of every generation so far, in a frame of its own so that no
         * local keeps the context.
         */
        private static PooledDataSource pool(ApplicationContext context, int generation) throws Exception {
            DataSource registered = context.getBean(DataSource.class);
            if (!(registered instanceof ContextualDataSources.Contextual contextual)) {
                throw new IllegalStateException("generation " + generation + " is served " + registered + ", not the wrapper");
            }
            if (contextual.locator() != context) {
                throw new IllegalStateException("the wrapper of generation " + generation + " is bound to another context");
            }
            Class<?> store = context.getClassLoader().loadClass("app.Store");
            int rows = (int) store.getField("rows").get(context.getBean(store));
            if (rows != generation) {
                throw new IllegalStateException("generation " + generation + " saw " + rows + " row(s): the database did not survive");
            }
            return (PooledDataSource) contextual.target;
        }

        private static void check(DevRuntime runtime, PooledDataSource pool, int generation) throws Exception {
            if (pool(runtime.context().orElseThrow(), generation) != pool) {
                throw new IllegalStateException("generation " + generation + " has a new pool");
            }
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
