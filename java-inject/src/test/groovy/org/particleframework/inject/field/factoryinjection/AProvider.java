package org.particleframework.inject.field.factoryinjection;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class AProvider implements Provider<A> {
    final C c;

    @Inject
    C another;


    @Inject
    public AProvider(C c) {
        this.c = c;
    }

    @Override
    public A get() {
        return new AImpl(c, another);
    }
}
