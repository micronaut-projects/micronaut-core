package io.micronaut.docs.inject.typed;

import io.micronaut.context.BeanContext;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// tag::class[]
@MicronautTest
public class EngineSpec {
    @Inject
    BeanContext beanContext;

    @Test
    public void testEngine() {
        assertThrows(NoSuchBeanException.class, () ->
                beanContext.getBean(V8Engine.class) // <1>
        );
        final Engine engine = beanContext.getBean(Engine.class); // <2>
        assertTrue(engine instanceof V8Engine);
    }
}
// end::class[]
