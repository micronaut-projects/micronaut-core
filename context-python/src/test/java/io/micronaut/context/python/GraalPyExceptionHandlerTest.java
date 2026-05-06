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
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import static io.micronaut.context.python.GraalPyRuntimeUtil.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GraalPyExceptionHandlerTest {

    @Test
    void hostRuntimeExceptionsAreRethrownAcrossPythonBoundary() {
        try (Engine engine = GraalPyEngineFactory.buildPythonEngine();
             Context context = Context.newBuilder(PYTHON)
                 .allowAllAccess(true)
                 .engine(engine)
                 .build()) {
            context.getBindings(PYTHON).putMember("thrower", new Thrower());
            Value callback = context.eval(PYTHON, "lambda: thrower.throwRuntime()");

            IllegalStateException exception = assertThrows(IllegalStateException.class, callback::execute);

            assertEquals("boom", exception.getMessage());
        }
    }

    public static final class Thrower {
        public void throwRuntime() {
            throw new IllegalStateException("boom");
        }
    }
}
