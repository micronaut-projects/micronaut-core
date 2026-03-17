package io.micronaut.inject.field.samename.v1;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Requires(property = "spec.name", value = "SameFieldProtectedTest")
@Singleton
public class Abc {
}
