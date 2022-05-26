package io.micronaut.inject.typed;

import io.micronaut.context.annotation.Bean;

import jakarta.inject.Singleton;

@Bean(typed = Foo1.class)
@Singleton
public class FooImpl implements Foo1, Foo2 {
}
