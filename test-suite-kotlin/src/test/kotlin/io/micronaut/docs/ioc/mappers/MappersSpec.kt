package io.micronaut.docs.ioc.mappers

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class MappersSpec {

    @Test
    fun testMappers() {
        ApplicationContext.run().use { context ->
            // tag::mappers[]
            val productMappers = context.getBean(ProductMappers::class.java)
            val (name, price, distributor) = productMappers.toProductDTO(
                Product(
                    "MacBook",
                    910.50,
                    "Apple"
                )
            )
            Assertions.assertEquals("MacBook", name)
            Assertions.assertEquals("$1821.00", price)
            Assertions.assertEquals("Great Product Company", distributor)
            // end::mappers[]
        }
    }


    @Test
    fun testMerging() {
        ApplicationContext.run().use { context ->
            // tag::merge[]
            val mappers = context.getBean(ChristmasMappers::class.java)

            val result: ChristmasPresent = mappers.merge(
                PresentPackaging(1f, "red"),
                Present(10f, "teddy bear")
            )

            Assertions.assertEquals(11f, result.weight)
            Assertions.assertEquals("red", result.packagingColor)
            Assertions.assertEquals("teddy bear", result.type)
            Assertions.assertEquals("Merry christmas", result.greetingCard)
            // end::merge[]
        }
    }

    @Test
    fun testAdditionalMappers() {
        ApplicationContext.run().use { context ->
            // tag::additional[]
            val mappers = context.getBean(AdditionalMappers::class.java)

            var result: ChristmasPresent = mappers.merge(
                PresentPackaging(1f, "red"),
                Present(10f, "teddy bear"),
                AdditionalMappers.Card("Merry Christmas!")
            )

            Assertions.assertEquals(10f, result.weight)
            Assertions.assertNull(result.packagingColor)
            Assertions.assertEquals("teddy bear", result.type)
            Assertions.assertEquals("Merry Christmas!", result.greetingCard)

            result = mappers.update(
                result,
                mapOf(
                    Pair("packagingColor", "blue"),
                    Pair("christmasCard", "Merry Christmas!")
                )
            )

            Assertions.assertEquals(10f, result.weight)
            Assertions.assertEquals("blue", result.packagingColor)
            Assertions.assertEquals("teddy bear", result.type)
            Assertions.assertEquals("Merry Christmas!!!", result.greetingCard)

            result = mappers.mergeWithMergeStrategy(
                PresentPackaging(1f, "red"),
                Present(10f, "teddy bear")
            )

            Assertions.assertEquals(11f, result.weight)
            Assertions.assertEquals("red", result.packagingColor)
            Assertions.assertEquals("teddy bear", result.type)
            //end::additional[]
        }
    }

}
