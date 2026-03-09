package io.micronaut.aop.lazyproxytarget;

import io.micronaut.aop.InterceptedProxy;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java port of ProxyingMethodLevelAopSpec for lazy proxy target.
 * Refactored to JUnit parameterized tests without reflection.
 */
class ProxyingMethodLevelAopLazyTargetTest {

    @FunctionalInterface
    interface Invoker<T> {
        Object apply(T bean) throws Exception;
    }

    static Stream<Arguments> methodCases() {
        return Stream.of(
            // test (overloads)
            Arguments.of((Invoker<ProxyingClass>) b -> b.test("test"), "Name is changed"),
            Arguments.of((Invoker<ProxyingClass>) b -> b.test(10), "Age is 20"),
            Arguments.of((Invoker<ProxyingClass>) b -> b.test("test", 10), "Name is changed and age is 10"),
            Arguments.of((Invoker<ProxyingClass>) ProxyingClass::test, "noargs"),
            // testVoid
            Arguments.of((Invoker<ProxyingClass>) b -> { b.testVoid("test"); return null; }, null),
            Arguments.of((Invoker<ProxyingClass>) b -> { b.testVoid("test", 10); return null; }, null),
            // testBoolean
            Arguments.of((Invoker<ProxyingClass>) b -> b.testBoolean("test"), true),
            Arguments.of((Invoker<ProxyingClass>) b -> b.testBoolean("test", 10), true),
            // testInt
            Arguments.of((Invoker<ProxyingClass>) b -> b.testInt("test"), 1),
            Arguments.of((Invoker<ProxyingClass>) b -> b.testInt("test", 10), 20),
            // testShort
            Arguments.of((Invoker<ProxyingClass>) b -> b.testShort("test"), (short) 1),
            Arguments.of((Invoker<ProxyingClass>) b -> b.testShort("test", 10), (short) 20),
            // testChar
            Arguments.of((Invoker<ProxyingClass>) b -> b.testChar("test"), (char) 1),
            Arguments.of((Invoker<ProxyingClass>) b -> b.testChar("test", 10), (char) 20),
            // testByte
            Arguments.of((Invoker<ProxyingClass>) b -> b.testByte("test"), (byte) 1),
            Arguments.of((Invoker<ProxyingClass>) b -> b.testByte("test", 10), (byte) 20),
            // testFloat
            Arguments.of((Invoker<ProxyingClass>) b -> b.testFloat("test"), 1f),
            Arguments.of((Invoker<ProxyingClass>) b -> b.testFloat("test", 10), 20f),
            // testDouble
            Arguments.of((Invoker<ProxyingClass>) b -> b.testDouble("test"), 1d),
            Arguments.of((Invoker<ProxyingClass>) b -> b.testDouble("test", 10), 20d),
            // testByteArray
            Arguments.of((Invoker<ProxyingClass>) b -> b.testByteArray("test", "test".getBytes()), "test".getBytes()),
            // generics
            Arguments.of((Invoker<ProxyingClass>) b -> b.testGenericsWithExtends("test", 10), "Name is changed"),
            Arguments.of((Invoker<ProxyingClass>) b -> b.testListWithWildCardSuper("test", new ArrayList<>()), List.of("changed")),
            Arguments.of((Invoker<ProxyingClass>) b -> b.testListWithWildCardExtends("test", new ArrayList<String>()), List.of("changed"))
        );
    }

    @ParameterizedTest
    @MethodSource("methodCases")
    void testAopMethodInvocationVariants(Invoker<ProxyingClass> invoker, Object expected) throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            ProxyingClass foo = context.getBean(ProxyingClass.class);

            // With lazy proxy target, lifecycle should not have been invoked on the target yet
            assertEquals(0, ((ProxyingClass) foo).lifeCycleCount);

            Object result = invoker.apply(foo);
            if (expected instanceof byte[] expectedBytes) {
                assertArrayEquals(expectedBytes, (byte[]) result);
            } else if (expected instanceof List<?> expectedList) {
                assertEquals(expectedList, result);
            } else {
                assertEquals(expected, result);
            }

            // Verify proxy semantics
            InterceptedProxy proxy = org.junit.jupiter.api.Assertions.assertInstanceOf(InterceptedProxy.class, foo);
            ProxyingClass target = (ProxyingClass) proxy.interceptedTarget();
            // target.lifeCycleCount == 1
            assertEquals(1, target.lifeCycleCount);
        }
    }

    @Test
    void testAopSetup() throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            BeanDefinition<?> beanDefinition = context.getBeanDefinition(ProxyingClass.class);
            assertTrue(beanDefinition.findMethod("test", String.class).isPresent());

            // should not be a reflection based method
            String implClassName = beanDefinition.findMethod("test", String.class).get().getClass().getName();
            assertFalse(implClassName.contains("Reflection"));

            Object foo = context.getBean(ProxyingClass.class);
            org.junit.jupiter.api.Assertions.assertInstanceOf(InterceptedProxy.class, foo);

            // lifeCycleCount on proxy should be 0 before target init
            assertEquals(0, ((ProxyingClass) foo).lifeCycleCount);

            assertTrue(context.findExecutableMethod(ProxyingClass.class, "test", String.class).isPresent());
            String execClassName = context.findExecutableMethod(ProxyingClass.class, "test", String.class).get().getClass().getName();
            assertFalse(execClassName.contains("Reflection"));

            assertEquals("Name is changed", ((ProxyingClass) foo).test("test"));

            // Additionally verify target lifecycle was invoked
            ProxyingClass target = (ProxyingClass) ((InterceptedProxy) foo).interceptedTarget();
            assertEquals(1, target.lifeCycleCount);
        }
    }
}
