package io.micronaut.inject.autowired;

import io.micronaut.context.annotation.Autowired;
import jakarta.inject.Singleton;

@Singleton
public class Test {

    private Foo foo = new Foo("test");

    @Autowired(required = false)
    public void setFoo(Foo foo) {
        this.foo = foo;
    }
}

record Foo(String name) {}
