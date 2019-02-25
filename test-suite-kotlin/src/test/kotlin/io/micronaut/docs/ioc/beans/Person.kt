package io.micronaut.docs.ioc.beans

// tag::imports[]
import io.micronaut.core.annotation.Introspected
// end::imports[]

// tag::class[]
@Introspected
data class Person(var name : String)
// end::class[]