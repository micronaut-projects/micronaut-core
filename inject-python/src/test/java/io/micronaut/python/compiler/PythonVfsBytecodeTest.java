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

import io.micronaut.python.processing.PythonAstParser;
import org.graalvm.polyglot.Context;
import org.graalvm.python.embedding.GraalPyResources;
import org.graalvm.python.embedding.VirtualFileSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class PythonVfsBytecodeTest {

    @Test
    void importsThePackagedTransformerFromItsBytecodeCache() {
        VirtualFileSystem vfs = VirtualFileSystem.newBuilder()
            .resourceDirectory(PythonAstParser.INJECT_RESOURCES)
            .resourceLoadingClass(PythonAstParser.class)
            .build();
        try (Context context = GraalPyResources.contextBuilder(vfs).allowAllAccess(true).build()) {
            String cachePath = context.eval("python", """
                import ast
                import keyword
                import re
                import java
                import typing
                import builtins
                builtins.compile = lambda *args, **kwargs: (_ for _ in ()).throw(RuntimeError('source compilation is disabled'))
                import micronaut_transformer
                micronaut_transformer.__cached__
                """).asString();

            assertTrue(cachePath.contains("/__pycache__/micronaut_transformer."));
            assertTrue(cachePath.endsWith(".pyc"));
        }
    }
}
