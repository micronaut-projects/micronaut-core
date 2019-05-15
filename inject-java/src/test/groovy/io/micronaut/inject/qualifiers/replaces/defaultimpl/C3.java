package io.micronaut.inject.qualifiers.replaces.defaultimpl;

import io.micronaut.context.annotation.Replaces;

import javax.inject.Singleton;

@Singleton
@Replaces(C2.class)
public class C3 implements C {
}
