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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Test class for GraalPyRuntimeUtil to verify generic type handling.
 *
 * @author Micronaut Team
 * @since 5.0.0
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
    void testConvertHostOptional() {
        context.getBindings("python").putMember("optional", Optional.of("value"));

        Optional<String> result = GraalPyRuntimeUtil.convertOptional(context.eval("python", "optional"), String.class);

        assertEquals(Optional.of("value"), result);
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
}
