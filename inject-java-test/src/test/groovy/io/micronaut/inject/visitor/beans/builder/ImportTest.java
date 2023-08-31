package io.micronaut.inject.visitor.beans.builder;

import io.micronaut.core.annotation.Introspected;

@Introspected(classes = TestBuildMe5.class, builder = @Introspected.IntrospectionBuilder(builderMethod = "builder"))
public class ImportTest {
}
