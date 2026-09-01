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
package io.micronaut.context.python;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static io.micronaut.context.python.GraalPyRuntimeUtil.PYTHON;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PythonContextRuntimeTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void waitsForEveryContextUsingAnEngineBeforeRunningShutdownGate() {
        try (Engine sharedEngine = Engine.newBuilder(PYTHON).build();
             Engine otherEngine = Engine.newBuilder(PYTHON).build();
             Context first = Context.newBuilder(PYTHON).engine(sharedEngine).build();
             Context second = Context.newBuilder(PYTHON).engine(sharedEngine).build();
             Context unrelated = Context.newBuilder(PYTHON).engine(otherEngine).build()) {
            AtomicInteger invocations = new AtomicInteger();
            PythonContextRuntime.registerContext(first);
            PythonContextRuntime.registerContext(second);
            PythonContextRuntime.registerContext(unrelated);

            PythonContextRuntime.onNoContexts(sharedEngine, invocations::incrementAndGet);
            PythonContextRuntime.unregisterContext(unrelated);
            assertEquals(0, invocations.get());

            PythonContextRuntime.unregisterContext(first);
            assertEquals(0, invocations.get());

            PythonContextRuntime.unregisterContext(second);
            assertEquals(1, invocations.get());
        }
    }

    @Test
    void importsClassesAndScriptsFromPythonKeywordPackageSegments() throws IOException {
        Path asyncPackage = temporaryDirectory.resolve("keywordpkg").resolve("async");
        Files.createDirectories(asyncPackage);
        Files.writeString(temporaryDirectory.resolve("keywordpkg").resolve("__init__.py"), "");
        Files.writeString(asyncPackage.resolve("__init__.py"), """
            class Sample:
                value = "class"
            """);
        Files.writeString(asyncPackage.resolve("script.py"), """
            value = "script"
            """);

        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            context.getBindings(PYTHON).putMember("root", temporaryDirectory.toString());
            context.eval(PYTHON, "import sys\nsys.path.insert(0, root)");

            Value sample = PythonContextRuntime.findClass(new PythonContextRuntime.PythonClassReference(
                "keywordpkg.async",
                "Sample",
                new String[] {},
                "Sample",
                "class-instance:keywordpkg.async.Sample"
            ), context);
            Value script = PythonContextRuntime.findScript("keywordpkg.async", "script", context);

            assertEquals("class", sample.getMember("value").asString());
            assertEquals("script", script.getMember("value").asString());
        }
    }

    @Test
    void importsClassFromPythonStyleModuleWhenPackageExportIsUnavailable() throws IOException {
        Path packagePath = temporaryDirectory.resolve("example").resolve("micronaut");
        Files.createDirectories(packagePath);
        Files.writeString(temporaryDirectory.resolve("example").resolve("__init__.py"), "");
        Files.writeString(packagePath.resolve("__init__.py"), """
            import importlib
            TestWeatherApi = importlib.import_module("example.micronaut.test_weather_api")
            """);
        Files.writeString(packagePath.resolve("test_weather_api.py"), """
            class TestWeatherApi:
                value = "fixture"
            """);

        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            context.getBindings(PYTHON).putMember("root", temporaryDirectory.toString());
            context.eval(PYTHON, "import sys\nsys.path.insert(0, root)");

            Value testWeatherApi = PythonContextRuntime.findClass(new PythonContextRuntime.PythonClassReference(
                "example.micronaut",
                "TestWeatherApi",
                new String[] {},
                "TestWeatherApi",
                "class-instance:example.micronaut.TestWeatherApi"
            ), context);

            assertTrue(testWeatherApi.canInstantiate());
            assertEquals("fixture", testWeatherApi.getMember("value").asString());
        }
    }

    @Test
    void importsClassFromAnyPackageModuleWhenPackageExportIsUnavailable() throws IOException {
        Path packagePath = temporaryDirectory.resolve("example").resolve("micronaut");
        Files.createDirectories(packagePath);
        Files.writeString(temporaryDirectory.resolve("example").resolve("__init__.py"), "");
        Files.writeString(packagePath.resolve("__init__.py"), """
            __all__ = []
            """);
        Files.writeString(packagePath.resolve("forecast_controller.py"), """
            class ForecastService:
                value = "forecast"
            """);

        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            context.getBindings(PYTHON).putMember("root", temporaryDirectory.toString());
            context.eval(PYTHON, "import sys\nsys.path.insert(0, root)");

            Value forecastService = PythonContextRuntime.findClass(new PythonContextRuntime.PythonClassReference(
                "example.micronaut",
                "ForecastService",
                new String[] {},
                "ForecastService",
                "class-instance:example.micronaut.ForecastService"
            ), context);

            assertTrue(forecastService.canInstantiate());
            assertEquals("forecast", forecastService.getMember("value").asString());
        }
    }

    @Test
    void findsNestedClassFromPreSplitClassReference() throws IOException {
        Path packagePath = temporaryDirectory.resolve("example").resolve("nested");
        Files.createDirectories(packagePath);
        Files.writeString(temporaryDirectory.resolve("example").resolve("__init__.py"), "");
        Files.writeString(packagePath.resolve("__init__.py"), """
            class Outer:
                class Inner:
                    value = "nested"
            """);

        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            context.getBindings(PYTHON).putMember("root", temporaryDirectory.toString());
            context.eval(PYTHON, "import sys\nsys.path.insert(0, root)");
            String[] nestedMembers = {"Inner"};
            PythonContextRuntime.PythonClassReference classReference = new PythonContextRuntime.PythonClassReference(
                "example.nested",
                "Outer",
                nestedMembers,
                "Outer.Inner",
                "class-instance:example.nested.Outer.Inner"
            );
            nestedMembers[0] = "Changed";

            Value inner = PythonContextRuntime.findClass(classReference, context);

            assertEquals("nested", inner.getMember("value").asString());
            assertArrayEquals(new String[]{"Inner"}, classReference.nestedMemberNames());
        }
    }

    @Test
    void classReferenceDefensivelyCopiesNestedMemberNames() {
        String[] nestedMemberNames = {"Inner"};
        PythonContextRuntime.PythonClassReference classReference = new PythonContextRuntime.PythonClassReference(
            "example.nested",
            "Outer",
            nestedMemberNames,
            "Outer.Inner",
            "class-instance:example.nested.Outer.Inner"
        );
        nestedMemberNames[0] = "Changed";

        assertEquals("example.nested", classReference.packageName());
        assertEquals("Outer", classReference.rootName());
        assertArrayEquals(new String[]{"Inner"}, classReference.nestedMemberNames());
        assertEquals("Outer.Inner", classReference.displayName());
        assertEquals("class-instance:example.nested.Outer.Inner", classReference.cacheKey());
    }

    @Test
    void usesContextClassLoaderWhenInstantiatingPythonFromRuntimeThreads() {
        ClassLoader hostClassLoader = PythonContextRuntimeTest.class.getClassLoader();
        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        try (Context context = Context.newBuilder(PYTHON)
            .allowAllAccess(true)
            .hostClassLoader(hostClassLoader)
            .allowHostClassLookup(name -> true)
            .build()) {
            context.eval(PYTHON, """
                class NeedsHost:
                    def __init__(self):
                        import java
                        self.loaded = java.type("io.micronaut.context.python.PythonContextRuntime") is not None
                """);
            PythonContextRuntime.setContext(context, hostClassLoader);

            Thread.currentThread().setContextClassLoader(new ClassLoader(null) {
            });
            Value value = PythonContextRuntime.newInstance(new PythonContextRuntime.PythonClassReference(
                null,
                "NeedsHost",
                new String[0],
                "NeedsHost",
                "class-instance:python.NeedsHost"
            ));

            assertTrue(value.getMember("loaded").asBoolean());
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
            PythonContextRuntime.resetContext();
        }
    }

    @Test
    void uninitializedInstanceAcceptsNullProperties() {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            context.eval(PYTHON, "class Empty: pass");
            Value value = PythonContextRuntime.newUninitializedInstance(
                context,
                new PythonContextRuntime.PythonClassReference(
                    null,
                    "Empty",
                    new String[0],
                    "Empty",
                    "class-instance:python.Empty"
                ),
                null
            );

            assertEquals("Empty", value.getMetaObject().getMetaSimpleName());
        }
    }
}
