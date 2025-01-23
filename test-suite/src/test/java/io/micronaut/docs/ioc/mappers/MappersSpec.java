package io.micronaut.docs.ioc.mappers;

import io.micronaut.context.ApplicationContext;
import io.micronaut.docs.ioc.mappers.AdditionalMappers.Card;
import io.micronaut.docs.ioc.mappers.ChristmasTypes.ChristmasPresent;
import io.micronaut.docs.ioc.mappers.ChristmasTypes.Present;
import io.micronaut.docs.ioc.mappers.ChristmasTypes.PresentPackaging;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    void testError() {
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

    @Test
    void testMerging() {
        try (ApplicationContext context = ApplicationContext.run()) {
            // tag::merge[]
            ChristmasMappers mappers = context.getBean(ChristmasMappers.class);

            ChristmasPresent result = mappers.merge(
                new PresentPackaging(1f, "red"),
                new Present(10f, "teddy bear")
            );

            assertEquals(11f, result.weight());
            assertEquals("red", result.packagingColor());
            assertEquals("teddy bear", result.type());
            assertEquals("Merry christmas", result.greetingCard());
            // end::merge[]
        }
    }

    @Test
    void testAdditionalMappers() {
        try (ApplicationContext context = ApplicationContext.run()) {
            // tag::additional[]
            AdditionalMappers mappers = context.getBean(AdditionalMappers.class);

            ChristmasPresent result = mappers.merge(
                new PresentPackaging(1f, "red"),
                new Present(10f, "teddy bear"),
                new Card("Merry Christmas!")
            );

            assertEquals(10f, result.weight());
            assertNull(result.packagingColor());
            assertEquals("teddy bear", result.type());
            assertEquals("Merry Christmas!", result.greetingCard());

            result = mappers.update(
                result,
                Map.of(
                    "packagingColor", "blue",
                    "christmasCard", "Merry Christmas!"
                )
            );

            assertEquals(10f, result.weight());
            assertEquals("blue", result.packagingColor());
            assertEquals("teddy bear", result.type());
            assertEquals("Merry Christmas!!!", result.greetingCard());

            result = mappers.mergeWithMergeStrategy(
                new PresentPackaging(1f, "red"),
                new Present(10f, "teddy bear")
            );

            assertEquals(11f, result.weight());
            assertEquals("red", result.packagingColor());
            assertEquals("teddy bear", result.type());
            // end::additional[]
        }
    }
}
