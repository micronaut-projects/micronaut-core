package org.particleframework.inject.constructor.factoryinjection;

import org.particleframework.context.annotation.Provided;

import javax.inject.Inject;

@Provided
public class AImpl implements A {
    final C c;
    final C c2;
    @Inject
    protected D d;

    public AImpl(C c, C c2) {
        this.c = c;
        this.c2 = c2;
    }
}
