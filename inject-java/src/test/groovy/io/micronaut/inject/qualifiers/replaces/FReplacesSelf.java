package io.micronaut.inject.qualifiers.replaces;

import io.micronaut.aop.simple.Mutating;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;

import javax.inject.Singleton;

@Factory
public class FReplacesSelf {

    @Mutating("name")
    @Singleton
    @Replaces(F.class)
    F getF() {
        return () -> "replaces";
    }
}
