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
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PythonJavaBasesTest {

    @Test
    void nestedConstructionsAreAnsweredByClass() {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value outer = context.eval(PYTHON, "object()");
            Value inner = context.eval(PYTHON, "object()");

            PythonJavaBases.Construction outerConstruction = PythonJavaBases.constructing(outer, Outer.class);
            assertSame(outer, PythonJavaBases.underConstruction(Outer.class));
            assertNull(PythonJavaBases.underConstruction(Inner.class));

            // the super constructor of the outer instance creates the Java instance of another Python object
            PythonJavaBases.Construction innerConstruction = PythonJavaBases.constructing(inner, Inner.class);
            assertSame(inner, PythonJavaBases.underConstruction(Inner.class));
            assertSame(outer, PythonJavaBases.underConstruction(Outer.class), "a bridge on the outer instance still reaches the outer object");

            innerConstruction.finished();
            assertNull(PythonJavaBases.underConstruction(Inner.class));
            assertSame(outer, PythonJavaBases.underConstruction(Outer.class));

            outerConstruction.finished();
            assertNull(PythonJavaBases.underConstruction(Outer.class));
        }
    }

    @Test
    void aNestedConstructionAbandonedByAThrowingSuperConstructorIsPoppedWithTheEnclosingOne() {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value outer = context.eval(PYTHON, "object()");
            Value abandoned = context.eval(PYTHON, "object()");

            PythonJavaBases.Construction outerConstruction = PythonJavaBases.constructing(outer, Outer.class);
            // the super constructor of the outer instance starts an inner construction whose super constructor throws
            PythonJavaBases.constructing(abandoned, Inner.class);
            assertSame(outer, PythonJavaBases.underConstruction(Outer.class));

            outerConstruction.finished();
            assertNull(PythonJavaBases.underConstruction(Inner.class));
            assertNull(PythonJavaBases.underConstruction(Outer.class));
        }
    }

    private static final class Outer {
    }

    private static final class Inner {
    }
}
