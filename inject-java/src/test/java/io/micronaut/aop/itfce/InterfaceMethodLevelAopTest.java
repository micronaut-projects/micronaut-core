package io.micronaut.aop.itfce;

import io.micronaut.aop.Intercepted;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Java port of InterfaceMethodLevelAopSpec.
 * Refactored to JUnit parameterized tests without reflection.
 */
class InterfaceMethodLevelAopTest {

    @FunctionalInterface
    interface Invoker<T> {
        Object apply(T bean) throws Exception;
    }

    static Stream<Arguments> methodCases() {
        return Stream.of(
            // test (overloads)
            Arguments.of((Invoker<InterfaceClass<?>>) b -> b.test("test"), "Name is changed"),
            Arguments.of((Invoker<InterfaceClass<?>>) b -> b.test(10), "Age is 20"),
            Arguments.of((Invoker<InterfaceClass<?>>) b -> b.test("test", 10), "Name is changed and age is 10"),
            Arguments.of((Invoker<InterfaceClass<?>>) InterfaceClass::test, "noargs"),
            // testVoid
            Arguments.of((Invoker<InterfaceClass<?>>) b -> { b.testVoid("test"); return null; }, null),
            Arguments.of((Invoker<InterfaceClass<?>>) b -> { b.testVoid("test", 10); return null; }, null),
            // testBoolean
            Arguments.of((Invoker<InterfaceClass<?>>) b -> b.testBoolean("test"), true),
            Arguments.of((Invoker<InterfaceClass<?>>) b -> b.testBoolean("test", 10), true),
            // int
            Arguments.of((Invoker<InterfaceClass<?>>) b -> b.testInt("test"), 1),
            Arguments.of((Invoker<InterfaceClass<?>>) b -> b.testInt("test", 10), 20),
            // short
            Arguments.of((Invoker<InterfaceClass<?>>) b -> b.testShort("test"), (short) 1),
            Arguments.of((Invoker<InterfaceClass<?>>) b -> b.testShort("test", 10), (short) 20),
            // char
            Arguments.of((Invoker<InterfaceClass<?>>) b -> b.testChar("test"), (char) 1),
            Arguments.of((Invoker<InterfaceClass<?>>) b -> b.testChar("test", 10), (char) 20),
            // byte
            Arguments.of((Invoker<InterfaceClass<?>>) b -> b.testByte("test"), (byte) 1),
            Arguments.of((Invoker<InterfaceClass<?>>) b -> b.testByte("test", 10), (byte) 20),
            // float
            Arguments.of((Invoker<InterfaceClass<?>>) b -> b.testFloat("test"), 1f),
            Arguments.of((Invoker<InterfaceClass<?>>) b -> b.testFloat("test", 10), 20f),
            // double
            Arguments.of((Invoker<InterfaceClass<?>>) b -> b.testDouble("test"), 1d),
            Arguments.of((Invoker<InterfaceClass<?>>) b -> b.testDouble("test", 10), 20d),
            // byte array
            Arguments.of((Invoker<InterfaceClass<?>>) b -> b.testByteArray("test", "test".getBytes()), "test".getBytes()),
            // generics
            Arguments.of((Invoker<InterfaceClass<?>>) b -> b.testGenericsWithExtends("test", 10), "Name is changed"),
            Arguments.of((Invoker<InterfaceClass<?>>) b -> b.testListWithWildCardSuper("test", new ArrayList<>()), List.of("changed")),
            Arguments.of((Invoker<InterfaceClass<?>>) b -> b.testListWithWildCardExtends("test", new ArrayList<String>()), List.of("changed")),
            // generics from type (need raw cast to supply String)
            Arguments.of((Invoker<InterfaceClass<?>>) b -> ((InterfaceClass) b).testGenericsFromType("test", 10), "Name is changed")
        );
    }

    @ParameterizedTest
    @MethodSource("methodCases")
    void testAopMethodInvocationVariants(Invoker<InterfaceClass<?>> invoker, Object expected) throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            InterfaceClass<?> foo = context.getBean(InterfaceClass.class);

            Object result = invoker.apply(foo);
            if (expected instanceof byte[] expectedBytes) {
                assertArrayEquals(expectedBytes, (byte[]) result);
            } else if (expected instanceof List<?> expectedList) {
                assertEquals(expectedList, result);
            } else {
                assertEquals(expected, result);
            }
        }
    }

    @Test
    void testAopSetup() {
        try (ApplicationContext context = ApplicationContext.run()) {
            InterfaceClass<?> foo = context.getBean(InterfaceClass.class);
            assertInstanceOf(Intercepted.class, foo);
            assertEquals("Name is changed", foo.test("test"));
        }
    }
}
