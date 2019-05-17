package io.micronaut.inject.qualifiers.replaces.defaultimpl;

import io.micronaut.context.annotation.Replaces;

import javax.inject.Singleton;

@Singleton
@Replaces(F.class)
public class F2 implements F {
}
