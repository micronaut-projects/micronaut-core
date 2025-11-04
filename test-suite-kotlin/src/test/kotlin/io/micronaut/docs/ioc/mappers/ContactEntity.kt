package io.micronaut.docs.ioc.mappers

import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.Nullable
//tag::class[]
@Introspected
data class ContactEntity(@Nullable var id: Long? = null, val firstName: String, val lastName: String)
//end::class[]
