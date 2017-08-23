package org.particleframework.inject.qualifiers.primary;

import javax.inject.Inject;
import java.util.List;

public class B {
    @Inject
    List<A> all;

    @Inject
    A a;

    public List<A> getAll() {
        return all;
    }

    public A getA() {
        return a;
    }
}
