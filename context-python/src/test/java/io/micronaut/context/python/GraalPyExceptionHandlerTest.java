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

import java.util.Arrays;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void hostRuntimeExceptionsAreRethrownByContextHandler() {
        try (Engine engine = Engine.newBuilder()
                 .exceptionHandler(GraalPyExceptionHandler.RETHROW_HOST_RUNTIME_EXCEPTION)
                 .build();
             Context context = Context.newBuilder(PYTHON)
                 .allowAllAccess(true)
                 .engine(engine)
                 .exceptionHandler(GraalPyExceptionHandler.RETHROW_HOST_RUNTIME_EXCEPTION)
                 .build()) {
            context.getBindings(PYTHON).putMember("thrower", new Thrower());
            Value callback = context.eval(PYTHON, "lambda: thrower.throwRuntime()");

            IllegalStateException exception = assertThrows(IllegalStateException.class, callback::execute);

            assertEquals("boom", exception.getMessage());
        }
    }

    @Test
    void guestRuntimeExceptionsAreRethrownAsGeneratedWrappers() {
        try (Engine engine = Engine.newBuilder()
                 .exceptionHandler(GraalPyExceptionHandler.RETHROW_HOST_RUNTIME_EXCEPTION)
                 .build();
             Context context = Context.newBuilder(PYTHON)
                 .allowAllAccess(true)
                 .engine(engine)
                 .exceptionHandler(GraalPyExceptionHandler.RETHROW_HOST_RUNTIME_EXCEPTION)
                 .build()) {
            context.getBindings(PYTHON).putMember("pythonBoomClass", PythonBoom.class.getName());
            context.eval(PYTHON, """
                import java
                RuntimeException = java.type("java.lang.RuntimeException")

                class PythonBoom(RuntimeException):
                    pass

                PythonBoom.__module__ = pythonBoomClass

                def raise_boom():
                    raise PythonBoom()
                """);
            Value callback = context.getBindings(PYTHON).getMember("raise_boom");

            PythonBoom exception = assertThrows(PythonBoom.class, callback::execute);

            assertEquals("PythonBoom", exception.asPolyglotValue().getMetaObject().getMetaSimpleName());
        }
    }

    @Test
    void javaExceptionsCanBeCaughtAsPythonBaseException() {
        try (Context context = Context.newBuilder(PYTHON)
                 .allowAllAccess(true)
                 .build()) {
            context.getBindings(PYTHON).putMember("thrower", new Thrower());
            context.eval(PYTHON, """
                def catch_broad():
                    try:
                        thrower.throwRuntime()
                    except BaseException as e:
                        return e.getMessage()
                """);
            Value callback = context.getBindings(PYTHON).getMember("catch_broad");

            assertEquals("boom", callback.execute().asString());
        }
    }

    @Test
    void javaExceptionsCanBeCaughtAsJavaExceptionTypes() {
        try (Engine engine = GraalPyEngineFactory.buildPythonEngine();
             Context context = Context.newBuilder(PYTHON)
                 .allowAllAccess(true)
                 .engine(engine)
                 .exceptionHandler(GraalPyExceptionHandler.RETHROW_HOST_RUNTIME_EXCEPTION)
                 .build()) {
            context.getBindings(PYTHON).putMember("thrower", new Thrower());
            context.eval(PYTHON, """
                import java
                IllegalStateException = java.type("java.lang.IllegalStateException")

                def catch_exact():
                    try:
                        thrower.throwRuntime()
                    except IllegalStateException as e:
                        return e.getMessage()
                """);
            Value callback = context.getBindings(PYTHON).getMember("catch_exact");

            assertEquals("boom", callback.execute().asString());
        }
    }

    /**
     * A Java exception that Python creates and raises crosses back to Java with Truffle bookkeeping in
     * its suppressed exceptions: {@code HostException.wrap} shares the lazy guest stack trace with the
     * host throwable by attaching a {@code TruffleStackTrace.LazyStackTrace} to it. That entry is not
     * an exception the application suppressed, and a serializer rendering the suppressed exceptions of
     * an error response fails on it, for example the problem+json body provider with
     * {@code No serializable introspection present for type LazyStackTrace}.
     */
    @Test
    void truffleBookkeepingIsNotReportedAmongTheSuppressedExceptions() {
        try (Engine engine = GraalPyEngineFactory.buildPythonEngine();
             Context context = Context.newBuilder(PYTHON)
                 .allowAllAccess(true)
                 .engine(engine)
                 .exceptionHandler(GraalPyExceptionHandler.RETHROW_HOST_RUNTIME_EXCEPTION)
                 .build()) {
            context.eval(PYTHON, """
                import java
                IllegalStateException = java.type("java.lang.IllegalStateException")

                def raise_java():
                    raise IllegalStateException("boom")
                """);
            Value callback = context.getBindings(PYTHON).getMember("raise_java");

            IllegalStateException exception = assertThrows(IllegalStateException.class, callback::execute);

            assertEquals("boom", exception.getMessage());
            assertEquals(1, exception.getSuppressed().length,
                "Truffle attaches its guest stack trace to the host throwable and it cannot be taken off again");
            assertTrue(PythonExceptions.isTruffleStackTrace(exception.getSuppressed()[0]),
                "the attached entry: " + Arrays.toString(exception.getSuppressed()));
            assertEquals(0, PythonExceptions.suppressed(exception).length,
                "reported suppressed exceptions: " + Arrays.toString(PythonExceptions.suppressed(exception)));
        }
    }

    /**
     * The exceptions the application itself suppressed are not lost when the throwable crosses Python.
     */
    @Test
    void genuineSuppressedExceptionsSurviveTheCrossing() {
        try (Engine engine = GraalPyEngineFactory.buildPythonEngine();
             Context context = Context.newBuilder(PYTHON)
                 .allowAllAccess(true)
                 .engine(engine)
                 .exceptionHandler(GraalPyExceptionHandler.RETHROW_HOST_RUNTIME_EXCEPTION)
                 .build()) {
            context.getBindings(PYTHON).putMember("thrower", new Thrower());
            Value callback = context.eval(PYTHON, "lambda: thrower.throwWithSuppressed()");

            IllegalStateException exception = assertThrows(IllegalStateException.class, callback::execute);

            assertEquals("boom", exception.getMessage());
            Throwable[] suppressed = PythonExceptions.suppressed(exception);
            assertEquals(1, suppressed.length, "reported suppressed exceptions: " + Arrays.toString(suppressed));
            assertEquals("closed badly", suppressed[0].getMessage());
        }
    }

    public static final class Thrower {
        public void throwRuntime() {
            throw new IllegalStateException("boom");
        }

        public void throwWithSuppressed() {
            IllegalStateException exception = new IllegalStateException("boom");
            exception.addSuppressed(new IllegalArgumentException("closed badly"));
            throw exception;
        }
    }

    public static final class PythonBoom extends RuntimeException implements ValueCoercible {
        private final Value value;

        public PythonBoom(Value value) {
            this.value = value;
        }

        @Override
        public Value asPolyglotValue() {
            return value;
        }
    }
}
