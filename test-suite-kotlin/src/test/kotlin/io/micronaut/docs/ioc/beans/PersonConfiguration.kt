package io.micronaut.docs.ioc.beans

// tag::class[]
import io.micronaut.core.annotation.Introspected

@Introspected(classes = [Person::class])
class PersonConfiguration
// end::class[]
