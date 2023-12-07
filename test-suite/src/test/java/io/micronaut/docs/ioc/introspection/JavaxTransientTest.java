package io.micronaut.docs.ioc.introspection;

import io.micronaut.core.beans.BeanIntrospection;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaxTransientTest {
    @Test
    void testIntrospectionWithJavaxTransient() {
        BeanIntrospection<ObjectWithJavaxTransient> introspection = BeanIntrospection.getIntrospection(ObjectWithJavaxTransient.class);
        assertTrue(introspection.getProperty("tmp").isPresent());
    }
}
