package io.micronaut.aop.around.noproxytarget;

import io.micronaut.aop.InterceptedProxy;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ByteBuddyNoProxyTargetTest {

    @Test
    void test() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "RuntimeProxyTest"))) {
            ByteBuddyNoProxyTargetProxyingClass<String> proxyingClass = context.getBean(ByteBuddyNoProxyTargetProxyingClass.class);
            assertBean(proxyingClass);
        }
    }

    @Test
    void testWithConstructor() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "RuntimeProxyTest"))) {
            ByteBuddyNoProxyTargetWithConstructorProxyingClass<String> proxyingClass = context.getBean(ByteBuddyNoProxyTargetWithConstructorProxyingClass.class);
            assertBean(proxyingClass);
        }
    }

    @Test
    void testForceProxyTarget() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "RuntimeProxyTest"))) {
            ByteBuddyForceProxyTargetProxyingClass<String> proxyingClass = context.getBean(ByteBuddyForceProxyTargetProxyingClass.class);
            assertBean(proxyingClass);
            assertInstanceOf(InterceptedProxy.class, proxyingClass);
            InterceptedProxy<?> interceptedProxy = (InterceptedProxy<?>) proxyingClass;
            assertTrue(interceptedProxy.hasCachedInterceptedTarget());
            assertNotNull(interceptedProxy.interceptedTarget());
        }
    }

    private void assertBean(NoProxyTargetClass<String> proxyingClass) {
        if (proxyingClass instanceof InterceptedProxy<?> interceptedProxy) {
            assertLifeCycle(proxyingClass, (NoProxyTargetClass<String>) interceptedProxy.interceptedTarget());
        } else {
            assertLifeCycle(proxyingClass, proxyingClass);
        }
        // Assert primitive overloads and return types are correctly proxied
        assertEquals("Age is 10", proxyingClass.test(5));
        assertEquals("Name is changed and age is 5", proxyingClass.test("test", 5));
        assertEquals("noargs", proxyingClass.test());
        proxyingClass.testVoid("test");
        proxyingClass.testVoid("test", 10);
        assertTrue(proxyingClass.testBoolean("test"));
        assertTrue(proxyingClass.testBoolean("test", 10));
        assertEquals(1, proxyingClass.testInt("test"));
        assertEquals(10, proxyingClass.testInt("test", 5));
        assertEquals(1L, proxyingClass.testLong("test"));
        assertEquals(10L, proxyingClass.testLong("test", 5));
        assertEquals((short) 1, proxyingClass.testShort("test"));
        assertEquals((short) 10, proxyingClass.testShort("test", 5));
        assertEquals((byte) 1, proxyingClass.testByte("test"));
        assertEquals((byte) 10, proxyingClass.testByte("test", 5));
        assertEquals(1D, proxyingClass.testDouble("test"));
        assertEquals(10D, proxyingClass.testDouble("test", 5));
        assertEquals(1F, proxyingClass.testFloat("test"));
        assertEquals(10F, proxyingClass.testFloat("test", 5));
        assertEquals((char) 1, proxyingClass.testChar("test"));
        assertEquals((char) 10, proxyingClass.testChar("test", 5));
        byte[] data = new byte[]{1, 2, 3};
        assertArrayEquals(data, proxyingClass.testByteArray("test", data));
        assertEquals("Name is changed", proxyingClass.testGenericsWithExtends("test", 5));
        assertEquals(List.of("changed"), proxyingClass.testListWithWildCardSuper("test", List.of("a")));
        assertEquals(List.of("changed"), proxyingClass.testListWithWildCardExtends("test", List.of("a")));
        assertEquals("Name is changed", proxyingClass.testGenericsFromType("test", 5));
    }

    private void assertLifeCycle(NoProxyTargetClass<String> original, NoProxyTargetClass<String> target) {
        // Assert @PostConstruct init() was invoked exactly once
        assertEquals(1, target.lifeCycleCount, "PostConstruct init() should increment lifeCycleCount once");
        // Invocation count should be zero before any method calls
        assertEquals(0, target.invocationCount, "invocationCount should be zero before invoking test(String)");
        // Assert parameter was mutated by runtime proxy and result is correct
        assertEquals("Name is changed", original.test("test"));
        // Assert invocation count is incremented by test(String)
        assertEquals(1, target.invocationCount, "invocationCount should be incremented after test(String)");
    }
}
