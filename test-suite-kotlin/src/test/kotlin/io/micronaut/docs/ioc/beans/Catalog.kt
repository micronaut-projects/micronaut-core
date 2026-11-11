package io.micronaut.docs.ioc.beans

// tag::class[]
import io.micronaut.core.annotation.Introspected

@Introspected
class Catalog<T>(
    var prices: MutableList<out Number>,
    var items: MutableList<T>,
    var samples: Array<T>
)
// end::class[]
