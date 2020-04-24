package io.micronaut.docs.inject.generated;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.test.generated.IntrospectedExample;
import io.micronaut.context.BeanContext;
import org.junit.jupiter.api.Test;

public class VerifyIntrospectionSpec {

    @Test
    public void test() {
        BeanContext beanContext = BeanContext.run();

        assertTrue(BeanIntrospector.SHARED.findIntrospection(IntrospectedExample.class).isPresent());

        beanContext.stop();
    }

}
