package io.micronaut.docs.ioc.mappers;

// tag::class[]
import io.micronaut.core.annotation.Introspected;

@Introspected
public record Product(
    String name,
    double price,
    String manufacturer) {
}
// end::class[]
