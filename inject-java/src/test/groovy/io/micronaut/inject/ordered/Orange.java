package io.micronaut.inject.ordered;

import io.micronaut.core.annotation.Order;

import javax.inject.Singleton;

@Singleton
@Order(5)
public class Orange implements Fruit {
}
