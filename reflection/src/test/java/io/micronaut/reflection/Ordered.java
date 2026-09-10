package io.micronaut.reflection;

import io.micronaut.core.annotation.Order;
import jakarta.inject.Singleton;

/**
 * A bean whose class declares its order.
 */
@Singleton
@Order(42)
public class Ordered {
}
