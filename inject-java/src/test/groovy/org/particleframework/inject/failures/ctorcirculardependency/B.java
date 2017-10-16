package org.particleframework.inject.failures.ctorcirculardependency;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class B {
    @Inject
    protected A a;
}
