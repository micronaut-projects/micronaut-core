package io.micronaut.inject.qualifiers.stereotype;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Requires(property = "spec.name", value = "StereotypeQualifierSpec")
@MyStereotype
@Singleton
public class MyBean {
}
