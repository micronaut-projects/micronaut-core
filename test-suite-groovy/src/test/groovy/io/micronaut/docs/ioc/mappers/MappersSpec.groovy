package io.micronaut.docs.ioc.mappers

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.PendingFeature
import spock.lang.Shared
import spock.lang.Specification

class MappersSpec extends Specification {
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()

    @PendingFeature(reason = "Investigate Groovy incorrect method metadata and implementing default methods broken")
    void testMappers() {
        // tag::mappers[]
        given:
        ProductMappers productMappers = context.getBean(ProductMappers.class)

        when:
        ProductDTO productDTO = productMappers.toProductDTO(new Product(
                "MacBook",
                910.50,
                "Apple"
        ))

        then:
        productDTO.name == 'MacBook'
        productDTO.price == '$1821.00'
        productDTO.distributor == "Great Product Company"
        // end::mappers[]
    }
}
