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
package io.micronaut.aop.factory;

import io.micronaut.aop.Intercepted;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java port of AdviceDefinedOnConcreteClassFactorySpec.
 */
class AdviceDefinedOnConcreteClassFactoryTest {

    static Stream<org.junit.jupiter.params.provider.Arguments> testCases() {
        return Stream.of(
            arg("test", new Object[]{"test"}, "Name is changed"),
            arg("test", new Object[]{"test", 10}, "Name is changed and age is 10"),
            arg("test", new Object[]{}, "noargs"),
            arg("testVoid", new Object[]{"test"}, null),
            arg("testVoid", new Object[]{"test", 10}, null),
            arg("testBoolean", new Object[]{"test"}, true),
            arg("testBoolean", new Object[]{"test", 10}, true),
            arg("testInt", new Object[]{"test"}, 1),
            arg("testShort", new Object[]{"test"}, (short) 1),
            arg("testChar", new Object[]{"test"}, 1),
            arg("testByte", new Object[]{"test"}, (byte) 1),
            arg("testFloat", new Object[]{"test"}, 1f),
            arg("testDouble", new Object[]{"test"}, 1d),
            arg("testByteArray", new Object[]{"test", "test".getBytes()}, "test".getBytes()),
            arg("testGenericsWithExtends", new Object[]{"test", 10}, "Name is changed"),
            arg("testGenericsFromType", new Object[]{"test", 10}, "Name is changed"),
            arg("testListWithWildCardSuper", new Object[]{"test", List.of()}, List.of("changed")),
            arg("testListWithWildCardExtends", new Object[]{"test", List.of()}, List.of("changed"))
        );
    }

