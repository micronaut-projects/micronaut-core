package io.micronaut.docs.context.env;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.DefaultEnvironment;
import io.micronaut.context.env.Environment;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class DefaultEnvironmentSpec {

    // tag::disableEnvDeduction[]
    @Test
    public void testDisableEnvironmentDeductionViaBuilder() {
        ApplicationContext ctx = ApplicationContext.builder().deduceEnvironment(false).start();
        assertFalse(ctx.getEnvironment().getActiveNames().contains(Environment.TEST));
        ctx.close();
    }
    // end::disableEnvDeduction[]
}
