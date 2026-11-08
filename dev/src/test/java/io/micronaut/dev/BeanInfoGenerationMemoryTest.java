package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.loader.TestGenerations;
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
 * What reads a bean through the JDK's {@code java.beans.Introspector}, as Jinjava's EL resolver does for a template,
 * leaves the class in the JDK's soft caches, which would keep its generation until the memory runs short.
 */
class BeanInfoGenerationMemoryTest {

    private static final String COLLECTED = "generation 1 collected";

    @TempDir
    Path project;

    @Test
    void aBeanReadThroughTheIntrospectorDoesNotKeepItsGeneration() throws Exception {
        ProbeJvm.assertPasses(Probe.class, project, COLLECTED, "-Dprobe.thread=current");
    }

    @Test
    void aBeanReadOnAVirtualThreadDoesNotKeepItsGeneration() throws Exception {
        ProbeJvm.assertPasses(Probe.class, project, COLLECTED, "-Dprobe.thread=virtual");
    }

    @Test
    void aBeanReadInAThreadGroupOfItsOwnDoesNotKeepItsGeneration() throws Exception {
        ProbeJvm.assertPasses(Probe.class, project, COLLECTED, "-Dprobe.thread=group");
    }

    @Test
    void aBeanReadInTheRootThreadGroupDoesNotKeepItsGeneration() throws Exception {
        ProbeJvm.assertPasses(Probe.class, project, COLLECTED, "-Dprobe.thread=root");
    }

    @Test
    void withoutJavaDesktopNothingIsFlushed() throws Exception {
        ProbeJvm.assertPasses(Flush.class, project, Flush.FORGOTTEN, "--limit-modules", "java.base,java.logging,java.xml,java.naming,java.management,jdk.unsupported");
    }

    @Test
    void onANativeImageNothingIsFlushed() throws Exception {
        ProbeJvm.assertPasses(Flush.class, project, Flush.KEPT, "-Dorg.graalvm.nativeimage.imagecode=runtime");
    }

    @Test
    void onTheJvmTheBeanInfosOfThisThreadGroupAreFlushed() throws Exception {
        ProbeJvm.assertPasses(Flush.class, project, Flush.FLUSHED);
    }

    /**
     * Forgets a retired generation, and tells what became of a bean info, where the JVM has one.
     */
    public static final class Flush {

        static final String FORGOTTEN = "forgotten without java.desktop";
        static final String KEPT = "the bean info was kept";
        static final String FLUSHED = "the bean info was flushed";

        public static void main(String[] args) throws Exception {
            System.out.println(ModuleLayer.boot().findModule("java.desktop").isPresent() ? withBeanInfo() : withoutBeanInfo());
            System.out.flush();
            System.exit(0);
        }

        private static String withoutBeanInfo() {
            forget();
            return FORGOTTEN;
        }

        private static String withBeanInfo() throws Exception {
            Object before = java.beans.Introspector.getBeanInfo(Flush.class);
            forget();
            return before == java.beans.Introspector.getBeanInfo(Flush.class) ? KEPT : FLUSHED;
        }

        private static void forget() {
            GenerationMemory.forgetRetired(TestGenerations.over(2, List.of()));
        }
    }

    /**
     * Runs an application that reads one of its classes through the introspector, restarts it once and reports whether
     * its first generation was collected.
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
                            .properties(java.util.Map.of("micronaut.server.port", -1))
                            .mainClass(Application.class)
                            .start();
                    }
                }
                """);
            Files.writeString(src.resolve("Book.java"), """
                package app;
                public class Book {
                    public String getTitle() { return "title"; }
                }
                """);
            Files.writeString(src.resolve("BookReader.java"), """
                package app;
                @io.micronaut.context.annotation.Context
                public class BookReader {
                    @jakarta.annotation.PostConstruct
                    void read() throws Exception {
                        Runnable read = () -> {
                            try {
                                System.out.println(Thread.currentThread().getThreadGroup().getName() + " read "
                                    + java.beans.Introspector.getBeanInfo(Book.class).getPropertyDescriptors().length + " properties");
                            } catch (java.beans.IntrospectionException e) {
                                throw new IllegalStateException(e);
                            }
                        };
                        switch (System.getProperty("probe.thread")) {
                            case "virtual" -> Thread.ofVirtual().start(read).join();
                            case "group" -> {
                                Thread thread = new Thread(new ThreadGroup("of its own"), read);
                                thread.start();
                                thread.join();
                            }
                            case "root" -> {
                                ThreadGroup root = Thread.currentThread().getThreadGroup();
                                while (root.getParent() != null) {
                                    root = root.getParent();
                                }
                                Thread thread = new Thread(root, read);
                                thread.start();
                                thread.join();
                            }
                            default -> read.run();
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
