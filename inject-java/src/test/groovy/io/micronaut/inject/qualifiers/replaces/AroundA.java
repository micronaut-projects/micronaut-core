package io.micronaut.inject.qualifiers.replaces;

import io.micronaut.aop.simple.Mutating;

import javax.inject.Singleton;

@Mutating("name")
@Singleton
public class AroundA implements AroundOps {
    @Override
    public String test(String name) {
        return "foo " + name;
    }
}
