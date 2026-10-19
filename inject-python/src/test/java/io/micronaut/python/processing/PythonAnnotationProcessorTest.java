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
package io.micronaut.python.processing;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PythonAnnotationProcessorTest {

    @Test
    void generatedLauncherLoadsMembersFromItsOwnDirectory(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("__micronaut_members_test.py"), "answer = 42\n__all__ = ['answer']\n");
        try (Context context = Context.newBuilder("python").allowIO(IOAccess.ALL).build()) {
            context.getBindings("python").putMember("launcher_source", PythonAnnotationProcessor.LAUNCHER_SOURCE);
            context.getBindings("python").putMember("launcher_path", directory.resolve("__main__.py").toString());
            assertEquals(42, context.eval("python", """
                namespace = {'__file__': launcher_path}
                exec(launcher_source, namespace)
                namespace['answer']
                """).asInt());
        }
    }

    @Test
    void normalizesWindowsResourcePathSeparators() {
        assertEquals(
            "example/micronaut/forecast_controller.py",
            PythonAnnotationProcessor.normalizeResourcePath("example\\micronaut\\forecast_controller.py")
        );
    }

    @Test
    void normalizesWindowsBytecodeCachePathSeparators() {
        assertEquals(
            "GRAALPY-VFS/micronaut-application/src/example/micronaut/__pycache__/forecast_controller.graalpy253-313.pyc",
            PythonAnnotationProcessor.cacheFilePath(
                "GRAALPY-VFS/micronaut-application/src/example/micronaut/forecast_controller.py",
                "micronaut\\__pycache__\\forecast_controller.graalpy253-313.pyc"
            )
        );
    }
}
