package io.micronaut.inject.foreach.generic;

import jakarta.inject.Singleton;

@Singleton
public class IntegerMyReader1 implements MyReader1<Integer> {
    @Override
    public Integer read() {
        return 1;
    }
}
