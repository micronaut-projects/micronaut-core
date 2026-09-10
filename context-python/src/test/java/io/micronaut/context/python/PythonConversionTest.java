/*
 * Copyright 2017-2025 original authors
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import io.micronaut.http.HttpResponse;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.context.ApplicationContext;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Tests for PythonConversion to verify generic type handling.
 *
 * @author Micronaut Team
 * @since 5.2.0
 */
class PythonConversionTest {

    private Context context;

    @BeforeEach
    void setUp() {
        // Create a GraalPy context for testing
        context = Context.newBuilder("python")
            .allowAllAccess(true)
            .build();
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void testConvertListWithIntegers() {
        // Create a Python list with integers
        Value pythonList = context.eval("python", "[1, 2, 3, 4, 5]");

        // Test conversion with Integer type
        List<Integer> result = PythonConversion.convertList(pythonList, Integer.class);

        assertNotNull(result);
        assertEquals(5, result.size());
        assertEquals(List.of(1, 2, 3, 4, 5), result);
    }

    @Test
    void testConvertListWithStrings() {
        // Create a Python list with strings
        Value pythonList = context.eval("python", "['hello', 'world', 'test']");

        // Test conversion with String type
        List<String> result = PythonConversion.convertList(pythonList, String.class);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(List.of("hello", "world", "test"), result);
    }

    @Test
    void convertsPublisherValuesWithGeneratedElementConverter() {
        Value pythonValue = context.eval("python", "'hello'");

        List<String> result = new java.util.ArrayList<>();
        PythonHttpConversion.convertPublisher(Publishers.just(pythonValue), Value::asString)
            .subscribe(new Subscriber<>() {
                @Override
                public void onSubscribe(Subscription subscription) {
                    subscription.request(1);
                }

                @Override
                public void onNext(String value) {
                    result.add(value);
                }

                @Override
                public void onError(Throwable throwable) {
                    throw new AssertionError(throwable);
                }

                @Override
                public void onComplete() {
                    // the result is asserted once the synchronous publisher has completed
                }
            });

        assertEquals(List.of("hello"), result);
    }

    @Test
    void testConvertPythonEnumValueToJavaEnum() {
        Value language = context.eval("python", """
            from enum import Enum

            class Language(Enum):
                GROOVY = "groovy"
                JAVA = "java"
                KOTLIN = "kotlin"

            Language.JAVA
            """);

        assertEquals(TestLanguage.JAVA, PythonConversion.convertValue(language, TestLanguage.class));
    }

    @Test
    void testConvertPythonStringToJavaEnum() {
        Value language = context.eval("python", "'KOTLIN'");

        assertEquals(TestLanguage.KOTLIN, PythonConversion.convertValue(language, TestLanguage.class));
    }

    @Test
    void convertsPythonStandardLibraryValuesThroughHostAccess() {
        try (Context mappedContext = Context.newBuilder("python")
            .allowAllAccess(true)
            .allowHostAccess(new GraalPyHostAccessFactory().hostAccess(List.of()))
            .build()) {
            assertEquals(LocalDate.of(2026, 7, 21), mappedContext.eval("python", "__import__('datetime').date(2026, 7, 21)").as(LocalDate.class));
            assertEquals(LocalTime.of(12, 34, 56, 123_000_000), mappedContext.eval("python", "__import__('datetime').time(12, 34, 56, 123000)").as(LocalTime.class));
            assertEquals(LocalDateTime.of(2026, 7, 21, 12, 34, 56, 123_000_000), mappedContext.eval("python", "__import__('datetime').datetime(2026, 7, 21, 12, 34, 56, 123000)").as(LocalDateTime.class));
            assertEquals(Duration.ofSeconds(-1, 999_999_000), mappedContext.eval("python", "__import__('datetime').timedelta(microseconds=-1)").as(Duration.class));
            assertEquals(ZoneOffset.ofHoursMinutes(5, 30), mappedContext.eval("python", "__import__('datetime').timezone(__import__('datetime').timedelta(hours=5, minutes=30))").as(ZoneOffset.class));
            assertEquals(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), mappedContext.eval("python", "__import__('uuid').UUID('123e4567-e89b-12d3-a456-426614174000')").as(UUID.class));
            Value awareTime = mappedContext.eval("python", "__import__('datetime').time(12, tzinfo=__import__('datetime').timezone.utc)");
            assertThrows(RuntimeException.class, () -> awareTime.as(LocalTime.class));
            Value customZone = mappedContext.eval("python", "type('CustomZone', (__import__('datetime').tzinfo,), {'utcoffset': lambda self, value: __import__('datetime').timedelta(hours=1)})()");
            assertThrows(RuntimeException.class, () -> customZone.as(ZoneOffset.class));
        }
    }

