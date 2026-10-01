package io.micronaut.dev.python;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.DevRuntime;
import io.micronaut.dev.MicronautDevMain;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonReloadTest {

    @TempDir
    Path project;

    @Test
    void aPythonModuleRunsFromACleanCheckoutAndItsPythonAndJavaEditsStartNewGenerations() throws Exception {
        PythonFixture fixture = PythonFixture.create(project);
        List<String> classpath = PythonFixture.testClasspath().stream().map(Path::toString).toList();
        Files.write(project.resolve("cp.argfile"), classpath);
        Path manifestFile = project.resolve("dev.properties");
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=pyronaut_application.PyronautMain
            micronaut.dev.strategy=restart
            micronaut.dev.reloadable=build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.sources.python=src/main/python
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.python.output=build/classes
            micronaut.dev.compile.java.output=build/classes
            """);

        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[0]);
        try {
            // compiled from a clean checkout by the Python compiler, the Java source with it
            ApplicationContext first = runtime.context().orElseThrow();
            assertEquals(1, runtime.generation());
            assertEquals("Hello one", greet(first));

            // a Python edit: the module is read again by the GraalPy context of the next generation
            fixture.writeGreeter("two");
            runtime.reload();
            ApplicationContext second = runtime.awaitGeneration(2, Duration.ofMinutes(2));
            assertFalse(first.isRunning());
            assertEquals("Hello two", greet(second));

            // a Java edit, compiled with the module
            fixture.writeGreeting("Hi ");
            runtime.reload();
            ApplicationContext third = runtime.awaitGeneration(3, Duration.ofMinutes(2));
            assertEquals("Hi two", greet(third));

            // a broken Python edit: the generation keeps running and the failure is reported
            fixture.writePython("app/greeter.py", "class Broken(:\n");
            runtime.reload();
            assertTrue(runtime.lastFailure().isPresent());
            assertEquals(3, runtime.generation());
            assertTrue(third.isRunning());
            assertEquals("Hi two", greet(third));

            // the fix
            fixture.writeGreeter("three");
            runtime.reload();
            ApplicationContext fourth = runtime.awaitGeneration(4, Duration.ofMinutes(2));
            assertTrue(runtime.lastFailure().isEmpty());
            assertEquals("Hi three", greet(fourth));
        } finally {
            runtime.close();
        }
    }

    private static String greet(ApplicationContext context) throws Exception {
        Class<?> type = context.getClassLoader().loadClass("app.PythonGreeter");
        return (String) type.getMethod("greet").invoke(context.getBean(type));
    }
}
