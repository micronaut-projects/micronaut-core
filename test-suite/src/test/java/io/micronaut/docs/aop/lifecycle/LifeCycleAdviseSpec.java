package io.micronaut.docs.aop.lifecycle;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class LifeCycleAdviseSpec {

    @Test
    void testLifeCycleAdvise() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            // tag::test[]
            final ProductService productService = applicationContext.getBean(ProductService.class);

            Product product = applicationContext.createBean(Product.class, "Apple"); // <1>
            assertTrue(product.isActive());
            assertTrue(productService.findProduct("APPLE").isPresent());

            applicationContext.destroyBean(product); // <2>
            assertFalse(product.isActive());
            assertFalse(productService.findProduct("APPLE").isPresent());
            // end::test[]
        }
    }
}
