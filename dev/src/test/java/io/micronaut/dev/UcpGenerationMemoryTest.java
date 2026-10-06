package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.manifest.DevManifest;
import oracle.ucp.UniversalConnectionPoolLifeCycleState;
import oracle.ucp.admin.UniversalConnectionPoolManagerImpl;
import oracle.ucp.jdbc.PoolDataSource;
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
 * A UCP pool of an H2 database survives the restarts of an application in development mode, since the factory method
 * that makes it is annotated with {@link io.micronaut.context.annotation.Retain}, and the first generation is collected:
 * UCP's JVM-wide threads, which the first use of a pool starts and which belong to no pool, are given the parent tier's
 * loader by the restart, and the shutdown hook UCP's pool manager registers as it initializes, which no restart sees,
 * is created outside any generation.
 * <p>The application runs in a JVM of its own: UCP must not have been used there by any other test.</p>
 */
class UcpGenerationMemoryTest {

    private static final String PASSED = "the UCP pool was retained and generation 1 collected";

    @TempDir
    Path project;

    @Test
    void theUcpPoolIsRetainedAndTheFirstGenerationCollected() throws Exception {
        ProbeJvm.run(Probe.class, project, PASSED);
    }

    /**
     * Runs an application with a UCP pool of H2 in development mode, restarts it twice and reports what was retained
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
                            .properties(java.util.Map.of("spec.name", "UcpGenerationMemoryTest", "micronaut.server.port", -1))
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
            PoolDataSource pool;
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
                if (UcpDataSourceFactory.CREATED.get() != 1) {
                    System.out.println("the pool was created " + UcpDataSourceFactory.CREATED.get() + " time(s)");
                    return 1;
                }
                if (UniversalConnectionPoolManagerImpl.getUniversalConnectionPoolManager().getConnectionPool(UcpDataSourceFactory.POOL_NAME)
                        .getLifeCycleState() != UniversalConnectionPoolLifeCycleState.LIFE_CYCLE_RUNNING) {
                    System.out.println("the retained pool is not running");
                    return 1;
                }
                List<String> ucpThreads = Thread.getAllStackTraces().keySet().stream().map(Thread::getName).filter(name -> name.startsWith("UCP")).sorted().toList();
                if (ucpThreads.isEmpty()) {
                    System.out.println("UCP started none of its threads");
                    return 1;
                }
                System.out.println("UCP threads: " + ucpThreads);
                if (!ProbeJvm.collected(first)) {
                    System.out.println("generation 1 is still reachable");
                    return 1;
                }
            } finally {
                runtime.close();
            }
            if (UniversalConnectionPoolManagerImpl.getUniversalConnectionPoolManager().getConnectionPoolNames().length != 0) {
                System.out.println("the pool was not destroyed with the last generation");
                return 1;
            }
            System.out.println(PASSED);
            return 0;
        }

        /**
         * The pool of the generation, after checking that the application saw the rows of every generation so far, in
         * a frame of its own so that no local keeps the context.
         */
        private static PoolDataSource pool(ApplicationContext context, int generation) throws Exception {
            DataSource registered = context.getBean(DataSource.class);
            if (!(registered instanceof PoolDataSource pool)) {
                throw new IllegalStateException("generation " + generation + " is served " + registered + ", not a UCP pool");
            }
            Class<?> store = context.getClassLoader().loadClass("app.Store");
            int rows = (int) store.getField("rows").get(context.getBean(store));
            if (rows != generation) {
                throw new IllegalStateException("generation " + generation + " saw " + rows + " row(s): the database did not survive");
            }
            return pool;
        }

        private static void check(DevRuntime runtime, PoolDataSource pool, int generation) throws Exception {
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
