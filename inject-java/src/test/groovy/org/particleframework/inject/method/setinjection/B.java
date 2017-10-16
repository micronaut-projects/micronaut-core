package org.particleframework.inject.method.setinjection;

import javax.inject.Inject;
import java.util.Set;

public class B {
    private Set<A> all;

    @Inject
    void setA(Set<A> a) {
        this.all = a;
    }

    Set<A> getAll() {
        return this.all;
    }
}
