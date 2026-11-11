package io.micronaut.docs.server.asyncbody;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record Person(String name, int age) {
}
