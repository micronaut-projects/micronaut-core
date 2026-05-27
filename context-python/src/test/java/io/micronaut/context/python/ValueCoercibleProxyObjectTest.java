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
import org.dataloader.MappedBatchLoader;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static io.micronaut.context.python.GraalPyRuntimeUtil.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

    @Test
    void valueCoercibleCanBePassedBackToJavaAsGeneratedInterface() {
        try (Context context = Context.newBuilder(PYTHON)
            .allowHostAccess(new GraalPyHostAccessFactory().hostAccess(List.of(new PythonMessageMapping())))
            .allowHostClassLookup(name -> true)
            .build()) {
            Value messageValue = context.eval(PYTHON, """
                class PythonMessage:
                    def __init__(self, text):
                        self.text = text
                PythonMessage("Hello")
                """);
            context.getBindings(PYTHON).putMember("message", new PythonMessage(messageValue));
            context.getBindings(PYTHON).putMember("acceptor", new MessageAcceptor());

            assertEquals("Hello John", context.eval(PYTHON, "acceptor.greet(message)").asString());
        }
    }

    @Test
    void pythonValueCanBePassedBackToJavaAsGeneratedInterface() {
        try (Context context = Context.newBuilder(PYTHON)
            .allowHostAccess(new GraalPyHostAccessFactory().hostAccess(List.of(new PythonMessageMapping())))
            .build()) {
            context.getBindings(PYTHON).putMember("acceptor", new MessageAcceptor());

            assertEquals("Hello John", context.eval(PYTHON, """
                class PythonMessage:
                    def __init__(self, text):
                        self.text = text
                acceptor.greet(PythonMessage("Hello"))
                """).asString());
        }
    }

    @Test
    void valueCoercibleCanBePassedBackToOverloadedStaticJavaMethodAsGeneratedInterface() {
        try (Context context = Context.newBuilder(PYTHON)
            .allowHostAccess(new GraalPyHostAccessFactory().hostAccess(List.of(new PythonMessageMapping())))
            .allowHostClassLookup(name -> true)
            .build()) {
            Value messageValue = context.eval(PYTHON, """
                class PythonMessage:
                    def __init__(self, text):
                        self.text = text
                PythonMessage("Hello")
                """);
            context.getBindings(PYTHON).putMember("message", new PythonMessage(messageValue));
            assertEquals("Hello John", context.eval(PYTHON, """
                import java
                StaticMessageAcceptor = java.type('io.micronaut.context.python.ValueCoercibleProxyObjectTest$StaticMessageAcceptor')
                StaticMessageAcceptor.greet(message)
                """).asString());
        }
    }

    @Test
    void pythonValueCanBePassedBackToOverloadedStaticJavaMethodAsGeneratedInterface() {
        try (Context context = Context.newBuilder(PYTHON)
            .allowHostAccess(new GraalPyHostAccessFactory().hostAccess(List.of(new PythonMessageMapping())))
            .allowHostClassLookup(name -> true)
            .build()) {
            assertEquals("Hello John", context.eval(PYTHON, """
                import java
                StaticMessageAcceptor = java.type('io.micronaut.context.python.ValueCoercibleProxyObjectTest$StaticMessageAcceptor')

                class PythonMessage:
                    def __init__(self, text):
                        self.text = text

                StaticMessageAcceptor.greet(PythonMessage("Hello"))
                """).asString());
        }
    }

    @Test
    void pythonValueWithMatchingPythonMethodCanBePassedBackToOverloadedStaticJavaMethodAsGeneratedInterface() {
        try (Context context = Context.newBuilder(PYTHON)
            .allowHostAccess(new GraalPyHostAccessFactory().hostAccess(List.of(new PythonMessageMapping())))
            .allowHostClassLookup(name -> true)
            .build()) {
            assertEquals("Hello John", context.eval(PYTHON, """
                import java
                StaticMessageAcceptor = java.type('io.micronaut.context.python.ValueCoercibleProxyObjectTest$StaticMessageAcceptor')

                class PythonMessage:
                    def __init__(self, text):
                        self.text = text

                    def greet(self, name):
                        return self.text + " " + name

                StaticMessageAcceptor.greet(PythonMessage("Hello"))
                """).asString());
        }
    }

    @Test
    void pythonValueCanBePassedBackToOverloadedStaticJavaMethodWhenFunctionalInterfacesShareMethodName() {
        try (Context context = Context.newBuilder(PYTHON)
            .allowHostAccess(new GraalPyHostAccessFactory().hostAccess(List.of(new PythonMessageMapping())))
            .allowHostClassLookup(name -> true)
            .build()) {
            assertEquals("Hello John", context.eval(PYTHON, """
                import java
                SharedNameStaticMessageAcceptor = java.type('io.micronaut.context.python.ValueCoercibleProxyObjectTest$SharedNameStaticMessageAcceptor')

                class PythonMessage:
                    def __init__(self, text):
                        self.text = text

                    def greet(self, name):
                        return self.text + " " + name

                SharedNameStaticMessageAcceptor.greet(PythonMessage("Hello"))
                """).asString());
        }
    }

    @Test
    void valueCoercibleCanBePassedToJavaDataloaderMappedBatchLoaderFactory() {
        try (Context context = Context.newBuilder(PYTHON)
            .allowHostAccess(new GraalPyHostAccessFactory().hostAccess(List.of(new PythonMappedBatchLoaderMapping())))
            .allowHostClassLookup(name -> true)
            .build()) {
            Value loaderValue = context.eval(PYTHON, """
                class PythonMappedBatchLoader:
                    def load(self, keys):
                        return None
                PythonMappedBatchLoader()
                """);
            context.getBindings(PYTHON).putMember("batch_loader", new PythonMappedBatchLoader(loaderValue));

            Value result = context.eval(PYTHON, """
                import java
                DataLoaderFactory = java.type('org.dataloader.DataLoaderFactory')

                DataLoaderFactory.newMappedDataLoader(batch_loader)
                """);

            assertNotNull(result.asHostObject());
        }
    }

    @Test
    void valueCoercibleCanBePassedThroughPythonMethodToJavaDataloaderMappedBatchLoaderFactory() {
        try (Context context = Context.newBuilder(PYTHON)
            .allowHostAccess(new GraalPyHostAccessFactory().hostAccess(List.of(new PythonMappedBatchLoaderMapping())))
            .allowHostClassLookup(name -> true)
            .build()) {
            Value factory = context.eval(PYTHON, """
                import java
                DataLoaderFactory = java.type('org.dataloader.DataLoaderFactory')

                class DataLoaderRegistryFactory:
                    def data_loader_registry(self, batch_loader):
                        return DataLoaderFactory.newMappedDataLoader(batch_loader)

                DataLoaderRegistryFactory()
                """);
            Value loaderValue = context.eval(PYTHON, """
                class PythonMappedBatchLoader:
                    def load(self, keys):
                        return None
                PythonMappedBatchLoader()
                """);
            PythonMappedBatchLoader batchLoader = new PythonMappedBatchLoader(loaderValue);

            Value result = GraalPyRuntimeUtil.invokePythonMethod(
                factory,
                "data_loader_registry",
                new Object[] {GraalPyRuntimeUtil.coerceToContext(batchLoader, factory.getContext())}
            );

            assertNotNull(result.asHostObject());
        }
    }

    @Test
    void pythonValueCanBePassedToJavaDataloaderMappedBatchLoaderFactory() {
        try (Context context = Context.newBuilder(PYTHON)
            .allowHostAccess(new GraalPyHostAccessFactory().hostAccess(List.of(new PythonMappedBatchLoaderMapping())))
            .allowHostClassLookup(name -> true)
            .build()) {
            Value result = context.eval(PYTHON, """
                import java
                DataLoaderFactory = java.type('org.dataloader.DataLoaderFactory')

                class PythonMappedBatchLoader:
                    def load(self, keys):
                        return None

                DataLoaderFactory.newMappedDataLoader(PythonMappedBatchLoader())
                """);

            assertNotNull(result.asHostObject());
        }
    }

    @Test
    void bootstrapHostAccessLoadsTargetMappingsForReusableContexts() {
        try (Context context = Context.newBuilder(PYTHON)
            .allowHostAccess(GraalPyContextFactory.bootstrapHostAccess(ValueCoercibleProxyObjectTest.class.getClassLoader()))
            .allowHostClassLookup(name -> true)
            .build()) {
            Value loaderValue = context.eval(PYTHON, """
                class PythonMappedBatchLoader:
                    def load(self, keys):
                        return None
                PythonMappedBatchLoader()
                """);
            context.getBindings(PYTHON).putMember("batch_loader", new PythonMappedBatchLoader(loaderValue));

            Value result = context.eval(PYTHON, """
                import java
                DataLoaderFactory = java.type('org.dataloader.DataLoaderFactory')

                DataLoaderFactory.newMappedDataLoader(batch_loader)
                """);

            assertNotNull(result.asHostObject());
        }
    }

    private record PythonMessage(Value value) implements ValueCoercible, MessageGreeter {
        @Override
        public Value asPolyglotValue() {
            return value;
        }

        @Override
        public String greet(String name) {
            return value.getMember("text").asString() + " " + name;
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

    public interface MessageGreeter {
        String greet(String name);
    }

    public interface OtherMessageGreeter {
        String greetOther(String name);
    }

    public interface ContextMessageGreeter {
        String greet(String name, String context);
    }

    public record PythonMessageMapping() implements TargetTypeMapping<PythonMessage> {
        @Override
        public Class<PythonMessage> targetType() {
            return PythonMessage.class;
        }

        @Override
        public Class<?>[] assignableTargetTypes() {
            return new Class<?>[] {MessageGreeter.class};
        }

        @Override
        public PythonMessage convert(Value value) {
            return new PythonMessage(value);
        }
    }

    public static final class MessageAcceptor {
        public String greet(MessageGreeter greeter) {
            return greeter.greet("John");
        }
    }

    public static final class StaticMessageAcceptor {
        public static String greet(MessageGreeter greeter) {
            return greeter.greet("John");
        }

        public static String greet(OtherMessageGreeter greeter) {
            return greeter.greetOther("John");
        }
    }

    public static final class SharedNameStaticMessageAcceptor {
        public static String greet(MessageGreeter greeter) {
            return greeter.greet("John");
        }

        public static String greet(ContextMessageGreeter greeter) {
            return greeter.greet("John", "context");
        }
    }

    public record PythonMappedBatchLoader(Value value) implements ValueCoercible, MappedBatchLoader<Integer, String> {
        @Override
        public Value asPolyglotValue() {
            return value;
        }

        @Override
        public CompletionStage<Map<Integer, String>> load(Set<Integer> keys) {
            Map<Integer, String> result = new HashMap<>();
            for (Integer key : keys) {
                result.put(key, "value-" + key);
            }
            return CompletableFuture.completedFuture(result);
        }
    }

    public record PythonMappedBatchLoaderMapping() implements TargetTypeMapping<PythonMappedBatchLoader> {
        @Override
        public Class<PythonMappedBatchLoader> targetType() {
            return PythonMappedBatchLoader.class;
        }

        @Override
        public Class<?>[] assignableTargetTypes() {
            return new Class<?>[] {MappedBatchLoader.class};
        }

        @Override
        public PythonMappedBatchLoader convert(Value value) {
            return new PythonMappedBatchLoader(value);
        }
    }
}
