package io.micronaut.inject.method.arrayinjection;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;

public class B {
    private List<A> all;

    @Inject
    void setA(A[] a) {
        this.all = Arrays.asList(a);
    }

    List<A> getAll() {
        return this.all;
    }
}