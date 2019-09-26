package io.micronaut.docs.inject.generated;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.context.BeanContext;
import org.junit.jupiter.api.Test;

public class VerifyDefinitionInjectionSpec {

    @Test
    public void test() {
        BeanContext beanContext = BeanContext.run();

        MainBean mainBean = beanContext.getBean(MainBean.class);

        assertTrue(mainBean.check());

        beanContext.stop();
    }

}
