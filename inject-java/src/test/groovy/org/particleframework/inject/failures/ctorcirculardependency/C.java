package org.particleframework.inject.failures.ctorcirculardependency;

import javax.inject.Inject;

public class C {
    @Inject
    public C(B b ) {}
}
