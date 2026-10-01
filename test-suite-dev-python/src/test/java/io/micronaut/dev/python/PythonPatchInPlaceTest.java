package io.micronaut.dev.python;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.DevRuntime;
import io.micronaut.dev.MicronautDevMain;
import io.micronaut.dev.manifest.DevManifest;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.graalvm.polyglot.Engine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier one of reloading Python: an edit that changed only bodies leaves the generated classes as they
 * were, and is patched into the running interpreters, the primary context and the pooled ones, without a
 * new generation.
 */
class PythonPatchInPlaceTest {

    private static final String POOL_PROPERTY = "micronaut.python.pool.enabled";

    @TempDir
    Path project;

    private String poolSetting;

    @BeforeEach
    void enablePool() {
        // the build disables the pool for the dev tests; the pooled contexts must be patched too
        poolSetting = System.clearProperty(POOL_PROPERTY);
    }

    @AfterEach
    void restorePool() {
        if (poolSetting != null) {
            System.setProperty(POOL_PROPERTY, poolSetting);
        }
    }

    @Test
    void aBodyOnlyEditIsPatchedIntoTheRunningGenerationAndAStructuralEditStartsANewOne() throws Exception {
        PythonFixture fixture = PythonFixture.create(project);
        writeShout(fixture);
        writeSpeaker(fixture, "quiet one");
        writePooled(fixture, "pooled one");
        Path manifestFile = manifest(true);

        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[0]);
        try {
            ApplicationContext context = runtime.context().orElseThrow();
            assertEquals(1, runtime.generation());
            assertEquals("Hello one", greet(context));
            Object speaker = speaker(context);
            // an intercepted method: the proxy holds the function object of the bean's class
            assertEquals("QUIET ONE!", speak(speaker));
            Engine engine = engine(context);
            // a bean whose calls borrow a pooled context: the module is imported there too
            assertEquals("pooled one", pooled(context));

            // bodies only: the module is patched in every context, the generation keeps running
            fixture.writeGreeter("two");
            writeSpeaker(fixture, "quiet two");
            writePooled(fixture, "pooled two");
            runtime.reload();
            assertEquals(1, runtime.generation());
            assertEquals(1, runtime.inPlacePatches());
            assertSame(context, runtime.context().orElseThrow());
            assertTrue(context.isRunning());
            assertSame(engine, engine(context));
            assertEquals("Hello two", greet(context));
            assertSame(speaker, speaker(context));
            assertEquals("QUIET TWO!", speak(speaker));
            assertEquals("pooled two", pooled(context));

            // the interceptor's own body
            writeShout(fixture, "?");
            runtime.reload();
            assertEquals(1, runtime.generation());
            assertEquals(2, runtime.inPlacePatches());
            assertEquals("QUIET TWO?", speak(speaker));

            // a broken edit: the compilation fails and the generation keeps running the code it has
            fixture.writePython("app/greeter.py", "class Broken(:\n");
            runtime.reload();
            assertTrue(runtime.lastFailure().isPresent());
            assertEquals(1, runtime.generation());
            assertEquals("Hello two", greet(context));

            // a structural edit, a method the generated class did not have: a new generation
            fixture.writePython("app/greeter.py", """
                from jakarta.inject import Singleton
                from base import Greeting


                @Singleton
                class PythonGreeter(Greeting):
                    def name(self) -> str:
                        return "three"

                    def extra(self, count: int) -> int:
                        return count
                """);
            runtime.reload();
            ApplicationContext second = runtime.awaitGeneration(2, Duration.ofMinutes(2));
            assertTrue(runtime.lastFailure().isEmpty());
            assertEquals(2, runtime.inPlacePatches());
            assertEquals("Hello three", greet(second));

            // a module added: the file system of the running contexts does not list it, so a new generation
            fixture.writePython("app/added.py", "VALUE = 1\n");
            runtime.reload();
            runtime.awaitGeneration(3, Duration.ofMinutes(2));
            assertEquals(2, runtime.inPlacePatches());
        } finally {
            runtime.close();
        }
    }

    @Test
    void patchingInPlaceCanBeTurnedOff() throws Exception {
        PythonFixture fixture = PythonFixture.create(project);
        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifest(false)), new String[0]);
        try {
            fixture.writeGreeter("two");
            runtime.reload();
            ApplicationContext second = runtime.awaitGeneration(2, Duration.ofMinutes(2));
            assertEquals(0, runtime.inPlacePatches());
            assertEquals("Hello two", greet(second));
        } finally {
            runtime.close();
        }
    }

    private Path manifest(boolean patchInPlace) throws Exception {
        List<String> classpath = PythonFixture.testClasspath().stream().map(Path::toString).toList();
        Files.write(project.resolve("cp.argfile"), classpath);
        Path manifestFile = project.resolve("dev.properties");
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=pyronaut_application.PyronautMain
            micronaut.dev.strategy=restart
            micronaut.dev.patch-in-place=%s
            micronaut.dev.reloadable=build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.sources.python=src/main/python
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.python.output=build/classes
            micronaut.dev.compile.java.output=build/classes
            """.formatted(patchInPlace));
        return manifestFile;
    }

    private static void writeShout(PythonFixture fixture) throws Exception {
        writeShout(fixture, "!");
    }

    private static void writeShout(PythonFixture fixture, String suffix) throws Exception {
        fixture.writePython("app/shout.py", """
            import java
            from micronaut.aop import Around, InterceptorBean, MethodInvocationContext
            from micronaut.context.annotation import Executable

            MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")


            @Executable
            @Around
            def Shout(func):
                return func


            @InterceptorBean(Shout)
            class ShoutInterceptor(MethodInterceptor):
                def intercept(self, context: MethodInvocationContext):
                    return str(context.proceed()).upper() + "%s"
            """.formatted(suffix));
    }

    private static void writeSpeaker(PythonFixture fixture, String words) throws Exception {
        fixture.writePython("app/speaker.py", """
            from jakarta.inject import Singleton
            from .shout import Shout


            @Singleton
            class Speaker:
                @Shout
                def speak(self) -> str:
                    return "%s"
            """.formatted(words));
    }

    private static void writePooled(PythonFixture fixture, String value) throws Exception {
        fixture.writePython("app/pooled.py", """
            from micronaut.context.python.scope import ContextPooled


            @ContextPooled
            class PooledValue:
                def value(self) -> str:
                    return "%s"
            """.formatted(value));
    }

    private static String pooled(ApplicationContext context) throws Exception {
        Object bean = context.getBean(context.getClassLoader().loadClass("app.PooledValue"));
        return (String) bean.getClass().getMethod("value").invoke(bean);
    }

    private static Engine engine(ApplicationContext context) {
        return context.getBean(Engine.class, Qualifiers.byName("python"));
    }

    private static Object speaker(ApplicationContext context) throws Exception {
        return context.getBean(context.getClassLoader().loadClass("app.Speaker"));
    }

    private static String speak(Object speaker) throws Exception {
        return (String) speaker.getClass().getMethod("speak").invoke(speaker);
    }

    private static String greet(ApplicationContext context) throws Exception {
        Class<?> type = context.getClassLoader().loadClass("app.PythonGreeter");
        return (String) type.getMethod("greet").invoke(context.getBean(type));
    }
}
