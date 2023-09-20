package io.micronaut.http.bench;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record Person(
    String name,
    int age
) {
}
