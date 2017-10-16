package org.particleframework.inject.field.streaminjection;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class B {
    @Inject
    private Stream<A> all;

    @Inject
    protected Stream<A> another;

    @Inject
    protected Stream<A> another2;

    private List<A> allList;

    List<A> getAll() {
        if(allList == null) {
            allList = this.all.collect(Collectors.toList());
        }
        return allList;
    }
}
