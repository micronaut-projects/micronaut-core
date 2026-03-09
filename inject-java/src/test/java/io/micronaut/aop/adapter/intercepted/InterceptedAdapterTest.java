package io.micronaut.aop.adapter.intercepted;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Java port of InterceptedAdapterSpec.
 */
class InterceptedAdapterTest {

    @Test
    void testInterceptorOnAnEvent() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            MyBean service = ctx.getBean(MyBean.class);
            TransactionalEventInterceptor interceptor = ctx.getBean(TransactionalEventInterceptor.class);

            assertEquals(0L, interceptor.count);

            service.triggerEvent();

            assertEquals(1L, service.count);

            assertEquals(1L, interceptor.count);

            assertEquals("test", interceptor.executableMethod.getMethodName());
        }
    }
}