    @Test
    void convertsMicronautJavaTypeFacadeToClassArgument() {
        try (Context mappedContext = Context.newBuilder("python")
            .allowAllAccess(true)
            .allowHostAccess(new GraalPyHostAccessFactory().hostAccess(List.of()))
            .build()) {
            Value result = mappedContext.eval("python", """
                import java

                class _MicronautJavaType:
                    def __init__(self, target, interface=False):
                        self._target = target
                        self._interface = interface

                    def _resolved(self):
                        if isinstance(self._target, str):
                            self._target = java.type(self._target)
                        return self._target

                    def __getattr__(self, name):
                        return getattr(self._resolved(), name)

                    def __call__(self, *args, **kwargs):
                        return self._resolved()(*args, **kwargs)

                ClassAcceptor = java.type("io.micronaut.context.python.PythonConversionTest$ClassAcceptor")
                ClassAcceptor.name(_MicronautJavaType(java.type("java.lang.String"), True))
                """);

            assertEquals("java.lang.String", result.asString());
        }
    }

    @Test
    void passesMicronautJavaTypeFacadeToApplicationContextFindBean() {
        try (ApplicationContext applicationContext = ApplicationContext.run();
             Context mappedContext = Context.newBuilder("python")
                 .allowAllAccess(true)
                 .allowHostAccess(new GraalPyHostAccessFactory().hostAccess(List.of()))
                 .build()) {
            mappedContext.getBindings("python").putMember("applicationContext", applicationContext);

            assertEquals(false, mappedContext.eval("python", """
                import java

                class _MicronautJavaType:
                    def __init__(self, target, interface=False):
                        self._target = target
                        self._interface = interface

                    def _resolved(self):
                        if isinstance(self._target, str):
                            self._target = java.type(self._target)
                        return self._target

                    def __getattr__(self, name):
                        return getattr(self._resolved(), name)

                applicationContext.findBean(_MicronautJavaType("java.lang.String", True)).isPresent()
                """).asBoolean());
        }
    }

    @Test
    void testInvokePythonMethodBindsClassDescriptorWhenAttributeShadowsMethod() {
        Value instance = context.eval("python", """
            class Example:
                def __init__(self):
                    self.currentDate = "field"
                    self.className = "field"
                    self.staticName = "field"

                def currentDate(self):
                    return "method:" + self.currentDate

                @classmethod
                def className(cls):
                    return cls.__name__

                @staticmethod
                def staticName():
                    return "static"

            Example()
            """);

        assertEquals("field", instance.getMember("currentDate").asString());
        assertEquals(
            "method:field",
            PythonInvocation.invokePythonMethod(instance, "currentDate", new Object[0]).asString()
        );
        assertEquals(
            "Example",
            PythonInvocation.invokePythonMethod(instance, "className", new Object[0]).asString()
        );
        assertEquals(
            "static",
            PythonInvocation.invokePythonMethod(instance, "staticName", new Object[0]).asString()
        );
    }

    @Test
    void testInvokePythonMethodUsesPythonOverrideOfJavaDefaultMethod() {
        Value instance = context.eval("python", """
            import java

            AsyncSender = java.type("io.micronaut.context.python.PythonConversionTest$AsyncSender")

            class Sender(AsyncSender):
                def sendAsync(self, email):
                    return "python:" + email

            Sender()
            """);

        assertEquals(
            "python:hello",
            PythonInvocation.invokePythonMethod(instance, "sendAsync", new Object[] {"hello"}).asString()
        );
    }

