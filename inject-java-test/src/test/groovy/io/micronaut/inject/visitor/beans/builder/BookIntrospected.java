package io.micronaut.inject.visitor.beans.builder;

import io.micronaut.core.annotation.Introspected;

@Introspected(classes = Book.class, builder = @Introspected.IntrospectionBuilder(builderClass = Book.Builder.class))
public class BookIntrospected {
}
