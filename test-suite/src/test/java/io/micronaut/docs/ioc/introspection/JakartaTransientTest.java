package io.micronaut.docs.ioc.introspection;

import io.micronaut.core.beans.BeanIntrospection;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JakartaTransientTest {
    @Test
    void testIntrospectionWithJakartaTransient() {
        BeanIntrospection<ObjectWithJakartaTransient> introspection = BeanIntrospection.getIntrospection(ObjectWithJakartaTransient.class);
        assertTrue(introspection.getProperty("tmp").isPresent());
    }
}
