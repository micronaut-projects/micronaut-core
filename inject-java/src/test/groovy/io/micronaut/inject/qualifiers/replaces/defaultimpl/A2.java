package io.micronaut.inject.qualifiers.replaces.defaultimpl;

import io.micronaut.context.annotation.Replaces;

import javax.inject.Singleton;

@Singleton
@Replaces(A.class)
public class A2 implements A {
}
