package io.micronaut.docs.ioc.mappers

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.PendingFeature
import spock.lang.Shared
import spock.lang.Specification

class MappersSpec extends Specification {
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run(["spec.name": "MappersSpec"])

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

    void testMerging() {
        // tag::merge[]
        given:
        ChristmasMappers mappers = context.getBean(ChristmasMappers.class)

        when:
        ChristmasPresent result = mappers.merge(
                new PresentPackaging(1f, "red"),
                new Present(10f, "teddy bear")
        )

        then:
        11 == result.weight
        "red" == result.packagingColor
        "teddy bear" == result.type
        "Merry christmas" == result.greetingCard
        // end::merge[]
    }

    void testAdditionalMappers() {
        // tag::additional[]
        given:
        AdditionalMappers mappers = context.getBean(AdditionalMappers.class)

        when:
        ChristmasPresent result = mappers.merge(
                new PresentPackaging(1f, "red"),
                new Present(10f, "teddy bear"),
                new AdditionalMappers.Card("Merry Christmas!")
        )

        then:
        10 == result.weight
        result.packagingColor == null
        "teddy bear" == result.type
        "Merry Christmas!" == result.greetingCard

        when:
        result = mappers.update(
                result,
                [
                        "packagingColor": "blue",
                        "christmasCard": "Merry Christmas!"
                ]
        )

        then:
        10 == result.weight;
        result.packagingColor == "blue"
        result.type == "teddy bear"
        result.greetingCard == "Merry Christmas!!!"

        when:
        result = mappers.mergeWithMergeStrategy(
                new PresentPackaging(1, "red"),
                new Present(10, "teddy bear")
        )

        then:
        result.weight == 11
        result.packagingColor == "red"
        result.type == "teddy bear"
        // end::additional[]
    }
}
