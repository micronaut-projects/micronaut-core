package io.micronaut.inject.qualifiers.replaces.defaultimpl;

import io.micronaut.context.annotation.Replaces;

import javax.inject.Singleton;

@Singleton
@Replaces(E.class)
public class E2 implements E {
}
