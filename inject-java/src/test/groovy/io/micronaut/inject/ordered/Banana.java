package io.micronaut.inject.ordered;

import io.micronaut.core.annotation.Order;

import javax.inject.Singleton;

@Singleton
@Order(-3)
public class Banana implements Fruit {
}
