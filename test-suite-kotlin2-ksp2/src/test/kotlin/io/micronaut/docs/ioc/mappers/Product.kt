package io.micronaut.docs.ioc.mappers

import io.micronaut.core.annotation.Introspected

// tag::class[]
@Introspected
data class Product(val name: String, val price: Double, val manufacturer: String)
// end::class[]
