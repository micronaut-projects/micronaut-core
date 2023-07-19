package io.micronaut.docs.ioc.mappers

// tag::class[]
import groovy.transform.Canonical
import io.micronaut.core.annotation.Introspected

@Canonical
@Introspected
class Product {
    String name
    double price
    String manufacturer
}
// end::class[]
