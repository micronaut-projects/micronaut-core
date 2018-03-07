package io.micronaut.inject.qualifiers.replaces;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Replaces;

import javax.inject.Singleton;

@Replaces(A1.class)
@Singleton
public class A2 implements A {}