    private static Object invoke(Object bean, String name, Object... args) throws Exception {
        Class<?>[] argTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            if (a == null) {
                argTypes[i] = Object.class;
            } else if (a instanceof Integer) {
                argTypes[i] = int.class;
            } else if (a instanceof Short) {
                argTypes[i] = short.class;
            } else if (a instanceof Byte) {
                argTypes[i] = byte.class;
            } else if (a instanceof Long) {
                argTypes[i] = long.class;
            } else if (a instanceof Float) {
                argTypes[i] = float.class;
            } else if (a instanceof Double) {
                argTypes[i] = double.class;
            } else if (a instanceof Boolean) {
                argTypes[i] = boolean.class;
            } else if (a instanceof Character) {
                argTypes[i] = char.class;
            } else if (a instanceof byte[]) {
                argTypes[i] = byte[].class;
            } else {
                argTypes[i] = a.getClass();
            }
        }
        Method m = resolveMethod(bean.getClass(), name, argTypes);
        return m.invoke(bean, args);
    }

    private static Method resolveMethod(Class<?> clazz, String name, Class<?>[] argTypes) throws NoSuchMethodException {
        // First try exact public method
        try {
            return clazz.getMethod(name, argTypes);
        } catch (NoSuchMethodException ignore) {
            // fall through
        }
        // Try with boxed types if primitives were specified
        Class<?>[] boxed = new Class<?>[argTypes.length];
        for (int i = 0; i < argTypes.length; i++) {
            Class<?> t = argTypes[i];
            boxed[i] = t != null && t.isPrimitive() ? wrap(t) : t;
        }
        try {
            return clazz.getMethod(name, boxed);
        } catch (NoSuchMethodException ignore) {
            // fall through
        }
        // Last resort: search hierarchy (public + declared) and match by assignability (handles generics erasure e.g. CharSequence vs String, List<?> vs Immutable list)
        java.util.List<Method> candidates = new java.util.ArrayList<>();
        candidates.addAll(java.util.Arrays.asList(clazz.getMethods())); // public, including inherited
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            candidates.addAll(java.util.Arrays.asList(c.getDeclaredMethods())); // declared on each supertype
        }
        for (Method candidate : candidates) {
            if (!candidate.getName().equals(name)) continue;
            Class<?>[] params = candidate.getParameterTypes();
            if (params.length != argTypes.length) continue;
            boolean compatible = true;
            for (int i = 0; i < params.length; i++) {
                Class<?> param = params[i];
                Class<?> arg = argTypes[i];
                if (arg == null) continue;
                if (param.isPrimitive()) {
                    Class<?> w = wrap(param);
                    if (!(w.equals(arg) || w.isAssignableFrom(arg))) {
                        compatible = false;
                        break;
                    }
                } else {
                    if (arg.isPrimitive()) {
                        Class<?> wa = wrap(arg);
                        if (!param.isAssignableFrom(wa)) {
                            compatible = false;
                            break;
                        }
                    } else if (!param.isAssignableFrom(arg)) {
                        compatible = false;
                        break;
                    }
                }
            }
            if (compatible) {
                try {
                    candidate.setAccessible(true);
                } catch (Exception ignored) {
                    // best effort
                }
                return candidate;
            }
        }
        // Fallback: choose a method by name and parameter count (last resort for generic bridge/erasure)
        for (Method candidate : candidates) {
            if (candidate.getName().equals(name) && candidate.getParameterCount() == argTypes.length) {
                try {
                    candidate.setAccessible(true);
                } catch (Exception ignored) {
                }
                return candidate;
            }
        }
        // Final fallback: if only one method with that name exists, return it (unique name)
        Method uniqueByName = null;
        for (Method candidate : candidates) {
            if (candidate.getName().equals(name)) {
                if (uniqueByName != null) {
                    uniqueByName = null;
                    break;
                }
                uniqueByName = candidate;
            }
        }
        if (uniqueByName != null) {
            try {
                uniqueByName.setAccessible(true);
            } catch (Exception ignored) {
            }
            return uniqueByName;
        }
        // As a last resort, return the first method with the given name
        for (Method candidate : candidates) {
            if (candidate.getName().equals(name)) {
                try {
                    candidate.setAccessible(true);
                } catch (Exception ignored) {
                }
                return candidate;
            }
        }
        throw new NoSuchMethodException(clazz.getName() + "." + name);
    }

    private static Class<?> unwrap(Class<?> c) {
        if (c == Boolean.class) return boolean.class;
        if (c == Byte.class) return byte.class;
        if (c == Short.class) return short.class;
        if (c == Character.class) return char.class;
        if (c == Integer.class) return int.class;
        if (c == Long.class) return long.class;
        if (c == Float.class) return float.class;
        if (c == Double.class) return double.class;
        return c;
    }

    private static Class<?> wrap(Class<?> c) {
        return Map.of(
            boolean.class, Boolean.class,
            byte.class, Byte.class,
            short.class, Short.class,
            char.class, Character.class,
            int.class, Integer.class,
            long.class, Long.class,
            float.class, Float.class,
            double.class, Double.class
        ).get(c);
    }

    private static void assertExpected(Object expected, Object actual) {
        if (expected == null) {
            assertNull(actual);
            return;
        }
        if (expected instanceof byte[] expBytes) {
            assertArrayEquals(expBytes, (byte[]) actual);
            return;
        }
        if (actual instanceof Character ch && expected instanceof Number n) {
            assertEquals(n.intValue(), (int) ch.charValue());
            return;
        }
        if (expected instanceof List expList) {
            assertEquals(expList, actual);
            return;
        }
        assertEquals(expected, actual);
    }

    private static org.junit.jupiter.params.provider.Arguments arg(String method, Object[] args, Object result) {
        return org.junit.jupiter.params.provider.Arguments.of(method, args, result);
    }

    @ParameterizedTest
    @MethodSource("testCases")
    void testNamedBeanMethods(String method, Object[] args, Object expected) throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            ConcreteClass foo = context.getBean(ConcreteClass.class, Qualifiers.byName("another"));

            assertInstanceOf(Intercepted.class, foo);
            Object actual = invoke(foo, method, args);
            assertExpected(expected, actual);
        }
    }

    @ParameterizedTest
    @MethodSource("testCases")
    void testDefaultBeanMethods(String method, Object[] args, Object expected) throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            ConcreteClass foo = context.getBean(ConcreteClass.class);

            assertInstanceOf(Intercepted.class, foo);
            Object actual = invoke(foo, method, args);
            assertExpected(expected, actual);
        }
    }

    @Test
    void sanityChecks() throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            ConcreteClass bean = context.getBean(ConcreteClass.class);
            assertEquals("Name is changed", invoke(bean, "test", "test"));
            assertEquals("noargs", invoke(bean, "test"));
            assertNull(invoke(bean, "testVoid", "test"));
            assertTrue((Boolean) invoke(bean, "testBoolean", "test"));
            assertArrayEquals("abc".getBytes(), (byte[]) invoke(bean, "testByteArray", "test", "abc".getBytes()));
        }
    }
}
