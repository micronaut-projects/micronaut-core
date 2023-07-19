package io.micronaut.docs.ioc.mappers;

// tag::class[]
import io.micronaut.core.annotation.Introspected;

@Introspected
public record ProductDTO(String name, String price, String distributor) {
}
// end::class[]
