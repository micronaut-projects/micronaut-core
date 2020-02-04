package io.micronaut.docs.qualifiers.replaces.defaultimpl;

import io.micronaut.context.BeanContext;
import io.micronaut.context.DefaultBeanContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DefaultImplementationSpec {

    @Test
    void testTheDefaultIsReplaced() {
        BeanContext ctx = BeanContext.run();
        Assertions.assertTrue(ctx.getBean(ResponseStrategy.class) instanceof CustomResponseStrategy);
        ctx.close();
    }
}
