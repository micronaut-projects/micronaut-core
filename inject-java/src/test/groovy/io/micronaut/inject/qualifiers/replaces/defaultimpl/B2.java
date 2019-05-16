package io.micronaut.inject.qualifiers.replaces.defaultimpl;

import io.micronaut.context.annotation.Replaces;

import javax.inject.Singleton;

@Singleton
@Replaces(B.class)
public class B2 implements B {
}
