package io.micronaut.aop.adapter.priv3;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InterceptorPrivate3Spec {

    @Test
    void shouldIntercept() {
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of("spec.name", InterceptorPrivate3Spec.class.getSimpleName()))) {
            String string = applicationContext.getBean(MyObject.class).testMe();
            assertEquals("beaninterceptor2interceptor1", string);
        }
    }
}
