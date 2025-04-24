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
            Assertions.assertThrows(
                NonUniqueBeanException.class,
                () -> beanContext.getBean(Foo.class)
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
