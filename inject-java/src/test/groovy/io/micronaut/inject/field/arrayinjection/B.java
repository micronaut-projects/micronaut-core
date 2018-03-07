package io.micronaut.inject.field.arrayinjection;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;

public class B {
    @Inject
    private A[] all;

    List<A> getAll() {
        return Arrays.asList(this.all);
    }
}
