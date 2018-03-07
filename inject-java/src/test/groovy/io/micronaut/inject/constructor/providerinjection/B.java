package io.micronaut.inject.constructor.providerinjection;

import javax.inject.Inject;
import javax.inject.Provider;

public class B {
    private Provider<A> a;

    @Inject
    public B(Provider<A> a) {
        this.a = a;
    }

    public A getA() {
        return this.a.get();
    }
}
