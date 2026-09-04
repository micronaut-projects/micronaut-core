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

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.graalvm.polyglot.Context;
import org.graalvm.python.embedding.GraalPyResources;
import org.graalvm.python.embedding.VirtualFileSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static io.micronaut.context.python.GraalPyRuntimeUtil.PYTHON;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GraalPyContextFactoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesActiveVirtualEnvExecutableWithoutPyenvVersion() throws IOException {
        Path executable = temporaryDirectory.resolve(".venv/bin/python");
        Files.createDirectories(executable.getParent());
        Files.writeString(executable, "");

        assertEquals(
            executable,
            GraalPyContextFactory.resolveVirtualEnvExecutable(Map.of("VIRTUAL_ENV", temporaryDirectory.resolve(".venv").toString())).orElseThrow()
        );
    }

    @Test
    void ignoresMissingVirtualEnvExecutable() {
        assertTrue(GraalPyContextFactory.resolveVirtualEnvExecutable(Map.of("VIRTUAL_ENV", temporaryDirectory.resolve(".venv").toString())).isEmpty());
    }

    @Test
    void ignoresBlankVirtualEnv() {
        assertTrue(GraalPyContextFactory.resolveVirtualEnvExecutable(Map.of("VIRTUAL_ENV", " ")).isEmpty());
    }

    @Test
    void inheritsVirtualEnvIntoGuestPython() throws IOException {
        String virtualEnv = System.getenv("VIRTUAL_ENV");
        assumeTrue(virtualEnv != null && !virtualEnv.isBlank());

        PythonContextRuntime.setReuseContext(false);
        PythonContextRuntime.resetContext();
        try (Context context = GraalPyContextFactory.bootstrapReusableContext(getClass().getClassLoader())) {
            assertEquals(virtualEnv, context.eval(PYTHON, "import os; os.environ.get('VIRTUAL_ENV')").asString());
        } finally {
            PythonContextRuntime.setReuseContext(false);
            PythonContextRuntime.resetContext();
        }
    }

    @Test
    void contextOptionsCanBeConfiguredFromMicronautProperties() {
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of(
            "graalpy.context.options", Map.of("log.level", "FINE")
        ))) {
            GraalPyContextConfiguration config = applicationContext.getBean(GraalPyContextConfiguration.class);
            assertTrue(config.getOptions().containsKey("log.level"));
        }
    }

    @Test
    void importsPackagedAsyncioRuntimeFromBytecodeCache() {
        VirtualFileSystem vfs = VirtualFileSystem.newBuilder()
            .resourceDirectory(GraalPyContextFactory.APPLICATION_PATH)
            .resourceLoadingClass(GraalPyContextFactory.class)
            .build();
        try (Context context = GraalPyResources.contextBuilder(vfs).allowAllAccess(true).build()) {
            String cachePath = context.eval(PYTHON, "import micronaut_asyncio; micronaut_asyncio.__cached__").asString();

            assertTrue(cachePath.contains("/__pycache__/micronaut_asyncio."));
            assertTrue(cachePath.endsWith(".pyc"));
        }
    }
}
