package io.micronaut.docs.context.env;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.DefaultEnvironment;
import io.micronaut.context.env.Environment;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class DefaultEnvironmentSpec {
    @Test
    public void testEnvironmentSystemPropertyResolve() {
        System.setProperty("test.foo.bar", "10");
        Environment env = new DefaultEnvironment("test").start();

        assertEquals(10, (int)env.getProperty("test.foo.bar", Integer.class).get());
        assertEquals(10, (int)env.getRequiredProperty("test.foo.bar", Integer.class));
        assertEquals(10, (int)env.getProperty("test.foo.bar", Integer.class, 20));

        System.setProperty("test.foo.bar", "");
    }

    // tag::disableEnvDeduction[]
    @Test
    public void testDisableEnvironmentDeductionViaBuilder() {
        ApplicationContext ctx = ApplicationContext.build().deduceEnvironment(false).start();
        assertFalse(ctx.getEnvironment().getActiveNames().contains(Environment.TEST));
        ctx.close();
    }
    // end::disableEnvDeduction[]
}
