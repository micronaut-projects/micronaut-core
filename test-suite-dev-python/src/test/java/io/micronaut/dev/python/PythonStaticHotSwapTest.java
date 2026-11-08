/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.dev.python;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.DevRuntime;
import io.micronaut.dev.MicronautDevMain;
import io.micronaut.dev.manifest.DevManifest;
import io.micronaut.dev.manifest.DevReloadStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An edit of a statically compiled function changes the class compiled from it as well as the module: with the agent
 * attached, the JVM redefines the class and the module is patched, in the running generation.
 */
class PythonStaticHotSwapTest {

    private static final String SERVICE = "app/service.py";

    @TempDir
    Path project;

    @Test
    void aBodyEditOfACompiledMethodIsAppliedInPlaceAndAStructuralEditStartsANewGeneration() throws Exception {
        PythonFixture fixture = PythonFixture.create(project);
        fixture.writePython(SERVICE, service("one"));

        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifest("")), new String[0]);
        try {
            assertEquals(DevReloadStrategy.AUTO, runtime.strategy(), "byte-buddy-agent attaches to the test JVM");
            ApplicationContext context = runtime.context().orElseThrow();
            Class<?> type = context.getClassLoader().loadClass("app.Service");
            Object service = context.getBean(type);
            assertEquals("one", value(service));

            byte[] compiled = Files.readAllBytes(project.resolve("build/classes/app/Service.class"));
            fixture.writePython(SERVICE, service("two"));
            runtime.reload();
            // what this test is about: the body lives in the generated class, which the edit changed
            assertFalse(Arrays.equals(compiled, Files.readAllBytes(project.resolve("build/classes/app/Service.class"))));
            assertEquals(1, runtime.generation());
            assertEquals(1, runtime.redefinitions());
            assertEquals(1, runtime.inPlacePatches());
            assertSame(context, runtime.context().orElseThrow());
            assertEquals("two", value(service));

            // a broken edit: the compilation fails and the generation keeps running the code it has
            fixture.writePython(SERVICE, "class Broken(:\n");
            runtime.reload();
            assertTrue(runtime.lastFailure().isPresent());
            assertEquals(1, runtime.generation());
            assertEquals("two", value(service));

            // the fix, and another edit after it
            fixture.writePython(SERVICE, service("three"));
            runtime.reload();
            assertTrue(runtime.lastFailure().isEmpty());
            assertEquals(1, runtime.generation());
            assertEquals(2, runtime.redefinitions());
            assertEquals("three", value(service));

            // a method the generated class did not have: a new generation
            fixture.writePython(SERVICE, service("four") + """

                    def extra(self, count: int) -> int:
                        return count
                """);
            runtime.reload();
            ApplicationContext second = runtime.awaitGeneration(2, Duration.ofMinutes(2));
            assertEquals(2, runtime.redefinitions());
            assertEquals("four", value(second.getBean(second.getClassLoader().loadClass("app.Service"))));
        } finally {
            runtime.close();
        }
    }

    @Test
    void patchingInPlaceOffStartsANewGeneration() throws Exception {
        assertNewGeneration("micronaut.dev.patch-in-place=false\n");
    }

    @Test
    void theRestartStrategyStartsANewGeneration() throws Exception {
        assertNewGeneration("micronaut.dev.strategy=restart\n");
    }

    private void assertNewGeneration(String extra) throws Exception {
        PythonFixture fixture = PythonFixture.create(project);
        fixture.writePython(SERVICE, service("one"));
        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifest(extra)), new String[0]);
        try {
            fixture.writePython(SERVICE, service("two"));
            runtime.reload();
            ApplicationContext second = runtime.awaitGeneration(2, Duration.ofMinutes(2));
            assertEquals(0, runtime.redefinitions());
            assertEquals(0, runtime.inPlacePatches());
            assertEquals("two", value(second.getBean(second.getClassLoader().loadClass("app.Service"))));
        } finally {
            runtime.close();
        }
    }

    private static String service(String value) {
        return """
            from jakarta.inject import Singleton


            @Singleton
            class Service:
                def value(self) -> str:
                    return "%s"
            """.formatted(value);
    }

    private static String value(Object service) throws Exception {
        return (String) service.getClass().getMethod("value").invoke(service);
    }

    private Path manifest(String extra) throws Exception {
        List<String> classpath = PythonFixture.testClasspath().stream().map(Path::toString).toList();
        Files.write(project.resolve("cp.argfile"), classpath);
        Files.write(project.resolve("options.argfile"), List.of("-Amicronaut.python.compile.static=all"));
        Path manifestFile = project.resolve("dev.properties");
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=pyronaut_application.PyronautMain
            micronaut.dev.reloadable=build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.sources.python=src/main/python
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.python.output=build/classes
            micronaut.dev.compile.java.output=build/classes
            micronaut.dev.compile.python.options=@options.argfile
            """ + extra);
        return manifestFile;
    }
}
