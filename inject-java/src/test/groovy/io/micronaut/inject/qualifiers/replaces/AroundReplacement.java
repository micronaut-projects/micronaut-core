package io.micronaut.inject.qualifiers.replaces;

import io.micronaut.context.annotation.Replaces;

import javax.inject.Singleton;

@Singleton
@Replaces(AroundA.class)
public class AroundReplacement implements AroundOps {
    @Override
    public String test(String name) {
        return "good";
    }
}
