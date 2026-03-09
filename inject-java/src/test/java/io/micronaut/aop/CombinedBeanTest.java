package io.micronaut.aop;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Java port of CombinedBeanSpec.
 */
class CombinedBeanTest {

    @Test
    void testABeanWithBothAopAndExecutableMethods() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", "CombinedBeanSpec"))) {
            try {
                Class<?> beanType = Class.forName("io.micronaut.aop.CombinedBean");
                Object bean = ctx.getBean((Class) beanType);
                assertNotNull(bean);
            } catch (ClassNotFoundException e) {
                throw new AssertionError("CombinedBean class not found at runtime", e);
            }
        }
    }
}
