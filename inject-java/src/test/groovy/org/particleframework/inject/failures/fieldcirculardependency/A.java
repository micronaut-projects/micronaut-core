package org.particleframework.inject.failures.fieldcirculardependency;

import javax.inject.Singleton;

@Singleton
public class A {
    public A(C c) {}
}
