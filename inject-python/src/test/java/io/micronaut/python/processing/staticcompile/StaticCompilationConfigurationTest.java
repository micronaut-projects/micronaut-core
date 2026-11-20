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
package io.micronaut.python.processing.staticcompile;

import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticCompilationConfigurationTest {

    private static boolean enabled(StaticCompilationMode mode, String text) {
        return StaticCompilationConfiguration.of(mode).isEnabledFor(List.of(Source.newBuilder("python", text, "module.py").buildLiteral()));
    }

    @Test
    void modeOffPlansOnlyWhenASourceAppliesTheSwitch() {
        assertTrue(enabled(StaticCompilationMode.OFF, """
            class Service:
                @CompileStatic
                def run(self) -> int:
                    return 1
            """));
        assertTrue(enabled(StaticCompilationMode.OFF, "@ CompileStatic(True)\nclass Service:\n    pass\n"));
        assertTrue(enabled(StaticCompilationMode.OFF, "import pyronaut.build as build\n\n@build.CompileStatic\nclass Service:\n    pass\n"));
        assertTrue(enabled(StaticCompilationMode.OFF, "from pyronaut.build import CompileStatic as Fast\n\n@Fast\nclass Service:\n    pass\n"));
        // the module-level statement
        assertTrue(enabled(StaticCompilationMode.OFF, "from micronaut.context.python.annotation import CompileStatic\nCompileStatic()\n"));
        assertTrue(enabled(StaticCompilationMode.OFF, "CompileStatic\n"));
    }

    @Test
    void modeOffIgnoresAMentionThatIsNoSwitch() {
        assertFalse(enabled(StaticCompilationMode.OFF, """
            # CompileStatic is not used here
            from micronaut.context.python.annotation import CompileStatic

            class Service:
                \"""Could use @CompileStatic one day.\"""
                def run(self) -> str:
                    return "CompileStatic"  # @CompileStatic

                def other(self) -> str:
                    return CompileStatic.__name__
            """));
        assertFalse(enabled(StaticCompilationMode.OFF, "@NotCompileStatic\nclass Service:\n    pass\n"));
        assertFalse(enabled(StaticCompilationMode.OFF, "class Service:\n    pass\n"));
    }

    @Test
    void theOtherModesAlwaysPlan() {
        assertTrue(enabled(StaticCompilationMode.ANNOTATED, "class Service:\n    pass\n"));
        assertTrue(enabled(StaticCompilationMode.ALL, "class Service:\n    pass\n"));
    }
}
