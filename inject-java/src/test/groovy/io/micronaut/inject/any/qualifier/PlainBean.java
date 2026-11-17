package io.micronaut.inject.any.qualifier;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "AnyQualifierSpec")
public class PlainBean {
}
