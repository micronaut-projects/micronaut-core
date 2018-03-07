package io.micronaut.inject.constructor.arrayinjection;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;

public class B {
    private A[] all;

    @Inject
    public B(A[] all) {
        this.all = all;
    }

    public List<A> getAll() {
        return Arrays.asList(all);
    }
}
