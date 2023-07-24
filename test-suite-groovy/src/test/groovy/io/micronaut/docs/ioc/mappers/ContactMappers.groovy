package io.micronaut.docs.ioc.mappers

//tag::imports[]
import io.micronaut.context.annotation.Mapper
//end::imports[]
import io.micronaut.context.annotation.Requires

@Requires(property = "spec.name", value = "SimpleMapperSpec")
//tag::class[]
interface ContactMappers {
    @Mapper
    ContactEntity toEntity(ContactForm contactForm)
}
//end::class[]
