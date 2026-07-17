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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static io.micronaut.context.python.GraalPyRuntimeUtil.PYTHON;
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
    void contextOptionsCanBeConfiguredFromMicronautProperties() {
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of(
            "graalpy.context.options", Map.of("log.level", "FINE")
        ))) {
            GraalPyContextConfiguration config = applicationContext.getBean(GraalPyContextConfiguration.class);
            assertTrue(config.getOptions().containsKey("log.level"));
        }
    }
}