    @Test
    @Disabled("not yet implemented")
    void testConvertListWithMixedTypes() {
        // Create a Python list with mixed types
        Value pythonList = context.eval("python", "[1, 'hello', 2.5]");

        // Test conversion with Object type (should handle mixed types)
        List<Object> result = PythonConversion.convertList(pythonList, Object.class);

        assertNotNull(result);
        assertEquals(3, result.size());
        // The exact conversion depends on the convertValue implementation
        assertNotNull(result.get(0));
        assertNotNull(result.get(1));
        assertNotNull(result.get(2));
    }

    @Test
    void testConvertListEmpty() {
        // Create an empty Python list
        Value pythonList = context.eval("python", "[]");

        // Test conversion with Integer type
        List<Integer> result = PythonConversion.convertList(pythonList, Integer.class);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testConvertListNull() {
        // Test conversion with null value
        List<Integer> result = PythonConversion.convertList(null, Integer.class);

        assertNull(result);
    }

    @Test
    void testCoerceListNull() {
        assertNull(PythonCoercion.coerceList(null));
    }

    @Test
    void testCoerceMapNull() {
        assertNull(PythonCoercion.coerceMap(null));
    }

    @Test
    void testConvertListPythonNone() {
        // Test conversion with Python None value
        Value none = context.eval("python", "lambda: None").execute();
        List<Integer> result = PythonConversion.convertList(none, Integer.class);

        assertNull(result);
    }

    @Test
    void testConvertMapWithStringKeysAndIntegerValues() {
        // Create a Python dict with string keys and integer values
        Value pythonDict = context.eval("python", "{'a': 1, 'b': 2, 'c': 3}");

        // Test conversion with String keys and Integer values
        Map<String, Integer> result = getValue(pythonDict);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(1, result.get("a"));
        assertEquals(2, result.get("b"));
        assertEquals(3, result.get("c"));
    }

    Map<String, Integer> getValue(Value pythonDict) {
        return PythonConversion.convertMap(pythonDict, String.class, Integer.class);
    }

    @Test
    void testConvertMapWithIntegerKeysAndStringValues() {
        // Create a Python dict with integer keys and string values
        Value pythonDict = context.eval("python", "{1: 'one', 2: 'two', 3: 'three'}");

        // Test conversion with Integer keys and String values
        Map<Integer, String> result = PythonConversion.convertMap(pythonDict, Integer.class, String.class);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("one", result.get(1));
        assertEquals("two", result.get(2));
        assertEquals("three", result.get(3));
    }

    @Test
    void testConvertMapEmpty() {
        // Create an empty Python dict
        Value pythonDict = context.eval("python", "{}");

        // Test conversion
        Map<String, Integer> result = PythonConversion.convertMap(pythonDict, String.class, Integer.class);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testConvertMapNull() {
        // Test conversion with null value
        Map<String, Integer> result = PythonConversion.convertMap(null, String.class, Integer.class);

        assertNull(result);
    }

    @Test
    void testConvertMapPythonNone() {
        // Test conversion with Python None value
        Map<String, Integer> result = PythonConversion.convertMap(context.eval("python", "lambda: None").execute(), String.class, Integer.class);

        assertNull(result);
    }

    @Test
    void testDeclaredTypeCoercionPreservesMapImplementingHostObject() {
        HostModel model = new HostModel();
        model.put("existing", "value");

        Object result = PythonCoercion.coerceToContext(model, context, HostModelInterface.class);

        assertSame(model, result);
    }

    @Test
    void testDeclaredTypeCoercionPreservesConcreteMapImplementingHostObject() {
        HostModel model = new HostModel();
        model.put("existing", "value");

        Object result = PythonCoercion.coerceToContext(model, context, HostModel.class);

        assertSame(model, result);
    }

    @Test
    void testDeclaredTypeCoercionPreservesObjectDeclaredMapImplementingHostObject() {
        HostModel model = new HostModel();
        model.put("existing", "value");

        Object result = PythonCoercion.coerceToContext(model, context, Object.class);

        assertSame(model, result);
    }

    @Test
    void testDeclaredTypeCoercionCopiesDeclaredMap() {
        HostModel model = new HostModel();
        model.put("existing", "value");

        Object result = PythonCoercion.coerceToContext(model, context, Map.class);

        assertTrue(result instanceof Map);
        assertEquals(Map.of("existing", "value"), result);
    }

    @Test
    void coerceToContextUsesPooledConversionForTargetContext() {
        Value targetValue = context.eval("python", "{'context': 'target'}");
        AtomicInteger noArgumentConversions = new AtomicInteger();
        AtomicInteger targetConversions = new AtomicInteger();
        PooledValueCoercible body = new PooledValueCoercible() {
            @Override
            public Value asPolyglotValue() {
                noArgumentConversions.incrementAndGet();
                throw new AssertionError("The primary-context conversion must not be used");
            }

            @Override
            public Value asPolyglotValue(Context targetContext) {
                assertSame(context, targetContext);
                targetConversions.incrementAndGet();
                return targetValue;
            }
        };

        assertSame(targetValue, PythonCoercion.coerceToContext(body, context));

        Object nested = PythonCoercion.coerceToContext(
            List.of(Map.of("bodies", new Object[] {body})),
            context
        );
        List<?> nestedList = (List<?>) nested;
        Map<?, ?> nestedMap = (Map<?, ?>) nestedList.getFirst();
        assertArrayEquals(new Object[] {targetValue}, (Object[]) nestedMap.get("bodies"));
        assertArrayEquals(
            new Object[] {1, 2, 3},
            (Object[]) PythonCoercion.coerceToContext(new int[] {1, 2, 3}, context, int[].class)
        );

        assertEquals(2, targetConversions.get());
        assertEquals(0, noArgumentConversions.get());
    }

    @Test
    void putMemberDefersPooledWrapperConversionToTargetContext() {
        try (Context targetContext = Context.newBuilder("python").allowAllAccess(true).build()) {
            Value target = targetContext.eval("python", "type('Parent', (), {})()");
            AtomicInteger noArgumentConversions = new AtomicInteger();
            AtomicInteger targetConversions = new AtomicInteger();
            PooledValueCoercible child = new PooledValueCoercible() {
                @Override
                public Value asPolyglotValue() {
                    noArgumentConversions.incrementAndGet();
                    return context.eval("python", "type('WrongContext', (), {})()");
                }

                @Override
                public Value asPolyglotValue(Context context) {
                    assertEquals(targetContext, context);
                    targetConversions.incrementAndGet();
                    return context.eval("python", "type('Child', (), {'name': 'target'})()");
                }
            };

            PythonCoercion.putMember(target, "child", PythonCoercion.coerceValue(child));

            assertEquals("target", target.getMember("child").getMember("name").asString());
            assertEquals(1, targetConversions.get());
            assertEquals(0, noArgumentConversions.get());
        }
    }

    @Test
    void coerceArgumentsPreservesPooledWrapperIdentity() {
        Value targetValue = context.eval("python", "type('Body', (), {})()");
        AtomicInteger reconstructions = new AtomicInteger();
        PooledValueCoercible body = new PooledValueCoercible() {
            @Override
            public Value asPolyglotValue() {
                throw new AssertionError("The primary-context conversion must not be used");
            }

            @Override
            public Value asPolyglotValue(Context targetContext) {
                return PythonCoercion.coercePooledValue(this, targetContext);
            }

            @Override
            public Value reconstructPolyglotValue(Context targetContext) {
                assertSame(context, targetContext);
                reconstructions.incrementAndGet();
                return targetValue;
            }
        };

        Object[] converted = PythonCoercion.coerceArgumentsToContext(context, new Object[] {body, body});

        assertSame(converted[0], converted[1]);
        assertEquals(1, reconstructions.get());
    }

    @Test
    void interopPrimitiveArgumentsUseFastPath() {
        Object[] arguments = {null, 1, true, "value"};

        assertSame(arguments, PythonCoercion.coerceArgumentsToContext(context, arguments));
        assertSame(arguments[3], PythonCoercion.coerceToContext(arguments[3], context));
        assertSame(arguments[3], PythonCoercion.coerceToContext(arguments[3], context, String.class));
    }

    @Test
    void pooledConversionSupportsCyclesAfterTargetAllocation() {
        PooledValueCoercible node = new PooledValueCoercible() {
            @Override
            public Value asPolyglotValue() {
                throw new AssertionError("The primary-context conversion must not be used");
            }

            @Override
            public Value asPolyglotValue(Context targetContext) {
                return PythonCoercion.coercePooledValue(this, targetContext);
            }

            @Override
            public Value reconstructPolyglotValue(Context targetContext) {
                Value target = targetContext.eval("python", "type('Node', (), {})()");
                PythonCoercion.rememberPooledValue(this, targetContext, target);
                PythonCoercion.putMember(target, "next", this);
                return target;
            }
        };

        Value converted = (Value) PythonCoercion.coerceToContext(node, context);
        context.getBindings("python").putMember("converted_node", converted);

        assertTrue(context.eval("python", "converted_node.next is converted_node").asBoolean());
    }

    @Test
    void nonReconstructibleWrapperFromAnotherContextFailsLoudly() {
        try (Context other = Context.newBuilder("python").allowAllAccess(true).build()) {
            ValueCoercible wrapper = () -> other.eval("python", "object()");

            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PythonCoercion.coerceToContext(wrapper, context)
            );

            assertTrue(exception.getMessage().contains("cannot be reconstructed in the target context"));
            Value foreignValue = other.eval("python", "object()");
            assertThrows(
                IllegalArgumentException.class,
                () -> PythonCoercion.coerceToContext(foreignValue, context)
            );
        }
    }

    @Test
    void testConvertHostOptional() {
        context.getBindings("python").putMember("optional", Optional.of("value"));

        Optional<String> result = PythonConversion.convertOptional(context.eval("python", "optional"), String.class);

        assertEquals(Optional.of("value"), result);
    }

    @Test
    void testConvertHttpResponseUnwrapsValueCoercibleProxyBody() {
        TestBody body = new TestBody("DevOps");
        ProxyObject proxyBody = new ProxyObject() {
            @Override
            public Object getMember(String key) {
                if (ValueCoercible.HOST_OBJECT_MEMBER.equals(key)) {
                    return new ValueCoercible.HostObjectReference(body);
                }
                return null;
            }

            @Override
            public Object getMemberKeys() {
                return new String[] {ValueCoercible.HOST_OBJECT_MEMBER};
            }

            @Override
            public boolean hasMember(String key) {
                return ValueCoercible.HOST_OBJECT_MEMBER.equals(key);
            }

            @Override
            public void putMember(String key, Value value) {
                // a read-only host object: writes are ignored
            }
        };

        HttpResponse<TestBody> response = PythonHttpConversion.convertHttpResponse(HttpResponse.created(proxyBody), TestBody.class);

        assertSame(body, response.body());
    }

    @Test
    void testConvertHttpResponseConvertsForeignObjectBody() {
        HostAccess hostAccess = HostAccess.newBuilder(HostAccess.ALL)
            .targetTypeMapping(
                Value.class,
                TestBody.class,
                value -> value != null && value.hasMember("name"),
                value -> new TestBody(value.getMember("name").asString())
            )
            .build();
        try (Context mappingContext = Context.newBuilder(PythonContextRuntime.PYTHON)
            .allowHostAccess(hostAccess)
            .allowHostClassLookup(className -> true)
            .build()) {
            Value responseValue = mappingContext.eval(PythonContextRuntime.PYTHON, """
                import java

                HttpResponse = java.type("io.micronaut.http.HttpResponse")

                class Body:
                    def __init__(self, name):
                        self.name = name

                HttpResponse.created(Body("DevOps"))
                """);
            HttpResponse<?> rawResponse = responseValue.as(HttpResponse.class);

            HttpResponse<TestBody> response = PythonHttpConversion.convertHttpResponse(rawResponse, TestBody.class);

            assertEquals(new TestBody("DevOps"), response.body());
        }
    }

    @Test
    void testConvertSetWithIntegers() {
        // Create a Python set with integers
        Value pythonSet = context.eval("python", "{1, 2, 3, 4, 5}");

        // Test conversion with Integer type
        Set<Integer> result = PythonConversion.convertSet(pythonSet, Integer.class);

        assertNotNull(result);
        assertEquals(5, result.size());
        assertTrue(result.contains(1));
        assertTrue(result.contains(2));
        assertTrue(result.contains(3));
        assertTrue(result.contains(4));
        assertTrue(result.contains(5));
    }

    @Test
    void testConvertSetWithStrings() {
        // Create a Python set with strings
        Value pythonSet = context.eval("python", "{'hello', 'world', 'test'}");

        // Test conversion with String type
        Set<String> result = PythonConversion.convertSet(pythonSet, String.class);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.contains("hello"));
        assertTrue(result.contains("world"));
        assertTrue(result.contains("test"));
    }

