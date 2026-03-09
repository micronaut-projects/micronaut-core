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
 * Java port of InterfaceTypeLevelSpec.
 * Refactored to JUnit parameterized tests without reflection.
 */
class InterfaceTypeLevelTest {

    @FunctionalInterface
    interface Invoker<T> {
        Object apply(T bean) throws Exception;
    }

    static Stream<Arguments> methodCases() {
        return Stream.of(
            // test (overloads)
            Arguments.of((Invoker<InterfaceTypeLevel>) b -> b.test("test"), "Name is changed"),
            Arguments.of((Invoker<InterfaceTypeLevel>) b -> b.test("test", 10), "Name is changed and age is 10"),
            Arguments.of((Invoker<InterfaceTypeLevel>) InterfaceTypeLevel::test, "noargs"),
            // testVoid
            Arguments.of((Invoker<InterfaceTypeLevel>) b -> { b.testVoid("test"); return null; }, null),
            Arguments.of((Invoker<InterfaceTypeLevel>) b -> { b.testVoid("test", 10); return null; }, null),
            // testBoolean
            Arguments.of((Invoker<InterfaceTypeLevel>) b -> b.testBoolean("test"), true),
            Arguments.of((Invoker<InterfaceTypeLevel>) b -> b.testBoolean("test", 10), true),
            // primitives single arg (implicit in impl)
            Arguments.of((Invoker<InterfaceTypeLevel>) b -> b.testInt("test"), 1),
            Arguments.of((Invoker<InterfaceTypeLevel>) b -> b.testShort("test"), (short) 1),
            Arguments.of((Invoker<InterfaceTypeLevel>) b -> b.testChar("test"), (char) 1),
            Arguments.of((Invoker<InterfaceTypeLevel>) b -> b.testByte("test"), (byte) 1),
            Arguments.of((Invoker<InterfaceTypeLevel>) b -> b.testFloat("test"), 1f),
            Arguments.of((Invoker<InterfaceTypeLevel>) b -> b.testDouble("test"), 1d),
            // arrays, generics
            Arguments.of((Invoker<InterfaceTypeLevel>) b -> b.testByteArray("test", "test".getBytes()), "test".getBytes()),
            Arguments.of((Invoker<InterfaceTypeLevel>) b -> b.testGenericsWithExtends("test", 10), "Name is changed"),
            Arguments.of((Invoker<InterfaceTypeLevel>) b -> ((InterfaceTypeLevel) b).testGenericsFromType("test", 10), "Name is changed"),
            Arguments.of((Invoker<InterfaceTypeLevel>) b -> b.testListWithWildCardSuper("test", new ArrayList<>()), List.of("changed")),
            Arguments.of((Invoker<InterfaceTypeLevel>) b -> b.testListWithWildCardExtends("test", new ArrayList<String>()), List.of("changed"))
        );
    }

    @ParameterizedTest
    @MethodSource("methodCases")
    void testAopMethodInvocationVariants(Invoker<InterfaceTypeLevel> invoker, Object expected) throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            InterfaceTypeLevel<?> foo = context.getBean(InterfaceTypeLevel.class);
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
            InterfaceTypeLevel<?> foo = context.getBean(InterfaceTypeLevel.class);
            assertInstanceOf(Intercepted.class, foo);
            assertEquals("Name is changed", foo.test("test"));
        }
    }
}
