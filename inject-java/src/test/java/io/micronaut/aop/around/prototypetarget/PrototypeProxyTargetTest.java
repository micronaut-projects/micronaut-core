package io.micronaut.aop.around.prototypetarget;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PrototypeProxyTargetTest {

    @Test
    void prototypeScopedProxyTargetBeanResolves() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "PrototypeProxyTargetTest"))) {
            PrototypeProxyTargetBean bean = context.getBean(PrototypeProxyTargetBean.class);
            assertNotNull(bean);
            assertEquals("Name is changed via collaborator", bean.test("test"));
        }
    }

    @Test
    void eachResolutionYieldsANewBean() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "PrototypeProxyTargetTest"))) {
            assertNotNull(context.getBean(PrototypeProxyTargetBean.class));
            assertNotNull(context.getBean(PrototypeProxyTargetBean.class));
        }
    }
}
