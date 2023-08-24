package io.micronaut.docs.ioc.mappers;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MappersSpec {
    @Test
    void testMappers() {
        try (ApplicationContext context = ApplicationContext.run()) {
            // tag::mappers[]
            ProductMappers productMappers = context.getBean(ProductMappers.class);

            ProductDTO productDTO = productMappers.toProductDTO(new Product(
                "MacBook",
                910.50,
                "Apple"
            ));

            assertEquals("MacBook", productDTO.name());
            assertEquals("$1821.00", productDTO.price());
            assertEquals("Great Product Company", productDTO.distributor());
            // end::mappers[]
        }
    }

    @Test
    void tetError() {
        try (ApplicationContext context = ApplicationContext.run()) {
            ProductMappers2 productMappers = context.getBean(ProductMappers2.class);

            Assertions.assertThrows(IllegalArgumentException.class, () ->
                productMappers.toProductDTO(new Product(
                    "MacBook",
                    910.50,
                    "Apple"
                ))
            );
        }
    }
}
