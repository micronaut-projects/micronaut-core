package io.micronaut.aop.simple;

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

/**
 * Java port of SimpleClassTypeLevelAopSpec.
 */
class SimpleClassTypeLevelAopTest {

    @FunctionalInterface
    interface Invoker<T> {
        Object apply(T bean) throws Exception;
    }

    static Stream<Arguments> methodCases() {
        return Stream.of(
            // test (overloads)
            Arguments.of((Invoker<AnotherClass<?>>) b -> b.test("test"), "Name is changed"),
            Arguments.of((Invoker<AnotherClass<?>>) b -> b.test(10), "Age is 20"),
            Arguments.of((Invoker<AnotherClass<?>>) b -> b.test("test", 10), "Name is changed and age is 10"),
            Arguments.of((Invoker<AnotherClass<?>>) AnotherClass::test, "noargs"),
            // testVoid
            Arguments.of((Invoker<AnotherClass<?>>) b -> { b.testVoid("test"); return null; }, null),
            Arguments.of((Invoker<AnotherClass<?>>) b -> { b.testVoid("test", 10); return null; }, null),
            // testBoolean
            Arguments.of((Invoker<AnotherClass<?>>) b -> b.testBoolean("test"), true),
            Arguments.of((Invoker<AnotherClass<?>>) b -> b.testBoolean("test", 10), true),
            // testInt
            Arguments.of((Invoker<AnotherClass<?>>) b -> b.testInt("test"), 1),
            Arguments.of((Invoker<AnotherClass<?>>) b -> b.testInt("test", 10), 20),
            // testShort
            Arguments.of((Invoker<AnotherClass<?>>) b -> b.testShort("test"), (short) 1),
            Arguments.of((Invoker<AnotherClass<?>>) b -> b.testShort("test", 10), (short) 20),
            // testChar
            Arguments.of((Invoker<AnotherClass<?>>) b -> b.testChar("test"), (char) 1),
            Arguments.of((Invoker<AnotherClass<?>>) b -> b.testChar("test", 10), (char) 20),
            // testByte
            Arguments.of((Invoker<AnotherClass<?>>) b -> b.testByte("test"), (byte) 1),
            Arguments.of((Invoker<AnotherClass<?>>) b -> b.testByte("test", 10), (byte) 20),
            // testFloat
            Arguments.of((Invoker<AnotherClass<?>>) b -> b.testFloat("test"), 1f),
            Arguments.of((Invoker<AnotherClass<?>>) b -> b.testFloat("test", 10), 20f),
            // testDouble
            Arguments.of((Invoker<AnotherClass<?>>) b -> b.testDouble("test"), 1d),
            Arguments.of((Invoker<AnotherClass<?>>) b -> b.testDouble("test", 10), 20d),
            // testByteArray
            Arguments.of((Invoker<AnotherClass<?>>) b -> b.testByteArray("test", "test".getBytes()), "test".getBytes()),
            // generics
            Arguments.of((Invoker<AnotherClass<?>>) b -> b.testGenericsWithExtends("test", 10), "Name is changed"),
            Arguments.of((Invoker<AnotherClass<?>>) b -> ((AnotherClass) b).testGenericsFromType("test", 10), "Name is changed"),
            Arguments.of((Invoker<AnotherClass<?>>) b -> b.testListWithWildCardSuper("test", new ArrayList<>()), List.of("changed")),
            Arguments.of((Invoker<AnotherClass<?>>) b -> b.testListWithWildCardExtends("test", new ArrayList<String>()), List.of("changed"))
        );
    }

    @ParameterizedTest
    @MethodSource("methodCases")
    void testAopMethodInvocationVariants(Invoker<AnotherClass<?>> invoker, Object expected) throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            AnotherClass<?> foo = context.getBean(AnotherClass.class);
            Object result = invoker.apply(foo);
            if (expected instanceof byte[] expectedBytes) {
                assertArrayEquals(expectedBytes, (byte[]) result);
            } else if (expected instanceof List expectedList) {
                assertEquals(expectedList, result);
            } else {
                assertEquals(expected, result);
            }
        }
    }
}
