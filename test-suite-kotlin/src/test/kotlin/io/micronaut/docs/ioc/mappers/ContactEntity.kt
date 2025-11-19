package io.micronaut.docs.ioc.mappers

import io.micronaut.core.annotation.Introspected
//tag::class[]
@Introspected
data class ContactEntity(var id: Long? = null, val firstName: String, val lastName: String)
//end::class[]
