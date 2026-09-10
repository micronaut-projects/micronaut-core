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
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.python.embedding.GraalPyResources;
import org.graalvm.python.embedding.VirtualFileSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.graalvm.polyglot.PolyglotException;
import java.util.Map;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    void aReusableContextSharesItsEngineWithTheApplication() throws IOException {
        PythonContextRuntime.setReuseContext(false);
        PythonContextRuntime.resetContext();
        try (Context context = GraalPyContextFactory.bootstrapReusableContext(getClass().getClassLoader())) {
            try (ApplicationContext applicationContext = ApplicationContext.run(Map.of("micronaut.python.pool.enabled", true, "micronaut.python.pool.size", 1))) {
                assertSame(context.getEngine(), applicationContext.getBean(Engine.class), "the pool must compile against the bootstrap engine");
                assertSame(context, applicationContext.getBean(Context.class));
                Integer pooledSum = applicationContext.getBean(PythonContextExecutor.class).withContext(pooled -> pooled.eval(PYTHON, "1 + 2").asInt());
                assertEquals(3, pooledSum);
            }
            assertEquals(4, context.eval(PYTHON, "2 + 2").asInt(), "closing the application must not close the shared engine");
        } finally {
            PythonContextRuntime.setReuseContext(false);
            PythonContextRuntime.resetContext();
        }
    }

    @Test
    void hostClassLookupIsUnrestrictedByDefault() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            Context context = applicationContext.getBean(Context.class);
            assertTrue(applicationContext.getBean(GraalPyContextConfiguration.class).getHostClassLookup().isEmpty());
            // a class outside the JDK, Jakarta and framework packages: visible without configuration
            assertTrue(context.eval(PYTHON, "import java\njava.type('org.slf4j.LoggerFactory')").isMetaObject());
            assertTrue(context.eval(PYTHON, "java.type('org.graalvm.polyglot.Value')").isMetaObject());
        }
    }

    @Test
    void hostClassLookupCanBeRestrictedToPackages() {
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of(
            "graalpy.context.host-class-lookup", List.of("com.example", "org.slf4j.Logger")
        ))) {
            Context context = applicationContext.getBean(Context.class);
            assertEquals(List.of("com.example", "org.slf4j.Logger"), applicationContext.getBean(GraalPyContextConfiguration.class).getHostClassLookup());
            assertTrue(context.eval(PYTHON, "import java\njava.type('java.util.ArrayList')()").hasArrayElements());
            assertEquals("a", context.eval(PYTHON, "java.type('io.micronaut.core.util.StringUtils').trimToNull(' a ')").asString());
            assertTrue(context.eval(PYTHON, "java.type('org.slf4j.Logger')").isMetaObject(), "a class named exactly is visible");
            PolyglotException blocked = assertThrows(PolyglotException.class,
                () -> context.eval(PYTHON, "java.type('org.slf4j.LoggerFactory')"));
            assertTrue(blocked.getMessage().contains("org.slf4j.LoggerFactory"), blocked.getMessage());
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
