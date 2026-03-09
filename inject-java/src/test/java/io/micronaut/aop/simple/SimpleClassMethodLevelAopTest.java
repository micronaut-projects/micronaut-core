package io.micronaut.aop.simple;

import io.micronaut.aop.Intercepted;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.BeanDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java port of SimpleClassMethodLevelAopSpec.
 */
class SimpleClassMethodLevelAopTest {

    @FunctionalInterface
    interface Invoker<T> {
        Object apply(T bean) throws Exception;
    }

    static Stream<Arguments> methodCases() {
        return Stream.of(
            // test (overloads)
            Arguments.of((Invoker<SimpleClass<?>>) b -> b.test("test"), "Name is changed"),
            Arguments.of((Invoker<SimpleClass<?>>) b -> b.test(10), "Age is 20"),
            Arguments.of((Invoker<SimpleClass<?>>) b -> b.test("test", 10), "Name is changed and age is 10"),
            Arguments.of((Invoker<SimpleClass<?>>) SimpleClass::test, "noargs"),
            // testVoid
            Arguments.of((Invoker<SimpleClass<?>>) b -> { b.testVoid("test"); return null; }, null),
            Arguments.of((Invoker<SimpleClass<?>>) b -> { b.testVoid("test", 10); return null; }, null),
            // testBoolean
            Arguments.of((Invoker<SimpleClass<?>>) b -> b.testBoolean("test"), true),
            Arguments.of((Invoker<SimpleClass<?>>) b -> b.testBoolean("test", 10), true),
            // testInt
            Arguments.of((Invoker<SimpleClass<?>>) b -> b.testInt("test"), 1),
            Arguments.of((Invoker<SimpleClass<?>>) b -> b.testInt("test", 10), 20),
            // testShort
            Arguments.of((Invoker<SimpleClass<?>>) b -> b.testShort("test"), (short) 1),
            Arguments.of((Invoker<SimpleClass<?>>) b -> b.testShort("test", 10), (short) 20),
            // testChar
            Arguments.of((Invoker<SimpleClass<?>>) b -> b.testChar("test"), (char) 1),
            Arguments.of((Invoker<SimpleClass<?>>) b -> b.testChar("test", 10), (char) 20),
            // testByte
            Arguments.of((Invoker<SimpleClass<?>>) b -> b.testByte("test"), (byte) 1),
            Arguments.of((Invoker<SimpleClass<?>>) b -> b.testByte("test", 10), (byte) 20),
            // testFloat
            Arguments.of((Invoker<SimpleClass<?>>) b -> b.testFloat("test"), 1f),
            Arguments.of((Invoker<SimpleClass<?>>) b -> b.testFloat("test", 10), 20f),
            // testDouble
            Arguments.of((Invoker<SimpleClass<?>>) b -> b.testDouble("test"), 1d),
            Arguments.of((Invoker<SimpleClass<?>>) b -> b.testDouble("test", 10), 20d),
            // testByteArray
            Arguments.of((Invoker<SimpleClass<?>>) b -> b.testByteArray("test", "test".getBytes()), "test".getBytes()),
            // generics
            Arguments.of((Invoker<SimpleClass<?>>) b -> b.testGenericsWithExtends("test", 10), "Name is changed"),
            Arguments.of((Invoker<SimpleClass<?>>) b -> ((SimpleClass) b).testGenericsFromType("test", 10), "Name is changed"),
            Arguments.of((Invoker<SimpleClass<?>>) b -> b.testListWithWildCardSuper("test", new ArrayList<>()), List.of("changed")),
            Arguments.of((Invoker<SimpleClass<?>>) b -> b.testListWithWildCardExtends("test", new ArrayList<String>()), List.of("changed"))
        );
    }

    @ParameterizedTest
    @MethodSource("methodCases")
    void testAopMethodInvocationVariants(Invoker<SimpleClass<?>> invoker, Object expected) throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            SimpleClass<?> foo = context.getBean(SimpleClass.class);
            Object result = invoker.apply(foo);
            if (expected instanceof byte[] expectedBytes) {
                assertArrayEquals(expectedBytes, (byte[]) result);
            } else if (expected instanceof List expectedList) {
                assertEquals(expectedList, result);
            } else {
                assertEquals(expected, result);
            }
            // postConstructInvoked == true
            assertTrue(foo.isPostConstructInvoked());
        }
    }

    @Test
    void testAopSetup() {
        try (ApplicationContext context = ApplicationContext.run()) {
            // BeanDefinition has method "test(String)"
            BeanDefinition<?> beanDefinition = context.getBeanDefinition(SimpleClass.class);
            assertTrue(beanDefinition.findMethod("test", String.class).isPresent());

            // should not be a reflection based method
            String methodImplClassName = beanDefinition.findMethod("test", String.class).get().getClass().getName();
            assertFalse(methodImplClassName.contains("Reflection"));

            Object foo = context.getBean(SimpleClass.class);
            org.junit.jupiter.api.Assertions.assertInstanceOf(Intercepted.class, foo);

            // context.findExecutableMethod present and not reflection-based
            assertTrue(context.findExecutableMethod(SimpleClass.class, "test", String.class).isPresent());
            String execClassName = context.findExecutableMethod(SimpleClass.class, "test", String.class).get().getClass().getName();
            assertFalse(execClassName.contains("Reflection"));

            // foo.test("test") == "Name is changed"
            assertEquals("Name is changed", ((SimpleClass<?>) foo).test("test"));
        }
    }

    @Test
    void testModifyingInterceptorParametersNotSupported() {
        try (ApplicationContext context = ApplicationContext.run()) {
            SimpleClass<?> foo = context.getBean(SimpleClass.class);
            assertThrows(UnsupportedOperationException.class, foo::invalidInterceptor);
        }
    }
}
