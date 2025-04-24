package io.micronaut.context;

import io.micronaut.context.annotation.Secondary;
import io.micronaut.context.exceptions.NonUniqueBeanException;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import spock.lang.Specification;

class DefaultBeanContextTest extends Specification {
    @Test
    void testMultipleSecondaryBeans() {
        try (DefaultBeanContext beanContext = new DefaultBeanContext()) {
            beanContext.configure();
            NonUniqueBeanException e = Assertions.assertThrows(
                NonUniqueBeanException.class,
                () -> beanContext.getBean(Foo.class)
            );
            Assertions.assertTrue(
                "Multiple possible bean candidates found: [Foo1, Foo2]".equals(e.getMessage()) ||
                    "Multiple possible bean candidates found: [Foo2, Foo1]".equals(e.getMessage()),
                "Exception message was incorrect. Expected \"Multiple possible bean candidates found: [Foo1, Foo2]\"; got " + e.getMessage()
            );
        }
    }
    // classes used by testMultipleSecondaryBeans
    interface Foo {}
    @Singleton
    @Secondary
    static class Foo1 implements Foo {}
    @Singleton
    @Secondary
    static class Foo2 implements Foo {}
}
