package io.micronaut.aop.adapter.priv1;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InterceptorPrivate1Spec {

    @Test
    void shouldIntercept() {
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of("spec.name", InterceptorPrivate1Spec.class.getSimpleName()))) {
            String string = applicationContext.getBean(MyObject.class).testMe();
            assertEquals("interceptor", string);
        }
    }
}