    @Test
    void convertListWalksAGeneratorOnce() {
        Value generator = context.eval("python", "(n * n for n in range(4))");

        assertEquals(List.of(0, 1, 4, 9), PythonConversion.convertList(generator, Integer.class));
    }

    @Test
    void convertListReadsSetsAndDictViewsThroughTheIterator() {
        Value pythonSet = context.eval("python", "{3}");
        Value keys = context.eval("python", "{'a': 1, 'b': 2}.keys()");

        assertEquals(List.of(3), PythonConversion.convertList(pythonSet, Integer.class));
        assertEquals(List.of("a", "b"), PythonConversion.convertList(keys, String.class));
    }

    @Test
    void convertListUsesTheSequenceProtocolWithoutIter() {
        Value sequence = context.eval("python", """
            class Digits:
                def __len__(self):
                    return 3
                def __getitem__(self, index):
                    if index >= 3:
                        raise IndexError(index)
                    return str(index)
            Digits()
            """);

        assertEquals(List.of("0", "1", "2"), PythonConversion.convertList(sequence, String.class));
    }

    @Test
    void testConvertSetEmpty() {
        // Create an empty Python set
        Value pythonSet = context.eval("python", "set()");

        // Test conversion with Integer type
        Set<Integer> result = PythonConversion.convertSet(pythonSet, Integer.class);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testConvertSetNull() {
        // Test conversion with null value
        Set<Integer> result = PythonConversion.convertSet(null, Integer.class);

        assertNull(result);
    }

    @Test
    void testConvertSetPythonNone() {
        // Test conversion with Python None value
        Set<Integer> result = PythonConversion.convertSet(context.eval("python", "lambda: None").execute(), Integer.class);

        assertNull(result);
    }

    @Test
    void testNestedCollections() {
        // Create a Python list containing nested structures
        Value pythonList = context.eval("python", "[{'name': 'John', 'age': 30}, {'name': 'Jane', 'age': 25}]");

        // Test conversion - this should work with Object types
        List<Object> result = PythonConversion.convertList(pythonList, Object.class);

        assertNotNull(result);
        assertEquals(2, result.size());
        // The nested conversion depends on the convertValue implementation
    }

    @Test
    void testPrimitiveTypeConversion() {
        // Test individual primitive conversions by testing with collections
        List<Integer> intList = PythonConversion.convertList(context.eval("python", "[42]"), Integer.class);
        assertEquals(1, intList.size());
        assertEquals(42, intList.get(0));

        List<String> stringList = PythonConversion.convertList(context.eval("python", "['hello']"), String.class);
        assertEquals(1, stringList.size());
        assertEquals("hello", stringList.get(0));
    }

    @Test
    void testArrayConversion() {
        // Test conversion of Python arrays to Java collections
        Value pythonArray = context.eval("python", "[1, 2, 3]");

        List<Integer> result = PythonConversion.convertList(pythonArray, Integer.class);
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(List.of(1, 2, 3), result);
    }

    private record TestBody(String name) implements ValueCoercible {
        @Override
        public Value asPolyglotValue() {
            throw new UnsupportedOperationException("This test should unwrap the host object without converting through Python");
        }
    }

    private enum TestLanguage {
        GROOVY,
        JAVA,
        KOTLIN
    }

    interface HostModelInterface {

        void addAttribute(String name, Object value);
    }

    public interface AsyncSender {
        default String sendAsync(String email) {
            return "default:" + email;
        }
    }

    static final class HostModel extends java.util.HashMap<String, Object> implements HostModelInterface {

        @Override
        public void addAttribute(String name, Object value) {
            put(name, value);
        }
    }

    public static final class ClassAcceptor {
        public static String name(Class<?> type) {
            return type.getName();
        }
    }
}
