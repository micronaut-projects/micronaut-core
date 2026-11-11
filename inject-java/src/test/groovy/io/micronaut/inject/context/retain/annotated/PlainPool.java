package io.micronaut.inject.context.retain.annotated;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "RetainAnnotationSpec")
public class PlainPool {
}
