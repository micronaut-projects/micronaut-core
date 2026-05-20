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
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.micronaut.context.python.GraalPyRuntimeUtil.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ContextHolderTest {

    @TempDir
    Path temporaryDirectory;

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

            Value sample = ContextHolder.findClass("keywordpkg.async", "Sample", context);
            Value script = ContextHolder.findScript("keywordpkg.async", "script", context);

            assertEquals("class", sample.getMember("value").asString());
            assertEquals("script", script.getMember("value").asString());
        }
    }

    @Test
    void usesContextClassLoaderWhenInstantiatingPythonFromRuntimeThreads() {
        ClassLoader hostClassLoader = ContextHolderTest.class.getClassLoader();
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
                        self.loaded = java.type("io.micronaut.context.python.ContextHolder") is not None
                """);
            ContextHolder.setContext(context, hostClassLoader);

            Thread.currentThread().setContextClassLoader(new ClassLoader(null) {
            });
            Value value = ContextHolder.newInstance("NeedsHost");

            assertTrue(value.getMember("loaded").asBoolean());
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
            ContextHolder.resetContext();
        }
    }
}
