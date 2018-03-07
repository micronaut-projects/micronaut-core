package io.micronaut.inject.field.listinjection;

import javax.inject.Inject;
import java.util.List;

public class B {
    @Inject
    private List<A> all;

    List<A> getAll() {
        return this.all;
    }
}
