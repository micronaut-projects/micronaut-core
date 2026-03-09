package io.micronaut.aop.adapter.priv2;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InterceptorPrivate2SpecJ {

    @Test
    void shouldIntercept() {
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of("spec.name", InterceptorPrivate2SpecJ.class.getSimpleName()))) {
            String string = applicationContext.getBean(MyObjectJ.class).testMe();
            assertEquals("interceptor", string);
        }
    }
}
