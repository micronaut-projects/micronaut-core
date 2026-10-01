package io.micronaut.reflection;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * A bean the class annotations condition on a property.
 */
@Singleton
@Requires(property = "conditional.enabled", value = "true")
public class Conditional {
}
