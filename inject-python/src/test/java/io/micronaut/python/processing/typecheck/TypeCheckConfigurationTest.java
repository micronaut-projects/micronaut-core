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
package io.micronaut.python.processing.typecheck;

import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Under mode off the checker runs only for a compilation that names a switch decorator.
 */
final class TypeCheckConfigurationTest {

    private static Source source(String text) {
        return Source.newBuilder("python", text, "module.py").buildLiteral();
    }

    @Test
    void anAliasedImportOfTheSwitchEnablesTheChecker() {
        assertTrue(TypeCheckConfiguration.OFF.isEnabledFor(List.of(source("""
            from micronaut.context.python.annotation import TypeChecked as Checked

            @Checked
            class Service:
                pass
            """))));
    }

    @Test
    void aMentionInACommentOrInsideAnotherNameDoesNot() {
        assertFalse(TypeCheckConfiguration.OFF.isEnabledFor(List.of(source("""
            # TypeChecked is not used here
            class MyTypeCheckedThing:
                pass
            """))));
    }

    @Test
    void anyModeButOffAlwaysChecks() {
        assertTrue(TypeCheckConfiguration.of(TypeCheckMode.WARN).isEnabledFor(List.of(source("class Plain:\n    pass\n"))));
    }
}
