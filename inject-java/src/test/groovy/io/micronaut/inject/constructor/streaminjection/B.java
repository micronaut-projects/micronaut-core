package io.micronaut.inject.constructor.streaminjection;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class B {
    private Stream<A> all;

    @Inject
    public B(Stream<A> all) {
        this.all = all;
    }

    private List<A> allList;

    List<A> getAll() {
        if(allList == null) {
            allList = this.all.collect(Collectors.toList());
        }
        return allList;
    }
}
