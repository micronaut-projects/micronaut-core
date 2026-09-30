package io.micronaut.docs.ioc.beans

// tag::class[]
import io.micronaut.core.annotation.Introspected

@Introspected
class Catalog<T> {
    List<? extends Number> prices
    List<T> items
    T[] samples
}
// end::class[]
