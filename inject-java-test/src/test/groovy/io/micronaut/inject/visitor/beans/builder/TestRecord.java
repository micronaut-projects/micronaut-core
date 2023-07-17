package io.micronaut.inject.visitor.beans.builder;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record TestRecord(String name, int age) {
}
