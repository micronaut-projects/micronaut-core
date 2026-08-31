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
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;
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
 * Test class for GraalPyRuntimeUtil to verify generic type handling.
 *
 * @author Micronaut Team
 * @since 5.2.0
 */
class GraalPyRuntimeUtilTest {

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
        List<Integer> result = GraalPyRuntimeUtil.convertList(pythonList, Integer.class);

        assertNotNull(result);
        assertEquals(5, result.size());
        assertEquals(List.of(1, 2, 3, 4, 5), result);
    }

    @Test
    void testConvertListWithStrings() {
        // Create a Python list with strings
        Value pythonList = context.eval("python", "['hello', 'world', 'test']");

        // Test conversion with String type
        List<String> result = GraalPyRuntimeUtil.convertList(pythonList, String.class);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(List.of("hello", "world", "test"), result);
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

        assertEquals(TestLanguage.JAVA, GraalPyRuntimeUtil.convertValue(language, TestLanguage.class));
    }

    @Test
    void testConvertPythonStringToJavaEnum() {
        Value language = context.eval("python", "'KOTLIN'");

        assertEquals(TestLanguage.KOTLIN, GraalPyRuntimeUtil.convertValue(language, TestLanguage.class));
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
            assertThrows(RuntimeException.class, () -> mappedContext.eval("python", "__import__('datetime').time(12, tzinfo=__import__('datetime').timezone.utc)").as(LocalTime.class));
            assertThrows(RuntimeException.class, () -> mappedContext.eval("python", "type('CustomZone', (__import__('datetime').tzinfo,), {'utcoffset': lambda self, value: __import__('datetime').timedelta(hours=1)})()").as(ZoneOffset.class));
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
            GraalPyRuntimeUtil.invokePythonMethod(instance, "currentDate", new Object[0]).asString()
        );
        assertEquals(
            "Example",
            GraalPyRuntimeUtil.invokePythonMethod(instance, "className", new Object[0]).asString()
        );
        assertEquals(
            "static",
            GraalPyRuntimeUtil.invokePythonMethod(instance, "staticName", new Object[0]).asString()
        );
    }

    @Test
    @Disabled("not yet implemented")
    void testConvertListWithMixedTypes() {
        // Create a Python list with mixed types
        Value pythonList = context.eval("python", "[1, 'hello', 2.5]");

        // Test conversion with Object type (should handle mixed types)
        List<Object> result = GraalPyRuntimeUtil.convertList(pythonList, Object.class);

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
        List<Integer> result = GraalPyRuntimeUtil.convertList(pythonList, Integer.class);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testConvertListNull() {
        // Test conversion with null value
        List<Integer> result = GraalPyRuntimeUtil.convertList(null, Integer.class);

        assertNull(result);
    }

    @Test
    void testCoerceListNull() {
        assertNull(GraalPyRuntimeUtil.coerceList(null));
    }

    @Test
    void testCoerceMapNull() {
        assertNull(GraalPyRuntimeUtil.coerceMap(null));
    }

    @Test
    void testConvertListPythonNone() {
        // Test conversion with Python None value
        Value none = context.eval("python", "lambda: None").execute();
        List<Integer> result = GraalPyRuntimeUtil.convertList(none, Integer.class);

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
        return GraalPyRuntimeUtil.convertMap(pythonDict, String.class, Integer.class);
    }

    @Test
    void testConvertMapWithIntegerKeysAndStringValues() {
        // Create a Python dict with integer keys and string values
        Value pythonDict = context.eval("python", "{1: 'one', 2: 'two', 3: 'three'}");

        // Test conversion with Integer keys and String values
        Map<Integer, String> result = GraalPyRuntimeUtil.convertMap(pythonDict, Integer.class, String.class);

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
        Map<String, Integer> result = GraalPyRuntimeUtil.convertMap(pythonDict, String.class, Integer.class);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testConvertMapNull() {
        // Test conversion with null value
        Map<String, Integer> result = GraalPyRuntimeUtil.convertMap(null, String.class, Integer.class);

        assertNull(result);
    }

    @Test
    void testConvertMapPythonNone() {
        // Test conversion with Python None value
        Map<String, Integer> result = GraalPyRuntimeUtil.convertMap(context.eval("python", "lambda: None").execute(), String.class, Integer.class);

        assertNull(result);
    }

    @Test
    void testDeclaredTypeCoercionPreservesMapImplementingHostObject() {
        HostModel model = new HostModel();
        model.put("existing", "value");

        Object result = GraalPyRuntimeUtil.coerceToContext(model, context, HostModelInterface.class);

        assertSame(model, result);
    }

    @Test
    void testDeclaredTypeCoercionPreservesConcreteMapImplementingHostObject() {
        HostModel model = new HostModel();
        model.put("existing", "value");

        Object result = GraalPyRuntimeUtil.coerceToContext(model, context, HostModel.class);

        assertSame(model, result);
    }

    @Test
    void testDeclaredTypeCoercionPreservesObjectDeclaredMapImplementingHostObject() {
        HostModel model = new HostModel();
        model.put("existing", "value");

        Object result = GraalPyRuntimeUtil.coerceToContext(model, context, Object.class);

        assertSame(model, result);
    }

    @Test
    void testDeclaredTypeCoercionCopiesDeclaredMap() {
        HostModel model = new HostModel();
        model.put("existing", "value");

        Object result = GraalPyRuntimeUtil.coerceToContext(model, context, Map.class);

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

        assertSame(targetValue, GraalPyRuntimeUtil.coerceToContext(body, context));

        Object nested = GraalPyRuntimeUtil.coerceToContext(
            List.of(Map.of("bodies", new Object[] {body})),
            context
        );
        List<?> nestedList = (List<?>) nested;
        Map<?, ?> nestedMap = (Map<?, ?>) nestedList.getFirst();
        assertArrayEquals(new Object[] {targetValue}, (Object[]) nestedMap.get("bodies"));
        assertArrayEquals(
            new Object[] {1, 2, 3},
            (Object[]) GraalPyRuntimeUtil.coerceToContext(new int[] {1, 2, 3}, context, int[].class)
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

            GraalPyRuntimeUtil.putMember(target, "child", GraalPyRuntimeUtil.coerceValue(child));

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
                return GraalPyRuntimeUtil.coercePooledValue(this, targetContext);
            }

            @Override
            public Value reconstructPolyglotValue(Context targetContext) {
                assertSame(context, targetContext);
                reconstructions.incrementAndGet();
                return targetValue;
            }
        };

        Object[] converted = GraalPyRuntimeUtil.coerceArgumentsToContext(context, new Object[] {body, body});

        assertSame(converted[0], converted[1]);
        assertEquals(1, reconstructions.get());
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
                return GraalPyRuntimeUtil.coercePooledValue(this, targetContext);
            }

            @Override
            public Value reconstructPolyglotValue(Context targetContext) {
                Value target = targetContext.eval("python", "type('Node', (), {})()");
                GraalPyRuntimeUtil.rememberPooledValue(this, targetContext, target);
                GraalPyRuntimeUtil.putMember(target, "next", this);
                return target;
            }
        };

        Value converted = (Value) GraalPyRuntimeUtil.coerceToContext(node, context);
        context.getBindings("python").putMember("converted_node", converted);

        assertTrue(context.eval("python", "converted_node.next is converted_node").asBoolean());
    }

    @Test
    void nonReconstructibleWrapperFromAnotherContextFailsLoudly() {
        try (Context other = Context.newBuilder("python").allowAllAccess(true).build()) {
            ValueCoercible wrapper = () -> other.eval("python", "object()");

            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> GraalPyRuntimeUtil.coerceToContext(wrapper, context)
            );

            assertTrue(exception.getMessage().contains("cannot be reconstructed in the target context"));
            assertThrows(
                IllegalArgumentException.class,
                () -> GraalPyRuntimeUtil.coerceToContext(other.eval("python", "object()"), context)
            );
        }
    }

    @Test
    void testConvertHostOptional() {
        context.getBindings("python").putMember("optional", Optional.of("value"));

        Optional<String> result = GraalPyRuntimeUtil.convertOptional(context.eval("python", "optional"), String.class);

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
            }
        };

        HttpResponse<TestBody> response = GraalPyRuntimeUtil.convertHttpResponse(HttpResponse.created(proxyBody), TestBody.class);

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
        try (Context context = Context.newBuilder(GraalPyRuntimeUtil.PYTHON)
            .allowHostAccess(hostAccess)
            .allowHostClassLookup(className -> true)
            .build()) {
            Value responseValue = context.eval(GraalPyRuntimeUtil.PYTHON, """
                import java

                HttpResponse = java.type("io.micronaut.http.HttpResponse")

                class Body:
                    def __init__(self, name):
                        self.name = name

                HttpResponse.created(Body("DevOps"))
                """);
            HttpResponse<?> rawResponse = responseValue.as(HttpResponse.class);

            HttpResponse<TestBody> response = GraalPyRuntimeUtil.convertHttpResponse(rawResponse, TestBody.class);

            assertEquals(new TestBody("DevOps"), response.body());
        }
    }

    @Test
    void testConvertSetWithIntegers() {
        // Create a Python set with integers
        Value pythonSet = context.eval("python", "{1, 2, 3, 4, 5}");

        // Test conversion with Integer type
        Set<Integer> result = GraalPyRuntimeUtil.convertSet(pythonSet, Integer.class);

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
        Set<String> result = GraalPyRuntimeUtil.convertSet(pythonSet, String.class);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.contains("hello"));
        assertTrue(result.contains("world"));
        assertTrue(result.contains("test"));
    }

    @Test
    void testConvertSetEmpty() {
        // Create an empty Python set
        Value pythonSet = context.eval("python", "set()");

        // Test conversion with Integer type
        Set<Integer> result = GraalPyRuntimeUtil.convertSet(pythonSet, Integer.class);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testConvertSetNull() {
        // Test conversion with null value
        Set<Integer> result = GraalPyRuntimeUtil.convertSet(null, Integer.class);

        assertNull(result);
    }

    @Test
    void testConvertSetPythonNone() {
        // Test conversion with Python None value
        Set<Integer> result = GraalPyRuntimeUtil.convertSet(context.eval("python", "lambda: None").execute(), Integer.class);

        assertNull(result);
    }

    @Test
    void testNestedCollections() {
        // Create a Python list containing nested structures
        Value pythonList = context.eval("python", "[{'name': 'John', 'age': 30}, {'name': 'Jane', 'age': 25}]");

        // Test conversion - this should work with Object types
        List<Object> result = GraalPyRuntimeUtil.convertList(pythonList, Object.class);

        assertNotNull(result);
        assertEquals(2, result.size());
        // The nested conversion depends on the convertValue implementation
    }

    @Test
    void testPrimitiveTypeConversion() {
        // Test individual primitive conversions by testing with collections
        Value intValue = context.eval("python", "42");
        List<Integer> intList = GraalPyRuntimeUtil.convertList(context.eval("python", "[42]"), Integer.class);
        assertEquals(1, intList.size());
        assertEquals(42, intList.get(0));

        Value stringValue = context.eval("python", "'hello'");
        List<String> stringList = GraalPyRuntimeUtil.convertList(context.eval("python", "['hello']"), String.class);
        assertEquals(1, stringList.size());
        assertEquals("hello", stringList.get(0));
    }

    @Test
    void testArrayConversion() {
        // Test conversion of Python arrays to Java collections
        Value pythonArray = context.eval("python", "[1, 2, 3]");

        List<Integer> result = GraalPyRuntimeUtil.convertList(pythonArray, Integer.class);
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

    static final class HostModel extends java.util.HashMap<String, Object> implements HostModelInterface {

        @Override
        public void addAttribute(String name, Object value) {
            put(name, value);
        }
    }
}
