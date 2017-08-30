package org.particleframework.inject.qualifiers.primary;

import javax.inject.Inject;
import java.util.List;

public class B {
    @Inject
    private List<A> all;

    @Inject
    private A a;

    public List<A> getAll() {
        return all;
    }

    public void setAll(List<A> all) {
        this.all = all;
    }

    public A getA() {
        return a;
    }

    public void setA(A a) {
        this.a = a;
    }
}
