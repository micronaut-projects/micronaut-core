package io.micronaut.dev.python;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.DevRuntime;
import io.micronaut.dev.MicronautDevMain;
import io.micronaut.dev.manifest.DevManifest;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A retired generation is collectable: nothing the parent tier holds, such as a JVM shutdown hook or the
 * executable methods of a parent-tier bean definition, keeps its class loader, its application context or its
 * GraalPy context reachable.
 */
class PythonGenerationMemoryTest {

    private static final Duration COLLECTION_TIMEOUT = Duration.ofSeconds(60);

    @TempDir
    Path project;

    @Test
    void aRetiredGenerationItsContextAndItsGraalPyContextAreCollected() throws Exception {
        PythonFixture fixture = PythonFixture.create(project);
        List<String> classpath = PythonFixture.testClasspath().stream().map(Path::toString).toList();
        Files.write(project.resolve("cp.argfile"), classpath);
        Path manifestFile = project.resolve("dev.properties");
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=pyronaut_application.PyronautMain
            micronaut.dev.strategy=restart
            micronaut.dev.patch-in-place=false
            micronaut.dev.reloadable=build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.sources.python=src/main/python
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.python.output=build/classes
            micronaut.dev.compile.java.output=build/classes
            """);

        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[0]);
        try {
            fixture.writeGreeter("two");
            runtime.reload();
            runtime.awaitGeneration(2, Duration.ofMinutes(2));
            // the second generation, not the first: whatever the JVM initializes once, on the thread of the first
            // generation that needs it, keeps that thread's context class loader for good
            Retired second = retire(runtime);

            fixture.writeGreeter("three");
            runtime.reload();
            runtime.awaitGeneration(3, Duration.ofMinutes(2));
            fixture.writeGreeter("four");
            runtime.reload();
            ApplicationContext fourth = runtime.awaitGeneration(4, Duration.ofMinutes(2));
            assertEquals("Hello four", greet(fourth));

            awaitCollected(second.loader, "the class loader of generation 2");
            awaitCollected(second.applicationContext, "the application context of generation 2");
            awaitCollected(second.pythonContext, "the GraalPy context of generation 2");
        } finally {
            runtime.close();
        }
    }

    /**
     * Weak references to what the running generation owns, taken in a frame of its own so that no local of the
     * test keeps them.
     */
    private static Retired retire(DevRuntime runtime) {
        ApplicationContext context = runtime.context().orElseThrow();
        Context pythonContext = context.getBean(Context.class, Qualifiers.byName("python"));
        return new Retired(
            new WeakReference<>(context.getClassLoader()),
            new WeakReference<>(context),
            new WeakReference<>(pythonContext)
        );
    }

    private static void awaitCollected(WeakReference<?> reference, String what) throws InterruptedException {
        long deadline = System.nanoTime() + COLLECTION_TIMEOUT.toNanos();
        while (reference.get() != null && System.nanoTime() < deadline) {
            System.gc();
            Thread.sleep(200);
        }
        assertNull(reference.get(), what + " is still reachable");
    }

    private static String greet(ApplicationContext context) throws Exception {
        Class<?> type = context.getClassLoader().loadClass("app.PythonGreeter");
        return (String) type.getMethod("greet").invoke(context.getBean(type));
    }

    private record Retired(WeakReference<ClassLoader> loader,
                           WeakReference<ApplicationContext> applicationContext,
                           WeakReference<Context> pythonContext) {
    }
}
