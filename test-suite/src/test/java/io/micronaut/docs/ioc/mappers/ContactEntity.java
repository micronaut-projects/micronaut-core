package io.micronaut.docs.ioc.mappers;

import io.micronaut.core.annotation.Introspected;

//tag::class[]
@Introspected
public record ContactEntity(Long id, String firstName, String lastName) {
}
//end::class[]
