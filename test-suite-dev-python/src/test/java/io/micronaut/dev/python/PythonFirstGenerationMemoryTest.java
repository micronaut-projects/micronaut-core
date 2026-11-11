package io.micronaut.dev.python;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.beans.BeanIntrospectionProviders;
import io.micronaut.core.beans.BeanIntrospectionReference;
import io.micronaut.core.beans.BeanIntrospectionsProvider;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.dev.DevRuntime;
import io.micronaut.dev.MicronautDevMain;
import io.micronaut.dev.compile.CompilationResult;
import io.micronaut.dev.compile.PythonSourceCompiler;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No retired generation stays reachable, the first one included, in a JVM whose first generation is the first to use
 * Truffle and whose introspections are resolved with a provider that also lists what the thread's context class loader
 * sees, as a launcher's may. What the JVM initializes once, such as the shutdown hook of Truffle's {@code LibGraalObject}
 * on a JVM with libgraal, must have been initialized outside any generation, and the shared introspector must forget
 * what it indexed of a generation for a loader that does not see it.
 * <p>The application runs in a JVM of its own: another test must not have used Truffle there.</p>
 */
class PythonFirstGenerationMemoryTest {

    private static final String PASSED = "every retired generation was collected";
    private static final String USE_CONTEXT_CLASSLOADER = "micronaut.introspections.use.context.classloader";

    @TempDir
    Path project;

    @Test
    void everyRetiredGenerationIsCollectedTheFirstIncluded() throws Exception {
        // compiled here: a launch with no class output would compile first, and use Truffle outside any generation
        PythonFixture fixture = PythonFixture.create(project);
        fixture.writePython("app/model.py", """
            from micronaut.core.annotation import Introspected


            @Introspected
            class Model:
                pass
            """);
        try (PythonSourceCompiler compiler = new PythonSourceCompiler()) {
            CompilationResult result = compiler.compile(fixture.request(Set.of(), Set.of()).asFull());
            assertEquals(CompilationResult.Status.SUCCESS, result.status(), result.diagnostics().toString());
        }
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Path argfile = project.resolve("jvm.argfile");
        Files.write(argfile, List.of("-cp", "\"" + System.getProperty("java.class.path").replace("\\", "\\\\") + "\""));
        List<String> command = new ArrayList<>(List.of(java, "@" + argfile, "-Dmicronaut.python.pool.enabled=false", Probe.class.getName(), project.toString()));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(project.resolve("probe.log").toFile()).start();
        boolean exited = process.waitFor(5, TimeUnit.MINUTES);
        if (!exited) {
            process.destroyForcibly();
        }
        String output = Files.readString(project.resolve("probe.log"), StandardCharsets.UTF_8);
        // in the test report, to see what the probe did
        System.out.println(output);
        String tail = output.length() > 6000 ? output.substring(output.length() - 6000) : output;
        assertTrue(exited, "the probe did not finish:\n" + tail);
        assertTrue(output.contains(PASSED), tail);
        assertEquals(0, process.exitValue(), tail);
    }

    /**
     * Runs the fixture in development mode, restarts it twice and reports whether its retired generations were collected.
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
            PythonFixture fixture = new PythonFixture(project, project.resolve("src/main/python"), project.resolve("src/main/java"), project.resolve("build/classes"));
            Files.write(project.resolve("cp.argfile"), List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
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
            BeanIntrospectionsProvider listing = BeanIntrospectionProviders.get();
            BeanIntrospectionProviders.set(classLoader -> withContextClassLoader(listing, classLoader));
            System.setProperty(USE_CONTEXT_CLASSLOADER, "true");
            DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[0]);
            try {
                WeakReference<ClassLoader> first = lookUpInGeneration(runtime);
                for (int generation = 2; generation <= 3; generation++) {
                    fixture.writeGreeter("generation " + generation);
                    runtime.reload();
                    runtime.awaitGeneration(generation, Duration.ofMinutes(2));
                    lookUpInGeneration(runtime);
                }
                long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
                while (!runtime.classLoader().liveRetiredGenerations().isEmpty() && System.nanoTime() < deadline) {
                    System.gc();
                    Thread.sleep(200);
                }
                if (first.get() != null || !runtime.classLoader().liveRetiredGenerations().isEmpty()) {
                    System.out.println("still reachable: " + runtime.classLoader().liveRetiredGenerations().stream().map(loader -> loader.generation()).toList());
                    return 1;
                }
            } finally {
                runtime.close();
            }
            System.out.println(PASSED);
            return 0;
        }

        /**
         * What a launcher's provider may do: also list what the thread's context class loader sees.
         */
        private static List<BeanIntrospectionReference<Object>> withContextClassLoader(BeanIntrospectionsProvider listing, ClassLoader classLoader) {
            List<BeanIntrospectionReference<Object>> references = new ArrayList<>(listing.provide(classLoader));
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            if (contextClassLoader != null && contextClassLoader != classLoader) {
                references.addAll(listing.provide(contextClassLoader));
            }
            return references;
        }

        /**
         * Asks for the introspection of a type of the parent tier from a thread of the generation, as a condition or a
         * serializer does, in a frame of its own so that no local keeps the context: what the shared introspector then
         * indexes for its own loader holds the classes of this generation.
         *
         * @return A weak reference to the generation's loader
         */
        private static WeakReference<ClassLoader> lookUpInGeneration(DevRuntime runtime) {
            ApplicationContext context = runtime.context().orElseThrow();
            Thread thread = Thread.currentThread();
            ClassLoader previous = thread.getContextClassLoader();
            thread.setContextClassLoader(context.getClassLoader());
            try {
                BeanIntrospector.SHARED.findIntrospection(Probe.class);
                if (BeanIntrospector.SHARED.findIntrospections(reference -> reference.getName().equals("app.Model")).isEmpty()) {
                    throw new IllegalStateException("the generation lists no introspection of its own");
                }
            } finally {
                thread.setContextClassLoader(previous);
            }
            return new WeakReference<>(context.getClassLoader());
        }
    }
}
