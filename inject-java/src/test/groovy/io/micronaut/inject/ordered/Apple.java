package io.micronaut.inject.ordered;

import io.micronaut.core.annotation.Order;
import jakarta.inject.Singleton;

@Singleton
@Order(-3)
public class Apple implements Fruit {
}
