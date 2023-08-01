package io.micronaut.docs.ioc.mappers;

import io.micronaut.core.annotation.Introspected;

//tag::class[]
@Introspected
public record ContactForm(String firstName, String lastName) {
}
//end::class[]
