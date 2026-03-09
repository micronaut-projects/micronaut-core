package io.micronaut.aop.hotswap;

import io.micronaut.aop.HotSwappableInterceptedProxy;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * JUnit port of ProxyHotswapSpec.
 */
class ProxyHotswapTest {

    @Test
    void testAopSetupAttributes() {
        try (ApplicationContext context = ApplicationContext.run()) {
            HotswappableProxyingClass newInstance = new HotswappableProxyingClass();

            HotswappableProxyingClass foo = context.getBean(HotswappableProxyingClass.class);

            @SuppressWarnings("unchecked")
            HotSwappableInterceptedProxy<HotswappableProxyingClass> proxy = (HotSwappableInterceptedProxy<HotswappableProxyingClass>) org.junit.jupiter.api.Assertions.assertInstanceOf(HotSwappableInterceptedProxy.class, foo);
            HotswappableProxyingClass target = proxy.interceptedTarget();
            assertEquals(HotswappableProxyingClass.class, target.getClass());

            assertEquals("Name is changed", foo.test("test"));
            assertEquals("Name is test", foo.test2("test"));
            assertEquals(2, proxy.interceptedTarget().invocationCount);

            proxy.swap(newInstance);
            HotswappableProxyingClass swappedTarget = proxy.interceptedTarget();
            assertEquals(0, swappedTarget.invocationCount);
            assertNotSame(swappedTarget, foo);
            assertSame(newInstance, swappedTarget);
        } catch (Exception e) {
            fail(e);
        }
    }
}
