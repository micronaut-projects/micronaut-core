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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The documentation of a curated module is its docstring, whatever quotes and backslashes it holds.
 */
class FacadeModuleDocstringTest {

    @Test
    void theDocstringIsValidPythonWithTheDocumentation() {
        List<String> documentations = List.of(
            "Curated module.",
            "Says \"hello\"",
            "Ends with two quotes \"\"",
            "Holds \"\"\" three quotes",
            "A backslash \\ and a trailing one \\",
            "\""
        );
        try (Context context = Context.newBuilder("python").build()) {
            for (String documentation : documentations) {
                context.getBindings("python").putMember("source", PythonAnnotationProcessor.facadeDocstring(documentation) + "x = 1\n");
                String doc = context.eval("python", """
                    namespace = {}
                    exec(compile(source, 'facade', 'exec'), namespace)
                    namespace['__doc__']
                    """).asString();
                assertEquals(documentation, doc);
            }
        }
    }
}
