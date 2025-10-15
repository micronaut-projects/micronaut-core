package io.micronaut.inject.field.samename.v2;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Requires(property = "spec.name", value = "SameFieldPackageProtectedTest")
@Singleton
public class Abc {
}
