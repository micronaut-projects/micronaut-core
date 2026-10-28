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
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The host object of a Python object answering attribute reads with {@code __getattr__}.
 */
final class ValueCoerciblesTest {

    private static final String SOURCE = """
        class Missing(AttributeError):
            pass

        class Answering:
            def __init__(self, error):
                object.__setattr__(self, "error", error)

            def __getattr__(self, name):
                raise self.error(name)

        class Declaring:
            # a member the class declares, whose read raises
            def __init__(self, error):
                object.__setattr__(self, "error", error)

            @property
            def __micronaut_value_coercible_host__(self):
                raise self.error("no host")
        """;

    @Test
    void anAbsentMemberHasNoHostObject() {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            context.eval(PYTHON, SOURCE);
            Value absent = context.eval(PYTHON, "Answering(AttributeError)");
            Value missing = context.eval(PYTHON, "Answering(Missing)");
            Value declaredAbsent = context.eval(PYTHON, "Declaring(AttributeError)");
            Value declaredMissing = context.eval(PYTHON, "Declaring(Missing)");

            assertNull(ValueCoercibles.hostObject(absent, Object.class));
            assertNull(ValueCoercibles.hostObject(missing, Object.class));
            assertNull(ValueCoercibles.hostObject(declaredAbsent, Object.class));
            assertNull(ValueCoercibles.hostObject(declaredMissing, Object.class));
        }
    }

    @Test
    void anErrorRaisedReadingTheMemberIsNotSwallowed() {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            context.eval(PYTHON, SOURCE);
            Value failing = context.eval(PYTHON, "Answering(ValueError)");
            Value declaredFailing = context.eval(PYTHON, "Declaring(ValueError)");

            PolyglotException e = assertThrows(PolyglotException.class, () -> ValueCoercibles.hostObject(failing, Object.class));
            assertTrue(e.isGuestException());
            assertTrue(e.getMessage().contains(ValueCoercible.HOST_OBJECT_MEMBER), e.getMessage());
            PolyglotException declared = assertThrows(PolyglotException.class, () -> ValueCoercibles.hostObject(declaredFailing, Object.class));
            assertTrue(declared.getMessage().contains("no host"), declared.getMessage());
        }
    }
}
