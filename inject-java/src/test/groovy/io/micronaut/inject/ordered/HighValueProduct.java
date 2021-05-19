package io.micronaut.inject.ordered;

import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;

import javax.inject.Singleton;

@Order(Ordered.HIGHEST_PRECEDENCE)
@Singleton
public class HighValueProduct implements Product {
}
