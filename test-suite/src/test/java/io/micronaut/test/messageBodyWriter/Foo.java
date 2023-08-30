package io.micronaut.test.messageBodyWriter;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record Foo(String name) {
}
