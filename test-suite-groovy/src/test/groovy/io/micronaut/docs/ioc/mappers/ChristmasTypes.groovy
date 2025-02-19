package io.micronaut.docs.ioc.mappers

import groovy.transform.Canonical;
import io.micronaut.core.annotation.Introspected;

// tag::beans[]
@Canonical
@Introspected
class ChristmasPresent {
    String packagingColor
    String type
    Float weight
    String greetingCard
}

@Canonical
@Introspected
class PresentPackaging {
    Float weight
    String color
}

@Canonical
@Introspected
class Present {
    Float weight
    String type
}
// end::beans[]

