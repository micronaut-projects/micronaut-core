package io.micronaut.inject.field.setinjection;

import javax.inject.Inject;
import java.util.Set;

public class B {
    @Inject
    private Set<A> all;

    Set<A> getAll() {
        return this.all;
    }
}
