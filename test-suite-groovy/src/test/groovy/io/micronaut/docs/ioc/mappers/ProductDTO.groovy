package io.micronaut.docs.ioc.mappers

import groovy.transform.Canonical;

// tag::class[]
import io.micronaut.core.annotation.Introspected;

@Introspected
@Canonical
class ProductDTO {
    String name
    String price
    String distributor
}
// end::class[]
