package io.micronaut.docs.factories.primitive;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EngineSpec {
    @Test
    void testEngine() {
        try (ApplicationContext context = ApplicationContext.run()) {
            final V8Engine engine = context.getBean(V8Engine.class);
            assertEquals(
                    8,
                    engine.getCylinders()
            );
        }
    }
}
