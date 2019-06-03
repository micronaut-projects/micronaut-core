package io.micronaut.inject.qualifiers.replaces;

import io.micronaut.aop.simple.Mutating;

@Mutating("name")
public class HImpl implements H {

    @Override
    public String test(String name) {
        return "default " + name;
    }
}
