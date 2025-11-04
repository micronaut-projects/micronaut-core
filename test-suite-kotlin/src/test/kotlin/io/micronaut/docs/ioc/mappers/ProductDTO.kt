package io.micronaut.docs.ioc.mappers

import io.micronaut.core.annotation.Introspected

// tag::class[]
@Introspected
data class ProductDTO(val name: String, val price: String, val distributor: String)
// end::class[]
