package io.micronaut.inject.env;

import io.micronaut.context.env.DefaultEnvironment;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.DefaultEnvironment;
import io.micronaut.context.env.Environment;

import javax.inject.Inject;

public class A {
    @Inject
    Environment environment;

    @Inject
    DefaultEnvironment defaultEnvironment;
}
