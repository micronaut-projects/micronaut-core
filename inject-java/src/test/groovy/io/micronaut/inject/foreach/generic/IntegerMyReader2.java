package io.micronaut.inject.foreach.generic;

import jakarta.inject.Singleton;

@Singleton
public class IntegerMyReader2 implements MyReader2<Integer> {
    @Override
    public Integer read() {
        return 1;
    }
}
