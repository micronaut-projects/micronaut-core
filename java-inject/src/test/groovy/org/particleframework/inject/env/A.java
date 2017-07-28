package org.particleframework.inject.env;

import org.particleframework.context.env.DefaultEnvironment;
import org.particleframework.context.env.Environment;

import javax.inject.Inject;

public class A {
    @Inject
    Environment environment;

    @Inject
    DefaultEnvironment defaultEnvironment;
}
