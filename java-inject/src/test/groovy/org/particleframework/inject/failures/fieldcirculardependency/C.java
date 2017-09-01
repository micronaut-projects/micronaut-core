package org.particleframework.inject.failures.fieldcirculardependency;

import javax.inject.Inject;

public class C {
    @Inject
    protected B b;
}
