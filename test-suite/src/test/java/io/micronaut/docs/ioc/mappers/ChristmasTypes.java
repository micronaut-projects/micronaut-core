package io.micronaut.docs.ioc.mappers;

import io.micronaut.core.annotation.Introspected;

public interface ChristmasTypes {

    // tag::beans[]
    @Introspected
    record ChristmasPresent(
        String packagingColor,
        String type,
        Float weight,
        String greetingCard
    ) {
    }

    @Introspected
    record PresentPackaging(
        Float weight,
        String color
    ) {
    }

    @Introspected
    record Present(
        Float weight,
        String type
    ) {
    }
    // end::beans[]

}
