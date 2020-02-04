package io.micronaut.docs.lifecycle;

import io.micronaut.context.BeanContext;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class PreDestroyBeanSpec {

    @Test
    public void testBeanClosingOnContextClose() {
        // tag::start[]
        BeanContext ctx = BeanContext.run();
        PreDestroyBean preDestroyBean = ctx.getBean(PreDestroyBean.class);
        Connection connection = ctx.getBean(Connection.class);
        ctx.stop();
        // end::start[]

        assertTrue(preDestroyBean.stopped.get());
        assertTrue(connection.stopped.get());
    }
}
