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
package io.micronaut.python.compiler;

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PythonBytecodeCompilerTest {

    @Test
    void producesHashBasedBytecodeUsingGraalPyCacheNaming() {
        PythonBytecodeCompiler.Result result;
        try (PythonBytecodeCompiler compiler = new PythonBytecodeCompiler()) {
            result = compiler.compile("answer = 42", "/graalpy_vfs/src/example.py");
        }

        assertTrue(result.cachePath().replace('\\', '/').contains("/__pycache__/example."));
        assertTrue(result.cachePath().endsWith(".pyc"));
        assertFalse(result.bytes().length == 0);

        try (Context context = Context.newBuilder("python").allowAllAccess(true).build()) {
            context.getBindings("python").putMember("pyc", Base64.getEncoder().encodeToString(result.bytes()));
            assertEquals(42, context.eval("python", "import base64; import marshal; namespace = {}; exec(marshal.loads(base64.b64decode(pyc)[16:]), namespace); namespace['answer']").asInt());
        }
    }

    @Test
    void loadsRelocatedBytecodeAndRemapsCodeFilenames(@TempDir Path directory) throws IOException {
        String source = """
            def answer():
                return 42
            """;
        String logicalFilename = "/graalpy_vfs/src/example.py";
        PythonBytecodeCompiler.Result result;
        try (PythonBytecodeCompiler compiler = new PythonBytecodeCompiler()) {
            result = compiler.compile(source, logicalFilename);
        }
        Path relocatedSource = directory.resolve("example.py");
        Files.writeString(relocatedSource, source);

        try (Context context = Context.newBuilder("python").allowAllAccess(true).build()) {
            context.getBindings("python").putMember("pyc", Base64.getEncoder().encodeToString(result.bytes()));
            context.getBindings("python").putMember("filename", relocatedSource.toString());
            context.eval("python", """
                import base64
                import importlib.util
                import marshal
                from importlib.machinery import SourceFileLoader
                from pathlib import Path

                class CachedOnlyLoader(SourceFileLoader):
                    def source_to_code(self, *args, **kwargs):
                        raise AssertionError('source compilation is disabled')

                bytecode = base64.b64decode(pyc)
                compiled_filename = marshal.loads(bytecode[16:]).co_filename
                cache = Path(importlib.util.cache_from_source(filename))
                cache.parent.mkdir()
                cache.write_bytes(bytecode)
                loaded = CachedOnlyLoader('example', filename).get_code('example')
                namespace = {'__file__': filename}
                exec(loaded, namespace)
                """);

            assertEquals(logicalFilename, context.eval("python", "compiled_filename").asString());
            assertEquals(relocatedSource.toString(), context.eval("python", "loaded.co_filename").asString());
            assertEquals(relocatedSource.toString(), context.eval("python", "namespace['answer'].__code__.co_filename").asString());
            assertEquals(42, context.eval("python", "namespace['answer']()").asInt());
        }
    }
}
