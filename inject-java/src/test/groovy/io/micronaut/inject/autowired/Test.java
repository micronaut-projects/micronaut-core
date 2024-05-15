package io.micronaut.inject.autowired;

import io.micronaut.context.annotation.Autowired;
import jakarta.inject.Singleton;

@Singleton
public class Test {
    @Autowired(required = false)
    Foo foo = new Foo("test");
}

record Foo(String name) {}
