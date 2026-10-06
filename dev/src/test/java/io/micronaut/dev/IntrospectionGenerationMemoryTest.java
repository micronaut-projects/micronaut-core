package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * A restart watcher that reads an introspection of the generation it retires, after the class loader was swapped,
 * puts that generation back into the soft cache of {@code BeanIntrospector.SHARED}, which would keep it until the
 * memory runs short. The runtime forgets it again once the retired generation stopped.
 */
class IntrospectionGenerationMemoryTest {

    private static final String COLLECTED = "generation 1 collected";

    @TempDir
    Path project;

    @Test
    void aRestartWatcherReadingAnIntrospectionOfTheRetiringGenerationDoesNotKeepIt() throws Exception {
        ProbeJvm.run(Probe.class, project, COLLECTED);
    }

    /**
     * Runs an application whose restart watcher reads an introspection of its own generation, restarts it once and
     * reports whether its first generation was collected.
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
                            .properties(java.util.Map.of("spec.name", "IntrospectionGenerationMemoryTest", "micronaut.server.port", -1))
                            .mainClass(Application.class)
                            .start();
                    }
                }
                """);
            Files.writeString(src.resolve("Book.java"), """
                package app;
                @io.micronaut.core.annotation.Introspected
                public class Book {
                    private String title;
                    public String getTitle() { return title; }
                    public void setTitle(String title) { this.title = title; }
                }
                """);
            // reads its own generation's introspection as the restart retires it, as a schema reloader compares the
            // entities of the two generations
            Files.writeString(src.resolve("RestartWatcher.java"), """
                package app;
                @jakarta.inject.Singleton
                public class RestartWatcher implements io.micronaut.context.event.ApplicationEventListener<io.micronaut.context.reload.ClassChangeEvent> {
                    @Override
                    public void onApplicationEvent(io.micronaut.context.reload.ClassChangeEvent event) {
                        if (io.micronaut.core.beans.BeanIntrospector.SHARED.findIntrospection(Book.class).isEmpty()) {
                            throw new IllegalStateException("no introspection of Book");
                        }
                        System.out.println("read the introspection of generation " + event.generation());
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
                // one restart: the next one would forget the cache of every loader anyway, as it swaps the loader
                runtime.restart();
                runtime.awaitGeneration(2, Duration.ofMinutes(2));
                if (ProbeJvm.collected(first)) {
                    System.out.println(COLLECTED);
                    return 0;
                }
                System.out.println("generation 1 is still reachable");
                return 1;
            } finally {
                runtime.close();
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
