package io.micronaut.docs.ioc.mappers

import io.micronaut.core.annotation.Introspected

//tag::class[]
@Introspected
data class ContactForm(val firstName: String, val lastName: String)
//end::class[]
