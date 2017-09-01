package org.particleframework.inject.failures.fieldcirculardependency;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class B {
    @Inject
    protected A a;
}
