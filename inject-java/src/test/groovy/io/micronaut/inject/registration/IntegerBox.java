package io.micronaut.inject.registration;

import io.micronaut.context.annotation.Prototype;

@Prototype
public class IntegerBox implements Box<Integer> {

    @Override
    public Integer value() {
        return 1;
    }
}
