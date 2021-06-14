package io.micronaut.inject.ordered;

import io.micronaut.core.annotation.Order;
import jakarta.inject.Singleton;

@Singleton
@Order(5)
public class Orange implements Fruit {
}
