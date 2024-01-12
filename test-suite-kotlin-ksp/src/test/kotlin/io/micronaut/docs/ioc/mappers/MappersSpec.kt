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
}
