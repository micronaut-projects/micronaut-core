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

import java.util.List;

import static io.micronaut.context.python.GraalPyRuntimeUtil.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ValueCoercibleProxyObjectTest {

    @Test
    void valueCoercibleExposesUnderlyingPythonMembersAndHostMethodsToPython() {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value message = context.eval(PYTHON, """
                class Message:
                    def __init__(self, text):
                        self.text = text

                    def greet(self, name):
                        return self.text + " " + name
                Message("Hello")
                """);
            context.getBindings(PYTHON).putMember("message", new PythonMessage(message));

            assertEquals("Hello", context.eval(PYTHON, "message.text").asString());
            assertEquals("Hello John", context.eval(PYTHON, "message.greet('John')").asString());
            assertEquals("Hello", context.eval(PYTHON, "message.asPolyglotValue().text").asString());
            assertEquals("Message", context.eval(PYTHON, "message.asPolyglotValue().__class__.__name__").asString());

            context.eval(PYTHON, "message.text = 'Hi'");
            assertEquals("Hi", message.getMember("text").asString());
        }
    }

    @Test
    void valueCoercibleFallsBackToJavaMethodsForGeneratedAccessors() {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value pointValue = context.eval(PYTHON, "object()");
            context.getBindings(PYTHON).putMember("point", new PythonPoint(pointValue));

            assertEquals(10, context.eval(PYTHON, "point.getX()").asInt());
            assertEquals(20, context.eval(PYTHON, "point.getY()").asInt());
        }
    }

    @Test
    void valueCoercibleCanBePassedBackToJavaAsThrowable() {
        try (Context context = Context.newBuilder(PYTHON)
            .allowHostAccess(new GraalPyHostAccessFactory().hostAccess(List.of()))
            .build()) {
            Value exceptionValue = context.eval(PYTHON, "object()");
            context.getBindings(PYTHON).putMember("exception", new PythonException(exceptionValue));
            context.getBindings(PYTHON).putMember("acceptor", new ThrowableAcceptor());

            assertEquals("boom", context.eval(PYTHON, "acceptor.message(exception)").asString());
        }
    }

    private record PythonMessage(Value value) implements ValueCoercible {
        @Override
        public Value asPolyglotValue() {
            return value;
        }
    }

    public record PythonPoint(Value value) implements ValueCoercible {
        @Override
        public Value asPolyglotValue() {
            return value;
        }

        public int getX() {
            return 10;
        }

        public int getY() {
            return 20;
        }
    }

    public static final class PythonException extends RuntimeException implements ValueCoercible {
        private final Value value;

        PythonException(Value value) {
            super("boom");
            this.value = value;
        }

        @Override
        public Value asPolyglotValue() {
            return value;
        }
    }

    public static final class ThrowableAcceptor {
        public String message(Throwable throwable) {
            return throwable.getMessage();
        }
    }
}
