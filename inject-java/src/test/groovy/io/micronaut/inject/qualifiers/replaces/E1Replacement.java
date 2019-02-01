package io.micronaut.inject.qualifiers.replaces;

import io.micronaut.context.annotation.Replaces;

import javax.inject.Singleton;

@Singleton
@Replaces(bean = E.class, named = "E1")
public class E1Replacement implements E {
}
