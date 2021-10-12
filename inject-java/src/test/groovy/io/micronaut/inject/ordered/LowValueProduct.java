package io.micronaut.inject.ordered;

import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import jakarta.inject.Singleton;

@Order(Ordered.LOWEST_PRECEDENCE)
@Singleton
public class LowValueProduct implements Product {
}
