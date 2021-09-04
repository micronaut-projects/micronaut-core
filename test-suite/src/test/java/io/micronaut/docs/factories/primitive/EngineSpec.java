package io.micronaut.docs.factories.primitive;

import io.micronaut.context.BeanContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EngineSpec {
    @Test
    void testEngine() {
        try (BeanContext beanContext = BeanContext.run()) {
            final V8Engine engine = beanContext.getBean(V8Engine.class);
            assertEquals(
                    8,
                    engine.getCylinders()
            );
        }
    }
}
