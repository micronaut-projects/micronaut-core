package io.micronaut.inject.constructor.collectioninjection;

import javax.inject.Inject;
import java.util.Collection;

public class B {
    private Collection<A> all;

    @Inject
    public B(Collection<A> all) {
        this.all = all;
    }

    public Collection<A> getAll() {
        return all;
    }
}
